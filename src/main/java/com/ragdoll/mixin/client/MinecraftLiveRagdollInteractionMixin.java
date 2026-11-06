package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import com.ragdoll.client.RagdollClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftLiveRagdollInteractionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void ragdoll$preventLiveAttack(
            CallbackInfoReturnable<Boolean> callback) {
        if (ragdoll$isLiveRagdolled() || RagdollClient.isLocalSleepInteractionLocked()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void ragdoll$preventContinuousMining(
            boolean leftClick, CallbackInfo callback) {
        if (ragdoll$isLiveRagdolled() || RagdollClient.isLocalSleepInteractionLocked()) {
            callback.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void ragdoll$preventItemUse(CallbackInfo callback) {
        if (ragdoll$isLiveRagdolled() || RagdollClient.isLocalSleepInteractionLocked()) {
            callback.cancel();
        }
    }

    private static boolean ragdoll$isLiveRagdolled() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID());
    }
}
