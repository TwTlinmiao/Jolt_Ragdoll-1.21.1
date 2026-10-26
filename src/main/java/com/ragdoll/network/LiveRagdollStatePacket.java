package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record LiveRagdollStatePacket(UUID playerId, UUID ragdollId, boolean active)
        implements CustomPacketPayload {
    public static final Type<LiveRagdollStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "live_ragdoll_state"));
    public static final StreamCodec<FriendlyByteBuf, LiveRagdollStatePacket> CODEC =
            StreamCodec.of(LiveRagdollStatePacket::encode, LiveRagdollStatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, LiveRagdollStatePacket packet) {
        buffer.writeUUID(packet.playerId);
        buffer.writeUUID(packet.ragdollId);
        buffer.writeBoolean(packet.active);
    }

    private static LiveRagdollStatePacket decode(FriendlyByteBuf buffer) {
        return new LiveRagdollStatePacket(buffer.readUUID(), buffer.readUUID(), buffer.readBoolean());
    }
}
