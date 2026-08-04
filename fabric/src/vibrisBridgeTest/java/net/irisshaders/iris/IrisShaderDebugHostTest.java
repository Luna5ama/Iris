package net.irisshaders.iris;

import dev.vibris.api.ReloadResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrisShaderDebugHostTest {
	@Test
	void reloadActionUsesFixedVibrisShaderpackReload() throws Exception {
		AtomicInteger reloads = new AtomicInteger();
		IrisShaderDebugHost host = new IrisShaderDebugHost(() -> {
			reloads.incrementAndGet();
			return ReloadResult.success(List.of());
		});

		host.reloadShaders();

		assertEquals(1, reloads.get());
	}

	@Test
	void reloadActionReportsFixedVibrisShaderpackFailure() {
		ReloadResult.Diagnostic diagnostic = new ReloadResult.Diagnostic(
			ReloadResult.Severity.ERROR,
			"shaderpack",
			0,
			"Fixed Vibris shaderpack reload failed");
		IrisShaderDebugHost host = new IrisShaderDebugHost(
			() -> ReloadResult.failurePreservingActiveState(List.of(diagnostic)));

		IllegalStateException failure = assertThrows(IllegalStateException.class, host::reloadShaders);

		assertEquals("Fixed Vibris shaderpack reload failed", failure.getMessage());
	}
}
