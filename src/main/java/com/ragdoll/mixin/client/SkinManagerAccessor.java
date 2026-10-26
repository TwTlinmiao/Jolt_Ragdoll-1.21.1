package com.ragdoll.mixin.client;

import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SkinManager.class)
public interface SkinManagerAccessor {
    @Invoker("registerTextures")
    CompletableFuture<PlayerSkin> ragdoll$registerTextures(UUID playerId, MinecraftProfileTextures textures);
}
