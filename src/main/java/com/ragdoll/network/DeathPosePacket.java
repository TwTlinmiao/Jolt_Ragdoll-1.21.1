package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Last living full-body animation; the server supplies position and equipment. */
public record DeathPosePacket(RagdollInitialLimbPose pose) implements CustomPacketPayload {
    public static final Type<DeathPosePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "death_pose"));
    public static final StreamCodec<FriendlyByteBuf, DeathPosePacket> CODEC =
            StreamCodec.of(DeathPosePacket::encode, DeathPosePacket::decode);

    private static void encode(FriendlyByteBuf buffer, DeathPosePacket packet) {
        for (float value : packet.pose.values()) buffer.writeFloat(value);
    }

    private static DeathPosePacket decode(FriendlyByteBuf buffer) {
        float[] values = new float[RagdollInitialLimbPose.VALUE_COUNT];
        for (int i = 0; i < values.length; i++) values[i] = buffer.readFloat();
        return new DeathPosePacket(RagdollInitialLimbPose.fromValues(values));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
