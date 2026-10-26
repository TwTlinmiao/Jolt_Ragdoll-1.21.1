package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollVisualEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererRagdollEffectsMixin {
    // Clear both interpolation endpoints before rendering, including the frame
    // immediately after entering ragdoll state and before the next player tick.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ragdoll$clearPortalDistortion(net.minecraft.client.DeltaTracker tracker, CallbackInfo ci) {
        if (RagdollVisualEffects.localHidden()) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            player.oSpinningEffectIntensity = 0;
            player.spinningEffectIntensity = 0;
        }
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideCameraAnimation(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (RagdollVisualEffects.localHidden()) ci.cancel();
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideTotemAnimation(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (RagdollVisualEffects.localHidden()) ci.cancel();
    }
}
