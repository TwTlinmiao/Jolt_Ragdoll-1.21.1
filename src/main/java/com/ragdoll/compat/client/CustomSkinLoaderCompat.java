package com.ragdoll.compat.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.ragdoll.RagdollMod;
import com.ragdoll.mixin.client.SkinManagerAccessor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;

/** Optional CSL bridge; never fetch a skin site on the render thread. */
public final class CustomSkinLoaderCompat {
    private static final Cache<SkinKey, CompletableFuture<PlayerSkin>> SKINS = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(Duration.ofMinutes(1)).build();
    private static boolean checked;
    private static Method getUserProfile;
    private static boolean warned;

    private CustomSkinLoaderCompat() {}

    public static CompletableFuture<PlayerSkin> resolve(GameProfile profile) {
        if (!checked) {
            checked = true;
            try {
                getUserProfile = Class.forName("customskinloader.fake.FakeSkinManager")
                        .getMethod("getUserProfile", Object.class, GameProfile.class, boolean.class);
            } catch (ClassNotFoundException ignored) {
                // CSL is optional.
            } catch (ReflectiveOperationException | LinkageError exception) {
                warn(exception);
            }
        }
        if (getUserProfile == null) return null;
        SkinKey key = new SkinKey(profile.getId(), profile.getName());
        CompletableFuture<PlayerSkin> existing = SKINS.getIfPresent(key);
        if (existing != null) return existing;
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture<PlayerSkin> future = CompletableFuture.supplyAsync(() -> load(profile), Util.backgroundExecutor())
                .thenComposeAsync(textures -> textures == null
                        ? CompletableFuture.completedFuture(null)
                        : ((SkinManagerAccessor) minecraft.getSkinManager())
                                .ragdoll$registerTextures(profile.getId(), textures), minecraft)
                .exceptionally(exception -> {
                    warn(exception);
                    return null;
                });
        SKINS.put(key, future);
        return future;
    }

    @SuppressWarnings("unchecked")
    private static MinecraftProfileTextures load(GameProfile profile) {
        try {
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
                    (Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>)
                            getUserProfile.invoke(null, null, profile, false);
            if (textures == null || textures.isEmpty()) return null;
            return new MinecraftProfileTextures(textures.get(MinecraftProfileTexture.Type.SKIN),
                    textures.get(MinecraftProfileTexture.Type.CAPE), textures.get(MinecraftProfileTexture.Type.ELYTRA),
                    SignatureState.UNSIGNED);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to query CustomSkinLoader for " + profile.getName(), exception);
        }
    }

    private static synchronized void warn(Throwable exception) {
        if (!warned) {
            warned = true;
            RagdollMod.LOGGER.warn("CustomSkinLoader ragdoll skin lookup failed; using normal skin lookup", exception);
        }
    }

    public static void clear() {
        SKINS.invalidateAll();
    }

    private record SkinKey(UUID id, String name) {}
}
