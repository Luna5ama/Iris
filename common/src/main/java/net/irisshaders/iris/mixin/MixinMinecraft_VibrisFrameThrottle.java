package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.irisshaders.iris.vibris.IrisVibrisLifecycle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft_VibrisFrameThrottle {
	@Redirect(
		method = "runTick",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;getFramerateLimit()I")
	)
	private int iris$vibrisIdleFramerateLimit(FramerateLimitTracker tracker) {
		return IrisVibrisLifecycle.idleFramerateLimit(tracker.getFramerateLimit());
	}

	@Redirect(
		method = "runTick",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V")
	)
	private void iris$vibrisInterruptibleFrameLimit(int framerateLimit) {
		IrisVibrisLifecycle.limitDisplayFps(framerateLimit);
	}

	@Inject(
		method = "runTick",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V",
			shift = At.Shift.AFTER
		)
	)
	private void iris$vibrisYieldRenderLoop(boolean tick, CallbackInfo ci) {
		try {
			Thread.sleep(1);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
