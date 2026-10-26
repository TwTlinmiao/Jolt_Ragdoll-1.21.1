package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SleepRagdollStatePacket(UUID playerId, UUID ragdollId, boolean active)
        implements CustomPacketPayload {
    public static final Type<SleepRagdollStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "sleep_ragdoll_state"));
    public static final StreamCodec<FriendlyByteBuf, SleepRagdollStatePacket> CODEC =
            StreamCodec.of(SleepRagdollStatePacket::encode, SleepRagdollStatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, SleepRagdollStatePacket packet) {
        buffer.writeUUID(packet.playerId);
        buffer.writeUUID(packet.ragdollId);
        buffer.writeBoolean(packet.active);
    }

    private static SleepRagdollStatePacket decode(FriendlyByteBuf buffer) {
        return new SleepRagdollStatePacket(
                buffer.readUUID(), buffer.readUUID(), buffer.readBoolean());
    }
}
