package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    // Suppress visuals only: tickEffects must still update potion durations and
    // item use must still complete normally while the player is a ragdoll.
    @Redirect(method = {"tickEffects", "baseTick", "spawnItemParticles", "handleEntityEvent"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void ragdoll$hideLivePlayerParticles(Level level, ParticleOptions particle,
            double x, double y, double z, double vx, double vy, double vz) {
        if (com.ragdoll.client.RagdollVisualEffects.hidden((LivingEntity) (Object) this)) return;
        level.addParticle(particle, x, y, z, vx, vy, vz);
    }

    @Redirect(method = "handleEntityEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/HoneyBlock;showJumpParticles(Lnet/minecraft/world/entity/Entity;)V"))
    private void ragdoll$hideHoneyJump(net.minecraft.world.entity.Entity entity) {
        if (!com.ragdoll.client.RagdollVisualEffects.hidden(entity)) {
            net.minecraft.world.level.block.HoneyBlock.showJumpParticles(entity);
        }
    }

    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideOriginalDeathPoof(CallbackInfo callbackInfo) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (RagdollClient.shouldHideOriginalDeath(entity)) {
            callbackInfo.cancel();
        }
    }
}
