package com.ragdoll.client;

import com.ragdoll.RagdollConfig;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.network.RagdollNetwork;
import com.ragdoll.network.SleepRagdollRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mirrors player sleep state as a temporary, client-side ragdoll. */
final class SleepRagdollTracker {
    // Cached player motion is blocks/tick; Jolt expects blocks/second. The
    // physics world then applies its 0.4 spawn scale, so include its inverse.
    private static final double GROUNDED_HORIZONTAL_INERTIA_SCALE = 35.0;
    private static final double AIRBORNE_HORIZONTAL_INERTIA_SCALE = 36.0;
    private static final double SLEEP_VERTICAL_INERTIA_SCALE = 9.0;
    private static final double MIN_MOVEMENT_SPEED_SQR = 0.0004;
    private static final double MIN_TRACKED_HORIZONTAL_SPEED_SQR = 0.0004;
    private static final double GROUNDED_FORWARD_SPEED = 0.9;
    private static final double AIRBORNE_FORWARD_SPEED = 1.35;
    private static final int PRE_BED_MOTION_LIFETIME_TICKS = 40;
    private static final Map<UUID, Integer> NEXT_REQUEST_TICKS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POSITIONS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_VELOCITIES = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_HORIZONTAL_VELOCITIES = new HashMap<>();
    private static final Map<UUID, CapturedMotion> PRE_BED_MOTIONS = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_ON_GROUND = new HashMap<>();
    private static final Map<UUID, RagdollInitialPose> LAST_POSES = new HashMap<>();
    private static final Map<UUID, Float> LAST_YAWS = new HashMap<>();
    private static final Map<UUID, Float> LAST_PITCHES = new HashMap<>();
    private static final Map<UUID, RagdollInitialLimbPose> LAST_LIMB_POSES = new HashMap<>();
    private static ClientLevel level;

    private SleepRagdollTracker() {}

    static UUID ragdollId(UUID playerId) {
        return ClientPhysicsRuntime.sleepRagdollId(playerId);
    }

    static boolean isLocalSleepRagdollActive(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.isSleeping()
                && ragdollId(minecraft.player.getUUID()) != null;
    }

    static void captureBedInteraction(Minecraft minecraft, AbstractClientPlayer player) {
        if (!RagdollConfig.enabled() || !RagdollConfig.sleepRagdollEnabled()
                || minecraft.level == null
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                || !(minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock)) {
            return;
        }
        Vec3 movement = player.position().subtract(player.xOld, player.yOld, player.zOld);
        Vec3 velocity = player.getDeltaMovement();
        double movementHorizontalSqr = movement.x * movement.x + movement.z * movement.z;
        double velocityHorizontalSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        Vec3 horizontal = movementHorizontalSqr > velocityHorizontalSqr ? movement : velocity;
        double vertical = Math.abs(movement.y) > Math.abs(velocity.y) ? movement.y : velocity.y;
        PRE_BED_MOTIONS.put(player.getUUID(), new CapturedMotion(
                new Vec3(horizontal.x, 0, horizontal.z), vertical, player.onGround(),
                player.tickCount + PRE_BED_MOTION_LIFETIME_TICKS));
    }

