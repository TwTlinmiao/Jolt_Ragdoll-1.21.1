package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.compat.CompatibilityDiagnostics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;

/** Renders Immersive Armors' material-specific decorative pieces on ragdolls. */
final class ImmersiveArmorCompat {
    private static final String ARMOR_ITEM = "immersive_armors.item.ExtendedArmorItem";

    private ImmersiveArmorCompat() {}

    static void render(LivingEntity entity, ItemStack stack, EquipmentSlot slot, String part,
                       HumanoidModel<?> model, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!ModList.get().isLoaded("immersive_armors")) return;
        try {
            Class<?> armorType = Class.forName(ARMOR_ITEM);
            if (!armorType.isInstance(stack.getItem())) return;
            Object material = armorType.getMethod("getExtendedMaterial").invoke(stack.getItem());
            var pieces = material.getClass().getMethod("getPieces", EquipmentSlot.class)
                    .invoke(material, slot);
            if (!(pieces instanceof Iterable<?> iterable)) return;
            var render = Class.forName("immersive_armors.client.render.entity.piece.Piece")
                    .getMethod("render", PoseStack.class, MultiBufferSource.class, int.class,
                            LivingEntity.class, ItemStack.class, float.class,
                            EquipmentSlot.class, HumanoidModel.class);
            for (Object piece : iterable) {
                render.invoke(piece, pose, buffers, light, entity, stack, 0.0f, slot, model);
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Keep this integration optional across Immersive Armors versions.
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : error;
            CompatibilityDiagnostics.failure("immersive_armors", stack,
                    cause instanceof Exception exception ? exception : new RuntimeException(cause));
        }
    }
}
