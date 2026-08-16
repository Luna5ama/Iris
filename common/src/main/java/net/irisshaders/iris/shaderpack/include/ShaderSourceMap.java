package net.irisshaders.iris.shaderpack.include;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderSourceMap {
	private static final Cache<String, ShaderSourceMap> PARSE_CACHE = CacheBuilder.newBuilder()
		.maximumSize(512)
		.weakKeys()
		.softValues()
		.build();
	private static final String METADATA_PREFIX = "// IRIS_SOURCE ";
	private static final Pattern METADATA_PATTERN = Pattern.compile(
		"(?m)^// IRIS_SOURCE (\\d+) ([A-Za-z0-9_-]+)\\R?");
	private static final Pattern COLON_LOCATION = Pattern.compile(
		"(?<![\\p{Alnum}_./-])(\\d+):(\\d+)(?=[:(])");
	private static final Pattern PAREN_LOCATION = Pattern.compile(
		"(?<!\\S)(\\d+)\\((\\d+)\\)");

	private final String sourceWithoutMetadata;
	private final Map<Integer, String> sourcePaths;

	private ShaderSourceMap(String sourceWithoutMetadata, Map<Integer, String> sourcePaths) {
		this.sourceWithoutMetadata = sourceWithoutMetadata;
		this.sourcePaths = sourcePaths.isEmpty()
			? Map.of()
			: Collections.unmodifiableMap(new TreeMap<>(sourcePaths));
	}

	public static ShaderSourceMap parse(String source) {
		if (!containsReservedMarker(source)) {
			return new ShaderSourceMap(source, Map.of());
		}
		ShaderSourceMap cached = PARSE_CACHE.getIfPresent(source);
		if (cached != null) {
			return cached;
		}

		Matcher matcher = METADATA_PATTERN.matcher(source);
		Map<Integer, String> sourcePaths = new TreeMap<>();
		StringBuilder stripped = new StringBuilder();
		while (matcher.find()) {
			int sourceId = Integer.parseInt(matcher.group(1));
			String path = new String(Base64.getUrlDecoder().decode(matcher.group(2)), StandardCharsets.UTF_8);
			sourcePaths.put(sourceId, path);
			matcher.appendReplacement(stripped, "");
		}
		matcher.appendTail(stripped);
		ShaderSourceMap parsed = new ShaderSourceMap(stripped.toString(), sourcePaths);
		PARSE_CACHE.put(source, parsed);
		return parsed;
	}

	public static boolean containsReservedMarker(String source) {
		return source.contains(METADATA_PREFIX);
	}

	public static String appendMetadata(String source, Map<Integer, String> sourcePaths) {
		String stripped = parse(source).sourceWithoutMetadata();
		if (sourcePaths.isEmpty()) {
			return stripped;
		}

		StringBuilder builder = new StringBuilder(stripped);
		if (!stripped.endsWith("\n")) {
			builder.append('\n');
		}
		for (var entry : new TreeMap<>(sourcePaths).entrySet()) {
			String encodedPath = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8));
			builder.append(METADATA_PREFIX).append(entry.getKey()).append(' ')
				.append(encodedPath).append('\n');
		}
		return builder.toString();
	}

	public String appendMetadataTo(String source) {
		return appendMetadata(source, sourcePaths);
	}

	public ShaderSourceMap withSourcePath(int sourceId, String path) {
		Map<Integer, String> updatedPaths = new TreeMap<>(sourcePaths);
		updatedPaths.put(sourceId, path);
		return new ShaderSourceMap(sourceWithoutMetadata, updatedPaths);
	}

	public String sourceWithoutMetadata() {
		return sourceWithoutMetadata;
	}

	public Map<Integer, String> sourcePaths() {
		return sourcePaths;
	}

	public String remapLog(String log) {
		if (log.isEmpty() || sourcePaths.isEmpty()) {
			return log;
		}

		LinkedHashSet<Integer> referencedSources = new LinkedHashSet<>();
		String remapped = replaceLocations(log, COLON_LOCATION, referencedSources);
		remapped = replaceLocations(remapped, PAREN_LOCATION, referencedSources).stripTrailing();

		List<Integer> legendSources = referencedSources.isEmpty()
			? new ArrayList<>(sourcePaths.keySet())
			: new ArrayList<>(referencedSources);
		Collections.sort(legendSources);

		StringBuilder builder = new StringBuilder(remapped).append("\n\nShader source map:");
		for (int sourceId : legendSources) {
			builder.append("\n  ").append(sourceId).append(" = ").append(sourcePaths.get(sourceId));
		}
		return builder.toString();
	}

	private String replaceLocations(String log, Pattern pattern, LinkedHashSet<Integer> referencedSources) {
		Matcher matcher = pattern.matcher(log);
		StringBuilder remapped = new StringBuilder();
		while (matcher.find()) {
			int sourceId = Integer.parseInt(matcher.group(1));
			String path = sourcePaths.get(sourceId);
			if (path == null) {
				matcher.appendReplacement(remapped, Matcher.quoteReplacement(matcher.group()));
				continue;
			}
			referencedSources.add(sourceId);
			matcher.appendReplacement(remapped, Matcher.quoteReplacement(path + ":" + matcher.group(2)));
		}
		matcher.appendTail(remapped);
		return remapped.toString();
	}
}
