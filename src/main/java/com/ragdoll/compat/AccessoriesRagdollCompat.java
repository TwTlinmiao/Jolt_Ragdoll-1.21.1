package com.ragdoll.compat;

import com.ragdoll.model.RagdollAccessorySnapshot;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AccessoriesRagdollCompat {
    private AccessoriesRagdollCompat() {
    }

    static RagdollAccessorySnapshot capture(
            Player player, RagdollAccessorySnapshot base) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return base;
        }

        Map<String, List<ItemStack>> items = new LinkedHashMap<>();
        Map<String, List<ItemStack>> cosmetics = new LinkedHashMap<>();
        Map<String, List<Boolean>> renderOptions = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends AccessoriesContainer> entry
                : capability.getContainers().entrySet()) {
            AccessoriesContainer container = entry.getValue();
            List<ItemStack> slotItems =
                    copy(container.getAccessories(), container.getSize());
            List<ItemStack> slotCosmetics = copy(
                    container.getCosmeticAccessories(), container.getSize());
            {
                items.put(entry.getKey(), slotItems);
                cosmetics.put(entry.getKey(), slotCosmetics);
                renderOptions.put(entry.getKey(), renderOptions(container));
            }
        }
        return base.withAccessories(items, cosmetics, renderOptions);
    }

    static List<ItemStack> takeItems(ServerPlayer player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (AccessoriesContainer container : capability.getContainers().values()) {
            collectAndClear(container.getAccessories(), container.getSize(), result);
            collectAndClear(container.getCosmeticAccessories(), container.getSize(), result);
        }
        return result;
    }

    static void restore(
            Player player, RagdollAccessorySnapshot snapshot, List<ItemStack> items) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return;
        for (var entry : snapshot.accessoriesItems().entrySet()) {
            AccessoriesContainer container = capability.getContainers().get(entry.getKey());
            if (container == null) continue;
            restoreStacks(container.getAccessories(), entry.getValue(), items);
        }
        for (var entry : snapshot.accessoriesCosmeticItems().entrySet()) {
            AccessoriesContainer container = capability.getContainers().get(entry.getKey());
            if (container == null) continue;
            restoreStacks(container.getCosmeticAccessories(), entry.getValue(), items);
        }
    }

    private static void restoreStacks(
            Container target, List<ItemStack> original, List<ItemStack> items) {
        for (int slot = 0; slot < original.size() && slot < target.getContainerSize(); ++slot) {
            ItemStack saved = original.get(slot);
            if (saved.isEmpty() || !target.getItem(slot).isEmpty()) continue;
            for (int i = 0; i < items.size(); ++i) {
                ItemStack candidate = items.get(i);
                if (ItemStack.isSameItemSameComponents(candidate, saved)
                        && candidate.getCount() == saved.getCount()) {
                    target.setItem(slot, candidate.copy());
                    items.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }

    private static void collectAndClear(
            Container container, int size, List<ItemStack> target) {
        for (int i = 0; i < size; ++i) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) target.add(stack.copy());
            container.setItem(i, ItemStack.EMPTY);
        }
    }

    @SuppressWarnings("unchecked")
    static AutoCloseable mirrorForRendering(
            Player player, RagdollAccessorySnapshot snapshot, String partName) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return () -> {};
        // Restore whole containers instead of resizing the shared originals:
        // dynamic slot counts and cosmetics must match the saved corpse.
        var holder = (com.ragdoll.mixin.client.AccessoriesHolderAccessor) capability.getHolder();
        var containers = (Map<String, AccessoriesContainer>) (Map<?, ?>) holder.ragdoll$getContainers();
        var previous = new LinkedHashMap<>(containers);
        var previousTypes = holder.ragdoll$getValidTypes();
        Map<String, AccessoriesContainer> mirrored = new LinkedHashMap<>();
        var slots = new java.util.LinkedHashSet<>(previous.keySet());
        slots.addAll(snapshot.accessoriesItems().keySet());
        slots.addAll(snapshot.accessoriesCosmeticItems().keySet());
        for (String slot : slots) {
            var type = io.wispforest.accessories.data.SlotTypeLoader.getSlotType(player, slot);
            if (type == null) continue; // Slot definition was removed by a datapack reload.
            var items = snapshot.accessoriesItems().getOrDefault(slot, List.of());
            var cosmetics = snapshot.accessoriesCosmeticItems().getOrDefault(slot, List.of());
            var renders = snapshot.accessoriesRenderOptions().getOrDefault(slot, List.of());
            int count = Math.max(type.amount(), Math.max(items.size(), cosmetics.size()));
            var container = new io.wispforest.accessories.impl.AccessoriesContainerImpl(
                    capability, new SnapshotSlotType(type, count));
            for (int i = 0; i < count; i++) {
                container.getAccessories().setItem(i, stackAt(items, i).copy());
                container.getCosmeticAccessories().setItem(i, stackAt(cosmetics, i).copy());
                container.renderOptions().set(i, i >= renders.size() || Boolean.TRUE.equals(renders.get(i)));
            }
            mirrored.put(slot, container);
        }
        AutoCloseable restore = () -> {
            containers.clear();
            containers.putAll(previous);
            holder.ragdoll$setValidTypes(previousTypes);
        };
        try {
            containers.clear();
            containers.putAll(mirrored);
            holder.ragdoll$setValidTypes(null);
            return restore;
        } catch (RuntimeException error) {
            try { restore.close(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    private record SnapshotSlotType(io.wispforest.accessories.api.slot.SlotType original, int amount)
            implements io.wispforest.accessories.api.slot.SlotType {
        public String name() { return original.name(); }
        public net.minecraft.resources.ResourceLocation icon() { return original.icon(); }
        public int order() { return original.order(); }
        public Set<net.minecraft.resources.ResourceLocation> validators() { return original.validators(); }
        public io.wispforest.accessories.api.DropRule dropRule() { return original.dropRule(); }
    }

    private static List<ItemStack> copy(Container container, int size) {
        List<ItemStack> result = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            result.add(container.getItem(i).copy());
        }
        return result;
    }

    private static List<Boolean> renderOptions(
            AccessoriesContainer container) {
        List<Boolean> result = new ArrayList<>(container.getSize());
        for (int i = 0; i < container.getSize(); ++i) {
            result.add(container.shouldRender(i));
        }
        return result;
    }


    private static ItemStack stackAt(List<ItemStack> stacks, int index) {
        return index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }


}
