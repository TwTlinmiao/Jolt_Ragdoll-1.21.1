package com.ragdoll.client;

import com.mojang.authlib.properties.Property;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.compat.RagdollAccessoryCapture;
import com.ragdoll.server.RagdollDeathKnockback;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

/** Builds a local player-death snapshot without networking or persistence. */
public final class RagdollPlayerDeaths {
    private RagdollPlayerDeaths() {
    }

    static RagdollDeathSnapshot create(AbstractClientPlayer player) {
        Vec3 velocity = player.getDeltaMovement()
                .add(RagdollDeathKnockback.fromLastDamage(player));
        return create(player, UUID.randomUUID(), velocity);
    }

    /** Snapshot a living player for an addon, without adding death knockback. */
    public static RagdollDeathSnapshot create(
            AbstractClientPlayer player, UUID ragdollId, Vec3 velocity) {
        SkinTexture skin = skinTexture(player);
        return new RagdollDeathSnapshot(
                ragdollId,
                player.getUUID(),
                player.getGameProfile().getName(),
                RagdollDeathSnapshot.DEFAULT_MODEL_SCALE,
                skin.value(),
                skin.signature(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.yBodyRot,
                initialPose(player),
                player.getXRot(),
                (float) velocity.x,
                (float) velocity.y,
                (float) velocity.z,
                shownModelParts(player),
                player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                player.getItemBySlot(EquipmentSlot.LEGS).copy(),
                player.getItemBySlot(EquipmentSlot.FEET).copy(),
                player.getMainArm() == HumanoidArm.LEFT,
                player.getMainHandItem().copy(),
                player.getOffhandItem().copy(),
                RagdollAccessoryCapture.capture(player));
    }

    static RagdollInitialPose initialPose(
            AbstractClientPlayer player) {
        Pose pose = player.getPose();
        if (pose == Pose.SWIMMING && player.isInWater()) {
            return RagdollInitialPose.SWIMMING;
        }
        if (pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
            return pose == Pose.FALL_FLYING
                    ? RagdollInitialPose.ELYTRA : RagdollInitialPose.PRONE;
        }
        if (pose == Pose.SWIMMING) return RagdollInitialPose.CRAWLING;
        return pose == Pose.CROUCHING
                ? RagdollInitialPose.CROUCHING
                : RagdollInitialPose.STANDING;
    }

    private static int shownModelParts(AbstractClientPlayer player) {
        int result = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                result |= part.getMask();
            }
        }
        return result;
    }

    private static SkinTexture skinTexture(AbstractClientPlayer player) {
        Collection<Property> textures = player.getGameProfile()
                .getProperties()
                .get("textures");
        if (textures.isEmpty()) {
            return SkinTexture.EMPTY;
        }
        Property texture = textures.iterator().next();
        return new SkinTexture(
                nullToEmpty(texture.value()),
                nullToEmpty(texture.signature()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SkinTexture(String value, String signature) {
        private static final SkinTexture EMPTY = new SkinTexture("", "");
    }
}
