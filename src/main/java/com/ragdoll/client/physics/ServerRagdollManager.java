package com.ragdoll.client.physics;

import com.ragdoll.RagdollConfig;
import com.ragdoll.RagdollMod;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.network.RagdollControlRequestPacket;
import com.ragdoll.network.RagdollRemovePacket;
import com.ragdoll.network.RagdollSpawnPacket;
import com.ragdoll.network.RagdollStatePacket;
import com.ragdoll.network.RagdollPhysicsGunActionPacket;
import com.ragdoll.network.RagdollPhysicsGunSyncPacket;
import com.ragdoll.network.LiveRagdollStatePacket;
import com.ragdoll.network.LiveRagdollRequestPacket;
import com.ragdoll.network.SleepRagdollRequestPacket;
import com.ragdoll.network.SleepRagdollStatePacket;
import com.ragdoll.mixin.client.EntityFallFlyingAccessor;
import com.ragdoll.item.RagdollItems;
import com.ragdoll.server.RagdollSavedData;
import com.ragdoll.server.ServerPlayerDeaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.joml.Quaternionf;

/**
 * Owns command-spawned ragdolls on the logical server. Clients receive
 * transforms and never advance these bodies as authoritative simulations.
 */
public final class ServerRagdollManager {
    private static final double MAX_CONTROL_DISTANCE = 6.0;
    private static final double TICKS_TO_SECONDS = 20.0;
    private static final long GRAB_TIMEOUT_TICKS = 10L;
    private static final Map<ServerLevel, LinkedHashMap<UUID, ServerRagdoll>> BY_LEVEL =
            new java.util.WeakHashMap<>();
    private static final Map<UUID, PhysicsGunGrab> PHYSICS_GUN_GRABS =
            new LinkedHashMap<>();
    private static final Set<UUID> PHYSICS_GUN_ATTEMPTS =
            new HashSet<>();
    private static final Map<UUID, LiveRagdollSession> LIVE_RAGDOLLS = new LinkedHashMap<>();
    private static final Map<UUID, SleepRagdollSession> SLEEP_RAGDOLLS = new LinkedHashMap<>();
    private static final double PHYSICS_GUN_MAX_DISTANCE = 450.0;
    private static final double PHYSICS_GUN_MIN_DISTANCE = 2.0;

    private ServerRagdollManager() {
    }

    public static boolean isPlayerRagdolled(UUID playerId) {
        return LIVE_RAGDOLLS.containsKey(playerId) || SLEEP_RAGDOLLS.containsKey(playerId);
    }

    public static boolean spawn(ServerLevel level, RagdollSpawnPacket packet) {
        return spawn(level, packet, false);
    }

    private static boolean spawn(ServerLevel level, RagdollSpawnPacket packet, boolean live) {
        if (!RagdollConfig.enabled()) {
            return false;
        }
        JoltBootstrap.initialize();
        RagdollDeathSnapshot snapshot = packet.toSnapshot();
        BlockPos origin = BlockPos.containing(snapshot.x(), snapshot.y(), snapshot.z());
        if (!JoltPhysicsWorld.isTerrainRegionLoaded(level, origin)) {
            RagdollMod.LOGGER.warn(
                    "Refused to create server ragdoll {} because its terrain is not loaded at {}",
                    packet.ragdollId(), origin);
            return false;
        }
        JoltPhysicsWorld world = createWorld(level, origin, snapshot,
                packet.initialLimbPose(), live);
        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        ServerRagdoll replaced = ragdolls.remove(packet.ragdollId());
        if (replaced != null) {
            replaced.world.close();
        }
        trimToLimit(level, ragdolls);
        ServerRagdoll ragdoll = new ServerRagdoll(packet, world, live);
        ragdolls.put(packet.ragdollId(), ragdoll);
        RagdollPhysicsState initialState = world.captureState();
        if (!live) RagdollSavedData.get(level).put(packet, initialState, 0L);
        PacketDistributor.sendToPlayersInDimension(level, packet);
        PacketDistributor.sendToPlayersInDimension(
                level, createStatePacket(ragdoll, initialState));
        RagdollMod.LOGGER.info(
                "Spawned server-authoritative ragdoll {} in {}",
                packet.ragdollId(), level.dimension().location());
        return true;
    }

    public static void toggleLiveRagdoll(ServerPlayer player) {
        toggleLiveRagdoll(player, new LiveRagdollRequestPacket());
    }

    public static void toggleLiveRagdoll(ServerPlayer player, LiveRagdollRequestPacket request) {
        LiveRagdollSession current = LIVE_RAGDOLLS.get(player.getUUID());
        if (current != null) {
            endLiveRagdoll(player, true);
            return;
        }
        if (!RagdollConfig.liveRagdollEnabled() || !player.isAlive()
                || player.isSpectator() || player.isSleeping() || player.isPassenger()) return;
        UUID ragdollId = UUID.randomUUID();
        double vx = finiteMotion(request.velocityX(), player.getDeltaMovement().x * TICKS_TO_SECONDS);
        double vy = finiteMotion(request.velocityY(), player.getDeltaMovement().y * TICKS_TO_SECONDS);
        double vz = finiteMotion(request.velocityZ(), player.getDeltaMovement().z * TICKS_TO_SECONDS);
        RagdollDeathSnapshot snapshot = ServerPlayerDeaths.createSnapshot(
                player, ragdollId, new Vec3(vx, vy, vz));
        if (request.initialPose() != null) {
            snapshot = snapshot.withInitialPose(request.initialPose());
        }
        if (request.hasTransform() && validClientTransform(player, request)) {
            snapshot = snapshot.withTransform(request.x(), request.y(), request.z(),
                    request.bodyYaw(), request.pitch());
        }
        RagdollSpawnPacket packet = new RagdollSpawnPacket(
                snapshot, request.initialLimbPose(), true);
        if (!spawn(player.serverLevel(), packet, true)) return;
        LIVE_RAGDOLLS.put(player.getUUID(), new LiveRagdollSession(
                player.serverLevel(), ragdollId, player.isNoGravity(), player.noPhysics,
                player.getPose(), player.getForcedPose(), player.getDeltaMovement(),
                player.fallDistance));
        player.stopUsingItem();
        player.setSprinting(false);
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setForcedPose(Pose.SWIMMING);
        player.setPose(Pose.SWIMMING);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        PacketDistributor.sendToPlayersInDimension(player.serverLevel(),
                new LiveRagdollStatePacket(player.getUUID(), ragdollId, true));
    }

