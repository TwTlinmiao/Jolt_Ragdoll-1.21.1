package com.ragdoll.client.physics;

/** Speed-dependent, dissipative rebound for rigid-body impacts and terrain sweep recovery. */
final class RagdollImpactResponse {
    private static final float MINIMUM_IMPACT_SPEED = 1.0f;
    private static final float FULL_RESPONSE_SPEED = 6.0f;
    private static final float MINIMUM_RESTITUTION = 0.22f;
    private static final float MAXIMUM_RESTITUTION = 0.60f;
    private static final float MAXIMUM_REBOUND_SPEED = 4.0f;

    private RagdollImpactResponse() {}

    static float restitution(float impactSpeed) {
        if (!Float.isFinite(impactSpeed) || impactSpeed < MINIMUM_IMPACT_SPEED) return 0.0f;
        float blend = Math.min(1.0f, (impactSpeed - MINIMUM_IMPACT_SPEED)
                / (FULL_RESPONSE_SPEED - MINIMUM_IMPACT_SPEED));
        float coefficient = MINIMUM_RESTITUTION
                + (MAXIMUM_RESTITUTION - MINIMUM_RESTITUTION) * blend;
        return Math.min(coefficient, MAXIMUM_REBOUND_SPEED / impactSpeed);
    }

    static float sweepVerticalVelocity(float incomingY) {
        if (!Float.isFinite(incomingY)) return 0.0f;
        // Do not erase a rebound already produced by the physics solver.
        if (incomingY >= 0.0f) return incomingY;
        float impactSpeed = -incomingY;
        return impactSpeed * restitution(impactSpeed);
    }
}
