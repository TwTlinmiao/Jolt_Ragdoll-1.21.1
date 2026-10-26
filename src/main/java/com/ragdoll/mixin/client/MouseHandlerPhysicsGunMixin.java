package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollPhysicsGunClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 1100)
abstract class MouseHandlerPhysicsGunMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(
            method = "turnPlayer",
            at = @At("HEAD"),
            cancellable = true)
    private void ragdoll$rotatePhysicsGunObject(
            CallbackInfo callback) {
        if (!RagdollPhysicsGunClient.interceptMouseTurn(
                accumulatedDX, accumulatedDY)) {
            return;
        }
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        callback.cancel();
    }

    @Inject(
            method = "onPress",
            at = @At("HEAD"),
            cancellable = true)
    private void ragdoll$handlePhysicsGunButtons(
            long window,
            int button,
            int action,
            int modifiers,
            CallbackInfo callback) {
        if (RagdollPhysicsGunClient.handleMouseButton(
                button, action)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true)
    private void ragdoll$handlePhysicsGunScroll(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo callback) {
        if (RagdollPhysicsGunClient.handleMouseScroll(vertical)) {
            callback.cancel();
        }
    }
}
