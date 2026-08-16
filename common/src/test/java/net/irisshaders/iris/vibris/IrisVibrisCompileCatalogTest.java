package net.irisshaders.iris.vibris;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrisVibrisCompileCatalogTest {
	@Test
	void preservesTheUtf8PatchedSourceHashContract() throws Exception {
		String source = "ascii \u007f \u0080 \u07ff \u0800 \ud83d\ude00 malformed \ud800 x \udc00";
		String expected = referenceHash("COMPUTE", source);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update("vibris-patched-program-v1".getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
		Method updateField = IrisVibrisCompileCatalog.class.getDeclaredMethod(
			"updateField", MessageDigest.class, String.class);
		updateField.setAccessible(true);
		updateField.invoke(null, digest, "COMPUTE");
		updateField.invoke(null, digest, source);

		assertEquals(expected, HexFormat.of().formatHex(digest.digest()));
	}

	private static String referenceHash(String type, String source) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update("vibris-patched-program-v1".getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
		updateReferenceField(digest, type);
		updateReferenceField(digest, source);
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void updateReferenceField(MessageDigest digest, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