    static void tick(Minecraft minecraft) {
        ClientLevel currentLevel = minecraft.level;
        if (level != currentLevel) {
            clear();
            level = currentLevel;
        }
        if (currentLevel == null || !RagdollConfig.enabled()
                || !RagdollConfig.sleepRagdollEnabled()) {
            clear();
            return;
        }

        for (var entity : currentLevel.players()) {
            if (!(entity instanceof AbstractClientPlayer player)) continue;
            UUID playerId = player.getUUID();
            CapturedMotion capturedMotion = PRE_BED_MOTIONS.get(playerId);
            if (capturedMotion != null && player.tickCount > capturedMotion.expiresAt()) {
                PRE_BED_MOTIONS.remove(playerId);
                capturedMotion = null;
            }
            if (player.isSleeping()) {
                if (player == minecraft.player
                        && ClientPhysicsRuntime.sleepRagdollId(playerId) == null
                        && player.tickCount >= NEXT_REQUEST_TICKS.getOrDefault(playerId, 0)) {
                    Vec3 origin = LAST_POSITIONS.getOrDefault(playerId, player.position());
                    boolean wasOnGround = capturedMotion != null ? capturedMotion.onGround()
                            : LAST_ON_GROUND.getOrDefault(playerId, true);
                    Vec3 verticalVelocity = capturedMotion != null
                            ? new Vec3(0, capturedMotion.vertical(), 0)
                            : LAST_VELOCITIES.getOrDefault(playerId, Vec3.ZERO);
                    Vec3 horizontalVelocity = capturedMotion != null
                            ? capturedMotion.horizontal()
                            : LAST_HORIZONTAL_VELOCITIES.getOrDefault(playerId, Vec3.ZERO);
                    Vec3 velocity = sleepVelocity(player,
                            verticalVelocity, horizontalVelocity, wasOnGround);
                    PRE_BED_MOTIONS.remove(playerId);
                    RagdollInitialPose initialPose = LAST_POSES.getOrDefault(
                            playerId, RagdollInitialPose.STANDING);
                    float yaw = LAST_YAWS.getOrDefault(playerId, player.yBodyRot);
                    float pitch = LAST_PITCHES.getOrDefault(playerId, player.getXRot());
                    RagdollInitialLimbPose limbPose = LAST_LIMB_POSES.get(playerId);
                    RagdollNetwork.beginSleepRagdoll(new SleepRagdollRequestPacket(
                            limbPose, initialPose, origin.x, origin.y, origin.z,
                            yaw, pitch, (float) velocity.x, (float) velocity.y,
                            (float) velocity.z, wasOnGround));
                    NEXT_REQUEST_TICKS.put(playerId, player.tickCount + 20);
                }
            } else {
                NEXT_REQUEST_TICKS.remove(playerId);
                LAST_POSITIONS.put(playerId, player.position());
                Vec3 movement = player.position().subtract(player.xOld, player.yOld, player.zOld);
                Vec3 velocity = player.getDeltaMovement();
                double movementHorizontalSqr = movement.x * movement.x + movement.z * movement.z;
                double velocityHorizontalSqr = velocity.x * velocity.x + velocity.z * velocity.z;
                Vec3 horizontal = movementHorizontalSqr > velocityHorizontalSqr ? movement : velocity;
                double vertical = Math.abs(movement.y) > Math.abs(velocity.y) ? movement.y : velocity.y;
                Vec3 previousHorizontal = LAST_HORIZONTAL_VELOCITIES.getOrDefault(playerId, Vec3.ZERO);
                if (horizontal.x * horizontal.x + horizontal.z * horizontal.z
                        >= MIN_TRACKED_HORIZONTAL_SPEED_SQR) {
                    LAST_HORIZONTAL_VELOCITIES.put(playerId,
                            new Vec3(horizontal.x, 0, horizontal.z));
                } else if (previousHorizontal.lengthSqr() >= MIN_TRACKED_HORIZONTAL_SPEED_SQR) {
                    LAST_HORIZONTAL_VELOCITIES.put(playerId, previousHorizontal.scale(0.5));
                } else {
                    LAST_HORIZONTAL_VELOCITIES.remove(playerId);
                }
                LAST_VELOCITIES.put(playerId, new Vec3(0, vertical, 0));
                LAST_ON_GROUND.put(playerId, player.onGround());
                LAST_POSES.put(playerId, RagdollPlayerDeaths.initialPose(player));
                LAST_YAWS.put(playerId, player.yBodyRot);
                LAST_PITCHES.put(playerId, player.getXRot());
                RagdollInitialLimbPose limbPose = captureRenderedLimbPose(minecraft, player);
                if (limbPose != null) LAST_LIMB_POSES.put(playerId, limbPose);
            }
        }
    }

    private static RagdollInitialLimbPose captureRenderedLimbPose(
            Minecraft minecraft, AbstractClientPlayer player) {
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        return renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer
                ? RagdollInitialLimbPose.captureCurrentPose(
                        player, playerRenderer) : null;
    }

    private static Vec3 sleepVelocity(AbstractClientPlayer player, Vec3 previousVelocity,
                                      Vec3 previousHorizontal, boolean wasOnGround) {
        Vec3 horizontal = new Vec3(previousHorizontal.x, 0, previousHorizontal.z);
        if (horizontal.lengthSqr() < MIN_MOVEMENT_SPEED_SQR) {
            horizontal = player.getLookAngle().multiply(1, 0, 1).normalize()
                    .scale(wasOnGround ? GROUNDED_FORWARD_SPEED : AIRBORNE_FORWARD_SPEED);
        } else {
            horizontal = horizontal.scale(wasOnGround
                    ? GROUNDED_HORIZONTAL_INERTIA_SCALE : AIRBORNE_HORIZONTAL_INERTIA_SCALE);
        }
        // Convert all captured per-tick motion to per-second physics velocity;
        // preserve the jump/fall trajectory instead of retaining only its pose.
        return new Vec3(horizontal.x,
                previousVelocity.y * SLEEP_VERTICAL_INERTIA_SCALE,
                horizontal.z);
    }

    private static void clear() {
        NEXT_REQUEST_TICKS.clear();
        LAST_POSITIONS.clear();
        LAST_VELOCITIES.clear();
        LAST_HORIZONTAL_VELOCITIES.clear();
        LAST_ON_GROUND.clear();
        LAST_POSES.clear();
        LAST_YAWS.clear();
        LAST_PITCHES.clear();
        LAST_LIMB_POSES.clear();
        PRE_BED_MOTIONS.clear();
    }

    private record CapturedMotion(Vec3 horizontal, double vertical, boolean onGround,
                                  int expiresAt) {}
}
