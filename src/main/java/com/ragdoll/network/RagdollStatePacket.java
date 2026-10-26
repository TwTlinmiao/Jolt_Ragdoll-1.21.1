package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import com.ragdoll.client.physics.RagdollPhysicsState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RagdollStatePacket(
        UUID ragdollId,
        long serverTimestampNanos,
        long sequence,
        RagdollPhysicsState state) implements CustomPacketPayload {
    public static final Type<RagdollStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "ragdoll_state"));
    public static final StreamCodec<FriendlyByteBuf, RagdollStatePacket> CODEC = StreamCodec.of(
            RagdollStatePacket::encode,
            RagdollStatePacket::decode);

    private static final int MAX_PARTS = 16;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, RagdollStatePacket packet) {
        write(
                buffer,
                packet.ragdollId,
                packet.serverTimestampNanos,
                packet.sequence,
                packet.state);
    }

    static void write(
            FriendlyByteBuf buffer,
            UUID ragdollId,
            long serverTimestampNanos,
            long sequence,
            RagdollPhysicsState state) {
        buffer.writeUUID(ragdollId);
        buffer.writeLong(serverTimestampNanos);
        buffer.writeVarLong(sequence);
        List<RagdollPhysicsState.PartState> parts = state.parts();
        buffer.writeVarInt(parts.size());
        for (RagdollPhysicsState.PartState part : parts) {
            buffer.writeUtf(part.partName(), 32);
            buffer.writeDouble(part.x());
            buffer.writeDouble(part.y());
            buffer.writeDouble(part.z());
            buffer.writeFloat(part.rotationX());
            buffer.writeFloat(part.rotationY());
            buffer.writeFloat(part.rotationZ());
            buffer.writeFloat(part.rotationW());
            buffer.writeFloat(part.linearX());
            buffer.writeFloat(part.linearY());
            buffer.writeFloat(part.linearZ());
            buffer.writeFloat(part.angularX());
            buffer.writeFloat(part.angularY());
            buffer.writeFloat(part.angularZ());
            buffer.writeBoolean(part.frozen());
        }
    }

    private static RagdollStatePacket decode(FriendlyByteBuf buffer) {
        Decoded decoded = read(buffer);
        return new RagdollStatePacket(
                decoded.ragdollId,
                decoded.serverTimestampNanos,
                decoded.sequence,
                decoded.state);
    }

    static Decoded read(FriendlyByteBuf buffer) {
        UUID ragdollId = buffer.readUUID();
        long serverTimestampNanos = buffer.readLong();
        long sequence = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count <= 0 || count > MAX_PARTS) {
            throw new IllegalArgumentException("Invalid ragdoll state part count: " + count);
        }
        List<RagdollPhysicsState.PartState> parts = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            parts.add(new RagdollPhysicsState.PartState(
                    buffer.readUtf(32),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readBoolean()));
        }
        return new Decoded(
                ragdollId,
                serverTimestampNanos,
                sequence,
                new RagdollPhysicsState(parts));
    }

    record Decoded(
            UUID ragdollId,
            long serverTimestampNanos,
            long sequence,
            RagdollPhysicsState state) {}
}
