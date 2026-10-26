package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollPhysicsGunClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 1100)
abstract class KeyboardHandlerPhysicsGunMixin {

    @Inject(
            method = "keyPress",
            at = @At("HEAD"),
            cancellable = true)
    private void ragdoll$handlePhysicsGunRotationKey(
            long window,
            int key,
            int scanCode,
            int action,
            int modifiers,
            CallbackInfo callback) {
        if (RagdollPhysicsGunClient.handleRotationKey(
                window, key, action)) {
            callback.cancel();
        }
    }
}
