package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CameraType.class, priority = 2000)
abstract class CameraTypeRagdollPerspectiveMixin {
    @Shadow @Final private boolean firstPerson;

    @Inject(method = "isFirstPerson", at = @At("HEAD"), cancellable = true)
    private void ragdoll$useVanillaPerspectiveDuringRagdoll(
            CallbackInfoReturnable<Boolean> callback) {
        if (RagdollClient.isRagdollPerspectiveRestricted()) {
            callback.setReturnValue(this.firstPerson);
        }
    }
}
