package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LiveRagdollControlPacket(
        boolean leftHand,
        boolean rightHand,
        float strafe,
        float forward) implements CustomPacketPayload {
    public static final Type<LiveRagdollControlPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "live_ragdoll_control"));
    public static final StreamCodec<FriendlyByteBuf, LiveRagdollControlPacket> CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeBoolean(packet.leftHand());
                buffer.writeBoolean(packet.rightHand());
                buffer.writeFloat(packet.strafe());
                buffer.writeFloat(packet.forward());
            }, buffer -> new LiveRagdollControlPacket(
                    buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readFloat(), buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
