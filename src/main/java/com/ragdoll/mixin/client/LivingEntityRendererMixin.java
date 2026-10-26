package com.ragdoll.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.client.RagdollClient;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow @Final protected M model;

    @Inject(method = "isShaking", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideFrozenShaking(LivingEntity entity,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden(entity)) ci.setReturnValue(false);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void ragdoll$captureRenderedPose(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (entity instanceof AbstractClientPlayer player
                && model instanceof PlayerModel<?> playerModel) {
            RagdollInitialLimbPose.captureRenderedPose(
                    player, playerModel, partialTick);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideReplacedDeath(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo callbackInfo) {
        if (RagdollClient.shouldHideOriginalDeath(entity)) {
            callbackInfo.cancel();
        }
    }
}
