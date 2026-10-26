package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.compat.CompatibilityDiagnostics;
import com.ragdoll.model.RagdollAccessorySnapshot;
import java.lang.reflect.Method;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

final class SophisticatedBackpackRenderer {
    private static Method render;
    private static boolean initialized;

    static void render(RagdollAccessorySnapshot snapshot, LivingEntity corpse, PlayerRenderer parent,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        if (snapshot.backpack().isEmpty()) return;
        pose.pushPose();
        try (var ignored = WearableModelState.capture(parent.getModel())) {
            if (!initialized) {
                initialized = true;
                render = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackLayerRenderer")
                        .getMethod("renderBackpack", EntityModel.class, LivingEntity.class, PoseStack.class,
                                MultiBufferSource.class, int.class, ItemStack.class, boolean.class);
            }
            if (render != null) render.invoke(null, parent.getModel(), corpse, pose, buffers, light,
                    snapshot.backpack(), !snapshot.backpackInArmorSlot()
                            && !corpse.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
        } catch (ReflectiveOperationException | RuntimeException error) {
            CompatibilityDiagnostics.failure("sophisticatedbackpacks/render", snapshot.backpack(), error);
        } finally {
            pose.popPose();
        }
    }
}
