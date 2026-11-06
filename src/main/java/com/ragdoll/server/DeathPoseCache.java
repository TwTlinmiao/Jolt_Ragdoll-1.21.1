package com.ragdoll.server;

import com.ragdoll.RagdollConfig;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import com.ragdoll.client.physics.ServerRagdollManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Server-thread cache, keyed by player entity so respawns cannot reuse old poses. */
public final class DeathPoseCache {
    private static final Map<ServerPlayer, Entry> POSES = new WeakHashMap<>();

    private DeathPoseCache() {}

    public static void update(ServerPlayer player, RagdollInitialLimbPose pose) {
        if (!RagdollConfig.enabled() || !player.isAlive() || player.isSleeping()
                || ServerRagdollManager.isPlayerRagdolled(player.getUUID())) return;
        POSES.put(player, new Entry(player.serverLevel(), player.serverLevel().getGameTime(), pose));
    }

    public static RagdollInitialLimbPose take(ServerPlayer player) {
        Entry entry = POSES.remove(player);
        if (entry == null || entry.level != player.serverLevel()) return null;
        long age = player.serverLevel().getGameTime() - entry.tick;
        return age >= 0 && age <= 10 ? entry.pose : null;
    }

    private record Entry(ServerLevel level, long tick, RagdollInitialLimbPose pose) {}
}
