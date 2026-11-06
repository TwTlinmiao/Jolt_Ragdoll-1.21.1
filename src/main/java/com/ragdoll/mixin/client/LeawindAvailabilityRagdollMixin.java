package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.ThirdPerson", remap = false, priority = 2000)
abstract class LeawindAvailabilityRagdollMixin {
    @Inject(method = "isAvailable()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ragdoll$disableLeawindWhileRagdolled(
            CallbackInfoReturnable<Boolean> callback) {
        if (RagdollClient.isRagdollPerspectiveRestricted()) {
            callback.setReturnValue(false);
        }
    }
}
