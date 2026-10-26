package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RagdollPhysicsGunSyncPacket(
        List<GrabData> grabs,
        List<UUID> tryingPlayers) implements CustomPacketPayload {
    private static final int MAX_PLAYERS = 256;
    public static final Type<RagdollPhysicsGunSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    RagdollMod.MOD_ID, "physics_gun_sync"));
    public static final StreamCodec<FriendlyByteBuf,
            RagdollPhysicsGunSyncPacket> CODEC = StreamCodec.of(
            RagdollPhysicsGunSyncPacket::encode,
            RagdollPhysicsGunSyncPacket::decode);

    public RagdollPhysicsGunSyncPacket {
        grabs = List.copyOf(grabs);
        tryingPlayers = List.copyOf(tryingPlayers);
    }

    private static void encode(
            FriendlyByteBuf buffer,
            RagdollPhysicsGunSyncPacket packet) {
        buffer.writeVarInt(packet.grabs.size());
        for (GrabData grab : packet.grabs) {
            buffer.writeUUID(grab.playerId);
            buffer.writeUUID(grab.ragdollId);
            buffer.writeUtf(grab.partName, 16);
            buffer.writeDouble(grab.localX);
            buffer.writeDouble(grab.localY);
            buffer.writeDouble(grab.localZ);
        }
        buffer.writeVarInt(packet.tryingPlayers.size());
        for (UUID playerId : packet.tryingPlayers) {
            buffer.writeUUID(playerId);
        }
    }

    private static RagdollPhysicsGunSyncPacket decode(
            FriendlyByteBuf buffer) {
        int grabCount = buffer.readVarInt();
        if (grabCount < 0 || grabCount > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "Invalid physics gun grab count");
        }
        List<GrabData> grabs = new ArrayList<>(grabCount);
        for (int i = 0; i < grabCount; ++i) {
            grabs.add(new GrabData(
                    buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(16),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()));
        }
        int tryingCount = buffer.readVarInt();
        if (tryingCount < 0 || tryingCount > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "Invalid physics gun attempt count");
        }
        List<UUID> trying = new ArrayList<>(tryingCount);
        for (int i = 0; i < tryingCount; ++i) {
            trying.add(buffer.readUUID());
        }
        return new RagdollPhysicsGunSyncPacket(grabs, trying);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record GrabData(
            UUID playerId,
            UUID ragdollId,
            String partName,
            double localX,
            double localY,
            double localZ) {
    }
}
