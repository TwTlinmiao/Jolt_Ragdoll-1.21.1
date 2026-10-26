package com.ragdoll.model;

public enum RagdollInitialPose {
    STANDING,
    CROUCHING,
    PRONE,
    CRAWLING,
    SWIMMING,
    ELYTRA;

    public static RagdollInitialPose byId(int id) {
        RagdollInitialPose[] values = values();
        return id >= 0 && id < values.length ? values[id] : STANDING;
    }
}
