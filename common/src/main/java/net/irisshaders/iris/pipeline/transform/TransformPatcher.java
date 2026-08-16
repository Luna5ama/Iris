package net.irisshaders.iris.pipeline.transform;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.ast.data.TypedTreeCache;
import io.github.douira.glsl_transformer.ast.node.Profile;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.VersionStatement;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.NumberedSourceLocation;
import io.github.douira.glsl_transformer.ast.transform.TransformationException;
import io.github.douira.glsl_transformer.parser.ParsingException;
import io.github.douira.glsl_transformer.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.token_filter.TokenChannel;
import io.github.douira.glsl_transformer.token_filter.TokenFilter;
import io.github.douira.glsl_transformer.util.LRUCache;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisLimits;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.parameter.ComputeParameters;
import net.irisshaders.iris.pipeline.transform.parameter.DHParameters;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;
import net.irisshaders.iris.pipeline.transform.parameter.TextureStageParameters;
import net.irisshaders.iris.pipeline.transform.parameter.VanillaParameters;
import net.irisshaders.iris.pipeline.transform.transformer.CommonTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.CompatibilityTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.CompositeCoreTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.CompositeTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.DHGenericTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.DHTerrainTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.LayoutTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.SodiumCoreTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.SodiumTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.TextureTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.VanillaCoreTransformer;
import net.irisshaders.iris.pipeline.transform.transformer.VanillaTransformer;
import net.irisshaders.iris.shaderpack.include.ShaderSourceMap;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import org.antlr.v4.runtime.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.SoftReference;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The transform patcher (triforce 2) uses glsl-transformer's ASTTransformer to
 * do shader transformation.
 * <p>
 * The TransformPatcher does caching on the source string and associated
 * parameters. For this to work, all objects contained in a parameter must have
 * an equals method and they must never be changed after having been used for
 * patching. Since the cache also contains the source string, it doesn't need to
 * be disabled when developing shaderpacks. However, when changes are made to
 * the patcher, the cache should be disabled with {@link #useCache}.
 * <p>
 * NOTE: This patcher expects (and ensures) that the string doesn't contain any
 * (!) unparsed preprocessor directives. The allowed directives are #extension,
 * #pragma, and Iris-generated #line directives carrying source locations. If any
 * other directive appears in the string, it will throw.
 */
public class TransformPatcher {
	// TODO: Only do the NewLines patches if the source code isn't from
	// gbuffers_lines (what does this mean?)
	static final TokenFilter<Parameters> parseTokenFilter = new ChannelFilter<>(TokenChannel.PREPROCESSOR) {
		@Override
		public boolean isTokenAllowed(Token token) {
			if (!super.isTokenAllowed(token)) {
				if (token.getType() == GLSLLexer.NR_LINE) {
					return true;
				}
				throw new IllegalArgumentException("Unparsed preprocessor directives such as '" + token.getText()
					+ "' may not be present at this stage of shader processing!");
			}
			return true;
		}
	};
	private static final boolean useCache = true;
	private static final Map<CacheKey, SoftReference<Map<PatchShaderType, String>>> cache = new LRUCache<>(256);
	private static final Cache<String, HashCode> SOURCE_HASH_CACHE = CacheBuilder.newBuilder()
		.maximumSize(2048)
		.weakKeys()
		.build();
	private static final List<String> internalPrefixes = List.of("iris_", "irisMain", "moj_import");
	private static final int IRIS_GENERATED_SOURCE_ID = 1_000_000_000;
	private static final String IRIS_GENERATED_SOURCE_NAME = "<Iris-generated shader code>";
	private static final NumberedSourceLocation IRIS_GENERATED_SOURCE_LOCATION =
		new NumberedSourceLocation(0, 1, IRIS_GENERATED_SOURCE_ID);
	private static final Pattern versionPattern = Pattern.compile("#version\\s+(\\d+)", Pattern.DOTALL);
	private static final EnumASTTransformer<Parameters, PatchShaderType> transformer;
	static Logger LOGGER = LogManager.getLogger(TransformPatcher.class);

	static {
		transformer = new EnumASTTransformer<>(PatchShaderType.class) {
			{
				setRootSupplier(RootSupplier.EXACT_UNORDERED);
				setParsingCacheStrategy(ParsingCacheStrategy.TWO_TIER);
				setParseLineDirectives(true);
			}

			@Override
			public TranslationUnit parseTranslationUnit(Root rootInstance, String input) {
				// parse #version directive using an efficient regex before parsing so that the
				// parser can be set to the correct version
				Matcher matcher = versionPattern.matcher(input);
				if (!matcher.find()) {
					throw new IllegalArgumentException(
						"No #version directive found in source code! See debugging.md for more information.");
				}
				transformer.getLexer().version = Version.fromNumber(Integer.parseInt(matcher.group(1)));

				return super.parseTranslationUnit(rootInstance, input);
			}
		};
		transformer.setTransformation((trees, parameters) -> {
			for (PatchShaderType type : PatchShaderType.values()) {
				TranslationUnit tree = trees.get(type);
				if (tree == null) {
					continue;
				}
				tree.outputOptions.enablePrintInfo();

				parameters.type = type;
				Root root = tree.getRoot();

				// check for illegal references to internal Iris shader interfaces
				String internalIdentifier = findInternalIdentifier(root);
				if (internalIdentifier != null) {
					throw new IllegalArgumentException(
						"Detected a potential reference to unstable and internal Iris shader interfaces (iris_, irisMain and moj_import). This isn't currently supported. Violation: "
							+ internalIdentifier + ". See debugging.md for more information.");
				}

				root.indexBuildSession(() -> {
					VersionStatement versionStatement = tree.getVersionStatement();
					if (versionStatement == null) {
						throw new IllegalStateException("Missing the version statement!");
					}
					Profile profile = versionStatement.profile;
					Version version = versionStatement.version;
					if (Objects.requireNonNull(parameters.patch) == Patch.COMPUTE) {// we can assume the version is at least 400 because it's a compute shader
						versionStatement.profile = Profile.CORE;
						CommonTransformer.transform(transformer, tree, root, parameters, true);
					} else {// handling of Optifine's special core profile mode
						boolean isLine = (parameters.patch == Patch.VANILLA && ((VanillaParameters) parameters).isLines());

						if (profile == Profile.CORE || version.number >= 150 && profile == null || isLine) {
							// patch the version number to at least 330
							if (version.number < 330) {
								versionStatement.version = Version.GLSL33;
							}

							switch (parameters.patch) {
								case COMPOSITE:
									CompositeCoreTransformer.transform(transformer, tree, root, parameters);
									break;
								case SODIUM:
									SodiumParameters sodiumParameters = (SodiumParameters) parameters;
									SodiumCoreTransformer.transform(transformer, tree, root, sodiumParameters);
									break;
								case VANILLA:
									VanillaCoreTransformer.transform(transformer, tree, root, (VanillaParameters) parameters);
									break;
								default:
									throw new UnsupportedOperationException("Unknown patch type: " + parameters.patch);
							}

							if (parameters.type == PatchShaderType.FRAGMENT) {
								CompatibilityTransformer.transformFragmentCore(transformer, tree, root, parameters);
							}
						} else {
							// patch the version number to at least 330
							if (version.number < 330) {
								versionStatement.version = Version.GLSL33;
							}
							versionStatement.profile = Profile.CORE;

							switch (parameters.patch) {
								case COMPOSITE:
									CompositeTransformer.transform(transformer, tree, root, parameters);
									break;
								case SODIUM:
									SodiumParameters sodiumParameters = (SodiumParameters) parameters;
									SodiumTransformer.transform(transformer, tree, root, sodiumParameters);
									break;
								case VANILLA:
									VanillaTransformer.transform(transformer, tree, root, (VanillaParameters) parameters);
									break;
								case DH_TERRAIN:
									DHTerrainTransformer.transform(transformer, tree, root, parameters);
									break;
								case DH_GENERIC:
									DHGenericTransformer.transform(transformer, tree, root, parameters);
									break;
								default:
									throw new UnsupportedOperationException("Unknown patch type: " + parameters.patch);
							}
						}
					}
					TextureTransformer.transform(transformer, tree, root,
						parameters.getTextureStage(), parameters.getTextureMap());
					CompatibilityTransformer.transformEach(transformer, tree, root, parameters);
				});
			}

			// the compatibility transformer does a grouped transformation
			CompatibilityTransformer.transformGrouped(transformer, trees, parameters);

			if (IrisLimits.VK_CONFORMANCE) {
				LayoutTransformer.transformGrouped(transformer, trees, parameters);
			}

			for (TranslationUnit tree : trees.values()) {
				if (tree != null) {
					markGeneratedSourceLocations(tree);
				}
			}
		});
		transformer.setTokenFilter(parseTokenFilter);
	}

	private static Map<PatchShaderType, String> transformInternal(
		String name,
		Map<PatchShaderType, String> inputs,
		Parameters parameters) {
		try {
			// set shader name
			parameters.name = name;
			EnumMap<PatchShaderType, ShaderSourceMap> sourceMaps = new EnumMap<>(PatchShaderType.class);
			inputs.forEach((type, source) -> {
				if (source != null) {
					sourceMaps.put(type, ShaderSourceMap.parse(source)
						.withSourcePath(IRIS_GENERATED_SOURCE_ID, IRIS_GENERATED_SOURCE_NAME));
				}
			});
			Map<PatchShaderType, String> transformed = transformer.transform(inputs, parameters);
			appendSourceMapMetadata(transformed, sourceMaps);
			return transformed;
		} catch (TransformationException | ParsingException | IllegalStateException | IllegalArgumentException e) {
			// print the offending programs and rethrow to stop the loading process
			ShaderPrinter.printProgram("errored_" + name).addSources(inputs).print();
			throw new ShaderCompileException(name, e);
		}
	}

	private static boolean hasAnyPrefix(String value, List<String> prefixes) {
		for (String prefix : prefixes) {
			if (value.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	static String findInternalIdentifier(Root root) {
		for (String identifier : root.identifierIndex.index.keySet()) {
			if (hasAnyPrefix(identifier, internalPrefixes)) {
				return identifier;
			}
		}
		return null;
	}

	public static void clearCaches() {
		cache.clear();
		clearParsingCaches();
	}

	public static void clearParsingCaches() {
		transformer.setParsingCacheStrategy(EnumASTTransformer.ParsingCacheStrategy.TWO_TIER);
		transformer.setBuildCache(new TypedTreeCache<>());
		transformer.setTokenFilter(parseTokenFilter);
	}

	static void appendSourceMapMetadata(Map<PatchShaderType, String> transformed,
										Map<PatchShaderType, ShaderSourceMap> sourceMaps) {
		transformed.replaceAll((type, source) -> source == null
			? null
			: sourceMaps.get(type).appendMetadataTo(source));
	}

	static void markGeneratedSourceLocations(TranslationUnit tree) {
		for (var entry : tree.getRoot().nodeIndex.index.entrySet()) {
			Class<ASTNode> nodeType = entry.getKey();
			if (!ExternalDeclaration.class.isAssignableFrom(nodeType)
				&& !Statement.class.isAssignableFrom(nodeType)) {
				continue;
			}
			for (ASTNode node : entry.getValue()) {
				if (node.getSourceLocation() == null || !node.getSourceLocation().canPrint()) {
					node.setSourceLocation(IRIS_GENERATED_SOURCE_LOCATION);
				}
			}
		}
	}

	private static Map<PatchShaderType, String> transform(String name, String vertex, String geometry, String tessControl, String tessEval, String fragment,
														  Parameters parameters) {
		// stop if all are null
		if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
			return null;
		}

		// check if this has been cached
		CacheKey key;
		Map<PatchShaderType, String> result = null;
		if (useCache) {
			key = new CacheKey(parameters, vertex, geometry, tessControl, tessEval, fragment);
			var attempt = cache.get(key);
			var attemptResult = attempt != null ? attempt.get() : null;
			if (attemptResult != null) {
				result = attemptResult;
			}
		}

		// if there is no cache result, transform the shaders
		if (result == null) {
			transformer.setPrintType(Iris.getIrisConfig().areDebugOptionsEnabled() ? PrintType.INDENTED_ANNOTATED : PrintType.SIMPLE_ANNOTATED);
			EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
			inputs.put(PatchShaderType.VERTEX, vertex);
			inputs.put(PatchShaderType.GEOMETRY, geometry);
			inputs.put(PatchShaderType.TESS_CONTROL, tessControl);
			inputs.put(PatchShaderType.TESS_EVAL, tessEval);
			inputs.put(PatchShaderType.FRAGMENT, fragment);

			result = transformInternal(name, inputs, parameters);
			if (useCache) {
				cache.put(key, new SoftReference<>(result));
			}
		}
		return result;
	}

	private static Map<PatchShaderType, String> transformCompute(String name, String compute, Parameters parameters) {
		// stop if all are null
		if (compute == null) {
			return null;
		}

		// check if this has been cached
		CacheKey key;
		Map<PatchShaderType, String> result = null;
		if (useCache) {
			key = new CacheKey(parameters, compute);
			var attempt = cache.get(key);
			var attemptResult = attempt != null ? attempt.get() : null;
			if (attemptResult != null) {
				result = attemptResult;
			}
		}

		// if there is no cache result, transform the shaders
		if (result == null) {
			transformer.setPrintType(Iris.getIrisConfig().areDebugOptionsEnabled() ? PrintType.INDENTED_ANNOTATED : PrintType.SIMPLE_ANNOTATED);
			EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
			inputs.put(PatchShaderType.COMPUTE, compute);

			result = transformInternal(name, inputs, parameters);
			if (useCache) {
				cache.put(key, new SoftReference<>(result));
			}
		}
		return result;
	}

	public static Map<PatchShaderType, String> patchVanilla(
		String name, String vertex, String geometry, String tessControl, String tessEval, String fragment,
		AlphaTest alpha, boolean isLines, boolean isClouds,
		boolean hasChunkOffset,
		ShaderAttributeInputs inputs,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return transform(name, vertex, geometry, tessControl, tessEval, fragment,
			new VanillaParameters(Patch.VANILLA, textureMap, alpha, isLines, isClouds, hasChunkOffset, inputs, geometry != null, tessControl != null || tessEval != null));
	}


	public static Map<PatchShaderType, String> patchDHTerrain(
		String name, String vertex, String tessControl, String tessEval, String geometry, String fragment,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return transform(name, vertex, geometry, tessControl, tessEval, fragment,
			new DHParameters(Patch.DH_TERRAIN, textureMap));
	}


	public static Map<PatchShaderType, String> patchDHGeneric(
		String name, String vertex, String tessControl, String tessEval, String geometry, String fragment,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return transform(name, vertex, geometry, tessControl, tessEval, fragment,
			new DHParameters(Patch.DH_GENERIC, textureMap));

	}

	public static Map<PatchShaderType, String> patchSodium(String name, String vertex, String geometry, String tessControl, String tessEval, String fragment,
														   AlphaTest alpha,
														   Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return transform(name, vertex, geometry, tessControl, tessEval, fragment,
			new SodiumParameters(Patch.SODIUM, textureMap, alpha));
	}

	public static Map<PatchShaderType, String> patchComposite(
		String name, String vertex, String geometry, String fragment,
		TextureStage stage,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return transform(name, vertex, geometry, null, null, fragment, new TextureStageParameters(Patch.COMPOSITE, stage, textureMap));
	}

	public static String patchCompute(
		String name, String compute,
		TextureStage stage,
		Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		return transformCompute(name, compute, new ComputeParameters(Patch.COMPUTE, stage, textureMap))
			.getOrDefault(PatchShaderType.COMPUTE, null);
	}

	private static class CacheKey {
		final Parameters parameters;
		final HashCode sourceHash;

		public CacheKey(Parameters parameters, String vertex, String geometry, String tessControl, String tessEval, String fragment) {
			this.parameters = parameters;
			this.sourceHash = sourceHash(vertex, geometry, tessControl, tessEval, fragment);
		}

		public CacheKey(Parameters parameters, String compute) {
			this.parameters = parameters;
			this.sourceHash = sourceHash(compute);
		}

		@Override
		public int hashCode() {
			return Objects.hash(parameters, sourceHash);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			CacheKey other = (CacheKey) obj;
			return Objects.equals(parameters, other.parameters) && sourceHash.equals(other.sourceHash);
		}

		private static HashCode sourceHash(String... sources) {
			Hasher hasher = Hashing.sha256().newHasher();
			hasher.putInt(sources.length);
			for (String source : sources) {
				if (source == null) {
					hasher.putInt(-1);
				} else {
					hasher.putInt(source.length());
					hasher.putBytes(hashSource(source).asBytes());
				}
			}
			return hasher.hash();
		}

		private static HashCode hashSource(String source) {
			HashCode cached = SOURCE_HASH_CACHE.getIfPresent(source);
			if (cached != null) {
				return cached;
			}
			HashCode hash = Hashing.sha256().hashUnencodedChars(source);
			SOURCE_HASH_CACHE.put(source, hash);
			return hash;
		}
	}
}
