package com.ragdoll.client.physics;

public final class PoseSnapshotRegressionChecks {
    private PoseSnapshotRegressionChecks() {}

    public static void main(String[] args) {
        var snapshot = new RagdollInitialLimbPose(
                new RagdollInitialLimbPose.PartPose(1, 2, 3, 0.1f, 0.2f, 0.3f),
                new RagdollInitialLimbPose.PartPose(4, 5, 6, 0.4f, 0.5f, 0.6f),
                new RagdollInitialLimbPose.PartPose(7, 8, 9, 0.7f, 0.8f, 0.9f),
                new RagdollInitialLimbPose.PartPose(10, 11, 12, 1.0f, 1.1f, 1.2f),
                new RagdollInitialLimbPose.PartPose(13, 14, 15, 1.3f, 1.4f, 1.5f),
                new RagdollInitialLimbPose.PartPose(16, 17, 18, 1.6f, 1.7f, 1.8f),
                2.1f, 1.9f, 2.0f);
        float[] values = snapshot.values();
        if (values.length != RagdollInitialLimbPose.VALUE_COUNT) {
            throw new AssertionError("Unexpected pose payload length: " + values.length);
        }
        if (!snapshot.equals(RagdollInitialLimbPose.fromValues(values))) {
            throw new AssertionError("Pose snapshot changed during network roundtrip");
        }

        values[0] = Float.NaN;
        values[1] = 1000.0f;
        values[3] = (float) (Math.PI * 4.25);
        var sanitized = RagdollInitialLimbPose.fromValues(values);
        if (sanitized.body().x() != 0.0f
                || sanitized.body().y() != 64.0f
                || Math.abs(sanitized.body().xRot()) > (float) Math.PI) {
            throw new AssertionError("Invalid pose values were not sanitized");
        }
        System.out.println("Pose snapshot checks passed (39-value roundtrip and bounds).");
    }
}
