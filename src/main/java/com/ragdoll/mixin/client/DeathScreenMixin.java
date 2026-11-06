package com.ragdoll.mixin.client;

import com.ragdoll.RagdollConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
abstract class DeathScreenMixin {
    // The shared helper also covers vanilla's return-to-title confirmation.
    @Inject(method = "renderDeathBackground", at = @At("HEAD"), cancellable = true)
    private static void ragdoll$darkBackground(GuiGraphics graphics, int width, int height,
                                               CallbackInfo ci) {
        if (!RagdollConfig.enabled()) return;
        graphics.fillGradient(0, 0, width, height, 0x60000000, 0xA0000000);
        ci.cancel();
    }
}
