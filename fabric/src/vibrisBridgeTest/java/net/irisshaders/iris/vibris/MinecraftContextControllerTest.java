package net.irisshaders.iris.vibris;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftContextControllerTest {
	@Test
	void differentLoadedSaveRequiresAutonomousSwitch() {
		assertTrue(MinecraftContextController.requiresSaveSwitch("New World (7)", "craftcollection2"));
		assertFalse(MinecraftContextController.requiresSaveSwitch("New World (7)", "New World (7)"));
	}
}
