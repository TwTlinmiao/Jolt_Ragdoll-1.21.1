package com.ragdoll.client.physics;

/** Converts vanilla-style blast falloff into a bounded ragdoll velocity change. */
final class RagdollExplosionResponse {
    private static final double MAX_VELOCITY_CHANGE = 20.0;

    private RagdollExplosionResponse() {}

    static BlastVelocity velocity(double distance, double radius,
                                  double offsetX, double offsetY, double offsetZ,
                                  double exposure) {
        if (!Double.isFinite(distance) || !Double.isFinite(radius)
                || !Double.isFinite(offsetX) || !Double.isFinite(offsetY)
                || !Double.isFinite(offsetZ) || !Double.isFinite(exposure)
                || distance < 0 || radius <= 0 || exposure <= 0) {
            return BlastVelocity.ZERO;
        }
        double range = radius * 2.0;
        if (distance >= range) return BlastVelocity.ZERO;

        double length = Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        double x = length > 1.0e-6 ? offsetX / length : 0.0;
        double y = length > 1.0e-6 ? offsetY / length : 1.0;
        double z = length > 1.0e-6 ? offsetZ / length : 0.0;
        double upwardBias = 0.15;
        y += upwardBias;
        double directionLength = Math.sqrt(x * x + y * y + z * z);
        double strength = MAX_VELOCITY_CHANGE
                * (1.0 - distance / range) * Math.clamp(exposure, 0.0, 1.0);
        return new BlastVelocity(x / directionLength * strength,
                y / directionLength * strength, z / directionLength * strength);
    }

    record BlastVelocity(double x, double y, double z) {
        static final BlastVelocity ZERO = new BlastVelocity(0, 0, 0);
    }
}
