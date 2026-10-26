package com.ragdoll.server;

import com.mojang.authlib.properties.Property;
import com.ragdoll.client.physics.ServerRagdollManager;
import com.ragdoll.compat.RagdollAccessoryCapture;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.network.RagdollSpawnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Collection;
import java.util.UUID;

/** Creates one authoritative ragdoll when a server-side player death occurs. */
public final class ServerPlayerDeaths {
    private static final String PLAYER_RAGDOLL_MOD_ID = "player_ragdoll";

    private ServerPlayerDeaths() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerRagdollManager.endLiveRagdoll(player, false);
        if (ModList.get().isLoaded(PLAYER_RAGDOLL_MOD_ID)) return;
        Vec3 velocity = player.getDeltaMovement()
                .add(RagdollDeathKnockback.fromSource(player, event.getSource()));
        UUID ragdollId = UUID.randomUUID();
        if (ServerRagdollManager.spawn(
                player.serverLevel(),
                new RagdollSpawnPacket(createSnapshot(
                        player, ragdollId, velocity)))) {
            RagdollLootService.captureOnDeath(player, ragdollId);
        }
    }

    public static RagdollDeathSnapshot createSnapshot(
            ServerPlayer player, UUID ragdollId, Vec3 velocity) {
        return createSnapshot(player, ragdollId, velocity, player.position());
    }

    public static RagdollDeathSnapshot createSnapshot(
            ServerPlayer player, UUID ragdollId, Vec3 velocity, Vec3 position) {
        SkinTexture skin = skinTexture(player);
        return new RagdollDeathSnapshot(
                ragdollId,
                player.getUUID(),
                player.getGameProfile().getName(),
                RagdollDeathSnapshot.DEFAULT_MODEL_SCALE,
                skin.value(),
                skin.signature(),
                position.x,
                position.y,
                position.z,
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

    private static RagdollInitialPose initialPose(ServerPlayer player) {
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

    private static int shownModelParts(ServerPlayer player) {
        int result = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) {
                result |= part.getMask();
            }
        }
        return result;
    }

    private static SkinTexture skinTexture(ServerPlayer player) {
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
