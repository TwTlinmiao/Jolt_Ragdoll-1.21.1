package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RagdollPushPacket(UUID ragdollId) implements CustomPacketPayload {
    public static final Type<RagdollPushPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "ragdoll_push"));
    public static final StreamCodec<FriendlyByteBuf, RagdollPushPacket> CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeBoolean(packet.ragdollId() != null);
                if (packet.ragdollId() != null) {
                    buffer.writeUUID(packet.ragdollId());
                }
            }, buffer -> new RagdollPushPacket(
                    buffer.readBoolean() ? buffer.readUUID() : null));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
