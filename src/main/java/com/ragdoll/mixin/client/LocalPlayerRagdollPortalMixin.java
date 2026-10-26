package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollVisualEffects;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerRagdollPortalMixin {
    @Inject(method = "handleConfusionTransitionEffect", at = @At("HEAD"), cancellable = true)
    private void ragdoll$disablePortalTransition(boolean useConfusion, CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (RagdollVisualEffects.hidden(player)) {
            player.oSpinningEffectIntensity = 0;
            player.spinningEffectIntensity = 0;
            player.portalProcess = null;
            ci.cancel();
        }
    }
}
