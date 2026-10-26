package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.compat.AccessoryPartBindings;
import com.ragdoll.compat.CompatibilityDiagnostics;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.menu.ArmorSlotTypes;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AccessoriesRagdollRenderer {
    private AccessoriesRagdollRenderer() {
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
        slots.addAll(snapshot.accessoriesItems().keySet());
        slots.addAll(snapshot.accessoriesCosmeticItems().keySet());
        PlayerModel model = parent.getModel();

        for (String slot : slots) {
            if (ArmorSlotTypes.isArmorType(slot)) {
                continue;
            }
            List<ItemStack> items =
                    snapshot.accessoriesItems().getOrDefault(slot, List.of());
            List<ItemStack> cosmetics = snapshot.accessoriesCosmeticItems()
                    .getOrDefault(slot, List.of());
            int count = Math.max(items.size(), cosmetics.size());
            for (int index = 0; index < count; ++index) {
                boolean visible = CuriosRagdollRenderer.shouldRender(
                        snapshot.accessoriesRenderOptions(), slot, index);
                ItemStack stack = CuriosRagdollRenderer.effectiveStack(
                        items, io.wispforest.accessories.Accessories.config().clientOptions
                                .showCosmeticAccessories() ? cosmetics : List.of(), index);
                if (stack.isEmpty() || !AccessoryPartBindings.belongsToPart(
                        stack, slot, index, partName, true)) {
                    continue;
                }
                AccessoryRenderer renderer =
                        AccessoriesRendererRegistry.getRenderer(stack);
                if ((!RagdollWearableAdapters.has(stack) && renderer.isEmpty())
                        || (RagdollWearableAdapters.has(stack) ? !visible : !renderer.shouldRender(visible))) {
                    CompatibilityDiagnostics.missing("accessories/" + slot, stack, "No enabled wearable renderer");
                    continue;
                }

                SlotReference reference = SlotReference.of(entity, slot, index);
                poseStack.pushPose();
                try (var state = WearableModelState.capture(model)) {
                    if (RagdollWearableAdapters.render(new RagdollWearableAdapters.Context(
                            stack, entity, partName, slot, index, model, poseStack, buffers, light))) continue;
                    renderer.render(
                            stack,
                            reference,
                            poseStack,
                            model,
                            buffers,
                            light,
                            0.0f,
                            0.0f,
                            partialTick,
                            0.0f,
                            0.0f,
                            0.0f);
                } catch (RuntimeException error) {
                    CompatibilityDiagnostics.failure("accessories/" + slot, stack, error);
                } finally {
                    poseStack.popPose();
                }
            }
        }
    }

    static ItemStack cosmeticArmorOverride(
            RagdollAccessorySnapshot snapshot, EquipmentSlot slot) {
        var reference = ArmorSlotTypes.getReferenceFromSlot(slot);
        if (reference == null) {
            return null;
        }
        String slotName = reference.slotName();
        if (!CuriosRagdollRenderer.shouldRender(
                snapshot.accessoriesRenderOptions(), slotName, 0)) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> cosmetics =
                snapshot.accessoriesCosmeticItems().get(slotName);
        if (!io.wispforest.accessories.Accessories.config().clientOptions.showCosmeticAccessories()) return null;
        if (cosmetics == null || cosmetics.isEmpty()) {
            return null;
        }
        ItemStack cosmetic = cosmetics.getFirst();
        return cosmetic.isEmpty() ? null : cosmetic;
    }


}
