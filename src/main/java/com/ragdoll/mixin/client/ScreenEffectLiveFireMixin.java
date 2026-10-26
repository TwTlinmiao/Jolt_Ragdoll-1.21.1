package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectLiveFireMixin {
    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true)
    private static void ragdoll$hideProxyOverlays(Minecraft minecraft, PoseStack poseStack,
                                                 CallbackInfo ci) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden(minecraft.player)) ci.cancel();
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void ragdoll$hideLiveFireOverlay(Minecraft minecraft, PoseStack poseStack,
                                                   CallbackInfo ci) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden(minecraft.player)) {
            ci.cancel();
        }
    }
}
