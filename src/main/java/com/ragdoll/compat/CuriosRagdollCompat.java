package com.ragdoll.compat;

import com.ragdoll.model.RagdollAccessorySnapshot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CuriosRagdollCompat {
    private CuriosRagdollCompat() {
    }

    static RagdollAccessorySnapshot capture(
            Player player, RagdollAccessorySnapshot base) {
        var inventory = player.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) {
            return base;
        }

        Map<String, List<ItemStack>> items = new LinkedHashMap<>();
        Map<String, List<ItemStack>> cosmetics = new LinkedHashMap<>();
        Map<String, List<Boolean>> renderOptions = new LinkedHashMap<>();
        for (Map.Entry<String, ICurioStacksHandler> entry
                : inventory.getCurios().entrySet()) {
            ICurioStacksHandler handler = entry.getValue();
            List<ItemStack> slotItems = copy(handler.getStacks());
            List<ItemStack> slotCosmetics =
                    copy(handler.getCosmeticStacks());
            {
                items.put(entry.getKey(), slotItems);
                cosmetics.put(entry.getKey(), slotCosmetics);
                renderOptions.put(
                        entry.getKey(), List.copyOf(handler.getRenders()));
            }
        }
        return base.withCurios(items, cosmetics, renderOptions);
    }

    static List<ItemStack> takeItems(ServerPlayer player) {
        var inventory = player.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (ICurioStacksHandler handler : inventory.getCurios().values()) {
            collectAndClear(handler.getStacks(), result);
            collectAndClear(handler.getCosmeticStacks(), result);
        }
        return result;
    }

    static void restore(
            Player player, RagdollAccessorySnapshot snapshot, List<ItemStack> items) {
        var inventory = player.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) return;
        for (var entry : snapshot.curiosItems().entrySet()) {
            ICurioStacksHandler handler = inventory.getCurios().get(entry.getKey());
            if (handler == null) continue;
            restoreStacks(handler.getStacks(), entry.getValue(), items);
        }
        for (var entry : snapshot.curiosCosmeticItems().entrySet()) {
            ICurioStacksHandler handler = inventory.getCurios().get(entry.getKey());
            if (handler == null) continue;
            restoreStacks(handler.getCosmeticStacks(), entry.getValue(), items);
        }
    }

    private static void restoreStacks(
            IDynamicStackHandler target, List<ItemStack> original, List<ItemStack> items) {
        for (int slot = 0; slot < original.size() && slot < target.getSlots(); ++slot) {
            ItemStack saved = original.get(slot);
            if (saved.isEmpty() || !target.getStackInSlot(slot).isEmpty()) continue;
            int index = findMatching(items, saved);
            if (index < 0) continue;
            target.setStackInSlot(slot, items.get(index).copy());
            items.set(index, ItemStack.EMPTY);
        }
    }

    private static int findMatching(List<ItemStack> items, ItemStack saved) {
        for (int i = 0; i < items.size(); ++i) {
            if (ItemStack.isSameItemSameComponents(items.get(i), saved)
                    && items.get(i).getCount() == saved.getCount()) return i;
        }
        return -1;
    }

    private static void collectAndClear(
            IDynamicStackHandler handler, List<ItemStack> target) {
        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) target.add(stack.copy());
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    static AutoCloseable mirrorForRendering(
            Player player, RagdollAccessorySnapshot snapshot, String partName) {
        var inventory = player.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) return () -> {};
        Map<String, ICurioStacksHandler> previous = new LinkedHashMap<>(inventory.getCurios());
        Map<String, ICurioStacksHandler> mirrored = new LinkedHashMap<>();
        var slots = new java.util.LinkedHashSet<>(snapshot.curiosItems().keySet());
        slots.addAll(snapshot.curiosCosmeticItems().keySet());
        // Keep empty native slots too: some renderers query their presence.
        slots.addAll(previous.keySet());
        for (String slot : slots) {
            var items = snapshot.curiosItems().getOrDefault(slot, List.of());
            var cosmetics = snapshot.curiosCosmeticItems().getOrDefault(slot, List.of());
            var renders = snapshot.curiosRenderOptions().getOrDefault(slot, List.of());
            int count = Math.max(items.size(), cosmetics.size());
            if (previous.containsKey(slot)) count = Math.max(count, previous.get(slot).getSlots());
            var handler = new top.theillusivec4.curios.common.inventory.CurioStacksHandler(
                    inventory, slot, count, true, true, true,
                    top.theillusivec4.curios.api.type.capability.ICurio.DropRule.DEFAULT);
            for (int i = 0; i < count; i++) {
                handler.getStacks().setStackInSlot(i, stackAt(items, i).copy());
                handler.getCosmeticStacks().setStackInSlot(i, stackAt(cosmetics, i).copy());
                handler.getRenders().set(i, i >= renders.size() || Boolean.TRUE.equals(renders.get(i)));
            }
            mirrored.put(slot, handler);
        }
        try {
            inventory.setCurios(mirrored);
        } catch (RuntimeException error) {
            inventory.setCurios(previous);
            throw error;
        }
        return () -> inventory.setCurios(previous);
    }

    private static List<ItemStack> copy(IDynamicStackHandler handler) {
        List<ItemStack> result = new ArrayList<>(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); ++i) {
            result.add(handler.getStackInSlot(i).copy());
        }
        return result;
    }


    private static ItemStack stackAt(List<ItemStack> stacks, int index) {
        return index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }

}
