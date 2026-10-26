package com.ragdoll.client.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.ragdoll.RagdollMod;

public final class JoltBootstrap {
    private static boolean initialized;
    private static JobSystemThreadPool sharedJobSystem;
    private static int sharedJobSystemUsers;

    private JoltBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            JoltNativeLoader.load();
            JoltPhysicsObject.startCleaner();
            Jolt.registerDefaultAllocator();
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();

            if (!Jolt.newFactory()) {
                throw new IllegalStateException("Jolt failed to create its global factory");
            }

            Jolt.registerTypes();
            initialized = true;
            RagdollMod.LOGGER.info("Jolt Physics initialized successfully");
        } catch (RuntimeException | LinkageError exception) {
            RagdollMod.LOGGER.error("Unable to initialize Jolt Physics", exception);
            throw exception;
        }
    }

    static synchronized JobSystemThreadPool acquireSharedJobSystem() {
        if (!initialized) {
            throw new IllegalStateException(
                    "Jolt must be initialized before acquiring its job system");
        }
        if (sharedJobSystem == null) {
            sharedJobSystem = new JobSystemThreadPool(
                    Jolt.cMaxPhysicsJobs,
                    Jolt.cMaxPhysicsBarriers,
                    1);
            RagdollMod.LOGGER.info("Created shared Jolt job system");
        }
        ++sharedJobSystemUsers;
        return sharedJobSystem;
    }

    static synchronized void releaseSharedJobSystem(
            JobSystemThreadPool jobSystem) {
        if (jobSystem != sharedJobSystem || sharedJobSystemUsers <= 0) {
            throw new IllegalStateException(
                    "Attempted to release an unknown Jolt job system");
        }
        --sharedJobSystemUsers;
        if (sharedJobSystemUsers == 0) {
            sharedJobSystem.close();
            sharedJobSystem = null;
            RagdollMod.LOGGER.info("Closed shared Jolt job system");
        }
    }
}
