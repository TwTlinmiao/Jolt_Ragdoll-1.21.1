package com.ragdoll.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared by optional integrations; contains no optional mod classes. */
public final class AccessoryPartBindings {
    private static final List<String> PARTS = List.of(
            "head", "body", "left_arm", "right_arm", "left_leg", "right_leg");
    private static final Map<ResourceLocation, Set<String>> OVERRIDES = new ConcurrentHashMap<>();

    private AccessoryPartBindings() {}

    /** Register an exceptional renderer's attachment points during client setup. */
    public static void register(ResourceLocation item, Set<String> parts) {
        if (parts.isEmpty() || !PARTS.containsAll(parts)) {
            throw new IllegalArgumentException("Unknown ragdoll body parts: " + parts);
        }
        OVERRIDES.put(item, Set.copyOf(parts));
    }

    public static boolean belongsToPart(ItemStack stack, String slot, int index,
                                        String part, boolean splitHands) {
        Set<String> override = OVERRIDES.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (override != null) return override.contains(part);
        // Datapacks can bind even a generic curio/charm slot to exact body parts.
        boolean explicit = false;
        boolean selected = false;
        for (String candidate : PARTS) {
            if (tag(stack, "ragdoll", "accessory_parts/" + candidate)) {
                explicit = true;
                selected |= candidate.equals(part);
            }
        }
        if (explicit) return selected;
        String group = group(slot.toLowerCase(Locale.ROOT));
        if (group == null) {
            Set<String> groups = new java.util.HashSet<>();
            stack.getTags().forEach(key -> {
                ResourceLocation id = key.location();
                if (id.getNamespace().equals("curios") || id.getNamespace().equals("accessories")
                        || id.getNamespace().equals("artifacts")) {
                    String path = id.getPath();
                    String inferred = group(path.substring(path.lastIndexOf('/') + 1));
                    if (inferred != null) groups.add(inferred);
                }
            });
            // Broad "can equip anywhere" tags are ambiguous, so do not pick an
            // arbitrary limb. An explicit datapack binding resolves that case.
            if (groups.size() == 1) group = groups.iterator().next();
        }
        return matchesGroup(group == null ? "body" : group, slot.toLowerCase(Locale.ROOT), index, part, splitHands);
    }

    /** Slot-only fallback, also useful to integrations without a stack context. */
    public static boolean slotBelongsToPart(String slot, int index, String part, boolean splitHands) {
        String normalized = slot.toLowerCase(Locale.ROOT);
        String group = group(normalized);
        return matchesGroup(group == null ? "body" : group, normalized, index, part, splitHands);
    }

    private static boolean matchesGroup(String group, String slot, int index, String part, boolean splitHands) {
        return switch (group) {
            case "arms" -> splitHands && Set.of("hand", "ring").contains(slot)
                    ? part.equals(index % 2 == 0 ? "right_arm" : "left_arm")
                    : part.equals("left_arm") || part.equals("right_arm");
            case "legs" -> part.equals("left_leg") || part.equals("right_leg");
            default -> part.equals(group);
        };
    }

    private static boolean tag(ItemStack stack, String namespace, String path) {
        return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }

    private static String group(String value) {
        if (value.equals("ear") || value.equals("ears") || contains(value, "head", "hat", "helmet", "hair", "face", "mask", "goggle", "glass", "crown", "circlet", "earring")) return "head";
        if (contains(value, "hand", "ring", "glove", "gauntlet", "wrist", "bracelet", "bracer")) return "arms";
        if (contains(value, "feet", "foot", "boot", "shoe", "sock", "ankle", "leg", "shin")) return "legs";
        if (contains(value, "body", "chest", "neck", "belt", "back", "cape", "shoulder")) return "body";
        return null;
    }

    private static boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
