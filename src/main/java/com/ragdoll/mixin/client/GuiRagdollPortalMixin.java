package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollVisualEffects;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiRagdollPortalMixin {
    // Leave renderTextureOverlay intact: it also renders the freezing border.
    @Inject(method = {"renderPortalOverlay", "renderConfusionOverlay"},
            at = @At("HEAD"), cancellable = true)
    private void ragdoll$hidePortalOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
        if (RagdollVisualEffects.localHidden()) ci.cancel();
    }
}
