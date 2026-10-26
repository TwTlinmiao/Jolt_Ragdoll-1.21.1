package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Options.class)
abstract class OptionsLiveRagdollCameraMixin {
    @ModifyVariable(method = "setCameraType", at = @At("HEAD"), argsOnly = true)
    private CameraType ragdoll$limitLiveRagdollCamera(CameraType requested) {
        return requested == CameraType.THIRD_PERSON_FRONT
                && (RagdollClient.isLiveRagdollCameraRestrictionActive()
                || RagdollClient.isSleepRagdollCameraActive())
                ? CameraType.FIRST_PERSON : requested;
    }
}
