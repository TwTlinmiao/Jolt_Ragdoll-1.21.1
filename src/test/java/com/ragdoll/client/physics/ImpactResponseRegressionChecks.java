package com.ragdoll.client.physics;

public final class ImpactResponseRegressionChecks {
    public static void main(String[] args) {
        expect(0.0f, RagdollImpactResponse.sweepVerticalVelocity(-0.5f));
        expect(0.592f, RagdollImpactResponse.sweepVerticalVelocity(-2.0f));
        expect(2.62f, RagdollImpactResponse.sweepVerticalVelocity(-5.0f));
        expect(3.6f, RagdollImpactResponse.sweepVerticalVelocity(-6.0f));
        expect(4.0f, RagdollImpactResponse.sweepVerticalVelocity(-100.0f));
        expect(0.0f, RagdollImpactResponse.restitution(Float.POSITIVE_INFINITY));
        float lastRebound = 0.0f;
        for (float speed = 0.0f; speed <= 100.0f; speed += 0.25f) {
            float rebound = RagdollImpactResponse.sweepVerticalVelocity(-speed);
            if (rebound + 0.00001f < lastRebound || rebound > 4.00001f || rebound > speed) {
                throw new AssertionError("Invalid speed-dependent response at " + speed);
            }
            lastRebound = rebound;
        }
        expect(0.7f, RagdollImpactResponse.sweepVerticalVelocity(0.7f));
        expect(0.0f, RagdollImpactResponse.sweepVerticalVelocity(Float.NaN));
        float velocity = -5.0f;
        for (int i = 0; i < 5; i++) {
            float rebound = RagdollImpactResponse.sweepVerticalVelocity(velocity);
            if (rebound >= -velocity) throw new AssertionError("Impact added energy");
            if (rebound == 0.0f) break;
            velocity = -rebound;
            if (i == 4) throw new AssertionError("Small rebounds did not settle");
        }
        System.out.println("Impact response checks passed (threshold, rebound, cap, settling, existing rebound).");
    }

    private static void expect(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.00001f) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
