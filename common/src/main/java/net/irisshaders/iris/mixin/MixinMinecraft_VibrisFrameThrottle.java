package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.vibris.IrisVibrisLifecycle;
import net.irisshaders.iris.vibris.DeterministicWorldSimulation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(Minecraft.class)
public class MixinMinecraft_VibrisFrameThrottle {
	@Inject(method = "getDeltaTracker", at = @At("RETURN"), cancellable = true)
	private void iris$useDeterministicDeltaTracker(CallbackInfoReturnable<DeltaTracker> cir) {
		cir.setReturnValue(SystemTimeUniforms.resolveDeltaTracker(cir.getReturnValue()));
	}

	@WrapWithCondition(
		method = "tick()V",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"
		)
	)
	private boolean iris$advanceAmbientParticles(ClientLevel level, int x, int y, int z) {
		return !DeterministicWorldSimulation.isActive();
	}

	@WrapWithCondition(
		method = "tick()V",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/particle/ParticleEngine;tick()V"
		)
	)
	private boolean iris$advanceParticles(ParticleEngine particleEngine) {
		return !DeterministicWorldSimulation.isActive();
	}

	@WrapWithCondition(
		method = "tick()V",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;tick()V"
		)
	)
	private boolean iris$advanceGameRenderer(GameRenderer gameRenderer) {
		return !DeterministicWorldSimulation.isActive();
	}

	@WrapWithCondition(
		method = "tick()V",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickEntities()V"
		)
	)
	private boolean iris$tickEntities(ClientLevel level) {
		return !DeterministicWorldSimulation.isActive();
	}

	@WrapWithCondition(
		method = "tick()V",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickBlockEntities()V"
		)
	)
	private boolean iris$tickBlockEntities(ClientLevel level) {
		return !DeterministicWorldSimulation.isActive();
	}

	@WrapWithCondition(
		method = "tick()V",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V"
		)
	)
	private boolean iris$tickClientWorld(ClientLevel level, BooleanSupplier chunkSupplier) {
		return !DeterministicWorldSimulation.isActive();
	}

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
