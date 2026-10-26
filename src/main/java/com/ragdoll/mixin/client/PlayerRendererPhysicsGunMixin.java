package com.ragdoll.mixin.client;

import com.ragdoll.item.PhysicsGunItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Uses Velthoric's crossbow-hold pose for the Physics Gun.
 */
@Mixin(PlayerRenderer.class)
abstract class PlayerRendererPhysicsGunMixin {

    @Inject(
            method = "getArmPose(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("RETURN"),
            cancellable = true)
    private static void ragdoll$physicsGunArmPose(
            AbstractClientPlayer player,
            InteractionHand hand,
            CallbackInfoReturnable<HumanoidModel.ArmPose> callback) {
        ItemStack stack = player.getItemInHand(hand);
        if (com.ragdoll.RagdollConfig.enabled()
                && stack.getItem() instanceof PhysicsGunItem) {
            callback.setReturnValue(
                    HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }
}
