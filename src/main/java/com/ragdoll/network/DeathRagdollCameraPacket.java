package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Identifies this death's body, excluding older corpses and command copies. */
public record DeathRagdollCameraPacket(UUID ragdollId) implements CustomPacketPayload {
    public static final Type<DeathRagdollCameraPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RagdollMod.MOD_ID, "death_camera"));
    public static final StreamCodec<FriendlyByteBuf, DeathRagdollCameraPacket> CODEC =
            StreamCodec.of((buffer, packet) -> buffer.writeUUID(packet.ragdollId()),
                    buffer -> new DeathRagdollCameraPacket(buffer.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
