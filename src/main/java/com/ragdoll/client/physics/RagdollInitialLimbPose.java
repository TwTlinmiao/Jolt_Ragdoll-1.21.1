package com.ragdoll.client.physics;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import com.ragdoll.mixin.client.PlayerRendererAccessor;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Rendered player-model transforms captured before ragdoll creation. */
public record RagdollInitialLimbPose(
        PartPose body, PartPose head,
        PartPose leftArm, PartPose rightArm,
        PartPose leftLeg, PartPose rightLeg,
        float rendererBodyYaw, float rendererRootPitch, float rendererRootYaw) {
    public static final int VALUE_COUNT = 39;
    private static final Map<AbstractClientPlayer, RagdollInitialLimbPose> RENDERED_POSES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static RagdollInitialLimbPose capture(PlayerModel<?> model) {
        return new RagdollInitialLimbPose(
                PartPose.capture(model.body), PartPose.capture(model.head),
                PartPose.capture(model.leftArm), PartPose.capture(model.rightArm),
                PartPose.capture(model.leftLeg), PartPose.capture(model.rightLeg),
                0.0f, 0.0f, 0.0f);
    }

    /** Called immediately after vanilla has prepared and animated this player's model. */
    public static void captureRenderedPose(AbstractClientPlayer player,
                                           PlayerModel<?> model,
                                           float partialTick) {
        float[] root = rendererRootRotation(player, partialTick);
        float bodyYaw = (float) Math.toRadians(Mth.rotLerp(partialTick,
                player.yBodyRotO, player.yBodyRot));
        RENDERED_POSES.put(player,
                withRoot(capture(model), bodyYaw, root[0], root[1]));
    }

    public static RagdollInitialLimbPose captureCurrentPose(
            AbstractClientPlayer player, PlayerRenderer renderer) {
        RagdollInitialLimbPose rendered = RENDERED_POSES.get(player);
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson();
        if (!firstPerson && rendered != null) return rendered;

        // First-person hand rendering resets this shared model with zero limb
        // swing and clears crouching/swimming. It is not a full-body snapshot.
        // Prepare the same full-body animation as LivingEntityRenderer instead
        // of reading that model, or an old third-person frame, as-is.
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ((PlayerRendererAccessor) renderer).ragdoll$setModelProperties(player);
        model.attackTime = player.getAttackAnim(partialTick);
        boolean sitting = player.isPassenger() && player.getVehicle() != null
                && player.getVehicle().shouldRiderSit();
        model.riding = sitting;
        model.young = player.isBaby();
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        if (sitting && player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity vehicle) {
            bodyYaw = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
            float relativeYaw = Mth.clamp(Mth.wrapDegrees(headYaw - bodyYaw), -85.0f, 85.0f);
            bodyYaw = headYaw - relativeYaw;
            if (relativeYaw * relativeYaw > 2500.0f) bodyYaw += relativeYaw * 0.2f;
        }
        float relativeHeadYaw = headYaw - bodyYaw;
        float pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        if (LivingEntityRenderer.isEntityUpsideDown(player)) {
            relativeHeadYaw = -relativeHeadYaw;
            pitch = -pitch;
        }
        float swing = !sitting && player.isAlive() ? player.walkAnimation.position(partialTick) : 0;
        float speed = !sitting && player.isAlive() ? Math.min(player.walkAnimation.speed(partialTick), 1) : 0;
        if (player.isBaby()) swing *= 3;
        model.prepareMobModel(player, swing, speed, partialTick);
        model.setupAnim(player, swing, speed, player.tickCount + partialTick,
                Mth.wrapDegrees(relativeHeadYaw), pitch);
        float[] root = rendererRootRotation(player, partialTick);
        return withRoot(capture(model), (float) Math.toRadians(bodyYaw), root[0], root[1]);
    }

    public PartPose part(String name) {
        return switch (name) {
            case "body" -> body;
            case "head" -> head;
            case "left_arm" -> leftArm;
            case "right_arm" -> rightArm;
            case "left_leg" -> leftLeg;
            case "right_leg" -> rightLeg;
            default -> PartPose.ZERO;
        };
    }

    public float[] values() {
        float[] result = new float[VALUE_COUNT];
        PartPose[] parts = {body, head, leftArm, rightArm, leftLeg, rightLeg};
        int offset = 0;
        for (PartPose part : parts) {
            result[offset++] = part.x;
            result[offset++] = part.y;
            result[offset++] = part.z;
            result[offset++] = part.xRot;
            result[offset++] = part.yRot;
            result[offset++] = part.zRot;
        }
        result[offset++] = rendererRootPitch;
        result[offset++] = rendererRootYaw;
        result[offset] = rendererBodyYaw;
        return result;
    }

    public static RagdollInitialLimbPose fromValues(float[] values) {
        if (values.length != VALUE_COUNT) {
            throw new IllegalArgumentException("Expected 39 rendered pose values");
        }
        float[] safe = values.clone();
        for (int i = 0; i < safe.length; ++i) {
            float value = safe[i];
            if (!Float.isFinite(value)) {
                safe[i] = 0.0f;
            } else if (i % 6 < 3 && i < 36) {
                safe[i] = Mth.clamp(value, -64.0f, 64.0f);
            } else {
                safe[i] = (float) Math.IEEEremainder(value, Math.PI * 2.0);
            }
        }
        int offset = 0;
        PartPose body = PartPose.fromValues(safe, offset); offset += 6;
        PartPose head = PartPose.fromValues(safe, offset); offset += 6;
        PartPose leftArm = PartPose.fromValues(safe, offset); offset += 6;
        PartPose rightArm = PartPose.fromValues(safe, offset); offset += 6;
        PartPose leftLeg = PartPose.fromValues(safe, offset); offset += 6;
        PartPose rightLeg = PartPose.fromValues(safe, offset); offset += 6;
        return new RagdollInitialLimbPose(body, head, leftArm, rightArm,
                leftLeg, rightLeg, safe[38], safe[36], safe[37]);
    }

    private static RagdollInitialLimbPose withRoot(RagdollInitialLimbPose pose,
                                                    float bodyYaw, float pitch, float yaw) {
        return new RagdollInitialLimbPose(pose.body, pose.head,
                pose.leftArm, pose.rightArm, pose.leftLeg, pose.rightLeg,
                bodyYaw, pitch, yaw);
    }

    private static float[] rendererRootRotation(AbstractClientPlayer player,
                                                 float partialTick) {
        float pitch = 0.0f;
        float yaw = 0.0f;
        if (player.isFallFlying()) {
            float flightTicks = player.getFallFlyingTicks() + partialTick;
            float factor = Mth.clamp(flightTicks * flightTicks / 100.0f, 0.0f, 1.0f);
            pitch = (float) Math.toRadians(factor
                    * (-90.0f - player.getViewXRot(partialTick)));
            Vec3 view = player.getViewVector(partialTick);
            Vec3 movement = player.getDeltaMovementLerped(partialTick);
            double movementLength = movement.horizontalDistanceSqr();
            double viewLength = view.horizontalDistanceSqr();
            if (movementLength > 0.0 && viewLength > 0.0) {
                double dot = (movement.x * view.x + movement.z * view.z)
                        / Math.sqrt(movementLength * viewLength);
                double cross = movement.x * view.z - movement.z * view.x;
                yaw = (float) (Math.signum(cross)
                        * Math.acos(Mth.clamp(dot, -1.0, 1.0)));
            }
        } else if (player.getSwimAmount(partialTick) > 0.0f) {
            float swimPitch = player.isInWater()
                    ? -90.0f - player.getViewXRot(partialTick) : -90.0f;
            pitch = (float) Math.toRadians(Mth.lerp(
                    player.getSwimAmount(partialTick), 0.0f, swimPitch));
        } else if (player.isAutoSpinAttack()) {
            pitch = (float) Math.toRadians(
                    -90.0f - player.getViewXRot(partialTick));
            yaw = (float) Math.toRadians(
                    -(player.tickCount + partialTick) * 75.0f);
        }
        return new float[]{pitch, yaw};
    }

    public record PartPose(float x, float y, float z,
                           float xRot, float yRot, float zRot) {
        public static final PartPose ZERO = new PartPose(0, 0, 0, 0, 0, 0);

        private static PartPose capture(net.minecraft.client.model.geom.ModelPart part) {
            return new PartPose(part.x, part.y, part.z,
                    part.xRot, part.yRot, part.zRot);
        }

        private static PartPose fromValues(float[] values, int offset) {
            return new PartPose(values[offset], values[offset + 1], values[offset + 2],
                    values[offset + 3], values[offset + 4], values[offset + 5]);
        }

        public float[] rotations() {
            return new float[]{xRot, yRot, zRot};
        }
    }
}
