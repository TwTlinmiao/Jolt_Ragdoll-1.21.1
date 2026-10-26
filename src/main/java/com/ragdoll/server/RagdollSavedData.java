package com.ragdoll.server;

import com.ragdoll.RagdollMod;
import com.ragdoll.client.physics.RagdollPhysicsState;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.network.RagdollSpawnPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent authoritative ragdolls for one server dimension. */
public final class RagdollSavedData extends SavedData {
    private static final String DATA_NAME = "joltragdoll_ragdolls";
    private static final Factory<RagdollSavedData> FACTORY =
            new Factory<>(
                    RagdollSavedData::new,
                    RagdollSavedData::load,
                    null);

    private final Map<UUID, StoredRagdoll> entries = new LinkedHashMap<>();

    public static RagdollSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<StoredRagdoll> entries() {
        return List.copyOf(entries.values());
    }

    public void put(
            RagdollSpawnPacket packet,
            RagdollPhysicsState state,
            long ageTicks) {
        StoredRagdoll previous = entries.get(packet.ragdollId());
        entries.put(
                packet.ragdollId(),
                new StoredRagdoll(
                        packet,
                        state,
                        Math.max(0L, ageTicks),
                        previous == null ? List.of() : previous.loot()));
        setDirty();
    }

    public boolean setLoot(UUID ragdollId, List<ItemStack> loot) {
        StoredRagdoll previous = entries.get(ragdollId);
        if (previous == null) {
            return false;
        }
        List<ItemStack> copiedLoot = copyLoot(loot);
        RagdollSpawnPacket packet = previous.packet();
        if (!previous.loot().isEmpty() || !copiedLoot.isEmpty()) {
            packet = withLootAppearance(packet, copiedLoot);
        }
        entries.put(ragdollId, new StoredRagdoll(
                packet, previous.state(), previous.ageTicks(), copiedLoot));
        setDirty();
        return true;
    }

    private static RagdollSpawnPacket withLootAppearance(
            RagdollSpawnPacket packet, List<ItemStack> loot) {
        RagdollDeathSnapshot old = packet.snapshot();
        ItemStack helmet = itemAt(loot, 36);
        ItemStack chest = itemAt(loot, 37);
        ItemStack legs = itemAt(loot, 38);
        ItemStack boots = itemAt(loot, 39);
        ItemStack offhand = itemAt(loot, 40);
        ItemStack mainHand = containsMatching(loot, 0, Math.min(36, loot.size()), old.mainHand())
                ? old.mainHand() : ItemStack.EMPTY;
        RagdollAccessorySnapshot accessories = updateAccessories(old.accessories(), loot);
        RagdollDeathSnapshot updated = new RagdollDeathSnapshot(
                old.ragdollId(), old.playerId(), old.playerName(), old.modelScale(),
                old.skinTextureValue(), old.skinTextureSignature(), old.x(), old.y(), old.z(),
                old.yaw(), old.initialPose(), old.xRot(), old.velocityX(), old.velocityY(),
                old.velocityZ(), old.shownModelParts(), helmet, chest, legs, boots,
                old.mainArmLeft(), mainHand, offhand, accessories);
        return new RagdollSpawnPacket(updated);
    }

    private static RagdollAccessorySnapshot updateAccessories(
            RagdollAccessorySnapshot original, List<ItemStack> loot) {
        List<ItemStack> available = new ArrayList<>();
        for (int i = 41; i < loot.size(); ++i) {
            if (!loot.get(i).isEmpty()) available.add(loot.get(i).copy());
        }
        Map<String, List<ItemStack>> curios = filterAccessoryMap(
                original.curiosItems(), available);
        Map<String, List<ItemStack>> curiosCosmetics = filterAccessoryMap(
                original.curiosCosmeticItems(), available);
        Map<String, List<ItemStack>> accessories = filterAccessoryMap(
                original.accessoriesItems(), available);
        Map<String, List<ItemStack>> accessoryCosmetics = filterAccessoryMap(
                original.accessoriesCosmeticItems(), available);
        ItemStack backpack = consumeMatching(available, original.backpack())
                ? original.backpack() : ItemStack.EMPTY;
        return new RagdollAccessorySnapshot(
                curios, curiosCosmetics, original.curiosRenderOptions(),
                accessories, accessoryCosmetics, original.accessoriesRenderOptions(),
                backpack, original.backpackInArmorSlot());
    }

