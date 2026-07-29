package net.irisshaders.iris.mixin;

import com.mojang.realmsclient.client.RealmsClient;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.vibris.IrisVibrisAutomation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.quickplay.QuickPlay;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuickPlay.class)
public class MixinQuickPlayDev {
	@Inject(method = "connect", at = @At("HEAD"), cancellable = true)
	private static void iris$createMissingWorldIfDev(Minecraft minecraft, GameConfig.QuickPlayVariant variant,
											 RealmsClient realmsClient, CallbackInfo ci) {
		if (isDevRun() && variant instanceof GameConfig.QuickPlaySinglePlayerData singlePlayer &&
			!minecraft.getLevelSource().levelExists(singlePlayer.worldId())) {
			ci.cancel();
			createWorld(minecraft, singlePlayer.worldId());
		}
	}

	@Inject(method = "joinSingleplayerWorld", at = @At("HEAD"), cancellable = true)
	private static void iris$createWorldIfDev(Minecraft minecraft, String string, CallbackInfo ci) {
		if (isDevRun()) {
			ci.cancel();

			if (!minecraft.getLevelSource().levelExists(string)) {
				createWorld(minecraft, string);
			} else {
				minecraft.createWorldOpenFlows().openWorld(string, () -> minecraft.setScreen(new TitleScreen()));
			}
		}
	}

	private static boolean isDevRun() {
		return IrisPlatformHelpers.getInstance().isDevelopmentEnvironment() || IrisVibrisAutomation.enabled();
	}

	private static void createWorld(Minecraft minecraft, String name) {
		minecraft.createWorldOpenFlows().createFreshLevel(name, new LevelSettings(name, GameType.CREATIVE, false,
			Difficulty.HARD, true, new GameRules(FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS,
			FeatureFlags.REDSTONE_EXPERIMENTS)), WorldDataConfiguration.DEFAULT), WorldOptions.defaultWithRandomSeed(),
			WorldPresets::createNormalWorldDimensions, minecraft.screen);
	}
}
