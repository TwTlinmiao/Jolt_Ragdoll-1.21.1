package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RagdollRemovePacket(UUID ragdollId) implements CustomPacketPayload {
    public static final Type<RagdollRemovePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "remove_ragdoll"));
    public static final StreamCodec<FriendlyByteBuf, RagdollRemovePacket> CODEC = StreamCodec.of(
            RagdollRemovePacket::encode,
            RagdollRemovePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, RagdollRemovePacket packet) {
        buffer.writeUUID(packet.ragdollId);
    }

    private static RagdollRemovePacket decode(FriendlyByteBuf buffer) {
        return new RagdollRemovePacket(buffer.readUUID());
    }
}
