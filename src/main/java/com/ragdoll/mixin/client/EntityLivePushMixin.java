package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityLivePushMixin {
    // The server proxy has noPhysics, but remote client players do not inherit
    // that flag. Cancel the whole pair so neither participant gets ghost recoil.
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void ragdoll$skipLiveProxyPush(Entity other, CallbackInfo ci) {
        if (ragdoll$isLiveProxy((Entity) (Object) this) || ragdoll$isLiveProxy(other)) {
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean ragdoll$isLiveProxy(Entity entity) {
        return entity instanceof Player
                && ClientPhysicsRuntime.isLiveRagdolled(entity.getUUID());
    }
}
