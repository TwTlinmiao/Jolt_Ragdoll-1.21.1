package com.ragdoll.model;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable Curios and Accessories equipment captured when a ragdoll is created. */
public record RagdollAccessorySnapshot(
        Map<String, List<ItemStack>> curiosItems,
        Map<String, List<ItemStack>> curiosCosmeticItems,
        Map<String, List<Boolean>> curiosRenderOptions,
        Map<String, List<ItemStack>> accessoriesItems,
        Map<String, List<ItemStack>> accessoriesCosmeticItems,
        Map<String, List<Boolean>> accessoriesRenderOptions,
        ItemStack backpack,
        boolean backpackInArmorSlot) {
    public static final RagdollAccessorySnapshot EMPTY =
            new RagdollAccessorySnapshot(
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public RagdollAccessorySnapshot {
        backpack = backpack == null ? ItemStack.EMPTY : backpack.copy();
        curiosItems = copyItems(curiosItems);
        curiosCosmeticItems = copyItems(curiosCosmeticItems);
        curiosRenderOptions = copyBooleans(curiosRenderOptions);
        accessoriesItems = copyItems(accessoriesItems);
        accessoriesCosmeticItems = copyItems(accessoriesCosmeticItems);
        accessoriesRenderOptions = copyBooleans(accessoriesRenderOptions);
    }

    public RagdollAccessorySnapshot(
            Map<String, List<ItemStack>> curiosItems,
            Map<String, List<ItemStack>> curiosCosmeticItems,
            Map<String, List<Boolean>> curiosRenderOptions,
            Map<String, List<ItemStack>> accessoriesItems,
            Map<String, List<ItemStack>> accessoriesCosmeticItems,
            Map<String, List<Boolean>> accessoriesRenderOptions) {
        this(curiosItems, curiosCosmeticItems, curiosRenderOptions, accessoriesItems,
                accessoriesCosmeticItems, accessoriesRenderOptions, ItemStack.EMPTY, false);
    }

    public RagdollAccessorySnapshot withBackpack(ItemStack stack, boolean armorSlot) {
        return new RagdollAccessorySnapshot(curiosItems, curiosCosmeticItems, curiosRenderOptions,
                accessoriesItems, accessoriesCosmeticItems, accessoriesRenderOptions, stack, armorSlot);
    }

    public boolean isEmpty() {
        return backpack.isEmpty() && curiosItems.isEmpty()
                && curiosCosmeticItems.isEmpty()
                && curiosRenderOptions.isEmpty()
                && accessoriesItems.isEmpty()
                && accessoriesCosmeticItems.isEmpty()
                && accessoriesRenderOptions.isEmpty();
    }

    public RagdollAccessorySnapshot withCurios(
            Map<String, List<ItemStack>> items,
            Map<String, List<ItemStack>> cosmetics,
            Map<String, List<Boolean>> renderOptions) {
        return new RagdollAccessorySnapshot(
                items,
                cosmetics,
                renderOptions,
                accessoriesItems,
                accessoriesCosmeticItems,
                accessoriesRenderOptions, backpack, backpackInArmorSlot);
    }

    public RagdollAccessorySnapshot withAccessories(
            Map<String, List<ItemStack>> items,
            Map<String, List<ItemStack>> cosmetics,
            Map<String, List<Boolean>> renderOptions) {
        return new RagdollAccessorySnapshot(
                curiosItems,
                curiosCosmeticItems,
                curiosRenderOptions,
                items,
                cosmetics,
                renderOptions, backpack, backpackInArmorSlot);
    }

    private static Map<String, List<ItemStack>> copyItems(
            Map<String, List<ItemStack>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        source.forEach((slot, stacks) -> {
            if (slot == null || slot.isBlank() || stacks == null) {
                return;
            }
            List<ItemStack> copies = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                copies.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
            result.put(slot, List.copyOf(copies));
        });
        return Map.copyOf(result);
    }

    private static Map<String, List<Boolean>> copyBooleans(
            Map<String, List<Boolean>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Boolean>> result = new LinkedHashMap<>();
        source.forEach((slot, values) -> {
            if (slot != null && !slot.isBlank() && values != null) {
                result.put(slot, List.copyOf(values));
            }
        });
        return Map.copyOf(result);
    }
}
