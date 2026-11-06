package com.ragdoll.client.physics;

import net.minecraft.util.Mth;

/** Rendered player-model transforms captured before ragdoll creation. */
public record RagdollInitialLimbPose(
        PartPose body, PartPose head,
        PartPose leftArm, PartPose rightArm,
        PartPose leftLeg, PartPose rightLeg,
        float rendererBodyYaw, float rendererRootPitch, float rendererRootYaw) {
    public static final int VALUE_COUNT = 39;

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

    public record PartPose(float x, float y, float z,
                           float xRot, float yRot, float zRot) {
        public static final PartPose ZERO = new PartPose(0, 0, 0, 0, 0, 0);

        private static PartPose fromValues(float[] values, int offset) {
            return new PartPose(values[offset], values[offset + 1], values[offset + 2],
                    values[offset + 3], values[offset + 4], values[offset + 5]);
        }

        public float[] rotations() {
            return new float[]{xRot, yRot, zRot};
        }
    }
}
