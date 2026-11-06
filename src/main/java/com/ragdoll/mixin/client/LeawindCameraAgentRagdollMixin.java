package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.core.CameraAgent", remap = false, priority = 2000)
abstract class LeawindCameraAgentRagdollMixin {
    @Inject(method = "onCameraSetup", at = @At("HEAD"), cancellable = true, remap = false)
    private void ragdoll$skipLeawindCameraSetup(CallbackInfo callback) {
        if (RagdollClient.isRagdollPerspectiveRestricted()) callback.cancel();
    }
}
