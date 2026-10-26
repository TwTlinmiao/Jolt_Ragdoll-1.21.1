package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ServerRagdollManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Common/server-side portal guard; contains no client-only references. */
@Mixin(Entity.class)
abstract class EntityRagdollPortalMixin {
    @Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
    private void ragdoll$disablePortal(boolean allowPassengers, CallbackInfoReturnable<Boolean> ci) {
        if ((Object) this instanceof ServerPlayer player
                && ServerRagdollManager.isPlayerRagdolled(player.getUUID())) ci.setReturnValue(false);
    }

    @Inject(method = "handlePortal", at = @At("HEAD"), cancellable = true)
    private void ragdoll$clearPendingPortal(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && ServerRagdollManager.isPlayerRagdolled(player.getUUID())) {
            // Discard time accumulated before toggling, not merely pause it.
            player.portalProcess = null;
            ci.cancel();
        }
    }
}
