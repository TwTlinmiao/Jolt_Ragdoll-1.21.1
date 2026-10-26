package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RagdollLootOpenPacket(UUID ragdollId, boolean pickup)
        implements CustomPacketPayload {
    public static final Type<RagdollLootOpenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    RagdollMod.MOD_ID, "ragdoll_loot_open"));
    public static final StreamCodec<FriendlyByteBuf, RagdollLootOpenPacket> CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeUUID(packet.ragdollId)
                            .writeBoolean(packet.pickup),
                    buffer -> new RagdollLootOpenPacket(
                            buffer.readUUID(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
