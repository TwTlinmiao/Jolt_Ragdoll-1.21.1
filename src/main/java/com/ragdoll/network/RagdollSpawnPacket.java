package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete spawn snapshot. Keeping equipment, pose and initial velocity in the
 * server packet lets every client render the same death ragdoll.
 */
public record RagdollSpawnPacket(
        RagdollDeathSnapshot snapshot,
        RagdollInitialLimbPose initialLimbPose,
        boolean liveRagdoll) implements CustomPacketPayload {
    public static final Type<RagdollSpawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "spawn_ragdoll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RagdollSpawnPacket> CODEC =
            StreamCodec.of(RagdollSpawnPacket::encode, RagdollSpawnPacket::decode);

    public RagdollSpawnPacket {
        if (snapshot == null) {
            throw new IllegalArgumentException("Missing ragdoll snapshot");
        }
    }

    public RagdollSpawnPacket(RagdollDeathSnapshot snapshot) {
        this(snapshot, null, false);
    }

    public RagdollSpawnPacket(RagdollDeathSnapshot snapshot,
                              RagdollInitialLimbPose initialLimbPose) {
        this(snapshot, initialLimbPose, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public UUID ragdollId() {
        return snapshot.ragdollId();
    }

    public RagdollDeathSnapshot toSnapshot() {
        return snapshot;
    }

    public static RagdollSpawnPacket command(
            UUID ragdollId,
            UUID playerId,
            String playerName,
            String skinTextureValue,
            String skinTextureSignature,
            double x,
            double y,
            double z,
            float yaw,
            SpawnStyle style) {
        ItemStack chest = style == SpawnStyle.ELYTRA
                ? new ItemStack(Items.ELYTRA)
                : ItemStack.EMPTY;
        return new RagdollSpawnPacket(new RagdollDeathSnapshot(
                ragdollId,
                playerId,
                playerName,
                RagdollDeathSnapshot.DEFAULT_MODEL_SCALE,
                skinTextureValue,
                skinTextureSignature,
                x,
                y,
                z,
                yaw,
                RagdollInitialPose.STANDING,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                shownModelParts(style),
                ItemStack.EMPTY,
                chest,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                false,
                ItemStack.EMPTY,
                ItemStack.EMPTY));
    }

    private static int shownModelParts(SpawnStyle style) {
        int result = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (part == PlayerModelPart.CAPE && style != SpawnStyle.CAPE) {
                continue;
            }
            result |= part.getMask();
        }
        return result;
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer, RagdollSpawnPacket packet) {
        RagdollDeathSnapshot snapshot = packet.snapshot;
        buffer.writeUUID(snapshot.ragdollId());
        buffer.writeUUID(snapshot.playerId());
        buffer.writeUtf(snapshot.playerName(), 64);
        buffer.writeFloat(snapshot.modelScale());
        buffer.writeUtf(snapshot.skinTextureValue(), 32767);
        buffer.writeUtf(snapshot.skinTextureSignature(), 1024);
        buffer.writeDouble(snapshot.x());
        buffer.writeDouble(snapshot.y());
        buffer.writeDouble(snapshot.z());
        buffer.writeFloat(snapshot.yaw());
        buffer.writeEnum(snapshot.initialPose());
        buffer.writeFloat(snapshot.xRot());
        buffer.writeFloat(snapshot.velocityX());
        buffer.writeFloat(snapshot.velocityY());
        buffer.writeFloat(snapshot.velocityZ());
        buffer.writeVarInt(snapshot.shownModelParts());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.helmet());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.chestplate());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.leggings());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.boots());
        buffer.writeBoolean(snapshot.mainArmLeft());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.mainHand());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.offHand());
        writeAccessorySnapshot(buffer, snapshot.accessories());
        writeLimbPose(buffer, packet.initialLimbPose);
        buffer.writeBoolean(packet.liveRagdoll);
    }

    private static RagdollSpawnPacket decode(RegistryFriendlyByteBuf buffer) {
        RagdollDeathSnapshot snapshot = new RagdollDeathSnapshot(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readFloat(),
                buffer.readUtf(32767),
                buffer.readUtf(1024),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readEnum(RagdollInitialPose.class),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readBoolean(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                readAccessorySnapshot(buffer));
        RagdollInitialLimbPose limbPose = readLimbPose(buffer);
        return new RagdollSpawnPacket(snapshot, limbPose, buffer.readBoolean());
    }

    private static void writeLimbPose(RegistryFriendlyByteBuf buffer,
                                      RagdollInitialLimbPose pose) {
        buffer.writeBoolean(pose != null);
        if (pose == null) return;
        for (float rotation : pose.values()) buffer.writeFloat(rotation);
    }

    private static RagdollInitialLimbPose readLimbPose(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        float[] values = new float[RagdollInitialLimbPose.VALUE_COUNT];
        for (int i = 0; i < values.length; ++i) values[i] = buffer.readFloat();
        return RagdollInitialLimbPose.fromValues(values);
    }

    private static void writeAccessorySnapshot(
            RegistryFriendlyByteBuf buffer,
            RagdollAccessorySnapshot snapshot) {
        writeItemMap(buffer, snapshot.curiosItems());
        writeItemMap(buffer, snapshot.curiosCosmeticItems());
        writeBooleanMap(buffer, snapshot.curiosRenderOptions());
        writeItemMap(buffer, snapshot.accessoriesItems());
        writeItemMap(buffer, snapshot.accessoriesCosmeticItems());
        writeBooleanMap(buffer, snapshot.accessoriesRenderOptions());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, snapshot.backpack());
        buffer.writeBoolean(snapshot.backpackInArmorSlot());
    }

    private static RagdollAccessorySnapshot readAccessorySnapshot(
            RegistryFriendlyByteBuf buffer) {
        return new RagdollAccessorySnapshot(
                readItemMap(buffer),
                readItemMap(buffer),
                readBooleanMap(buffer),
                readItemMap(buffer),
                readItemMap(buffer),
                readBooleanMap(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readBoolean());
    }

    private static void writeItemMap(
            RegistryFriendlyByteBuf buffer,
            Map<String, List<ItemStack>> slots) {
        buffer.writeVarInt(slots.size());
        slots.forEach((slot, stacks) -> {
            buffer.writeUtf(slot, 128);
            buffer.writeVarInt(stacks.size());
            for (ItemStack stack : stacks) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            }
        });
    }

    private static Map<String, List<ItemStack>> readItemMap(
            RegistryFriendlyByteBuf buffer) {
        int slotCount = checkedSize(buffer.readVarInt(), 128, "accessory slots");
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        for (int slotIndex = 0; slotIndex < slotCount; ++slotIndex) {
            String slot = buffer.readUtf(128);
            int stackCount = checkedSize(
                    buffer.readVarInt(), 64, "accessory stacks");
            List<ItemStack> stacks = new ArrayList<>(stackCount);
            for (int i = 0; i < stackCount; ++i) {
                stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            }
            result.put(slot, stacks);
        }
        return result;
    }

    private static void writeBooleanMap(
            RegistryFriendlyByteBuf buffer,
            Map<String, List<Boolean>> slots) {
        buffer.writeVarInt(slots.size());
        slots.forEach((slot, values) -> {
            buffer.writeUtf(slot, 128);
            buffer.writeVarInt(values.size());
            for (boolean value : values) {
                buffer.writeBoolean(value);
            }
        });
    }

    private static Map<String, List<Boolean>> readBooleanMap(
            RegistryFriendlyByteBuf buffer) {
        int slotCount = checkedSize(buffer.readVarInt(), 128, "render slots");
        Map<String, List<Boolean>> result = new LinkedHashMap<>();
        for (int slotIndex = 0; slotIndex < slotCount; ++slotIndex) {
            String slot = buffer.readUtf(128);
            int valueCount = checkedSize(
                    buffer.readVarInt(), 64, "render options");
            List<Boolean> values = new ArrayList<>(valueCount);
            for (int i = 0; i < valueCount; ++i) {
                values.add(buffer.readBoolean());
            }
            result.put(slot, values);
        }
        return result;
    }

    private static int checkedSize(int value, int maximum, String name) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(
                    "Invalid " + name + " count: " + value);
        }
        return value;
    }

    public enum SpawnStyle {
        NONE,
        CAPE,
        ELYTRA
    }
}
