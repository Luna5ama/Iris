package net.irisshaders.iris.pipeline.transform;

import com.google.common.hash.HashCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
