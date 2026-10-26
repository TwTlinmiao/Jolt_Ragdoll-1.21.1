package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollVisualEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRagdollEffectsMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideHands(float partialTick, PoseStack poses,
            MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        if (RagdollVisualEffects.hidden(player)) ci.cancel();
    }
}
