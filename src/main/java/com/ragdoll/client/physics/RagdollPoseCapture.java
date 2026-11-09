package com.ragdoll.client.physics;

import com.ragdoll.mixin.client.PlayerRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-only capture of the last fully animated player model pose. */
public final class RagdollPoseCapture {
    private static final Map<AbstractClientPlayer, RagdollInitialLimbPose> RENDERED_POSES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RagdollPoseCapture() {}

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
        // swing and clears crouching/swimming. Recreate the full-body animation.
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
        float speed = !sitting && player.isAlive()
                ? Math.min(player.walkAnimation.speed(partialTick), 1) : 0;
        if (player.isBaby()) swing *= 3;
        model.prepareMobModel(player, swing, speed, partialTick);
        model.setupAnim(player, swing, speed, player.tickCount + partialTick,
                Mth.wrapDegrees(relativeHeadYaw), pitch);
        float[] root = rendererRootRotation(player, partialTick);
        return withRoot(capture(model), (float) Math.toRadians(bodyYaw), root[0], root[1]);
    }

    private static RagdollInitialLimbPose capture(PlayerModel<?> model) {
        return new RagdollInitialLimbPose(
                capture(model.body, 0.0f, 0.0f, 0.0f),
                capture(model.head, 0.0f, 0.0f, 0.0f),
                capture(model.leftArm, 5.0f, 2.0f, 0.0f),
                capture(model.rightArm, -5.0f, 2.0f, 0.0f),
                capture(model.leftLeg, 1.9f, 12.0f, 0.0f),
                capture(model.rightLeg, -1.9f, 12.0f, 0.0f),
                0.0f, 0.0f, 0.0f);
    }

    private static RagdollInitialLimbPose.PartPose capture(
            net.minecraft.client.model.geom.ModelPart part,
            float vanillaX, float vanillaY, float vanillaZ) {
        // Animation/resource-pack mods may move model pivots for their own
        // third-person renderer. Ragdoll joint anchors must stay on the
        // vanilla player skeleton; preserve only the animated rotations.
        return new RagdollInitialLimbPose.PartPose(vanillaX, vanillaY, vanillaZ,
                part.xRot, part.yRot, part.zRot);
    }

    private static RagdollInitialLimbPose withRoot(RagdollInitialLimbPose pose,
                                                    float bodyYaw, float pitch, float yaw) {
        return new RagdollInitialLimbPose(pose.body(), pose.head(),
                pose.leftArm(), pose.rightArm(), pose.leftLeg(), pose.rightLeg(),
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
}
