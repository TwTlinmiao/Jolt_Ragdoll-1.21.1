package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RagdollControlRequestPacket(
        UUID ragdollId,
        Action action,
        String partName,
        double hitX,
        double hitY,
        double hitZ,
        double targetX,
        double targetY,
        double targetZ,
        double directionX,
        double directionY,
        double directionZ) implements CustomPacketPayload {
    public static final Type<RagdollControlRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "ragdoll_control_request"));
    public static final StreamCodec<FriendlyByteBuf, RagdollControlRequestPacket> CODEC = StreamCodec.of(
            RagdollControlRequestPacket::encode,
            RagdollControlRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, RagdollControlRequestPacket packet) {
        buffer.writeUUID(packet.ragdollId);
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.partName, 16);
        buffer.writeDouble(packet.hitX);
        buffer.writeDouble(packet.hitY);
        buffer.writeDouble(packet.hitZ);
        buffer.writeDouble(packet.targetX);
        buffer.writeDouble(packet.targetY);
        buffer.writeDouble(packet.targetZ);
        buffer.writeDouble(packet.directionX);
        buffer.writeDouble(packet.directionY);
        buffer.writeDouble(packet.directionZ);
    }

    private static RagdollControlRequestPacket decode(FriendlyByteBuf buffer) {
        return new RagdollControlRequestPacket(
                buffer.readUUID(),
                buffer.readEnum(Action.class),
                buffer.readUtf(16),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble());
    }

    public static RagdollControlRequestPacket grab(
            UUID ragdollId,
            String partName,
            double hitX,
            double hitY,
            double hitZ,
            double targetX,
            double targetY,
            double targetZ) {
        return new RagdollControlRequestPacket(
                ragdollId, Action.GRAB, partName,
                hitX, hitY, hitZ,
                targetX, targetY, targetZ,
                0.0, 0.0, 0.0);
    }

    public static RagdollControlRequestPacket release(UUID ragdollId) {
        return new RagdollControlRequestPacket(
                ragdollId, Action.RELEASE, "",
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0);
    }

    public static RagdollControlRequestPacket attack(
            UUID ragdollId,
            String partName,
            double hitX,
            double hitY,
            double hitZ,
            double directionX,
            double directionY,
            double directionZ) {
        return new RagdollControlRequestPacket(
                ragdollId, Action.ATTACK, partName,
                hitX, hitY, hitZ,
                0.0, 0.0, 0.0,
                directionX, directionY, directionZ);
    }

    public enum Action {
        GRAB,
        RELEASE,
        ATTACK
    }
}
