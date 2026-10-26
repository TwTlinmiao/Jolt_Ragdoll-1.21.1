package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide effects from the invisible player proxy, without changing its gameplay state. */
@Mixin(Entity.class)
abstract class EntityLiveParticlesMixin {
    @Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
    private void ragdoll$denyClientPortal(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideLiveFireAnimation(CallbackInfoReturnable<Boolean> cir) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideLiveSprintParticles(CallbackInfo ci) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    @Redirect(method = "doWaterSplashEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void ragdoll$hideLiveSplashParticles(Level level, ParticleOptions particle,
            double x, double y, double z, double vx, double vy, double vz) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden((Entity) (Object) this)) return;
        level.addParticle(particle, x, y, z, vx, vy, vz);
    }

    @Redirect(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/HoneyBlock;showSlideParticles(Lnet/minecraft/world/entity/Entity;)V"))
    private void ragdoll$hideHoneySlide(Entity entity) {
        if (!com.ragdoll.client.RagdollVisualEffects.hidden(entity)) {
            net.minecraft.world.level.block.HoneyBlock.showSlideParticles(entity);
        }
    }
}
