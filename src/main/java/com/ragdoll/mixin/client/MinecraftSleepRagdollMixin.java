package com.ragdoll.mixin.client;

import com.ragdoll.client.RagdollClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
abstract class MinecraftSleepRagdollMixin {
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void ragdoll$suppressBedChat(Minecraft minecraft, Screen screen) {
        if (screen instanceof InBedChatScreen
                && RagdollClient.shouldReplaceSleepScreen()) {
            return;
        }
        minecraft.setScreen(screen);
    }
}
