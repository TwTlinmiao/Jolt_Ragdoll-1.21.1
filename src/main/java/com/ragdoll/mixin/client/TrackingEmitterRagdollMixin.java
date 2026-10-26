package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollVisualEffects;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackingEmitter.class)
abstract class TrackingEmitterRagdollMixin {
    @Shadow @Final private Entity entity;

    // Also catches emitters created before the player entered ragdoll state.
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideAttachedEmitter(CallbackInfo ci) {
        if (RagdollVisualEffects.hidden(entity)) {
            ((TrackingEmitter) (Object) this).remove();
            ci.cancel();
        }
    }
}
