package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RagdollPhysicsGunActionPacket(
        Action action,
        float value1,
        float value2) implements CustomPacketPayload {
    public static final Type<RagdollPhysicsGunActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    RagdollMod.MOD_ID, "physics_gun_action"));
    public static final StreamCodec<FriendlyByteBuf,
            RagdollPhysicsGunActionPacket> CODEC = StreamCodec.of(
            RagdollPhysicsGunActionPacket::encode,
            RagdollPhysicsGunActionPacket::decode);

    public RagdollPhysicsGunActionPacket(Action action) {
        this(action, 0.0f, 0.0f);
    }

    private static void encode(
            FriendlyByteBuf buffer,
            RagdollPhysicsGunActionPacket packet) {
        buffer.writeEnum(packet.action);
        if (packet.action == Action.UPDATE_SCROLL) {
            buffer.writeFloat(packet.value1);
        } else if (packet.action == Action.UPDATE_ROTATION) {
            buffer.writeFloat(packet.value1);
            buffer.writeFloat(packet.value2);
        }
    }

    private static RagdollPhysicsGunActionPacket decode(
            FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        return switch (action) {
            case UPDATE_SCROLL -> new RagdollPhysicsGunActionPacket(
                    action, buffer.readFloat(), 0.0f);
            case UPDATE_ROTATION -> new RagdollPhysicsGunActionPacket(
                    action, buffer.readFloat(), buffer.readFloat());
            default -> new RagdollPhysicsGunActionPacket(action);
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        START_GRAB_ATTEMPT,
        STOP_GRAB_ATTEMPT,
        UPDATE_SCROLL,
        UPDATE_ROTATION,
        FREEZE_OBJECT,
        START_ROTATION_MODE,
        STOP_ROTATION_MODE
    }
}
