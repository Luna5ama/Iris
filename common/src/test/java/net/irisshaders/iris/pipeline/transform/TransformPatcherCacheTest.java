package net.irisshaders.iris.pipeline.transform;

import com.google.common.hash.HashCode;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformPatcherCacheTest {
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