    private static Map<String, List<ItemStack>> filterAccessoryMap(
            Map<String, List<ItemStack>> source, List<ItemStack> available) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        source.forEach((slot, stacks) -> {
            List<ItemStack> filtered = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                filtered.add(consumeMatching(available, stack)
                        ? stack.copy() : ItemStack.EMPTY);
            }
            result.put(slot, filtered);
        });
        return result;
    }

    private static boolean containsMatching(
            List<ItemStack> items, int start, int end, ItemStack target) {
        if (target.isEmpty()) return false;
        for (int i = start; i < end; ++i) {
            if (ItemStack.isSameItemSameComponents(items.get(i), target)) return true;
        }
        return false;
    }

    private static boolean consumeMatching(List<ItemStack> items, ItemStack target) {
        if (target.isEmpty()) return false;
        for (ItemStack item : items) {
            if (ItemStack.isSameItemSameComponents(item, target)
                    && item.getCount() == target.getCount()) {
                items.remove(item);
                return true;
            }
        }
        return false;
    }

    private static ItemStack itemAt(List<ItemStack> items, int slot) {
        return slot < items.size() ? items.get(slot).copy() : ItemStack.EMPTY;
    }

    public boolean hasLoot(UUID ragdollId) {
        StoredRagdoll stored = entries.get(ragdollId);
        return stored != null && stored.loot().stream().anyMatch(
                stack -> !stack.isEmpty());
    }

    public boolean remove(UUID ragdollId) {
        if (entries.remove(ragdollId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public int clear() {
        int count = entries.size();
        if (count > 0) {
            entries.clear();
            setDirty();
        }
        return count;
    }

    private static RagdollSavedData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        RagdollSavedData data = new RagdollSavedData();
        ListTag list = tag.getList("Ragdolls", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); ++i) {
            try {
                StoredRagdoll stored =
                        loadStored(list.getCompound(i), registries);
                data.entries.put(stored.packet.ragdollId(), stored);
            } catch (RuntimeException exception) {
                RagdollMod.LOGGER.warn(
                        "Skipped invalid saved Jolt ragdoll", exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (StoredRagdoll stored : entries.values()) {
            list.add(saveStored(stored, registries));
        }
        tag.put("Ragdolls", list);
        return tag;
    }

    private static CompoundTag saveStored(
            StoredRagdoll stored, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Snapshot", saveSnapshot(stored.packet.snapshot(), registries));
        tag.put("Physics", savePhysics(stored.state));
        tag.putLong("Age", stored.ageTicks);
        tag.putInt("LootLayout", 2);
        tag.put("Loot", saveLoot(stored.loot, registries));
        return tag;
    }

    private static StoredRagdoll loadStored(
            CompoundTag tag, HolderLookup.Provider registries) {
        RagdollDeathSnapshot snapshot =
                loadSnapshot(tag.getCompound("Snapshot"), registries);
        RagdollPhysicsState state =
                loadPhysics(tag.getCompound("Physics"));
        return new StoredRagdoll(
                new RagdollSpawnPacket(snapshot),
                state,
                Math.max(0L, tag.getLong("Age")),
                loadLoot(
                        tag.getList("Loot", Tag.TAG_COMPOUND),
                        registries,
                        tag.getInt("LootLayout")));
    }

    private static ListTag saveLoot(
            List<ItemStack> loot, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack item : loot) {
            CompoundTag entry = new CompoundTag();
            saveItem(entry, registries, "Item", item);
            list.add(entry);
        }
        return list;
    }

    private static List<ItemStack> loadLoot(
            ListTag list,
            HolderLookup.Provider registries,
            int layoutVersion) {
        List<ItemStack> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); ++i) {
            ItemStack item = loadItem(list.getCompound(i), registries, "Item");
            result.add(item);
        }
        if (layoutVersion >= 2) return copyLoot(result);

        List<ItemStack> migrated = new ArrayList<>(41);
        for (int i = 0; i < 41; ++i) migrated.add(ItemStack.EMPTY);
        int equipmentCount = Math.min(5, result.size());
        for (int i = 0; i < equipmentCount; ++i) {
            migrated.set(36 + i, result.get(i));
        }
        int inventoryCount = Math.min(36, result.size() - equipmentCount);
        for (int i = 0; i < inventoryCount; ++i) {
            migrated.set(i, result.get(equipmentCount + i));
        }
        for (int i = equipmentCount + inventoryCount; i < result.size(); ++i) {
            migrated.add(result.get(i));
        }
        return copyLoot(migrated);
    }

    private static List<ItemStack> copyLoot(List<ItemStack> loot) {
        List<ItemStack> result = new ArrayList<>(loot.size());
        for (int i = 0; i < loot.size(); ++i) {
            ItemStack stack = loot.get(i);
            if (i < 41) {
                result.add(stack == null ? ItemStack.EMPTY : stack.copy());
            } else if (stack != null && !stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }

    private static CompoundTag saveSnapshot(
            RagdollDeathSnapshot snapshot,
            HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("RagdollId", snapshot.ragdollId());
        tag.putUUID("PlayerId", snapshot.playerId());
        tag.putString("PlayerName", snapshot.playerName());
        tag.putFloat("ModelScale", snapshot.modelScale());
        tag.putString("SkinValue", snapshot.skinTextureValue());
        tag.putString("SkinSignature", snapshot.skinTextureSignature());
        tag.putDouble("X", snapshot.x());
        tag.putDouble("Y", snapshot.y());
        tag.putDouble("Z", snapshot.z());
        tag.putFloat("Yaw", snapshot.yaw());
        tag.putString("InitialPose", snapshot.initialPose().name());
        tag.putFloat("XRot", snapshot.xRot());
        tag.putFloat("VelocityX", snapshot.velocityX());
        tag.putFloat("VelocityY", snapshot.velocityY());
        tag.putFloat("VelocityZ", snapshot.velocityZ());
        tag.putInt("ShownModelParts", snapshot.shownModelParts());
        saveItem(tag, registries, "Helmet", snapshot.helmet());
        saveItem(tag, registries, "Chestplate", snapshot.chestplate());
        saveItem(tag, registries, "Leggings", snapshot.leggings());
        saveItem(tag, registries, "Boots", snapshot.boots());
        tag.putBoolean("MainArmLeft", snapshot.mainArmLeft());
        saveItem(tag, registries, "MainHand", snapshot.mainHand());
        saveItem(tag, registries, "OffHand", snapshot.offHand());
        tag.put(
                "Accessories",
                saveAccessories(snapshot.accessories(), registries));
        return tag;
    }

    private static RagdollDeathSnapshot loadSnapshot(
            CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID("RagdollId") || !tag.hasUUID("PlayerId")) {
            throw new IllegalArgumentException("Missing ragdoll identity");
        }
        RagdollInitialPose initialPose;
        try {
            initialPose = RagdollInitialPose.valueOf(
                    tag.getString("InitialPose"));
        } catch (IllegalArgumentException ignored) {
            initialPose = RagdollInitialPose.STANDING;
        }
        return new RagdollDeathSnapshot(
                tag.getUUID("RagdollId"),
                tag.getUUID("PlayerId"),
                tag.getString("PlayerName"),
                tag.getFloat("ModelScale"),
                tag.getString("SkinValue"),
                tag.getString("SkinSignature"),
                tag.getDouble("X"),
                tag.getDouble("Y"),
                tag.getDouble("Z"),
                tag.getFloat("Yaw"),
                initialPose,
                tag.getFloat("XRot"),
                tag.getFloat("VelocityX"),
                tag.getFloat("VelocityY"),
                tag.getFloat("VelocityZ"),
                tag.getInt("ShownModelParts"),
                loadItem(tag, registries, "Helmet"),
                loadItem(tag, registries, "Chestplate"),
                loadItem(tag, registries, "Leggings"),
                loadItem(tag, registries, "Boots"),
                tag.getBoolean("MainArmLeft"),
                loadItem(tag, registries, "MainHand"),
                loadItem(tag, registries, "OffHand"),
                loadAccessories(tag.getCompound("Accessories"), registries));
    }

    private static CompoundTag savePhysics(RagdollPhysicsState state) {
        CompoundTag tag = new CompoundTag();
        ListTag parts = new ListTag();
        for (RagdollPhysicsState.PartState part : state.parts()) {
            CompoundTag partTag = new CompoundTag();
            partTag.putString("Name", part.partName());
            partTag.putDouble("X", part.x());
            partTag.putDouble("Y", part.y());
            partTag.putDouble("Z", part.z());
            partTag.putFloat("RotationX", part.rotationX());
            partTag.putFloat("RotationY", part.rotationY());
            partTag.putFloat("RotationZ", part.rotationZ());
            partTag.putFloat("RotationW", part.rotationW());
            partTag.putFloat("LinearX", part.linearX());
            partTag.putFloat("LinearY", part.linearY());
            partTag.putFloat("LinearZ", part.linearZ());
            partTag.putFloat("AngularX", part.angularX());
            partTag.putFloat("AngularY", part.angularY());
            partTag.putFloat("AngularZ", part.angularZ());
            partTag.putBoolean("Frozen", part.frozen());
            parts.add(partTag);
        }
        tag.put("Parts", parts);
        return tag;
    }

    private static RagdollPhysicsState loadPhysics(CompoundTag tag) {
        ListTag parts = tag.getList("Parts", Tag.TAG_COMPOUND);
        if (parts.isEmpty() || parts.size() > 16) {
            throw new IllegalArgumentException("Invalid saved physics state");
        }
        List<RagdollPhysicsState.PartState> result =
                new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); ++i) {
            CompoundTag part = parts.getCompound(i);
            result.add(new RagdollPhysicsState.PartState(
                    part.getString("Name"),
                    part.getDouble("X"),
                    part.getDouble("Y"),
                    part.getDouble("Z"),
                    part.getFloat("RotationX"),
                    part.getFloat("RotationY"),
                    part.getFloat("RotationZ"),
                    part.getFloat("RotationW"),
                    part.getFloat("LinearX"),
                    part.getFloat("LinearY"),
                    part.getFloat("LinearZ"),
                    part.getFloat("AngularX"),
                    part.getFloat("AngularY"),
                    part.getFloat("AngularZ"),
                    part.getBoolean("Frozen")));
        }
        return new RagdollPhysicsState(result);
    }

    private static CompoundTag saveAccessories(
            RagdollAccessorySnapshot snapshot,
            HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("CuriosItems", saveItemMap(snapshot.curiosItems(), registries));
        saveItem(tag, registries, "Backpack", snapshot.backpack());
        tag.putBoolean("BackpackInArmorSlot", snapshot.backpackInArmorSlot());
        tag.put(
                "CuriosCosmetics",
                saveItemMap(snapshot.curiosCosmeticItems(), registries));
        tag.put(
                "CuriosRender",
                saveBooleanMap(snapshot.curiosRenderOptions()));
        tag.put(
                "AccessoriesItems",
                saveItemMap(snapshot.accessoriesItems(), registries));
        tag.put(
                "AccessoriesCosmetics",
                saveItemMap(snapshot.accessoriesCosmeticItems(), registries));
        tag.put(
                "AccessoriesRender",
                saveBooleanMap(snapshot.accessoriesRenderOptions()));
        return tag;
    }

    private static RagdollAccessorySnapshot loadAccessories(
            CompoundTag tag, HolderLookup.Provider registries) {
        return new RagdollAccessorySnapshot(
                loadItemMap(tag, registries, "CuriosItems"),
                loadItemMap(tag, registries, "CuriosCosmetics"),
                loadBooleanMap(tag, "CuriosRender"),
                loadItemMap(tag, registries, "AccessoriesItems"),
                loadItemMap(tag, registries, "AccessoriesCosmetics"),
                loadBooleanMap(tag, "AccessoriesRender"),
                loadItem(tag, registries, "Backpack"),
                tag.getBoolean("BackpackInArmorSlot"));
    }

    private static ListTag saveItemMap(
            Map<String, List<ItemStack>> slots,
            HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        slots.forEach((slot, stacks) -> {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putString("Slot", slot);
            ListTag stackTags = new ListTag();
            for (ItemStack stack : stacks) {
                CompoundTag stackTag = new CompoundTag();
                saveItem(stackTag, registries, "Item", stack);
                stackTags.add(stackTag);
            }
            slotTag.put("Stacks", stackTags);
            result.add(slotTag);
        });
        return result;
    }

    private static Map<String, List<ItemStack>> loadItemMap(
            CompoundTag parent,
            HolderLookup.Provider registries,
            String key) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        ListTag slots = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); ++i) {
            CompoundTag slotTag = slots.getCompound(i);
            ListTag stackTags =
                    slotTag.getList("Stacks", Tag.TAG_COMPOUND);
            List<ItemStack> stacks = new ArrayList<>(stackTags.size());
            for (int j = 0; j < stackTags.size(); ++j) {
                stacks.add(loadItem(
                        stackTags.getCompound(j), registries, "Item"));
            }
            result.put(slotTag.getString("Slot"), stacks);
        }
        return result;
    }

    private static ListTag saveBooleanMap(
            Map<String, List<Boolean>> slots) {
        ListTag result = new ListTag();
        slots.forEach((slot, values) -> {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putString("Slot", slot);
            ListTag options = new ListTag();
            for (boolean value : values) {
                options.add(ByteTag.valueOf(value));
            }
            slotTag.put("Values", options);
            result.add(slotTag);
        });
        return result;
    }

    private static Map<String, List<Boolean>> loadBooleanMap(
            CompoundTag parent, String key) {
        Map<String, List<Boolean>> result = new LinkedHashMap<>();
        ListTag slots = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); ++i) {
            CompoundTag slotTag = slots.getCompound(i);
            ListTag options = slotTag.getList("Values", Tag.TAG_BYTE);
            List<Boolean> values = new ArrayList<>(options.size());
            for (int j = 0; j < options.size(); ++j) {
                values.add(
                        options.get(j) instanceof NumericTag numeric
                                && numeric.getAsByte() != 0);
            }
            result.put(slotTag.getString("Slot"), values);
        }
        return result;
    }

    private static void saveItem(
            CompoundTag tag,
            HolderLookup.Provider registries,
            String key,
            ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            tag.put(key, stack.save(registries));
        }
    }

    private static ItemStack loadItem(
            CompoundTag tag,
            HolderLookup.Provider registries,
            String key) {
        Tag itemTag = tag.get(key);
        return itemTag == null
                ? ItemStack.EMPTY
                : ItemStack.parse(registries, itemTag)
                .orElse(ItemStack.EMPTY);
    }

    public record StoredRagdoll(
            RagdollSpawnPacket packet,
            RagdollPhysicsState state,
            long ageTicks,
            List<ItemStack> loot) {
        public StoredRagdoll {
            loot = copyLoot(loot);
        }
    }
}
