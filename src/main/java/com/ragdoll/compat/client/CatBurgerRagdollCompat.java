package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.compat.CompatibilityDiagnostics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;

/** Invokes Cat Burger's follower formation renderer without a hard dependency. */
final class CatBurgerRagdollCompat {
    private CatBurgerRagdollCompat() {}

    static void render(LivingEntity entity, PoseStack pose, MultiBufferSource buffers, int light) {
        try {
            Class<?> followerUtil = Class.forName("net.zhaiji.catburger.util.FollowerUtil");
            var followers = followerUtil.getMethod("findAll", LivingEntity.class).invoke(null, entity);
            if (!(followers instanceof java.util.List<?> list) || list.isEmpty()) return;

            Class<?> renderer = Class.forName("net.zhaiji.catburger.client.render.FollowerRenderer");
            renderer.getMethod("renderAll", LivingEntity.class, java.util.List.class,
                    PoseStack.class, MultiBufferSource.class, int.class, float.class)
                    .invoke(null, entity, list, pose, buffers, light, 0.0f);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Older Cat Burger releases may not expose the follower renderer API.
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : error;
            CompatibilityDiagnostics.failure("catburger/followers", ItemStack.EMPTY,
                    cause instanceof Exception exception ? exception : new RuntimeException(cause));
        }
    }
}
