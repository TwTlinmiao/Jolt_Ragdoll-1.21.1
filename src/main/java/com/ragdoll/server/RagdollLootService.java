package com.ragdoll.server;

import com.ragdoll.compat.RagdollAccessoryCapture;
import com.ragdoll.client.physics.ServerRagdollManager;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.network.RagdollSpawnPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

public final class RagdollLootService {
    private static final int MENU_SLOTS = 54;
    private static final double OPEN_DISTANCE_SQUARED = 64.0;
    private static final Set<UUID> CAPTURED_DEATHS = new HashSet<>();

    private RagdollLootService() {
    }

    public static void captureOnDeath(ServerPlayer player, UUID ragdollId) {
        if (!com.ragdoll.RagdollConfig.corpseRagdollEnabled()
                || player.serverLevel().getGameRules()
                .getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        List<ItemStack> loot = new ArrayList<>(41);
        var inventory = player.getInventory();
        for (int i = 0; i < 36; ++i) {
            loot.add(inventory.getItem(i).copy());
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            loot.add(player.getItemBySlot(slot).copy());
        }
        loot.add(player.getOffhandItem().copy());
        for (ItemStack stack : RagdollAccessoryCapture.takeItems(player)) {
            loot.add(stack.copy());
        }

        if (!RagdollSavedData.get(player.serverLevel())
                .setLoot(ragdollId, loot)) {
            return;
        }
        ServerRagdollManager.syncAppearance(player.serverLevel(), ragdollId);
        if (loot.stream().anyMatch(stack -> !stack.isEmpty())) {
            CAPTURED_DEATHS.add(player.getUUID());
        }
        for (int i = 0; i < inventory.getContainerSize(); ++i) {
            inventory.setItem(i, ItemStack.EMPTY);
        }
    }

    public static void suppressVanillaDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CAPTURED_DEATHS.remove(player.getUUID())) {
            event.getDrops().clear();
        }
    }

    public static void open(ServerPlayer player, UUID ragdollId, boolean pickup) {
        RagdollSavedData savedData = RagdollSavedData.get(player.serverLevel());
        RagdollSavedData.StoredRagdoll stored = savedData.entries().stream()
                .filter(entry -> entry.packet().ragdollId().equals(ragdollId))
                .findFirst().orElse(null);
        if (stored == null
                || !com.ragdoll.RagdollConfig.corpseRagdollEnabled()
                || (com.ragdoll.RagdollConfig.corpseOwnerOnly()
                && !stored.packet().snapshot().playerId().equals(player.getUUID()))) {
            return;
        }
        Vec3 position = bodyPosition(stored);
        if (position == null
                || player.position().distanceToSqr(position)
                > OPEN_DISTANCE_SQUARED) {
            return;
        }
        if (pickup) {
            List<ItemStack> remaining = restoreItems(
                    player, stored.loot(), stored.packet().snapshot().accessories());
            savedData.setLoot(ragdollId, remaining);
            com.ragdoll.client.physics.ServerRagdollManager
                    .syncAppearance(player.serverLevel(), ragdollId);
            return;
        }

        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("container.ragdoll.corpse",
                        stored.packet().snapshot().playerName());
            }

            @Override
            public AbstractContainerMenu createMenu(
                    int containerId,
                    net.minecraft.world.entity.player.Inventory playerInventory,
                    net.minecraft.world.entity.player.Player ignored) {
                return new CorpseLootMenu(
                        containerId,
                        playerInventory,
                        savedData,
                        ragdollId,
                        stored.packet().snapshot().playerName(),
                        stored.loot());
            }
        };
        player.openMenu(provider, buffer -> buffer
                .writeUUID(ragdollId)
                .writeUtf(stored.packet().snapshot().playerName(), 64));
    }

    public static boolean clearRagdoll(
            ServerPlayer player, UUID ragdollId, List<ItemStack> currentLoot) {
        RagdollSavedData savedData = RagdollSavedData.get(player.serverLevel());
        RagdollSavedData.StoredRagdoll stored = savedData.entries().stream()
                .filter(entry -> entry.packet().ragdollId().equals(ragdollId))
                .findFirst().orElse(null);
        if (stored == null
                || !stored.packet().snapshot().playerId().equals(player.getUUID())) {
            return false;
        }
        Vec3 position = bodyPosition(stored);
        if (position == null
                || player.position().distanceToSqr(position) > OPEN_DISTANCE_SQUARED) {
            return false;
        }
        for (ItemStack stack : currentLoot) {
            if (stack == null || stack.isEmpty()) continue;
            player.serverLevel().addFreshEntity(new ItemEntity(
                    player.serverLevel(), position.x, position.y, position.z,
                    stack.copy()));
        }
        savedData.setLoot(ragdollId, List.of());
        boolean removed = ServerRagdollManager.removeEmptyRagdoll(
                player.serverLevel(), ragdollId);
        if (removed) player.closeContainer();
        return removed;
    }

    static List<ItemStack> restoreItems(
            Player player, List<ItemStack> loot, RagdollAccessorySnapshot accessories) {
        List<ItemStack> remaining = new ArrayList<>(loot.size());
        for (ItemStack stack : loot) remaining.add(stack.copy());
        var inventory = player.getInventory();

        for (int slot = 0; slot < 36 && slot < remaining.size(); ++slot) {
            ItemStack saved = remaining.get(slot);
            if (saved.isEmpty()) continue;
            ItemStack current = inventory.getItem(slot);
            if (current.isEmpty()) {
                inventory.setItem(slot, saved.copy());
                remaining.set(slot, ItemStack.EMPTY);
            } else if (ItemStack.isSameItemSameComponents(current, saved)
                    && current.getCount() + saved.getCount() <= current.getMaxStackSize()) {
                current.grow(saved.getCount());
                remaining.set(slot, ItemStack.EMPTY);
            } else {
                inventory.setItem(slot, saved.copy());
                remaining.set(slot, current.copy());
            }
        }

        EquipmentSlot[] equipmentSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND};
        for (int i = 0; i < equipmentSlots.length && 36 + i < remaining.size(); ++i) {
            int lootIndex = 36 + i;
            ItemStack saved = remaining.get(lootIndex);
            if (saved.isEmpty()) continue;
            EquipmentSlot equipmentSlot = equipmentSlots[i];
            ItemStack current = player.getItemBySlot(equipmentSlot);
            player.setItemSlot(equipmentSlot, saved.copy());
            remaining.set(lootIndex, current.copy());
        }

        if (remaining.size() > 41) {
            RagdollAccessoryCapture.restore(
                    player, accessories, remaining.subList(41, remaining.size()));
        }
        for (int i = 41; i < remaining.size(); ++i) {
            ItemStack stack = remaining.get(i);
            if (!stack.isEmpty() && inventory.add(stack)) {
                remaining.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }
        return remaining;
    }

    private static Vec3 bodyPosition(
            RagdollSavedData.StoredRagdoll stored) {
        for (var part : stored.state().parts()) {
            if ("body".equals(part.partName())) {
                return new Vec3(part.x(), part.y(), part.z());
            }
        }
        var snapshot = stored.packet().snapshot();
        return new Vec3(snapshot.x(), snapshot.y(), snapshot.z());
    }
}
