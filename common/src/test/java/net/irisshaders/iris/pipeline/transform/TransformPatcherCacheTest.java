package net.irisshaders.iris.pipeline.transform;

import com.google.common.cache.Cache;
import com.google.common.hash.HashCode;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformPatcherCacheTest {
	@Test
	void reusesSourceDigestsForTheSameSourceInstance() throws Exception {
		Field sourceHashCacheField = TransformPatcher.class.getDeclaredField("SOURCE_HASH_CACHE");
		sourceHashCacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Cache<String, HashCode> sourceHashCache = (Cache<String, HashCode>) sourceHashCacheField.get(null);
		sourceHashCache.invalidateAll();

		Class<?> cacheKey = Arrays.stream(TransformPatcher.class.getDeclaredClasses())
			.filter(type -> type.getSimpleName().equals("CacheKey"))
			.findFirst()
			.orElseThrow();
		Method sourceHash = cacheKey.getDeclaredMethod("sourceHash", String[].class);
		sourceHash.setAccessible(true);
		String source = new String("#version 330\nvoid main() {}\n");

		HashCode first = (HashCode) sourceHash.invoke(null, (Object) new String[]{source});
		HashCode second = (HashCode) sourceHash.invoke(null, (Object) new String[]{source});
		assertEquals(1, sourceHashCache.size());

		HashCode differentBoundary = (HashCode) sourceHash.invoke(null, (Object) new String[]{"#version 330\n", "void main() {}\n"});
		HashCode differentNullability = (HashCode) sourceHash.invoke(null, (Object) new String[]{source, null});

		assertEquals(first, second);
		assertNotEquals(first, differentBoundary);
		assertNotEquals(first, differentNullability);
		sourceHashCache.invalidateAll();
	}

	@Test
	void cacheKeysRetainSourceDigestsInsteadOfShaderSourceStrings() {
		Class<?> cacheKey = Arrays.stream(TransformPatcher.class.getDeclaredClasses())
			.filter(type -> type.getSimpleName().equals("CacheKey"))
			.findFirst()
			.orElseThrow();

		assertFalse(Arrays.stream(cacheKey.getDeclaredFields())
			.anyMatch(field -> field.getType() == String.class));
		assertTrue(Arrays.stream(cacheKey.getDeclaredFields())
			.anyMatch(field -> field.getType() == HashCode.class));
	}

	@Test
	void clearParsingCachesRetainsTransformResultsButDropsParserAndAstState() throws Exception {
		Field cacheField = TransformPatcher.class.getDeclaredField("cache");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<Object, Object> cache = (Map<Object, Object>) cacheField.get(null);
		Object key = new Object();
		Object value = new Object();
		cache.put(key, value);

		Field transformerField = TransformPatcher.class.getDeclaredField("transformer");
		transformerField.setAccessible(true);
		Object transformer = transformerField.get(null);
		Field parserField = ASTParser.class.getDeclaredField("parser");
		parserField.setAccessible(true);
		Field buildCacheField = ASTParser.class.getDeclaredField("buildCache");
		buildCacheField.setAccessible(true);
		Object parser = parserField.get(transformer);
		Object buildCache = buildCacheField.get(transformer);

		TransformPatcher.clearParsingCaches();

		assertSame(value, cache.get(key));
		assertNotSame(parser, parserField.get(transformer));
		assertNotSame(buildCache, buildCacheField.get(transformer));
		TransformPatcher.clearCaches();
	}

	@Test
	void clearCachesDropsTransformParserAndAstState() throws Exception {
		Field cacheField = TransformPatcher.class.getDeclaredField("cache");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<Object, Object> cache = (Map<Object, Object>) cacheField.get(null);
		cache.put(new Object(), new Object());

		Field transformerField = TransformPatcher.class.getDeclaredField("transformer");
		transformerField.setAccessible(true);
		Object transformer = transformerField.get(null);
		Field parserField = ASTParser.class.getDeclaredField("parser");
		parserField.setAccessible(true);
		Field buildCacheField = ASTParser.class.getDeclaredField("buildCache");
		buildCacheField.setAccessible(true);
		Object parser = parserField.get(transformer);
		Object buildCache = buildCacheField.get(transformer);

		TransformPatcher.clearCaches();

		assertTrue(cache.isEmpty());
		assertNotSame(parser, parserField.get(transformer));
		assertNotSame(buildCache, buildCacheField.get(transformer));
	}
}
