package net.irisshaders.iris.vibris;

import dev.vibris.api.RuntimeEnvironment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftVibrisRuntimeHostTest {
	@Test
	void preservesExactMaintainedRuntimeIdentity() {
		RuntimeEnvironment environment = MinecraftVibrisRuntimeHost.runtimeEnvironment(
			"1.21.11",
			"1.10.6-snapshot+mc1.21.11-local",
			"0.0.1-SNAPSHOT",
			"21.0.8+9-LTS",
			"Windows x86_64",
			"NVIDIA Corporation",
			"NVIDIA GeForce RTX 5090/PCIe/SSE2",
			"4.6.0 NVIDIA 581.29",
			"4.6.0 NVIDIA 581.29"
		);

		assertEquals("1.21.11", environment.minecraftVersion());
		assertEquals("1.10.6-snapshot+mc1.21.11-local", environment.irisVersion());
		assertEquals("0.0.1-SNAPSHOT", environment.vibrisVersion());
		assertEquals("21.0.8+9-LTS", environment.javaVersion());
		assertEquals("Windows x86_64", environment.operatingSystem());
		assertEquals("NVIDIA Corporation", environment.gpuVendor());
		assertEquals("NVIDIA GeForce RTX 5090/PCIe/SSE2", environment.gpuRenderer());
		assertEquals("4.6.0 NVIDIA 581.29", environment.openglVersion());
		assertEquals("4.6.0 NVIDIA 581.29", environment.driverVersion());
	}
}
