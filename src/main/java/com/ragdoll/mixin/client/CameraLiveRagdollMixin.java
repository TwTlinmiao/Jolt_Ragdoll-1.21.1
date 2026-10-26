package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import com.ragdoll.client.physics.RagdollPartRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraLiveRagdollMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void ragdoll$attachLiveCamera(BlockGetter level, Entity entity,
                                          boolean detached, boolean mirrored,
                                          float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null || entity != minecraft.player) return;
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        var ragdollId = ClientPhysicsRuntime.liveRagdollId(entity.getUUID());
        boolean sleepCamera = false;
        if (ragdollId == null && com.ragdoll.client.RagdollClient.isSleepRagdollCameraActive()) {
            ragdollId = com.ragdoll.client.RagdollClient.localSleepRagdollId();
            sleepCamera = ragdollId != null;
        }
        if (ragdollId == null) return;
        ClientPhysicsRuntime.setControlledHeadHidden(ragdollId, firstPerson);
        if (!sleepCamera && !com.ragdoll.RagdollConfig.liveRagdollCameraEnabled()) return;

        RagdollPartRenderState torso = ClientPhysicsRuntime.getPart(
                ragdollId, "body", partialTick);
        if (torso == null) return;
        Vec3 anchor = new Vec3(torso.x(), torso.y(), torso.z());
        Camera camera = (Camera) (Object) this;
        if (sleepCamera) {
            setRotation(com.ragdoll.client.RagdollClient.sleepCameraYaw(),
                    com.ragdoll.client.RagdollClient.sleepCameraPitch(), 0.0f);
        }
        if (!firstPerson) {
            Vector3f look = camera.getLookVector();
            Vec3 desired = anchor.subtract(new Vec3(look.x(), look.y(), look.z())
                    .scale(4.0));
            Vec3 cameraPosition = clipToBlocks(minecraft, entity, anchor, desired);
            setPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z);
            return;
        }

        RagdollPartRenderState head = ClientPhysicsRuntime.getPart(
                ragdollId, "head", partialTick);
        if (head == null) return;
        Quaternionf rotation = new Quaternionf(head.rotationX(), head.rotationY(),
                head.rotationZ(), head.rotationW()).normalize();
        Vector3f eyeOffset = rotation.transform(new Vector3f(0.0f, 0.05f, -0.325f));
        Vec3 headPosition = new Vec3(head.x(), head.y(), head.z());
        Vec3 desired = headPosition.add(eyeOffset.x, eyeOffset.y, eyeOffset.z);
        Vec3 viewPosition = clipToBlocks(minecraft, entity, headPosition, desired);
        setPosition(viewPosition.x, viewPosition.y, viewPosition.z);

        if (sleepCamera) return;

        Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
        float yaw = 180.0f - (float) Math.toDegrees(euler.y);
        float pitch = -(float) Math.toDegrees(euler.x);
        float roll = -(float) Math.toDegrees(euler.z);
        setRotation(yaw, pitch, roll);
    }

    private static Vec3 clipToBlocks(Minecraft minecraft, Entity entity,
                                     Vec3 head, Vec3 desired) {
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                head, desired, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) return desired;
        Vec3 direction = desired.subtract(head).normalize();
        return hit.getLocation().subtract(direction.scale(0.05));
    }
}
