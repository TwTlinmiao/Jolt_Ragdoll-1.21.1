package com.ragdoll.client.physics;

import com.ragdoll.RagdollConfig;

/** Physics behavior captured when an individual ragdoll is spawned. */
public record RagdollPhysicsSettings(boolean selfCollisionEnabled) {
    public static RagdollPhysicsSettings playerDeathDefaults() {
        return new RagdollPhysicsSettings(RagdollConfig.selfCollisionEnabled());
    }

    public static RagdollPhysicsSettings controlledPlayerDefaults() {
        return new RagdollPhysicsSettings(RagdollConfig.selfCollisionEnabled());
    }
}