    public static void beginSleepRagdoll(ServerPlayer player,
                                         SleepRagdollRequestPacket request) {
        if (!RagdollConfig.enabled() || !RagdollConfig.sleepRagdollEnabled()
                || player == null || request == null || !player.isAlive()
                || !player.isSleeping() || player.isSpectator()
                || SLEEP_RAGDOLLS.containsKey(player.getUUID())
                || !validSleepTransform(player, request)) {
            return;
        }
        UUID ragdollId = UUID.randomUUID();
        Vec3 velocity = new Vec3(
                finiteMotion(request.velocityX(), 0.0),
                finiteMotion(request.velocityY(), 0.0),
                finiteMotion(request.velocityZ(), 0.0));
        RagdollDeathSnapshot snapshot = ServerPlayerDeaths.createSnapshot(
                        player, ragdollId, velocity)
                .withInitialPose(request.initialPose())
                .withTransform(request.x(), request.y(), request.z(),
                        request.bodyYaw(), request.pitch());
        RagdollSpawnPacket packet = new RagdollSpawnPacket(
                snapshot, request.initialLimbPose(), true);
        if (!spawn(player.serverLevel(), packet, true)) return;
        SLEEP_RAGDOLLS.put(player.getUUID(),
                new SleepRagdollSession(player.serverLevel(), ragdollId));
        PacketDistributor.sendToPlayersInDimension(player.serverLevel(),
                new SleepRagdollStatePacket(player.getUUID(), ragdollId, true));
    }

    private static boolean validSleepTransform(ServerPlayer player,
                                                SleepRagdollRequestPacket request) {
        return Double.isFinite(request.x()) && Double.isFinite(request.y())
                && Double.isFinite(request.z()) && Float.isFinite(request.bodyYaw())
                && Float.isFinite(request.pitch()) && Math.abs(request.pitch()) <= 90.0f
                && request.initialPose() != null
                && player.position().distanceToSqr(request.x(), request.y(), request.z()) <= 64.0;
    }

    private static void endSleepRagdoll(UUID playerId) {
        SleepRagdollSession session = SLEEP_RAGDOLLS.remove(playerId);
        if (session == null) return;
        removeRagdoll(session.level, session.ragdollId);
        PacketDistributor.sendToPlayersInDimension(session.level,
                new SleepRagdollStatePacket(playerId, session.ragdollId, false));
    }

    private static void updateSleepRagdolls(ServerLevel level) {
        List<UUID> ended = new ArrayList<>();
        for (Map.Entry<UUID, SleepRagdollSession> entry : SLEEP_RAGDOLLS.entrySet()) {
            SleepRagdollSession session = entry.getValue();
            if (session.level != level) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (!RagdollConfig.enabled() || !RagdollConfig.sleepRagdollEnabled()
                    || player == null || player.serverLevel() != level
                    || !player.isSleeping()) {
                ended.add(entry.getKey());
            }
        }
        ended.forEach(ServerRagdollManager::endSleepRagdoll);
    }

    private static double finiteMotion(float requested, double fallback) {
        return Float.isFinite(requested) && Math.abs(requested) <= 80.0
                ? requested : fallback;
    }

    private static boolean validClientTransform(ServerPlayer player,
                                                LiveRagdollRequestPacket request) {
        return Double.isFinite(request.x()) && Double.isFinite(request.y())
                && Double.isFinite(request.z()) && Float.isFinite(request.bodyYaw())
                && Float.isFinite(request.pitch()) && Math.abs(request.pitch()) <= 90.0f
                && player.position().distanceToSqr(request.x(), request.y(), request.z()) <= 64.0;
    }

    public static void endLiveRagdoll(ServerPlayer player, boolean moveToBody) {
        LiveRagdollSession session = LIVE_RAGDOLLS.remove(player.getUUID());
        if (session == null) return;
        ServerLevel ragdollLevel = session.level;
        boolean leaveGliding = moveToBody && session.pose == Pose.FALL_FLYING;
        Vec3 ragdollVelocity = leaveGliding
                ? liveBodyVelocity(ragdollLevel, session.ragdollId) : null;
        if (moveToBody && player.serverLevel() == ragdollLevel) {
            Vec3 body = liveBodyPosition(ragdollLevel, session.ragdollId);
            if (body != null) {
                Vec3 safe = findSafePosition(player, body);
                if (safe != null) player.setPos(safe);
            }
        }
        if (leaveGliding) {
            ((EntityFallFlyingAccessor) (Object) player)
                    .ragdoll$setSharedFlag(7, false);
            player.setForcedPose(null);
            player.setPose(Pose.STANDING);
        } else {
            player.setForcedPose(session.forcedPose);
            player.setPose(session.pose == null ? Pose.STANDING : session.pose);
        }
        player.setNoGravity(session.noGravity);
        player.noPhysics = session.noPhysics;
        player.fallDistance = leaveGliding ? 0.0f : session.fallDistance;
        Vec3 restoredVelocity = session.velocity;
        if (leaveGliding) {
            double verticalVelocity = ragdollVelocity == null
                    ? session.velocity.y
                    : ragdollVelocity.y / TICKS_TO_SECONDS;
            restoredVelocity = new Vec3(0.0,
                    Math.clamp(verticalVelocity, -3.92, -0.08), 0.0);
        }
        player.setDeltaMovement(restoredVelocity);
        player.hurtMarked = true;
        removeRagdoll(ragdollLevel, session.ragdollId);
        PacketDistributor.sendToPlayersInDimension(ragdollLevel,
                new LiveRagdollStatePacket(player.getUUID(), session.ragdollId, false));
    }

