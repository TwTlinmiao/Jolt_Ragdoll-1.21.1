package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiSleepOverlayMixin {
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hidePortalOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
        if (com.ragdoll.client.RagdollVisualEffects.localHidden()) ci.cancel();
    }

    @Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void ragdoll$hideSleepOverlay(GuiGraphics graphics,
                                          DeltaTracker deltaTracker,
                                          CallbackInfo callback) {
        if (RagdollClient.isSleepRagdollCameraActive()) {
            callback.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ragdoll$renderWakeHint(GuiGraphics graphics,
                                        DeltaTracker deltaTracker,
                                        CallbackInfo callback) {
        if (!RagdollClient.isSleepRagdollCameraActive()) return;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        graphics.drawCenteredString(minecraft.font,
                Component.translatable("ragdoll.sleepRagdoll.wakeHint"),
                graphics.guiWidth() / 2, graphics.guiHeight() - 34, 0xFFFFFF);
    }
}
