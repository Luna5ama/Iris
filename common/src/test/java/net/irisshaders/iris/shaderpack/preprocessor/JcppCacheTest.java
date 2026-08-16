package net.irisshaders.iris.shaderpack.preprocessor;

import com.google.common.cache.Cache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JcppCacheTest {
	@Test
	void cacheEvictsIndividualEntriesInsteadOfClearingEverything() throws Exception {
		Field cacheField = JcppProcessor.class.getDeclaredField("CACHE");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Cache<Object, Object> cache = (Cache<Object, Object>) cacheField.get(null);

		cache.invalidateAll();
		for (int i = 0; i < 1025; i++) {
			cache.put(new Object(), new Object());
		}
		cache.cleanUp();

		assertTrue(cache.size() <= 1024);
		assertTrue(cache.size() > 0);
		cache.invalidateAll();
	}
}