    private static Vec3 liveBodyPosition(ServerLevel level, UUID ragdollId) {
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(level);
        ServerRagdoll ragdoll = ragdolls == null ? null : ragdolls.get(ragdollId);
        return ragdoll == null ? null : statePosition(ragdoll.world.captureState());
    }

    private static Vec3 liveBodyVelocity(ServerLevel level, UUID ragdollId) {
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(level);
        ServerRagdoll ragdoll = ragdolls == null ? null : ragdolls.get(ragdollId);
        if (ragdoll == null) return null;
        for (RagdollPhysicsState.PartState part
                : ragdoll.world.captureState().parts()) {
            if ("body".equals(part.partName())
                    && Double.isFinite(part.linearX())
                    && Double.isFinite(part.linearY())
                    && Double.isFinite(part.linearZ())) {
                return new Vec3(part.linearX(), part.linearY(), part.linearZ());
            }
        }
        return null;
    }

    private static Vec3 findSafePosition(ServerPlayer player, Vec3 body) {
        Vec3 feet = new Vec3(body.x, body.y - 0.5, body.z);
        for (int up = 0; up <= 3; ++up) {
            Vec3 candidate = feet.add(0, up, 0);
            AABB box = player.getBoundingBox().move(candidate.subtract(player.position()));
            if (player.serverLevel().noCollision(player, box)) return candidate;
        }
        return null;
    }

    public static int removeNearest(
            ServerLevel level,
            Vec3 position) {
        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.get(level);
        UUID closestId = null;
        double closestDistanceSquared =
                Double.POSITIVE_INFINITY;

        if (ragdolls != null) {
            for (Map.Entry<UUID, ServerRagdoll> entry
                    : ragdolls.entrySet()) {
                if (entry.getValue().live) continue;
                Vec3 ragdollPosition = statePosition(
                        entry.getValue().world.captureState());
                if (ragdollPosition == null) {
                    continue;
                }
                double distanceSquared =
                        ragdollPosition.distanceToSqr(position);
                if (distanceSquared < closestDistanceSquared) {
                    closestDistanceSquared = distanceSquared;
                    closestId = entry.getKey();
                }
            }
        }

        for (RagdollSavedData.StoredRagdoll stored
                : RagdollSavedData.get(level).entries()) {
            UUID ragdollId = stored.packet().ragdollId();
            if (ragdolls != null
                    && ragdolls.containsKey(ragdollId)) {
                continue;
            }
            Vec3 ragdollPosition =
                    statePosition(stored.state());
            if (ragdollPosition == null) {
                continue;
            }
            double distanceSquared =
                    ragdollPosition.distanceToSqr(position);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestId = ragdollId;
            }
        }

