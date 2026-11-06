package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
abstract class OptionsLiveRagdollCameraMixin {
    @ModifyVariable(method = "setCameraType", at = @At("HEAD"), argsOnly = true)
    private CameraType ragdoll$limitLiveRagdollCamera(CameraType requested) {
        return RagdollClient.restrictRagdollCameraType(requested);
    }

    @Inject(method = "getCameraType", at = @At("RETURN"), cancellable = true)
    private void ragdoll$hideNonVanillaRagdollPerspective(
            CallbackInfoReturnable<CameraType> callback) {
        CameraType restricted = RagdollClient.restrictRagdollCameraType(callback.getReturnValue());
        if (restricted != callback.getReturnValue()) callback.setReturnValue(restricted);
    }
}
