package com.ragdoll.client.physics;

import java.util.List;

/** Immutable local physics snapshot used while terrain is unloaded. */
public record RagdollPhysicsState(
        List<PartState> parts) {
    public RagdollPhysicsState {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Invalid ragdoll state");
        }
        parts = List.copyOf(parts);
    }

    public record PartState(
            String partName,
            double x,
            double y,
            double z,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            float linearX,
            float linearY,
            float linearZ,
            float angularX,
            float angularY,
            float angularZ,
            boolean frozen) {
    }
}
