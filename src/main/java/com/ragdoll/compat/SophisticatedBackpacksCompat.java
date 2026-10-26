package com.ragdoll.compat;

import com.ragdoll.model.RagdollAccessorySnapshot;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Captures the mod's native rendered slot, excluding slots with their own Curios renderer. */
public final class SophisticatedBackpacksCompat {
    private static Method getProvider, getRendered, getBackpack, isArmorSlot;
    private static boolean initialized;

    private SophisticatedBackpacksCompat() {}

    public static RagdollAccessorySnapshot capture(Player player, RagdollAccessorySnapshot snapshot) {
        try {
            initialize();
            if (getProvider == null) return snapshot;
            Optional<?> info = (Optional<?>) getRendered.invoke(getProvider.invoke(null), player);
            if (info.isEmpty()) return snapshot;
            return snapshot.withBackpack((ItemStack) getBackpack.invoke(info.get()),
                    (boolean) isArmorSlot.invoke(info.get()));
        } catch (ReflectiveOperationException | RuntimeException error) {
            CompatibilityDiagnostics.failure("sophisticatedbackpacks/capture", ItemStack.EMPTY, error);
            return snapshot;
        }
    }

    private static synchronized void initialize() throws ReflectiveOperationException {
        if (initialized) return;
        initialized = true;
        Class<?> provider = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider");
        Class<?> info = Class.forName(provider.getName() + "$RenderInfo");
        getRendered = provider.getMethod("getBackpackFromRendered", Player.class);
        getBackpack = info.getMethod("getBackpack");
        isArmorSlot = info.getMethod("isArmorSlot");
        getProvider = provider.getMethod("get");
    }
}
