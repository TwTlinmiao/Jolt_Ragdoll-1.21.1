package com.ragdoll.compat;

import com.ragdoll.model.RagdollAccessorySnapshot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** Loads optional accessory integrations only when their mod is present. */
public final class RagdollAccessoryCapture {
    private RagdollAccessoryCapture() {
    }

    public static RagdollAccessorySnapshot capture(Player player) {
        RagdollAccessorySnapshot result = RagdollAccessorySnapshot.EMPTY;
        if (ModList.get().isLoaded("curios")) {
            result = CuriosRagdollCompat.capture(player, result);
        }
        if (ModList.get().isLoaded("accessories")) {
            result = AccessoriesRagdollCompat.capture(player, result);
        }
        if (ModList.get().isLoaded("sophisticatedbackpacks")) {
            result = SophisticatedBackpacksCompat.capture(player, result);
        }
        return result;
    }

    public static List<ItemStack> takeItems(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        if (ModList.get().isLoaded("curios")) {
            result.addAll(CuriosRagdollCompat.takeItems(player));
        }
        if (ModList.get().isLoaded("accessories")) {
            result.addAll(AccessoriesRagdollCompat.takeItems(player));
        }
        return result;
    }

    public static void restore(
            Player player, RagdollAccessorySnapshot snapshot, List<ItemStack> items) {
        if (ModList.get().isLoaded("curios")) {
            CuriosRagdollCompat.restore(player, snapshot, items);
        }
        if (ModList.get().isLoaded("accessories")) {
            AccessoriesRagdollCompat.restore(player, snapshot, items);
        }
    }

    public static AutoCloseable mirrorForRendering(
            Player player,
            RagdollAccessorySnapshot snapshot,
            String partName) {
        AutoCloseable curios = () -> {};
        if (ModList.get().isLoaded("curios")) {
            try {
                curios = CuriosRagdollCompat.mirrorForRendering(player, snapshot, partName);
            } catch (RuntimeException error) {
                CompatibilityDiagnostics.failure("curios/snapshot", net.minecraft.world.item.ItemStack.EMPTY, error);
            }
        }
        AutoCloseable accessories;
        try {
            accessories = ModList.get().isLoaded("accessories")
                    ? AccessoriesRagdollCompat.mirrorForRendering(player, snapshot, partName)
                    : () -> {};
        } catch (RuntimeException error) {
            CompatibilityDiagnostics.failure("accessories/snapshot", net.minecraft.world.item.ItemStack.EMPTY, error);
            accessories = () -> {};
        }
        AutoCloseable restoreCurios = curios;
        AutoCloseable restoreAccessories = accessories;
        return () -> {
            try {
                restoreAccessories.close();
            } finally {
                restoreCurios.close();
            }
        };
    }
}