        return closestId != null
                && removeRagdoll(level, closestId) ? 1 : 0;
    }

    public static int removeAll(ServerLevel level) {
        List<ServerPlayer> livePlayers = LIVE_RAGDOLLS.entrySet().stream()
                .filter(entry -> entry.getValue().level == level)
                .map(entry -> level.getServer().getPlayerList().getPlayer(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
        for (ServerPlayer player : livePlayers) endLiveRagdoll(player, false);
        RagdollSavedData savedData =
                RagdollSavedData.get(level);
        Set<UUID> ragdollIds = new HashSet<>();
        for (RagdollSavedData.StoredRagdoll stored
                : savedData.entries()) {
            ragdollIds.add(stored.packet().ragdollId());
        }

        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.remove(level);
        if (ragdolls != null) {
            for (Map.Entry<UUID, ServerRagdoll> entry : ragdolls.entrySet()) {
                if (entry.getValue().live) continue;
                ragdollIds.add(entry.getKey());
                ServerRagdoll ragdoll = entry.getValue();
                ragdoll.world.close();
            }
            ragdolls.entrySet().removeIf(entry -> !entry.getValue().live);
            if (ragdolls.isEmpty()) BY_LEVEL.remove(level);
        }
        savedData.clear();
        for (UUID ragdollId : ragdollIds) {
            PacketDistributor.sendToPlayersInDimension(
                    level,
                    new RagdollRemovePacket(ragdollId));
        }
        clearPhysicsGunForLevel(level);
        return ragdollIds.size();
    }

    public static boolean removeEmptyRagdoll(
            ServerLevel level, UUID ragdollId) {
        return removeRagdoll(level, ragdollId);
    }

    public static void syncAppearance(ServerLevel level, UUID ragdollId) {
        for (RagdollSavedData.StoredRagdoll stored
                : RagdollSavedData.get(level).entries()) {
            if (stored.packet().ragdollId().equals(ragdollId)) {
                LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(level);
                ServerRagdoll active = ragdolls == null ? null : ragdolls.get(ragdollId);
                if (active != null) {
                    active.packet = stored.packet();
                }
                PacketDistributor.sendToPlayersInDimension(level, stored.packet());
                return;
            }
        }
    }

    private static boolean removeRagdoll(
            ServerLevel level,
            UUID ragdollId) {
        if (RagdollSavedData.get(level).hasLoot(ragdollId)) {
            return false;
        }
        boolean removed = RagdollSavedData.get(level)
                .remove(ragdollId);
        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.get(level);
        if (ragdolls != null) {
            ServerRagdoll ragdoll =
                    ragdolls.remove(ragdollId);
            if (ragdoll != null) {
                ragdoll.world.close();
                removed = true;
            }
            if (ragdolls.isEmpty()) {
                BY_LEVEL.remove(level);
            }
        }
        if (!removed) {
            return false;
        }

        boolean physicsGunChanged =
                PHYSICS_GUN_GRABS.values().removeIf(
                        grab -> grab.ragdollId.equals(
                                ragdollId));
        PacketDistributor.sendToPlayersInDimension(
                level, new RagdollRemovePacket(ragdollId));
        if (physicsGunChanged) {
            syncPhysicsGun(level);
        }
        return true;
    }

    private static Vec3 statePosition(
            RagdollPhysicsState state) {
        for (RagdollPhysicsState.PartState part
                : state.parts()) {
            if ("body".equals(part.partName())
                    && Double.isFinite(part.x())
                    && Double.isFinite(part.y())
                    && Double.isFinite(part.z())) {
                return new Vec3(
                        part.x(), part.y(), part.z());
            }
        }
        return null;
    }

    public static void handleControl(
            RagdollControlRequestPacket packet, ServerPlayer sender) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(sender.serverLevel());
        if (ragdolls == null) {
            return;
        }
        ServerRagdoll ragdoll = ragdolls.get(packet.ragdollId());
        if (ragdoll == null) {
            return;
        }
        if (packet.action() == RagdollControlRequestPacket.Action.RELEASE) {
            if (sender.getUUID().equals(ragdoll.grabber)) {
                ragdoll.releaseGrab();
            }
            return;
        }
        if (!validVector(packet.hitX(), packet.hitY(), packet.hitZ())) {
            return;
        }
        switch (packet.action()) {
            case GRAB -> {
                boolean sameGrabber = sender.getUUID().equals(ragdoll.grabber);
                boolean occupied = ragdoll.grabber != null && !sameGrabber;
                boolean stale = occupied
                        && sender.serverLevel().getGameTime() - ragdoll.lastControlTick
                        > GRAB_TIMEOUT_TICKS;
                if (!validVector(packet.targetX(), packet.targetY(), packet.targetZ())
                        || !nearPlayer(sender, packet.targetX(), packet.targetY(), packet.targetZ())
                        || !sameGrabber
                        && !nearPlayer(sender, packet.hitX(), packet.hitY(), packet.hitZ())
                        || occupied && !stale) {
                    return;
                }
                if (stale) {
                    ragdoll.releaseGrab();
                }
                if (ragdoll.world.updateGrab(
                        packet.partName(),
                        packet.hitX(),
                        packet.hitY(),
                        packet.hitZ(),
                        packet.targetX(),
                        packet.targetY(),
                        packet.targetZ())) {
                    ragdoll.grabber = sender.getUUID();
                    ragdoll.lastControlTick = sender.serverLevel().getGameTime();
                }
            }
            case ATTACK -> {
                if (!nearPlayer(sender, packet.hitX(), packet.hitY(), packet.hitZ())
                        || !validVector(
                        packet.directionX(), packet.directionY(), packet.directionZ())) {
                    return;
                }
                Vec3 direction = new Vec3(
                        packet.directionX(), packet.directionY(), packet.directionZ());
                if (direction.lengthSqr() < 0.5 || direction.lengthSqr() > 1.5) {
                    return;
                }
                ragdoll.world.applyAttack(
                        packet.partName(),
                        packet.hitX(),
                        packet.hitY(),
                        packet.hitZ(),
                        direction.x,
                        direction.y,
                        direction.z);
            }
            case RELEASE -> {
                // Handled before validating positional fields.
            }
        }
    }

    public static void handlePhysicsGun(
            RagdollPhysicsGunActionPacket packet,
            ServerPlayer player) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        if (packet == null || player == null) {
            return;
        }
        boolean holdingPhysicsGun =
                isHoldingPhysicsGun(player);
        if (!holdingPhysicsGun
                && packet.action()
                != RagdollPhysicsGunActionPacket.Action
                .STOP_GRAB_ATTEMPT) {
            stopPhysicsGun(player);
            return;
        }
        switch (packet.action()) {
            case START_GRAB_ATTEMPT -> {
                if (!PHYSICS_GUN_GRABS.containsKey(
                        player.getUUID())
                        && PHYSICS_GUN_ATTEMPTS.add(
                        player.getUUID())) {
                    syncPhysicsGun(player.serverLevel());
                }
            }
            case STOP_GRAB_ATTEMPT -> stopPhysicsGun(player);
            case UPDATE_SCROLL -> {
                if (!Float.isFinite(packet.value1())) {
                    return;
                }
                PhysicsGunGrab grab =
                        PHYSICS_GUN_GRABS.get(player.getUUID());
                if (grab != null) {
                    grab.distance = Math.clamp(
                            grab.distance + packet.value1(),
                            PHYSICS_GUN_MIN_DISTANCE,
                            PHYSICS_GUN_MAX_DISTANCE);
                }
            }
            case START_ROTATION_MODE ->
                    startPhysicsGunRotation(player);
            case STOP_ROTATION_MODE ->
                    stopPhysicsGunRotation(player);
            case UPDATE_ROTATION -> {
                if (Float.isFinite(packet.value1())
                        && Float.isFinite(packet.value2())) {
                    updatePhysicsGunRotation(
                            player,
                            packet.value1(),
                            packet.value2());
                }
            }
            case FREEZE_OBJECT -> freezePhysicsGunObject(player);
        }
    }

    /** Coalesce block changes until the next physics tick; never rebuild in a block callback. */
    public static void markTerrainDirty(ServerLevel level, BlockPos pos) {
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(level);
        if (ragdolls == null || ragdolls.isEmpty()) {
            return;
        }
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            ragdoll.world.markTerrainDirty(pos);
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        updateSleepRagdolls(level);
        if (!RagdollConfig.enabled()) {
            for (ServerPlayer player : level.players()) endLiveRagdoll(player, false);
            suspendRagdolls(level);
            return;
        }
        restoreSavedRagdolls(level);
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(level);
        if (ragdolls == null || ragdolls.isEmpty()) {
            clearPhysicsGunForLevel(level);
            return;
        }
        tickPhysicsGuns(level, ragdolls);
        long gameTime = level.getGameTime();
        long lifetime = RagdollConfig.despawnTicks();
        List<JoltPhysicsWorld> tickingWorlds = new ArrayList<>(ragdolls.size());
        Iterator<ServerRagdoll> iterator = ragdolls.values().iterator();
        while (iterator.hasNext()) {
            ServerRagdoll ragdoll = iterator.next();
            if (ragdoll.grabber != null
                    && gameTime - ragdoll.lastControlTick > GRAB_TIMEOUT_TICKS) {
                ragdoll.releaseGrab();
            }
            if (!ragdoll.live && lifetime > 0L
                    && ++ragdoll.ageTicks >= lifetime
                    && !RagdollSavedData.get(level).hasLoot(
                    ragdoll.packet.ragdollId())) {
                PacketDistributor.sendToPlayersInDimension(
                        level, new RagdollRemovePacket(ragdoll.packet.ragdollId()));
                RagdollSavedData.get(level).remove(
                        ragdoll.packet.ragdollId());
                ragdoll.world.close();
                iterator.remove();
                continue;
            }
            boolean shouldPause = !ragdoll.world.isSimulationRegionLoaded();
            if (shouldPause && !ragdoll.paused) {
                ragdoll.unloadedState = ragdoll.world.captureState();
            } else if (!shouldPause && ragdoll.paused) {
                ragdoll.world.refreshTerrainBeforeResume();
                if (ragdoll.unloadedState != null) {
                    ragdoll.world.applyState(ragdoll.unloadedState);
                }
                ragdoll.unloadedState = null;
            }
            ragdoll.paused = shouldPause;
            if (!shouldPause) {
                pushFromPlayers(level, ragdoll);
                tickingWorlds.add(ragdoll.world);
            }
        }
        if (ragdolls.isEmpty()) {
            BY_LEVEL.remove(level);
            return;
        }
        JoltPhysicsWorld.tickAll(tickingWorlds);
        RagdollSavedData savedData = RagdollSavedData.get(level);
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            RagdollPhysicsState state = ragdoll.world.captureState();
            if (ragdoll.live) {
                LiveRagdollSession session = LIVE_RAGDOLLS.get(ragdoll.packet.toSnapshot().playerId());
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(
                        ragdoll.packet.toSnapshot().playerId());
                Vec3 torso = statePosition(state);
                if (session != null && player != null && player.serverLevel() == level && torso != null) {
                    player.setPos(torso.x, torso.y - 0.5, torso.z);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                }
            } else {
                savedData.put(ragdoll.packet, state, ragdoll.ageTicks);
            }
            PacketDistributor.sendToPlayersInDimension(
                    level,
                    createStatePacket(ragdoll, state));
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendExisting(player);
            sendPhysicsGunState(player);
            sendLiveRagdollStates(player);
            sendSleepRagdollStates(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endLiveRagdoll(player, false);
            endSleepRagdoll(player.getUUID());
        }
    }

    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endLiveRagdoll(player, false);
            endSleepRagdoll(player.getUUID());
            PHYSICS_GUN_ATTEMPTS.remove(player.getUUID());
            PHYSICS_GUN_GRABS.remove(player.getUUID());
            sendExisting(player);
            sendPhysicsGunState(player);
            sendLiveRagdollStates(player);
            sendSleepRagdollStates(player);
        }
    }

    private static void sendLiveRagdollStates(ServerPlayer player) {
        for (Map.Entry<UUID, LiveRagdollSession> entry : LIVE_RAGDOLLS.entrySet()) {
            if (entry.getValue().level == player.serverLevel()) {
                PacketDistributor.sendToPlayer(player, new LiveRagdollStatePacket(
                        entry.getKey(), entry.getValue().ragdollId, true));
            }
        }
    }

    private static void sendSleepRagdollStates(ServerPlayer player) {
        for (Map.Entry<UUID, SleepRagdollSession> entry : SLEEP_RAGDOLLS.entrySet()) {
            if (entry.getValue().level == player.serverLevel()) {
                PacketDistributor.sendToPlayer(player, new SleepRagdollStatePacket(
                        entry.getKey(), entry.getValue().ragdollId, true));
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID playerId : new ArrayList<>(SLEEP_RAGDOLLS.keySet())) {
            endSleepRagdoll(playerId);
        }
        for (ServerLevel level : new ArrayList<>(BY_LEVEL.keySet())) {
            for (ServerPlayer player : level.players()) endLiveRagdoll(player, false);
        }
        for (Map.Entry<ServerLevel, LinkedHashMap<UUID, ServerRagdoll>> levelEntry
                : BY_LEVEL.entrySet()) {
            RagdollSavedData savedData =
                    RagdollSavedData.get(levelEntry.getKey());
            for (ServerRagdoll ragdoll : levelEntry.getValue().values()) {
                savedData.put(
                        ragdoll.packet,
                        ragdoll.world.captureState(),
                        ragdoll.ageTicks);
                ragdoll.world.close();
            }
        }
        BY_LEVEL.clear();
        PHYSICS_GUN_GRABS.clear();
        PHYSICS_GUN_ATTEMPTS.clear();
    }

    public static void onServerStarted(ServerStartedEvent event) {
        JoltBootstrap.initialize();
    }

    private static void tickPhysicsGuns(
            ServerLevel level,
            LinkedHashMap<UUID, ServerRagdoll> ragdolls) {
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            ragdoll.world.beginPhysicsGunTargetUpdate();
        }
        Set<UUID> presentPlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            UUID playerId = player.getUUID();
            presentPlayers.add(playerId);
            if (!isHoldingPhysicsGun(player)) {
                if (PHYSICS_GUN_ATTEMPTS.contains(playerId)
                        || PHYSICS_GUN_GRABS.containsKey(playerId)) {
                    stopPhysicsGun(player);
                }
                continue;
            }
            if (PHYSICS_GUN_ATTEMPTS.contains(playerId)
                    && !PHYSICS_GUN_GRABS.containsKey(playerId)) {
                startPhysicsGunGrab(player, ragdolls);
            }
            PhysicsGunGrab grab = PHYSICS_GUN_GRABS.get(playerId);
            if (grab == null) {
                continue;
            }
            ServerRagdoll ragdoll = ragdolls.get(grab.ragdollId);
            if (ragdoll == null || ragdoll.paused) {
                stopPhysicsGun(player);
                continue;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 target = eye.add(look.scale(grab.distance));
            Quaternionf targetRotation;
            if (grab.rotationMode) {
                targetRotation = new Quaternionf(grab.bodyRotation);
            } else {
                targetRotation = playerRotation(player)
                        .mul(new Quaternionf(
                                grab.playerRotation).conjugate())
                        .mul(new Quaternionf(grab.bodyRotation))
                        .normalize();
            }
            if (!ragdoll.world.setPhysicsGunTarget(
                    playerId,
                    grab.partName,
                    grab.localX,
                    grab.localY,
                    grab.localZ,
                    target.x,
                    target.y,
                    target.z,
                    targetRotation.x,
                    targetRotation.y,
                    targetRotation.z,
                    targetRotation.w,
                    grab.rotationMode)) {
                stopPhysicsGun(player);
            }
        }
        PHYSICS_GUN_ATTEMPTS.removeIf(playerId ->
                !presentPlayers.contains(playerId)
                        && level.getServer().getPlayerList()
                        .getPlayer(playerId) == null);
        PHYSICS_GUN_GRABS.keySet().removeIf(playerId ->
                !presentPlayers.contains(playerId)
                        && level.getServer().getPlayerList()
                        .getPlayer(playerId) == null);
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            ragdoll.world.finishPhysicsGunTargetUpdate();
        }
    }

    private static void startPhysicsGunGrab(
            ServerPlayer player,
            LinkedHashMap<UUID, ServerRagdoll> ragdolls) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        ServerRagdoll closestRagdoll = null;
        JoltPhysicsWorld.GrabHit closestHit = null;
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            JoltPhysicsWorld.GrabHit hit =
                    ragdoll.world.findGrabTarget(
                            eye.x,
                            eye.y,
                            eye.z,
                            direction.x,
                            direction.y,
                            direction.z,
                            PHYSICS_GUN_MAX_DISTANCE);
            if (hit != null && (closestHit == null
                    || hit.distance() < closestHit.distance())) {
                closestRagdoll = ragdoll;
                closestHit = hit;
            }
        }
        if (closestRagdoll == null) {
            return;
        }
        JoltPhysicsWorld.PhysicsGunHit hit =
                closestRagdoll.world.createPhysicsGunHit(closestHit);
        if (hit == null || isPartGrabbed(
                closestRagdoll.packet.ragdollId(), hit.partName())) {
            PHYSICS_GUN_ATTEMPTS.remove(player.getUUID());
            syncPhysicsGun(player.serverLevel());
            return;
        }
        Quaternionf bodyRotation = new Quaternionf(
                hit.rotationX(),
                hit.rotationY(),
                hit.rotationZ(),
                hit.rotationW()).normalize();
        PHYSICS_GUN_GRABS.put(
                player.getUUID(),
                new PhysicsGunGrab(
                        closestRagdoll.packet.ragdollId(),
                        hit.partName(),
                        hit.localX(),
                        hit.localY(),
                        hit.localZ(),
                        hit.distance(),
                        bodyRotation,
                        playerRotation(player)));
        PHYSICS_GUN_ATTEMPTS.remove(player.getUUID());
        syncPhysicsGun(player.serverLevel());
    }

    private static boolean isPartGrabbed(
            UUID ragdollId, String partName) {
        for (PhysicsGunGrab grab : PHYSICS_GUN_GRABS.values()) {
            if (grab.ragdollId.equals(ragdollId)
                    && grab.partName.equals(partName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHoldingPhysicsGun(
            ServerPlayer player) {
        return player.getMainHandItem().is(
                RagdollItems.PHYSICS_GUN.get())
                || player.getOffhandItem().is(
                RagdollItems.PHYSICS_GUN.get());
    }

    private static void startPhysicsGunRotation(
            ServerPlayer player) {
        PhysicsGunGrab grab =
                PHYSICS_GUN_GRABS.get(player.getUUID());
        if (grab == null || grab.rotationMode) {
            return;
        }
        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.get(player.serverLevel());
        ServerRagdoll ragdoll = ragdolls == null
                ? null : ragdolls.get(grab.ragdollId);
        Quaternionf currentBodyRotation = ragdoll == null
                ? null : ragdoll.world.physicsGunPartRotation(
                        grab.partName);
        if (currentBodyRotation == null) {
            return;
        }
        grab.bodyRotation = currentBodyRotation;
        grab.playerRotation = playerRotation(player);
        grab.rotationMode = true;
    }

    private static void stopPhysicsGunRotation(
            ServerPlayer player) {
        PhysicsGunGrab grab =
                PHYSICS_GUN_GRABS.get(player.getUUID());
        if (grab == null || !grab.rotationMode) {
            return;
        }
        grab.playerRotation = playerRotation(player);
        grab.rotationMode = false;
    }

    private static void updatePhysicsGunRotation(
            ServerPlayer player,
            float deltaX,
            float deltaY) {
        PhysicsGunGrab grab =
                PHYSICS_GUN_GRABS.get(player.getUUID());
        if (grab == null || !grab.rotationMode) {
            return;
        }
        float sensitivity = 0.003f;
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0e-7) {
            double yaw = Math.toRadians(player.getYRot());
            right = new Vec3(
                    -Math.sin(yaw), 0.0, Math.cos(yaw));
        } else {
            right = right.normalize();
        }
        Quaternionf yaw = new Quaternionf().fromAxisAngleRad(
                0.0f, 1.0f, 0.0f,
                deltaX * sensitivity);
        Quaternionf pitch = new Quaternionf().fromAxisAngleRad(
                (float) right.x,
                (float) right.y,
                (float) right.z,
                deltaY * sensitivity);
        grab.bodyRotation = yaw.mul(pitch)
                .mul(grab.bodyRotation)
                .normalize();
    }

    private static void freezePhysicsGunObject(
            ServerPlayer player) {
        PhysicsGunGrab grab =
                PHYSICS_GUN_GRABS.remove(player.getUUID());
        PHYSICS_GUN_ATTEMPTS.remove(player.getUUID());
        if (grab == null) {
            return;
        }
        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.get(player.serverLevel());
        ServerRagdoll ragdoll = ragdolls == null
                ? null : ragdolls.get(grab.ragdollId);
        if (ragdoll != null) {
            ragdoll.world.freezePhysicsGunPart(grab.partName);
        }
        syncPhysicsGun(player.serverLevel());
    }

    private static void stopPhysicsGun(ServerPlayer player) {
        boolean changed =
                PHYSICS_GUN_ATTEMPTS.remove(player.getUUID());
        changed |= PHYSICS_GUN_GRABS.remove(
                player.getUUID()) != null;
        if (changed) {
            syncPhysicsGun(player.serverLevel());
        }
    }

    private static Quaternionf playerRotation(
            ServerPlayer player) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(
                        -player.getYRot()))
                .rotateX((float) Math.toRadians(
                        player.getXRot()))
                .normalize();
    }

    private static void syncPhysicsGun(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(
                level, physicsGunPacket(level));
    }

    private static void sendPhysicsGunState(
            ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                physicsGunPacket(player.serverLevel()));
    }

    private static RagdollPhysicsGunSyncPacket physicsGunPacket(
            ServerLevel level) {
        List<RagdollPhysicsGunSyncPacket.GrabData> grabs =
                new ArrayList<>();
        Set<UUID> levelPlayers = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            levelPlayers.add(player.getUUID());
        }
        for (Map.Entry<UUID, PhysicsGunGrab> entry
                : PHYSICS_GUN_GRABS.entrySet()) {
            if (!levelPlayers.contains(entry.getKey())) {
                continue;
            }
            PhysicsGunGrab grab = entry.getValue();
            grabs.add(new RagdollPhysicsGunSyncPacket.GrabData(
                    entry.getKey(),
                    grab.ragdollId,
                    grab.partName,
                    grab.localX,
                    grab.localY,
                    grab.localZ));
        }
        List<UUID> attempts = PHYSICS_GUN_ATTEMPTS.stream()
                .filter(levelPlayers::contains)
                .toList();
        return new RagdollPhysicsGunSyncPacket(grabs, attempts);
    }

    private static void clearPhysicsGunForLevel(
            ServerLevel level) {
        boolean changed = false;
        for (ServerPlayer player : level.players()) {
            changed |= PHYSICS_GUN_ATTEMPTS.remove(
                    player.getUUID());
            changed |= PHYSICS_GUN_GRABS.remove(
                    player.getUUID()) != null;
        }
        if (changed) {
            syncPhysicsGun(level);
        }
    }

    private static void suspendRagdolls(ServerLevel level) {
        clearPhysicsGunForLevel(level);
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.remove(level);
        if (ragdolls == null) {
            return;
        }
        // Retain saved bodies so enabling the mod can restore them normally.
        RagdollSavedData savedData = RagdollSavedData.get(level);
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            ragdoll.releaseGrab();
            if (!ragdoll.live) savedData.put(ragdoll.packet, ragdoll.world.captureState(), ragdoll.ageTicks);
            PacketDistributor.sendToPlayersInDimension(
                    level, new RagdollRemovePacket(ragdoll.packet.ragdollId()));
            ragdoll.world.close();
        }
    }

    private static void sendExisting(ServerPlayer player) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        LinkedHashMap<UUID, ServerRagdoll> ragdolls = BY_LEVEL.get(player.serverLevel());
        if (ragdolls == null) {
            return;
        }
        for (ServerRagdoll ragdoll : ragdolls.values()) {
            PacketDistributor.sendToPlayer(player, ragdoll.packet);
            PacketDistributor.sendToPlayer(
                    player,
                    createStatePacket(ragdoll, ragdoll.world.captureState()));
        }
    }

    private static RagdollStatePacket createStatePacket(
            ServerRagdoll ragdoll, RagdollPhysicsState state) {
        return new RagdollStatePacket(
                ragdoll.packet.ragdollId(),
                System.nanoTime(),
                ++ragdoll.stateSequence,
                state);
    }

    private static void trimToLimit(
            ServerLevel level, LinkedHashMap<UUID, ServerRagdoll> ragdolls) {
        int maximum = RagdollConfig.maxRagdolls();
        while (ragdolls.size() >= maximum) {
            Iterator<Map.Entry<UUID, ServerRagdoll>> iterator =
                    ragdolls.entrySet().iterator();
            Map.Entry<UUID, ServerRagdoll> removable = null;
            while (iterator.hasNext()) {
                Map.Entry<UUID, ServerRagdoll> candidate = iterator.next();
                if (!RagdollSavedData.get(level).hasLoot(candidate.getKey())) {
                    if (candidate.getValue().live) continue;
                    removable = candidate;
                    break;
                }
            }
            if (removable == null) {
                return;
            }
            ragdolls.remove(removable.getKey());
            ServerRagdoll oldest = removable.getValue();
            oldest.world.close();
            RagdollSavedData.get(level).remove(
                    oldest.packet.ragdollId());
            PacketDistributor.sendToPlayersInDimension(
                    level, new RagdollRemovePacket(oldest.packet.ragdollId()));
        }
    }

    private static boolean nearPlayer(
            ServerPlayer player, double x, double y, double z) {
        return player.getEyePosition().distanceToSqr(x, y, z)
                <= MAX_CONTROL_DISTANCE * MAX_CONTROL_DISTANCE;
    }

    private static boolean validVector(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private static JoltPhysicsWorld createWorld(
            ServerLevel level,
            BlockPos origin,
            RagdollDeathSnapshot snapshot) {
        return createWorld(level, origin, snapshot, null, false);
    }

    private static JoltPhysicsWorld createWorld(
            ServerLevel level,
            BlockPos origin,
            RagdollDeathSnapshot snapshot,
            RagdollInitialLimbPose initialLimbPose,
            boolean liveRagdoll) {
        return JoltPhysicsWorld.createForDeath(
                level,
                origin,
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                snapshot.yaw(),
                snapshot.initialPose(),
                snapshot.xRot(),
                snapshot.velocityX(),
                snapshot.velocityY(),
                snapshot.velocityZ(),
                snapshot.modelScale(),
                liveRagdoll ? RagdollPhysicsSettings.controlledPlayerDefaults()
                        : RagdollPhysicsSettings.playerDeathDefaults(),
                initialLimbPose);
    }

    private static void restoreSavedRagdolls(ServerLevel level) {
        RagdollSavedData savedData = RagdollSavedData.get(level);
        LinkedHashMap<UUID, ServerRagdoll> ragdolls =
                BY_LEVEL.computeIfAbsent(
                        level, ignored -> new LinkedHashMap<>());
        long lifetime = RagdollConfig.despawnTicks();
        for (RagdollSavedData.StoredRagdoll stored : savedData.entries()) {
            UUID ragdollId = stored.packet().ragdollId();
            if (ragdolls.containsKey(ragdollId)) {
                continue;
            }
            if (lifetime > 0L && stored.ageTicks() >= lifetime
                    && stored.loot().isEmpty()) {
                savedData.remove(ragdollId);
                continue;
            }
            RagdollDeathSnapshot snapshot = stored.packet().toSnapshot();
            BlockPos origin =
                    BlockPos.containing(snapshot.x(), snapshot.y(), snapshot.z());
            BlockPos savedPosition =
                    savedPosition(stored.state(), origin);
            if (!JoltPhysicsWorld.isTerrainRegionLoaded(
                    level, savedPosition)) {
                continue;
            }
            JoltPhysicsWorld world = createWorld(level, origin, snapshot);
            if (world == null) {
                continue;
            }
            if (!world.applyState(stored.state())) {
                RagdollMod.LOGGER.warn(
                        "Could not restore saved physics state for ragdoll {}",
                        ragdollId);
            } else {
                world.refreshTerrainBeforeResume();
            }
            ServerRagdoll ragdoll =
                    new ServerRagdoll(stored.packet(), world);
            ragdoll.ageTicks = stored.ageTicks();
            ragdolls.put(ragdollId, ragdoll);
            PacketDistributor.sendToPlayersInDimension(
                    level, stored.packet());
            PacketDistributor.sendToPlayersInDimension(
                    level, createStatePacket(ragdoll, stored.state()));
            RagdollMod.LOGGER.info(
                    "Restored saved Jolt ragdoll {} in {}",
                    ragdollId,
                    level.dimension().location());
        }
        if (ragdolls.isEmpty()) {
            BY_LEVEL.remove(level);
        }
    }

    private static BlockPos savedPosition(
            RagdollPhysicsState state, BlockPos fallback) {
        for (RagdollPhysicsState.PartState part : state.parts()) {
            if ("body".equals(part.partName())
                    && Double.isFinite(part.x())
                    && Double.isFinite(part.y())
                    && Double.isFinite(part.z())) {
                return BlockPos.containing(part.x(), part.y(), part.z());
            }
        }
        return fallback;
    }

    private static void pushFromPlayers(
            ServerLevel level, ServerRagdoll ragdoll) {
        if (ragdoll.grabber != null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (LIVE_RAGDOLLS.containsKey(player.getUUID())) {
                continue;
            }
            if (player.isSpectator() || player.isDeadOrDying()) {
                continue;
            }
            Vec3 movementPerTick = player.position().subtract(
                    player.xOld, player.yOld, player.zOld);
            Vec3 velocityPerTick = player.getKnownMovement();
            if (movementPerTick.lengthSqr() > velocityPerTick.lengthSqr()) {
                velocityPerTick = movementPerTick;
            }
            Vec3 velocity = velocityPerTick.scale(TICKS_TO_SECONDS);
            Vec3 movement = movementPerTick.scale(TICKS_TO_SECONDS);
            AABB currentBounds = player.getBoundingBox();
            AABB bounds = currentBounds.minmax(currentBounds.move(
                    -movementPerTick.x,
                    -movementPerTick.y,
                    -movementPerTick.z)).inflate(0.12);
            ragdoll.world.pushFromPlayer(
                    bounds,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    movement.x,
                    movement.z);
        }
    }

    private static final class ServerRagdoll {
        private RagdollSpawnPacket packet;
        private final JoltPhysicsWorld world;
        private UUID grabber;
        private long lastControlTick;
        private long ageTicks;
        private long stateSequence;
        private boolean paused;
        private RagdollPhysicsState unloadedState;
        private final boolean live;

        private ServerRagdoll(RagdollSpawnPacket packet, JoltPhysicsWorld world) {
            this(packet, world, false);
        }

        private ServerRagdoll(RagdollSpawnPacket packet, JoltPhysicsWorld world, boolean live) {
            this.packet = packet;
            this.world = world;
            this.live = live;
        }

        private void releaseGrab() {
            world.releaseGrab();
            grabber = null;
            lastControlTick = 0L;
        }
    }

    private record LiveRagdollSession(ServerLevel level, UUID ragdollId, boolean noGravity,
                                      boolean noPhysics, Pose pose, Pose forcedPose,
                                      Vec3 velocity, float fallDistance) {}

    private record SleepRagdollSession(ServerLevel level, UUID ragdollId) {}

    private static final class PhysicsGunGrab {
        private final UUID ragdollId;
        private final String partName;
        private final double localX;
        private final double localY;
        private final double localZ;
        private double distance;
        private Quaternionf bodyRotation;
        private Quaternionf playerRotation;
        private boolean rotationMode;

        private PhysicsGunGrab(
                UUID ragdollId,
                String partName,
                double localX,
                double localY,
                double localZ,
                double distance,
                Quaternionf bodyRotation,
                Quaternionf playerRotation) {
            this.ragdollId = ragdollId;
            this.partName = partName;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.distance = Math.clamp(
                    distance,
                    PHYSICS_GUN_MIN_DISTANCE,
                    PHYSICS_GUN_MAX_DISTANCE);
            this.bodyRotation = bodyRotation;
            this.playerRotation = playerRotation;
        }
    }
}
