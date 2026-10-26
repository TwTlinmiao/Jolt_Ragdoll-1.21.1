package com.ragdoll.network;

import com.ragdoll.client.physics.RagdollInitialLimbPose;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LiveRagdollRequestPacket(RagdollInitialLimbPose initialLimbPose,
                                      RagdollInitialPose initialPose,
                                      double x, double y, double z,
                                      float bodyYaw, float pitch, boolean hasTransform,
                                      float velocityX, float velocityY, float velocityZ,
                                      boolean onGround) implements CustomPacketPayload {
    public static final Type<LiveRagdollRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "live_ragdoll_request"));
    public static final StreamCodec<FriendlyByteBuf, LiveRagdollRequestPacket> CODEC =
            StreamCodec.of(LiveRagdollRequestPacket::encode, LiveRagdollRequestPacket::decode);

    public LiveRagdollRequestPacket() {
        this(null, null, 0, 0, 0, 0, 0, false, 0, 0, 0, true);
    }

    public LiveRagdollRequestPacket(RagdollInitialLimbPose limbPose,
                                   float velocityX, float velocityY, float velocityZ,
                                   boolean onGround) {
        this(limbPose, null, 0, 0, 0, 0, 0, false,
                velocityX, velocityY, velocityZ, onGround);
    }

    public LiveRagdollRequestPacket(RagdollInitialLimbPose limbPose,
                                   RagdollInitialPose initialPose,
                                   double x, double y, double z,
                                   float bodyYaw, float pitch,
                                   float velocityX, float velocityY, float velocityZ,
                                   boolean onGround) {
        this(limbPose, initialPose, x, y, z, bodyYaw, pitch, true,
                velocityX, velocityY, velocityZ, onGround);
    }

    private static void encode(FriendlyByteBuf buffer, LiveRagdollRequestPacket packet) {
        buffer.writeBoolean(packet.initialLimbPose != null);
        if (packet.initialLimbPose != null) {
            for (float rotation : packet.initialLimbPose.values()) buffer.writeFloat(rotation);
        }
        buffer.writeBoolean(packet.initialPose != null);
        if (packet.initialPose != null) buffer.writeEnum(packet.initialPose);
        buffer.writeBoolean(packet.hasTransform);
        if (packet.hasTransform) {
            buffer.writeDouble(packet.x);
            buffer.writeDouble(packet.y);
            buffer.writeDouble(packet.z);
            buffer.writeFloat(packet.bodyYaw);
            buffer.writeFloat(packet.pitch);
        }
        buffer.writeFloat(packet.velocityX);
        buffer.writeFloat(packet.velocityY);
        buffer.writeFloat(packet.velocityZ);
        buffer.writeBoolean(packet.onGround);
    }

    private static LiveRagdollRequestPacket decode(FriendlyByteBuf buffer) {
        RagdollInitialLimbPose pose = null;
        if (buffer.readBoolean()) {
            float[] values = new float[RagdollInitialLimbPose.VALUE_COUNT];
            for (int i = 0; i < values.length; ++i) values[i] = buffer.readFloat();
            pose = RagdollInitialLimbPose.fromValues(values);
        }
        RagdollInitialPose initialPose = buffer.readBoolean()
                ? buffer.readEnum(RagdollInitialPose.class) : null;
        boolean hasTransform = buffer.readBoolean();
        double x = 0, y = 0, z = 0;
        float yaw = 0, pitch = 0;
        if (hasTransform) {
            x = buffer.readDouble();
            y = buffer.readDouble();
            z = buffer.readDouble();
            yaw = buffer.readFloat();
            pitch = buffer.readFloat();
        }
        return new LiveRagdollRequestPacket(pose, initialPose, x, y, z, yaw, pitch,
                hasTransform, buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
