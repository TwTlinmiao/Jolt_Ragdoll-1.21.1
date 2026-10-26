package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntitySleepCameraMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void ragdoll$captureSleepCameraTurn(double yawDelta, double pitchDelta,
                                                CallbackInfo callback) {
        if (RagdollClient.captureSleepCameraTurn(
                (Entity) (Object) this, yawDelta, pitchDelta)) {
            callback.cancel();
        }
    }
}
