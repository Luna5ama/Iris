package net.irisshaders.iris.shaderpack.preprocessor;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.IncludedSource;
import net.irisshaders.iris.shaderpack.include.ShaderSourceMap;
import net.irisshaders.iris.shaderpack.include.SourceLine;
import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class JcppProcessor {
	private static final ConcurrentHashMap<CacheKey, SoftReference<String>> CACHE = new ConcurrentHashMap<>();

	// Derived from GlShader from Canvas, licenced under LGPL
	public static String glslPreprocessSource(String source, List<StringPair> environmentDefines) {
		return glslPreprocessSource(source, ImmutableOrigins.EMPTY, environmentDefines);
	}

	public static String glslPreprocessSource(IncludedSource source, List<StringPair> environmentDefines) {
		return glslPreprocessSource(source.text(), source.lines(), environmentDefines);
	}

	private static String glslPreprocessSource(String source, List<SourceLine> origins, List<StringPair> environmentDefines) {
		if (CACHE.size() > 1024) {
			CACHE.clear();
			System.out.println("JCPP cache cleared");
		}

		var key = new CacheKey(
			Hashing.sha512().hashString(source, StandardCharsets.UTF_8),
			hashOrigins(origins),
			environmentDefines
		);

		return CACHE.compute(key, (k, v) -> {
			String cached = v == null ? null : v.get();
			if (cached != null) {
				return v;
			} else {
				String processed = glslPreprocessSourceUncached(source, origins, environmentDefines);
				return new SoftReference<>(processed);
			}
		}).get();
	}

	// Derived from GlShader from Canvas, licenced under LGPL
	private static String glslPreprocessSourceUncached(String source, List<SourceLine> origins, List<StringPair> environmentDefines) {
		if (source.contains(GlslCollectingListener.VERSION_MARKER)
			|| source.contains(GlslCollectingListener.EXTENSION_MARKER)
			|| ShaderSourceMap.containsReservedMarker(source)) {
			throw new RuntimeException("Some shader author is trying to exploit internal Iris implementation details, stop!");
		}

		// Note: This is an absolutely awful hack. But JCPP's lack of extensibility leaves me with no choice...
		//       We should write our own preprocessor at some point to avoid this.
		//
		// Why are we doing this awful hack instead of just using the preprocessor like a normal person? Because it lets
		// us only hoist #extension directives if they're actually used. This is needed for shader packs written on
		// lenient drivers that allow #extension directives to be placed anywhere to work on strict drivers like Mesa
		// that require #extension directives to occur at the top.
		//
		// TODO: This allows #version to not appear as the first non-comment non-whitespace thing in the file.
		//       That's not the behavior we want. If you're reading this, don't rely on this behavior.
		source = source.replace("#version", GlslCollectingListener.VERSION_MARKER);
		source = source.replace("#extension", GlslCollectingListener.EXTENSION_MARKER);

		// Remove null characters. Some packs, such as Chocapic High Performance, have random null characters that trip up JCPP.
		source = source.replace("\u0000", "");

		GlslCollectingListener listener = new GlslCollectingListener();

		@SuppressWarnings("resource") final Preprocessor pp = new Preprocessor();

		// Add the values of the environment defines without actually modifying the source code
		// of the shader program, one step down the road of having accurate line number reporting
		// in errors...
		try {
			for (StringPair envDefine : environmentDefines) {
				pp.addMacro(envDefine.key(), envDefine.value());
			}
		} catch (LexerException e) {
			throw new RuntimeException("Unexpected LexerException processing macros", e);
		}

		pp.setListener(listener);
		pp.addInput(new StringLexerSource(source, true));
		pp.addFeature(Feature.KEEPCOMMENTS);

		SourceMappingWriter writer = new SourceMappingWriter(origins);

		try {
			for (; ; ) {
				final Token tok = pp.token();
				if (tok == null) break;
				if (tok.getType() == Token.EOF) break;
				writer.append(tok);
			}
		} catch (final Exception e) {
			throw new RuntimeException("GLSL source pre-processing failed", e);
		}

		writer.appendTrailingNewline();

		source = listener.collectLines() + writer.output();
		source = ShaderSourceMap.appendMetadata(source, writer.sourcePaths());

		return source;
	}

	private static HashCode hashOrigins(List<SourceLine> origins) {
		var hasher = Hashing.sha512().newHasher();
		for (SourceLine origin : origins) {
			hasher.putUnencodedChars(origin.path().getPathString()).putInt(origin.line());
		}
		return hasher.hash();
	}

	private record CacheKey(HashCode sourceHash, HashCode originsHash, List<StringPair> environmentDefines) {
	}

	private static final class ImmutableOrigins {
		private static final List<SourceLine> EMPTY = List.of();
	}

	private static final class SourceMappingWriter {
		private final StringBuilder builder = new StringBuilder();
		private final StringBuilder leadingWhitespace = new StringBuilder();
		private final List<SourceLine> origins;
		private final Map<String, Integer> sourceIds = new TreeMap<>();
		private final Map<Integer, String> sourcePaths = new TreeMap<>();
		private Integer currentSourceId;
		private int currentSourceLine;
		private boolean lineStart = true;

		private SourceMappingWriter(List<SourceLine> origins) {
			this.origins = origins;
			origins.stream().map(line -> line.path().getPathString()).distinct().sorted()
				.forEach(path -> {
					int sourceId = sourceIds.size() + 1;
					sourceIds.put(path, sourceId);
					sourcePaths.put(sourceId, path);
				});
		}

		private void append(Token token) {
			String text = token.getText();
			if (lineStart && text.chars().allMatch(Character::isWhitespace)) {
				appendWhitespace(text);
				return;
			}

			if (lineStart) {
				SourceLine origin = originFor(token.getLine());
				if (origin != null) {
					int sourceId = sourceIds.get(origin.path().getPathString());
					if (currentSourceId == null || currentSourceId != sourceId || currentSourceLine != origin.line()) {
						builder.append("#line ").append(origin.line()).append(' ').append(sourceId).append('\n');
						currentSourceId = sourceId;
						currentSourceLine = origin.line();
					}
				}
				builder.append(leadingWhitespace);
				leadingWhitespace.setLength(0);
			}

			appendText(text);
		}

		private void appendWhitespace(String text) {
			int lastNewline = Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r'));
			if (lastNewline < 0) {
				leadingWhitespace.append(text);
				return;
			}
			builder.append(leadingWhitespace).append(text, 0, lastNewline + 1);
			leadingWhitespace.setLength(0);
			advanceLines(text);
			lineStart = true;
			if (lastNewline < text.length() - 1) {
				leadingWhitespace.append(text.substring(lastNewline + 1));
			}
		}

		private void appendText(String text) {
			builder.append(text);
			advanceLines(text);
			lineStart = text.endsWith("\n") || text.endsWith("\r");
		}

		private void advanceLines(String text) {
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == '\n') {
					currentSourceLine++;
				}
			}
		}

		private SourceLine originFor(int flattenedLine) {
			return flattenedLine >= 1 && flattenedLine <= origins.size() ? origins.get(flattenedLine - 1) : null;
		}

		private void appendTrailingNewline() {
			builder.append(leadingWhitespace);
			leadingWhitespace.setLength(0);
			builder.append('\n');
		}

		private String output() {
			return builder.toString();
		}

		private Map<Integer, String> sourcePaths() {
			return sourcePaths;
		}
	}
}
