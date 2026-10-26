package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.compat.AccessoryPartBindings;
import com.ragdoll.compat.CompatibilityDiagnostics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CuriosRagdollRenderer {
    private CuriosRagdollRenderer() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void render(
            RagdollAccessorySnapshot snapshot,
            String partName,
            LivingEntity entity,
            PlayerRenderer parent,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            float partialTick) {
        Set<String> slots = new LinkedHashSet<>();
        slots.addAll(snapshot.curiosItems().keySet());
        slots.addAll(snapshot.curiosCosmeticItems().keySet());
        for (String slot : slots) {
            List<ItemStack> items =
                    snapshot.curiosItems().getOrDefault(slot, List.of());
            List<ItemStack> cosmetics = snapshot.curiosCosmeticItems()
                    .getOrDefault(slot, List.of());
            int count = Math.max(items.size(), cosmetics.size());
            for (int index = 0; index < count; ++index) {
                ItemStack stack = effectiveStack(items, cosmetics, index);
                boolean cosmetic = index < cosmetics.size() && !cosmetics.get(index).isEmpty();
                boolean visible = shouldRender(snapshot.curiosRenderOptions(), slot, index);
                // Native Curios renders cosmetics even when the functional
                // slot's render toggle is off; preserve both context flags.
                if (stack.isEmpty() || (!cosmetic && !visible)
                        || !AccessoryPartBindings.belongsToPart(stack, slot, index, partName, false)) {
                    continue;
                }
                SlotContext context =
                        new SlotContext(slot, entity, index, cosmetic, visible);
                var registered = CuriosRendererRegistry.getRenderer(stack.getItem());
                if (registered.isEmpty() && !RagdollWearableAdapters.has(stack)) {
                    CompatibilityDiagnostics.missing("curios/" + slot, stack, "No wearable renderer registered by item mod");
                    continue;
                }
                            poseStack.pushPose();
                            try (var state = WearableModelState.capture(parent.getModel())) {
                                if (RagdollWearableAdapters.render(new RagdollWearableAdapters.Context(
                                        stack, entity, partName, slot, index, parent.getModel(), poseStack, buffers, light))) continue;
                                ICurioRenderer raw = registered.orElseThrow();
                                raw.render(
                                        stack,
                                        context,
                                        poseStack,
                                        parent,
                                        buffers,
                                        light,
                                        partialTick,
                                        0.0f,
                                        0.0f,
                                        0.0f,
                                        0.0f,
                                        0.0f);
                            } catch (RuntimeException error) {
                                CompatibilityDiagnostics.failure("curios/" + slot, stack, error);
                            } finally {
                                poseStack.popPose();
                            }
            }
        }
    }


    static ItemStack effectiveStack(
            List<ItemStack> items,
            List<ItemStack> cosmetics,
            int index) {
        if (index < cosmetics.size() && !cosmetics.get(index).isEmpty()) {
            return cosmetics.get(index);
        }
        return index < items.size() ? items.get(index) : ItemStack.EMPTY;
    }

    static boolean shouldRender(
            java.util.Map<String, List<Boolean>> options,
            String slot,
            int index) {
        List<Boolean> values = options.get(slot);
        return values == null
                || index >= values.size()
                || Boolean.TRUE.equals(values.get(index));
    }
}
