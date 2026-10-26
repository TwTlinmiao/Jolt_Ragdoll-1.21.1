package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import com.ragdoll.model.RagdollInitialPose;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SleepRagdollRequestPacket(
        RagdollInitialLimbPose initialLimbPose,
        RagdollInitialPose initialPose,
        double x, double y, double z,
        float bodyYaw, float pitch,
        float velocityX, float velocityY, float velocityZ,
        boolean onGround) implements CustomPacketPayload {
    public static final Type<SleepRagdollRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "sleep_ragdoll_request"));
    public static final StreamCodec<FriendlyByteBuf, SleepRagdollRequestPacket> CODEC =
            StreamCodec.of(SleepRagdollRequestPacket::encode, SleepRagdollRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, SleepRagdollRequestPacket packet) {
        buffer.writeBoolean(packet.initialLimbPose != null);
        if (packet.initialLimbPose != null) {
            for (float rotation : packet.initialLimbPose.values()) buffer.writeFloat(rotation);
        }
        buffer.writeEnum(packet.initialPose);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.bodyYaw);
        buffer.writeFloat(packet.pitch);
        buffer.writeFloat(packet.velocityX);
        buffer.writeFloat(packet.velocityY);
        buffer.writeFloat(packet.velocityZ);
        buffer.writeBoolean(packet.onGround);
    }

    private static SleepRagdollRequestPacket decode(FriendlyByteBuf buffer) {
        RagdollInitialLimbPose limbPose = null;
        if (buffer.readBoolean()) {
            float[] values = new float[RagdollInitialLimbPose.VALUE_COUNT];
            for (int i = 0; i < values.length; ++i) values[i] = buffer.readFloat();
            limbPose = RagdollInitialLimbPose.fromValues(values);
        }
        return new SleepRagdollRequestPacket(
                limbPose,
                buffer.readEnum(RagdollInitialPose.class),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean());
    }
}
