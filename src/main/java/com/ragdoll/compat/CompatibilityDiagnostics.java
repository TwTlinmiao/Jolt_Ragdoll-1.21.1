package com.ragdoll.compat;

import com.ragdoll.RagdollMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Report each failing integration/item once instead of once per frame. */
public final class CompatibilityDiagnostics {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private CompatibilityDiagnostics() {}

    public static void failure(String integration, ItemStack stack, Exception error) {
        String key = integration + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (REPORTED.add(key)) RagdollMod.LOGGER.warn("Ragdoll compatibility failure [{}]", key, error);
    }

    public static void missing(String integration, ItemStack stack, String reason) {
        String key = integration + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + reason;
        if (REPORTED.add(key)) RagdollMod.LOGGER.debug("Ragdoll compatibility [{}]: {}", key, reason);
    }
}
