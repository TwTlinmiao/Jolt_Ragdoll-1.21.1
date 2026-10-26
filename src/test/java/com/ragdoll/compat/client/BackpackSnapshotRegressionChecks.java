package com.ragdoll.compat.client;

import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.network.RagdollSpawnPacket;
import com.ragdoll.server.RagdollSavedData;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BackpackSnapshotRegressionChecks {
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The standalone harness bypasses NeoForge's registry-sync setup.
        var setSync = net.neoforged.neoforge.registries.BaseMappedRegistry.class.getDeclaredMethod("setSync", boolean.class);
        setSync.setAccessible(true);
        setSync.invoke(BuiltInRegistries.ITEM, true);
        setSync.invoke(BuiltInRegistries.DATA_COMPONENT_TYPE, true);
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var original = new ItemStack(Items.LEATHER_CHESTPLATE);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("saved backpack appearance"));
        var snapshot = RagdollAccessorySnapshot.EMPTY.withBackpack(original, true)
                .withCurios(Map.of("back", List.of(new ItemStack(Items.STICK))), Map.of(), Map.of())
                .withAccessories(Map.of(), Map.of(), Map.of());
        original.setCount(0);
        check(!snapshot.isEmpty() && !snapshot.backpack().isEmpty() && snapshot.backpackInArmorSlot(),
                "native backpack survives source mutation and accessory merges");

        var write = RagdollSpawnPacket.class.getDeclaredMethod("writeAccessorySnapshot",
                RegistryFriendlyByteBuf.class, RagdollAccessorySnapshot.class);
        var read = RagdollSpawnPacket.class.getDeclaredMethod("readAccessorySnapshot", RegistryFriendlyByteBuf.class);
        write.setAccessible(true);
        read.setAccessible(true);
        for (boolean armorSlot : new boolean[]{true, false}) {
            var input = snapshot.withBackpack(snapshot.backpack(), armorSlot);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                write.invoke(null, buffer, input);
                var decoded = (RagdollAccessorySnapshot) read.invoke(null, buffer);
                check(ItemStack.matches(input.backpack(), decoded.backpack())
                        && decoded.backpackInArmorSlot() == armorSlot && !buffer.isReadable(),
                        "network preserves backpack components, placement and packet boundaries");
            } finally {
                buffer.release();
            }
            var save = RagdollSavedData.class.getDeclaredMethod("saveAccessories",
                    RagdollAccessorySnapshot.class, HolderLookup.Provider.class);
            var load = RagdollSavedData.class.getDeclaredMethod("loadAccessories",
                    CompoundTag.class, HolderLookup.Provider.class);
            save.setAccessible(true);
            load.setAccessible(true);
            var tag = (CompoundTag) save.invoke(null, input, registries);
            var loaded = (RagdollAccessorySnapshot) load.invoke(null, tag, registries);
            check(ItemStack.matches(input.backpack(), loaded.backpack())
                    && loaded.backpackInArmorSlot() == armorSlot, "save/load preserves native backpack");
            tag.remove("Backpack");
            tag.remove("BackpackInArmorSlot");
            var legacy = (RagdollAccessorySnapshot) load.invoke(null, tag, registries);
            check(legacy.backpack().isEmpty() && legacy.curiosItems().containsKey("back"),
                    "old saves still load their Curios equipment");
        }
        check(!RagdollAccessorySnapshot.EMPTY.withBackpack(new ItemStack(Items.STICK), false).isEmpty(),
                "native backpack alone activates optional rendering");
        System.out.println("Backpack snapshot checks passed (copying, network, saves, legacy saves).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
