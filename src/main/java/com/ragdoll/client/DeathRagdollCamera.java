package com.ragdoll.client;

import com.ragdoll.RagdollConfig;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Death-only camera state; does not change the user's perspective preference. */
public final class DeathRagdollCamera {
    private static UUID target;
    private static LocalPlayer owner;
    private static boolean enteredDeath;
    private static int pendingTicks;
    private static Vec3 lastAnchor;
    private static float yaw;

    private DeathRagdollCamera() {}

    public static void target(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        reset();
        if (!RagdollConfig.enabled() || minecraft.player == null) return;
        target = id;
        owner = minecraft.player;
        pendingTicks = 100;
        yaw = owner.getYRot();
        lastAnchor = owner.position().add(0, 0.8, 0);
    }

    public static void tick(Minecraft minecraft) {
        if (owner == null) return;
        if (!RagdollConfig.enabled() || minecraft.level == null || minecraft.player != owner) {
            reset();
        } else if (owner.isDeadOrDying()) {
            enteredDeath = true;
        } else if (enteredDeath || --pendingTicks <= 0) {
            reset();
        }
    }

    public static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return RagdollConfig.enabled() && target != null && owner != null
                && minecraft.level != null && minecraft.player == owner && owner.isDeadOrDying();
    }

    public static Vec3 anchor(float partialTick) {
        var torso = ClientPhysicsRuntime.getPart(target, "body", partialTick);
        if (torso != null) lastAnchor = new Vec3(torso.x(), torso.y() + 0.2, torso.z());
        // Keep the final view if the corpse expires while the death screen is open.
        return lastAnchor;
    }

    public static float yaw() { return yaw; }

    public static void reset() {
        target = null;
        owner = null;
        enteredDeath = false;
        pendingTicks = 0;
        lastAnchor = null;
    }
}
