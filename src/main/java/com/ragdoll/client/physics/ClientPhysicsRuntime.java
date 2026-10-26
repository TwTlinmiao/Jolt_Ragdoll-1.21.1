package com.ragdoll.client.physics;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.ragdoll.RagdollMod;
import com.ragdoll.RagdollConfig;
import com.ragdoll.network.RagdollNetwork;
import com.ragdoll.network.RagdollControlRequestPacket;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.client.physics.RagdollPhysicsState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class ClientPhysicsRuntime {

    private static final String PLAYER_RAGDOLL_MOD_ID = "player_ragdoll";

    private static final int PENDING_RETRY_INTERVAL_TICKS = 5;

    private static final int PENDING_TIMEOUT_TICKS = 100;

    private static final int SKIN_READY_TIMEOUT_TICKS = 60;

    private static final int SKIN_REFRESH_INTERVAL_TICKS = 20;

    private static final double RENDER_BOUNDS_PADDING = 1.0;

    private static final double GRAB_REACH = 4.0;

    private static final double ATTACK_REACH = 4.0;

    private static final double TICKS_TO_SECONDS = 20.0;

    private static final List<RagdollInstance> RAGDOLLS = new ArrayList<>();

    private static final List<PendingRagdoll> PENDING_RAGDOLLS = new ArrayList<>();

    private static ClientLevel activeLevel;

    private static int skinRefreshCooldownTicks;

    private static ActiveGrab activeGrab;

    // Client-thread-only addon bodies: persistent until their owner releases them.
    private static final Map<UUID, UUID> CONTROLLED_PLAYERS = new LinkedHashMap<>();

    private static final java.util.Set<UUID> INTERACTIVE_CONTROLLED_RAGDOLLS =
            new java.util.HashSet<>();

    private static final java.util.Set<UUID> REMOTE_BODIES = new java.util.HashSet<>();

    private static final Map<UUID, RemoteRagdollInterpolator> REMOTE_STATES =
            new LinkedHashMap<>();

    private static final java.util.Set<UUID> HIDDEN_HEADS = new java.util.HashSet<>();
    private static final Map<UUID, UUID> LIVE_PLAYER_RAGDOLLS = new LinkedHashMap<>();
    private static final Map<UUID, UUID> SLEEP_PLAYER_RAGDOLLS = new LinkedHashMap<>();
    private static Vec3 previousLiveTorso;

    public static void setLiveRagdoll(UUID playerId, UUID ragdollId, boolean active) {
        if (active) LIVE_PLAYER_RAGDOLLS.put(playerId, ragdollId);
        else {
            LIVE_PLAYER_RAGDOLLS.remove(playerId, ragdollId);
            HIDDEN_HEADS.remove(ragdollId);
        }
    }

    public static boolean isLiveRagdolled(UUID playerId) {
        return LIVE_PLAYER_RAGDOLLS.containsKey(playerId);
    }

    public static UUID liveRagdollId(UUID playerId) {
        return LIVE_PLAYER_RAGDOLLS.get(playerId);
    }

    public static void setSleepRagdoll(UUID playerId, UUID ragdollId, boolean active) {
        if (active) SLEEP_PLAYER_RAGDOLLS.put(playerId, ragdollId);
        else {
            SLEEP_PLAYER_RAGDOLLS.remove(playerId, ragdollId);
            HIDDEN_HEADS.remove(ragdollId);
        }
    }

    public static UUID sleepRagdollId(UUID playerId) {
        return SLEEP_PLAYER_RAGDOLLS.get(playerId);
    }

    public static RagdollPartRenderState getLivePart(UUID playerId, String partName,
                                                      float partialTick) {
        UUID ragdollId = liveRagdollId(playerId);
        if (ragdollId == null) return null;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.id.equals(ragdollId)) {
                return ragdoll.world.getPartRenderState(partName, partialTick);
            }
        }
        return null;
    }

    public static RagdollPartRenderState getPart(UUID ragdollId, String partName,
                                                  float partialTick) {
        if (ragdollId == null) return null;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.id.equals(ragdollId)) {
                return ragdoll.world.getPartRenderState(partName, partialTick);
            }
        }
        return null;
    }

    public static void followLocalLiveRagdoll(Minecraft minecraft) {
        if (minecraft.player == null) return;
        UUID ragdollId = liveRagdollId(minecraft.player.getUUID());
        if (ragdollId == null) {
            previousLiveTorso = null;
            return;
        }
        RagdollPartRenderState torso = getLivePart(minecraft.player.getUUID(), "body", 1.0f);
        if (torso == null) return;
        Vec3 position = new Vec3(torso.x(), torso.y() - 0.5, torso.z());
        Vec3 velocity = previousLiveTorso == null ? Vec3.ZERO
                : position.subtract(previousLiveTorso).scale(20.0);
        double speed = velocity.length();
        if (speed > 3.0) velocity = velocity.scale(3.0 / speed);
        minecraft.player.setPos(position);
        minecraft.player.setDeltaMovement(velocity);
        previousLiveTorso = position;
    }

    public static void spawnControlledRagdoll(RagdollDeathSnapshot snapshot, boolean remote) {
        spawnControlledRagdoll(snapshot, remote, null);
    }

    public static void spawnControlledRagdoll(RagdollDeathSnapshot snapshot, boolean remote,
                                               RagdollInitialLimbPose initialLimbPose) {
        spawnControlledRagdoll(snapshot, remote, initialLimbPose, false);
    }

    public static void spawnControlledRagdoll(RagdollDeathSnapshot snapshot, boolean remote,
                                               RagdollInitialLimbPose initialLimbPose,
                                               boolean interactive) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null)
            return;
        if (activeLevel != level) {
            closeAll();
            activeLevel = level;
        }
        CONTROLLED_PLAYERS.put(snapshot.ragdollId(), snapshot.playerId());
        if (interactive) INTERACTIVE_CONTROLLED_RAGDOLLS.add(snapshot.ragdollId());
        if (remote)
            REMOTE_BODIES.add(snapshot.ragdollId());
        spawnDeathRagdoll(snapshot, null, RagdollPhysicsSettings.controlledPlayerDefaults(), initialLimbPose);
    }

    public static void spawnServerRagdoll(RagdollDeathSnapshot snapshot) {
        spawnServerRagdoll(snapshot, null);
    }

    public static void spawnServerRagdoll(RagdollDeathSnapshot snapshot,
                                         RagdollInitialLimbPose initialLimbPose) {
        spawnServerRagdoll(snapshot, initialLimbPose, false);
    }

    private static void spawnServerRagdoll(RagdollDeathSnapshot snapshot,
                                           RagdollInitialLimbPose initialLimbPose,
                                           boolean liveRagdoll) {
        if (!RagdollConfig.enabled() || snapshot == null) {
            return;
        }
        REMOTE_BODIES.add(snapshot.ragdollId());
        RagdollPhysicsSettings settings = liveRagdoll
                ? RagdollPhysicsSettings.controlledPlayerDefaults()
                : RagdollPhysicsSettings.playerDeathDefaults();
        spawnDeathRagdoll(snapshot, null, settings,
                initialLimbPose);
    }

    public static void updateServerRagdollAppearance(RagdollDeathSnapshot snapshot) {
        updateServerRagdollAppearance(snapshot, null);
    }

    public static void updateServerRagdollAppearance(RagdollDeathSnapshot snapshot,
                                                       RagdollInitialLimbPose initialLimbPose) {
        updateServerRagdollAppearance(snapshot, initialLimbPose, false);
    }

    public static void updateServerRagdollAppearance(RagdollDeathSnapshot snapshot,
                                                       RagdollInitialLimbPose initialLimbPose,
                                                       boolean liveRagdoll) {
        if (snapshot == null) return;
        boolean found = false;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (!ragdoll.id.equals(snapshot.ragdollId())) continue;
            ragdoll.updateAppearance(snapshot);
            found = true;
            break;
        }
        for (PendingRagdoll pending : PENDING_RAGDOLLS) {
            if (!pending.payload.ragdollId().equals(snapshot.ragdollId())) continue;
            pending.updateAppearance(snapshot);
            found = true;
            break;
        }
        if (!found) spawnServerRagdoll(snapshot, initialLimbPose, liveRagdoll);
    }

    public static void applySynchronizedState(
            UUID ragdollId,
            long serverTimestampNanos,
            long sequence,
            RagdollPhysicsState state) {
        if (!RagdollConfig.enabled() || ragdollId == null || state == null) {
            return;
        }
        REMOTE_BODIES.add(ragdollId);
        REMOTE_STATES
                .computeIfAbsent(
                        ragdollId, ignored -> new RemoteRagdollInterpolator())
                .add(
                        serverTimestampNanos,
                        sequence,
                        state,
                        System.nanoTime());
    }
    public static boolean isControlledPlayer(UUID playerId) {
        if (!RagdollConfig.enabled()) {
            return false;
        }
        if (LIVE_PLAYER_RAGDOLLS.containsKey(playerId)) return true;
        if (SLEEP_PLAYER_RAGDOLLS.containsKey(playerId)) return true;
        for (RagdollInstance body : RAGDOLLS) {
            if (playerId.equals(CONTROLLED_PLAYERS.get(body.id)))
                return true;
        }
        return false;
    }

    /** Rebuilds the collision shapes of all live ragdolls after a config slider change. */
    public static void refreshCollisionShape(String part) {
        for (RagdollInstance ragdoll : RAGDOLLS) {
            ragdoll.world.refreshCollisionShape(part);
        }
    }

    /**
     * A copied, interpolated pose, independent of visibility and head hiding.
     */
    public static RagdollPartRenderState getControlledPart(UUID id, String part, float partialTick) {
        for (RagdollInstance body : RAGDOLLS) {
            if (body.id.equals(id) && CONTROLLED_PLAYERS.containsKey(id)) {
                return body.world.getPartRenderState(part, partialTick);
            }
        }
        return null;
    }

    public static Vec3 getRagdollLocalPoint(
            UUID ragdollId,
            String partName,
            double localX,
            double localY,
            double localZ,
            float partialTick) {
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (!ragdoll.id.equals(ragdollId)) {
                continue;
            }
            RagdollPartRenderState part =
                    ragdoll.world.getPartRenderState(
                            partName, partialTick);
            if (part == null) {
                return null;
            }
            double[] offset = rotatePoint(
                    localX,
                    localY,
                    localZ,
                    part.rotationX(),
                    part.rotationY(),
                    part.rotationZ(),
                    part.rotationW());
            return new Vec3(
                    part.x() + offset[0],
                    part.y() + offset[1],
                    part.z() + offset[2]);
        }
        return null;
    }

    private static double[] rotatePoint(
            double x,
            double y,
            double z,
            double quaternionX,
            double quaternionY,
            double quaternionZ,
            double quaternionW) {
        double crossX = 2.0
                * (quaternionY * z - quaternionZ * y);
        double crossY = 2.0
                * (quaternionZ * x - quaternionX * z);
        double crossZ = 2.0
                * (quaternionX * y - quaternionY * x);
        return new double[]{
                x + quaternionW * crossX
                        + quaternionY * crossZ
                        - quaternionZ * crossY,
                y + quaternionW * crossY
                        + quaternionZ * crossX
                        - quaternionX * crossZ,
                z + quaternionW * crossZ
                        + quaternionX * crossY
                        - quaternionY * crossX};
    }

    public static RagdollPhysicsState captureControlledState(UUID id) {
        for (RagdollInstance body : RAGDOLLS) {
            if (body.id.equals(id) && CONTROLLED_PLAYERS.containsKey(id)) {
                return body.world.captureState();
            }
        }
        return null;
    }

    public static void submitControlledState(UUID id, RagdollPhysicsState state) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        if (REMOTE_BODIES.contains(id)) {
            REMOTE_STATES
                    .computeIfAbsent(
                            id, ignored -> new RemoteRagdollInterpolator())
                    .add(
                            System.nanoTime(),
                            System.nanoTime(),
                            state,
                            System.nanoTime());
        }
    }

    /**
     * Add world-space velocity in blocks/second on the owning client only.
     */
    public static boolean addControlledVelocity(UUID id, net.minecraft.world.phys.Vec3 delta) {
        if (!CONTROLLED_PLAYERS.containsKey(id) || REMOTE_BODIES.contains(id) || !Double.isFinite(delta.x) || !Double.isFinite(delta.y) || !Double.isFinite(delta.z) || delta.lengthSqr() > 10000)
            return false;
        for (RagdollInstance body : RAGDOLLS) {
            if (body.id.equals(id)) {
                body.world.addVelocity((float) delta.x, (float) delta.y, (float) delta.z);
                return true;
            }
        }
        return false;
    }

    public static void setControlledHeadHidden(UUID id, boolean hidden) {
        if (hidden && (CONTROLLED_PLAYERS.containsKey(id)
                || LIVE_PLAYER_RAGDOLLS.containsValue(id)
                || SLEEP_PLAYER_RAGDOLLS.containsValue(id)))
            HIDDEN_HEADS.add(id);
        else
            HIDDEN_HEADS.remove(id);
    }

    private ClientPhysicsRuntime() {
    }

    public static void tick(Minecraft minecraft) {
        if (!RagdollConfig.enabled()) {
            closeAll();
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null) {
            closeAll();
            return;
        }
        if (activeLevel != level) {
            closeAll();
            activeLevel = level;
        }
        if (minecraft.isPaused()) {
            return;
        }
        if (skinRefreshCooldownTicks > 0) {
            --skinRefreshCooldownTicks;
        }
        tickPendingRagdolls(level);
        removeExpiredRagdolls();
        if (skinRefreshCooldownTicks <= 0) {
            refreshRagdollSkins(level);
            skinRefreshCooldownTicks = SKIN_REFRESH_INTERVAL_TICKS;
        }
        pushRagdollsFromPlayer(minecraft);
        List<JoltPhysicsWorld> tickingWorlds = new ArrayList<>();
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (REMOTE_BODIES.contains(ragdoll.id)) {
                RemoteRagdollInterpolator stream =
                        REMOTE_STATES.get(ragdoll.id);
                RagdollPhysicsState streamed = stream == null
                        ? null
                        : stream.sample(System.nanoTime());
                if (streamed != null)
                    ragdoll.world.applyStreamedState(streamed);
                continue;
            }
            boolean shouldPause = !ragdoll.world.isSimulationRegionLoaded();
            boolean wasPaused = ragdoll.simulationPaused;
            if (shouldPause != ragdoll.simulationPaused) {
                RagdollMod.LOGGER.debug("{} Jolt simulation for ragdoll {} because its " + "terrain region is {}loaded", shouldPause ? "Paused" : "Resumed", ragdoll.id, shouldPause ? "not " : "");
            }
            if (shouldPause && !wasPaused) {
                ragdoll.unloadedState = ragdoll.world.captureState();
            } else if (!shouldPause && wasPaused) {
                ragdoll.world.refreshTerrainBeforeResume();
                if (ragdoll.unloadedState != null) {
                    ragdoll.world.applyState(ragdoll.unloadedState);
                }
                ragdoll.unloadedState = null;
            }
            ragdoll.simulationPaused = shouldPause;
            if (ragdoll.simulationPaused) {
                continue;
            }
            tickingWorlds.add(ragdoll.world);
        }
        JoltPhysicsWorld.tickAll(tickingWorlds);
    }

    public static void updateGrabInput(Minecraft minecraft, boolean grabDown) {
        if (!RagdollConfig.enabled()) {
            releaseGrab();
            return;
        }
        if (!grabDown || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            releaseGrab();
            return;
        }
        if (activeGrab == null) {
            activeGrab = selectGrabTarget(minecraft);
            if (activeGrab == null) {
                return;
            }
        }
        if (!RAGDOLLS.contains(activeGrab.ragdoll) || activeGrab.ragdoll.simulationPaused) {
            releaseGrab();
            return;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 direction = minecraft.player.getViewVector(1.0f).normalize();
        Vec3 target = eye.add(direction.scale(activeGrab.distance));
        if (REMOTE_BODIES.contains(activeGrab.ragdoll.id)) {
            RagdollNetwork.sendControl(RagdollControlRequestPacket.grab(
                    activeGrab.ragdoll.id,
                    activeGrab.part.name(),
                    activeGrab.grabX,
                    activeGrab.grabY,
                    activeGrab.grabZ,
                    target.x,
                    target.y,
                    target.z));
            return;
        }
        if (!activeGrab.ragdoll.world.updateGrab(activeGrab.part, activeGrab.grabX, activeGrab.grabY, activeGrab.grabZ, target.x, target.y, target.z)) {
            releaseGrab();
        }
    }

    public static boolean attackLookedAtRagdoll(Minecraft minecraft) {
        if (!RagdollConfig.enabled()) {
            return false;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || RAGDOLLS.isEmpty()) {
            return false;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 direction = minecraft.player.getViewVector(1.0f).normalize();
        double maximumDistance = ATTACK_REACH;
        HitResult vanillaHit = minecraft.hitResult;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            maximumDistance = Math.min(maximumDistance, eye.distanceTo(vanillaHit.getLocation()) + 0.05);
        }
        RagdollInstance closestRagdoll = null;
        JoltPhysicsWorld.GrabHit closestHit = null;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.simulationPaused || isNonInteractiveControlled(ragdoll.id)) {
                continue;
            }
            JoltPhysicsWorld.GrabHit hit = ragdoll.world.findGrabTarget(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z, maximumDistance);
            if (hit != null && (closestHit == null || hit.distance() < closestHit.distance())) {
                closestRagdoll = ragdoll;
                closestHit = hit;
            }
        }
        if (closestRagdoll == null) {
            return false;
        }
        if (activeGrab != null && activeGrab.ragdoll == closestRagdoll) {
            releaseGrab();
        }
        if (REMOTE_BODIES.contains(closestRagdoll.id)) {
            RagdollNetwork.sendControl(RagdollControlRequestPacket.attack(
                    closestRagdoll.id,
                    closestHit.part().name(),
                    closestHit.hitX(),
                    closestHit.hitY(),
                    closestHit.hitZ(),
                    direction.x,
                    direction.y,
                    direction.z));
            return true;
        }
        return closestRagdoll.world.applyAttack(closestHit.part(), closestHit.hitX(), closestHit.hitY(), closestHit.hitZ(), direction.x, direction.y, direction.z);
    }

    /**
     * Returns the id of the closest visible ragdoll under the crosshair.
     */
    public static UUID getLookedAtRagdoll(Minecraft minecraft) {
        return getLookedAtRagdoll(minecraft, false);
    }

    public static UUID getLookedAtBodyRagdoll(Minecraft minecraft) {
        return getLookedAtRagdoll(minecraft, true);
    }

    private static UUID getLookedAtRagdoll(Minecraft minecraft, boolean bodyOnly) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || RAGDOLLS.isEmpty()) {
            return null;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 direction = minecraft.player.getViewVector(1.0f).normalize();
        double maximumDistance = GRAB_REACH;
        HitResult vanillaHit = minecraft.hitResult;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            maximumDistance = Math.min(maximumDistance, eye.distanceTo(vanillaHit.getLocation()) + 0.05);
        }
        RagdollInstance closestRagdoll = null;
        JoltPhysicsWorld.GrabHit closestHit = null;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.simulationPaused
                    || bodyOnly && (CONTROLLED_PLAYERS.containsKey(ragdoll.id)
                    || SLEEP_PLAYER_RAGDOLLS.containsValue(ragdoll.id))
                    || !bodyOnly && isNonInteractiveControlled(ragdoll.id)) {
                continue;
            }
            JoltPhysicsWorld.GrabHit hit = ragdoll.world.findGrabTarget(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z, maximumDistance);
            if (hit != null && (closestHit == null || hit.distance() < closestHit.distance())) {
                closestRagdoll = ragdoll;
                closestHit = hit;
            }
        }
        return closestRagdoll == null
                || bodyOnly && (closestHit == null
                || !"body".equals(closestHit.part().name()))
                ? null : closestRagdoll.id;
    }

    private static ActiveGrab selectGrabTarget(Minecraft minecraft) {
        return selectGrabTarget(minecraft, GRAB_REACH);
    }

    private static ActiveGrab selectGrabTarget(
            Minecraft minecraft, double reach) {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 direction = minecraft.player.getViewVector(1.0f).normalize();
        double maximumDistance = reach;
        HitResult vanillaHit = minecraft.hitResult;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            maximumDistance = Math.min(maximumDistance, eye.distanceTo(vanillaHit.getLocation()) + 0.05);
        }
        RagdollInstance closestRagdoll = null;
        JoltPhysicsWorld.GrabHit closestHit = null;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.simulationPaused || isNonInteractiveControlled(ragdoll.id)) {
                continue;
            }
            JoltPhysicsWorld.GrabHit hit = ragdoll.world.findGrabTarget(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z, maximumDistance);
            if (hit != null && (closestHit == null || hit.distance() < closestHit.distance())) {
                closestRagdoll = ragdoll;
                closestHit = hit;
            }
        }
        return closestRagdoll == null ? null : new ActiveGrab(closestRagdoll, closestHit.part(), closestHit.distance(), closestHit.hitX(), closestHit.hitY(), closestHit.hitZ());
    }

    private static void releaseGrab() {
        if (activeGrab != null) {
            if (REMOTE_BODIES.contains(activeGrab.ragdoll.id)) {
                RagdollNetwork.sendControl(
                        RagdollControlRequestPacket.release(activeGrab.ragdoll.id));
            }
            activeGrab.ragdoll.world.releaseGrab();
            activeGrab = null;
        }
    }

    private static void removeExpiredRagdolls() {
        long lifetime = RagdollConfig.despawnTicks();
        if (lifetime <= 0L) {
            return;
        }
        Iterator<RagdollInstance> iterator = RAGDOLLS.iterator();
        while (iterator.hasNext()) {
            RagdollInstance ragdoll = iterator.next();
            if (isCompanionPlayerRagdoll(ragdoll)
                    || REMOTE_BODIES.contains(ragdoll.id)) {
                continue;
            }
            if (++ragdoll.ageTicks >= lifetime) {
                if (activeGrab != null && activeGrab.ragdoll == ragdoll) {
                    releaseGrab();
                }
                spawnDespawnParticles(Minecraft.getInstance(), ragdoll);
                ragdoll.world.close();
                iterator.remove();
            }
        }
    }

    private static void spawnDespawnParticles(Minecraft minecraft, RagdollInstance ragdoll) {
        if (minecraft.level == null) {
            return;
        }
        AABB bounds = ragdoll.world.getPartRenderBounds(0.0f, 0.0);
        if (bounds == null) {
            return;
        }
        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerY = (bounds.minY + bounds.maxY) * 0.5;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        for (int i = 0; i < 18; ++i) {
            double spreadX = (minecraft.level.getRandom().nextDouble() - 0.5) * (bounds.maxX - bounds.minX);
            double spreadY = minecraft.level.getRandom().nextDouble() * (bounds.maxY - bounds.minY);
            double spreadZ = (minecraft.level.getRandom().nextDouble() - 0.5) * (bounds.maxZ - bounds.minZ);
            minecraft.level.addParticle(ParticleTypes.POOF, centerX + spreadX, bounds.minY + spreadY, centerZ + spreadZ, 0.0, 0.035, 0.0);
        }
    }

    public static void spawnDeathRagdoll(RagdollDeathSnapshot payload) {
        spawnDeathRagdoll(payload, null, RagdollPhysicsSettings.playerDeathDefaults());
    }

    public static void spawnDeathRagdoll(RagdollDeathSnapshot payload, RagdollPhysicsState initialState) {
        spawnDeathRagdoll(payload, initialState, RagdollPhysicsSettings.playerDeathDefaults());
    }

    public static void spawnDeathRagdoll(RagdollDeathSnapshot payload, RagdollPhysicsState initialState, RagdollPhysicsSettings physicsSettings) {
        spawnDeathRagdoll(payload, initialState, physicsSettings, null);
    }

    private static void spawnDeathRagdoll(RagdollDeathSnapshot payload, RagdollPhysicsState initialState,
                                          RagdollPhysicsSettings physicsSettings,
                                          RagdollInitialLimbPose initialLimbPose) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        if (payload == null)
            return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || physicsSettings == null) {
            return;
        }
        if (activeLevel != level) {
            closeAll();
            activeLevel = level;
        }
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.id.equals(payload.ragdollId())) {
                return;
            }
        }
        for (PendingRagdoll pending : PENDING_RAGDOLLS) {
            if (pending.payload.ragdollId().equals(payload.ragdollId())) {
                return;
            }
        }
        SkinResolution skin = resolveSkin(level, payload);
        PendingRagdoll pending = new PendingRagdoll(payload, skin.skin(), skin.future(), initialState,
                physicsSettings, initialLimbPose);
        SpawnAttempt attempt = trySpawn(level, pending);
        if (attempt == SpawnAttempt.SUCCESS) {
            return;
        }
        if (!isCompanionPlayerRagdoll(payload)) {
            trimPendingRagdolls();
        }
        pending.lastAttempt = attempt;
        PENDING_RAGDOLLS.add(pending);
        RagdollMod.LOGGER.info("Queued death ragdoll for {} at {}; reason={}, pending={}", payload.playerId(), pending.origin, attempt.description, PENDING_RAGDOLLS.size());
    }

    private static void pushRagdollsFromPlayer(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || player.isSpectator() || player.isDeadOrDying()
                || isLiveRagdolled(player.getUUID())) {
            return;
        }
        Vec3 movementPerTick = player.position().subtract(
                player.xOld, player.yOld, player.zOld);
        Vec3 velocityPerTick = player.getDeltaMovement();
        if (movementPerTick.lengthSqr() > velocityPerTick.lengthSqr()) {
            velocityPerTick = movementPerTick;
        }
        Vec3 velocity = velocityPerTick.scale(TICKS_TO_SECONDS);
        Vec3 movement = movementPerTick.scale(TICKS_TO_SECONDS);
        AABB currentBounds = player.getBoundingBox();
        AABB playerBounds = currentBounds.minmax(currentBounds.move(
                -movementPerTick.x,
                -movementPerTick.y,
                -movementPerTick.z)).inflate(0.12);
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (isNonInteractiveControlled(ragdoll.id)
                    || REMOTE_BODIES.contains(ragdoll.id))
                continue;
            if (ragdoll.simulationPaused || activeGrab != null && activeGrab.ragdoll == ragdoll) {
                continue;
            }
            ragdoll.world.pushFromPlayer(
                    playerBounds,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    movement.x,
                    movement.z);
        }
    }

    private static SpawnAttempt trySpawn(ClientLevel level, PendingRagdoll pending) {
        SpawnAttempt skinAttempt = refreshPendingSkin(level, pending);
        if (skinAttempt != SpawnAttempt.SUCCESS) {
            return skinAttempt;
        }
        if (!JoltPhysicsWorld.isTerrainRegionLoaded(level, pending.origin)) {
            return SpawnAttempt.TERRAIN_NOT_LOADED;
        }
        RagdollDeathSnapshot payload = pending.payload;
        JoltPhysicsWorld world = JoltPhysicsWorld.createForDeath(level, pending.origin, payload.x(), payload.y(), payload.z(), payload.yaw(), payload.initialPose(), payload.xRot(), payload.velocityX(), payload.velocityY(), payload.velocityZ(), payload.modelScale(), pending.physicsSettings, pending.initialLimbPose);
        if (world == null) {
            return SpawnAttempt.NO_COLLISION_BOXES;
        }
        if (REMOTE_BODIES.contains(payload.ragdollId())) {
            world.setExternallyControlled();
        }
        if (pending.initialState != null && !world.applyState(pending.initialState)) {
            RagdollMod.LOGGER.warn("Ignored incompatible saved physics state for ragdoll {}", payload.ragdollId());
        }
        removeActiveRagdoll(payload.ragdollId());
        if (!isCompanionPlayerRagdoll(payload)) {
            trimActiveRagdolls();
        }
        RAGDOLLS.add(new RagdollInstance(payload.ragdollId(), payload.playerId(), payload.playerName(), payload.skinTextureValue(), payload.skinTextureSignature(), world, payload.modelScale(), pending.texture, pending.slim, pending.elytraTexture, pending.capeTexture, payload.shownModelParts(), pending.helmet.copy(), pending.chestplate.copy(), pending.leggings.copy(), pending.boots.copy(), payload.mainArmLeft(), pending.mainHand.copy(), pending.offHand.copy(), payload.accessories()));
        RagdollMod.LOGGER.info("Spawned client death ragdoll for {}; active corpses={}", payload.playerId(), RAGDOLLS.size());
        return SpawnAttempt.SUCCESS;
    }

    private static void trimPendingRagdolls() {
        int maximum = RagdollConfig.maxRagdolls();
        if (maximum <= 0) {
            return;
        }
        while (countBoundedPendingRagdolls() >= maximum) {
            PendingRagdoll oldest = removeOldestBoundedPendingRagdoll();
            if (oldest == null) {
                return;
            }
            RagdollMod.LOGGER.warn("Discarded oldest queued ragdoll for {} " + "to keep the queue bounded", oldest.payload.playerId());
        }
    }

    private static void trimActiveRagdolls() {
        int maximum = RagdollConfig.maxRagdolls();
        if (maximum <= 0) {
            return;
        }
        while (countBoundedActiveRagdolls() >= maximum) {
            Iterator<RagdollInstance> iterator = RAGDOLLS.iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                RagdollInstance oldest = iterator.next();
                if (isCompanionPlayerRagdoll(oldest)) {
                    continue;
                }
                if (activeGrab != null && activeGrab.ragdoll == oldest) {
                    releaseGrab();
                }
                oldest.world.close();
                iterator.remove();
                removed = true;
                break;
            }
            if (!removed) {
                return;
            }
        }
    }

    private static int countBoundedPendingRagdolls() {
        int count = 0;
        for (PendingRagdoll pending : PENDING_RAGDOLLS) {
            if (!isCompanionPlayerRagdoll(pending.payload)) {
                ++count;
            }
        }
        return count;
    }

    private static PendingRagdoll removeOldestBoundedPendingRagdoll() {
        Iterator<PendingRagdoll> iterator = PENDING_RAGDOLLS.iterator();
        while (iterator.hasNext()) {
            PendingRagdoll pending = iterator.next();
            if (!isCompanionPlayerRagdoll(pending.payload)) {
                iterator.remove();
                return pending;
            }
        }
        return null;
    }

    private static int countBoundedActiveRagdolls() {
        int count = 0;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (!isCompanionPlayerRagdoll(ragdoll)) {
                ++count;
            }
        }
        return count;
    }

    private static boolean isCompanionPlayerRagdoll(RagdollDeathSnapshot payload) {
        return CONTROLLED_PLAYERS.containsKey(payload.ragdollId()) || ModList.get().isLoaded(PLAYER_RAGDOLL_MOD_ID);
    }

    private static boolean isCompanionPlayerRagdoll(RagdollInstance ragdoll) {
        return CONTROLLED_PLAYERS.containsKey(ragdoll.id) || ModList.get().isLoaded(PLAYER_RAGDOLL_MOD_ID);
    }

    private static boolean isNonInteractiveControlled(UUID ragdollId) {
        return CONTROLLED_PLAYERS.containsKey(ragdollId)
                && !INTERACTIVE_CONTROLLED_RAGDOLLS.contains(ragdollId);
    }

    private static void tickPendingRagdolls(ClientLevel level) {
        Iterator<PendingRagdoll> iterator = PENDING_RAGDOLLS.iterator();
        while (iterator.hasNext()) {
            PendingRagdoll pending = iterator.next();
            ++pending.waitedTicks;
            if (pending.waitedTicks >= PENDING_TIMEOUT_TICKS) {
                RagdollMod.LOGGER.warn("Timed out queued death ragdoll for {} at {} after " + "{} ticks; last reason={}", pending.payload.playerId(), pending.origin, pending.waitedTicks, pending.lastAttempt.description);
                iterator.remove();
                continue;
            }
            if (pending.waitedTicks % PENDING_RETRY_INTERVAL_TICKS != 0) {
                continue;
            }
            SpawnAttempt attempt = trySpawn(level, pending);
            pending.lastAttempt = attempt;
            if (attempt == SpawnAttempt.SUCCESS) {
                RagdollMod.LOGGER.info("Spawned queued death ragdoll for {} after {} ticks", pending.payload.playerId(), pending.waitedTicks);
                iterator.remove();
            }
        }
    }

    public static List<RagdollRenderState> getVisibleRagdollRenderStates(float partialTick, Predicate<AABB> visibilityTest) {
        if (!RagdollConfig.enabled()) {
            return List.of();
        }
        if (RAGDOLLS.isEmpty()) {
            return List.of();
        }
        List<RagdollRenderState> result = new ArrayList<>(RAGDOLLS.size());
        for (RagdollInstance ragdoll : RAGDOLLS) {
            AABB bounds = ragdoll.world.getPartRenderBounds(partialTick, RENDER_BOUNDS_PADDING);
            if (bounds == null || !visibilityTest.test(bounds)) {
                continue;
            }
            List<RagdollPartRenderState> parts = ragdoll.world.getPartRenderStates(partialTick);
            if (HIDDEN_HEADS.contains(ragdoll.id)) {
                parts = parts.stream().filter(part -> !part.name().equals("head")).toList();
            }
            PlayerSkin resolvedSkin = ragdoll.resolvedSkin;
            ResourceLocation texture = resolvedSkin == null ? ragdoll.texture : resolvedSkin.texture();
            boolean slim = resolvedSkin == null ? ragdoll.slim : resolvedSkin.model() == PlayerSkin.Model.SLIM;
            ResourceLocation elytraTexture = resolvedSkin != null ? resolvedSkin.elytraTexture() : ragdoll.elytraTexture;
            ResourceLocation capeTexture = resolvedSkin != null ? resolvedSkin.capeTexture() : ragdoll.capeTexture;
            result.add(new RagdollRenderState(ragdoll.id, ragdoll.playerId, ragdoll.modelScale, texture, slim, elytraTexture, capeTexture, ragdoll.shownModelParts, ragdoll.helmet, ragdoll.chestplate, ragdoll.leggings, ragdoll.boots, ragdoll.mainArmLeft, ragdoll.mainHand, ragdoll.offHand, ragdoll.accessories, fadeAlpha(ragdoll.ageTicks, isCompanionPlayerRagdoll(ragdoll) ? 0L : RagdollConfig.despawnTicks()), parts));
        }
        return result;
    }

    private static float fadeAlpha(long ageTicks, long lifetime) {
        if (lifetime <= 0L) {
            return 1.0f;
        }
        long fadeTicks = Math.min(10L, Math.max(1L, lifetime / 6L));
        long remaining = lifetime - ageTicks;
        return Math.clamp(remaining / (float) fadeTicks, 0.0f, 1.0f);
    }

    private static void refreshRagdollSkins(ClientLevel level) {
        for (RagdollInstance ragdoll : RAGDOLLS) {
            PlayerSkin refreshedSkin = resolveSkin(level, ragdoll.playerId, ragdoll.playerName, ragdoll.skinTextureValue, ragdoll.skinTextureSignature).skin();
            if (isDefaultSkin(refreshedSkin, ragdoll.playerId) && hasNonDefaultSkin(ragdoll)) {
                continue;
            }
            ragdoll.resolvedSkin = refreshedSkin;
        }
    }

    private static boolean hasNonDefaultSkin(RagdollInstance ragdoll) {
        ResourceLocation currentTexture = ragdoll.resolvedSkin == null ? ragdoll.texture : ragdoll.resolvedSkin.texture();
        return !isDefaultTexture(currentTexture, ragdoll.playerId);
    }

    private static boolean isDefaultSkin(PlayerSkin skin, UUID playerId) {
        return skin == null || isDefaultTexture(skin.texture(), playerId);
    }

    private static boolean isDefaultTexture(ResourceLocation texture, UUID playerId) {
        return texture == null || DefaultPlayerSkin.get(playerId).texture().equals(texture);
    }

    public static boolean hasRagdollForPlayer(UUID playerId) {
        if (!RagdollConfig.enabled()) {
            return false;
        }
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.playerId.equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    private static SpawnAttempt refreshPendingSkin(ClientLevel level, PendingRagdoll pending) {
        SkinResolution resolution = resolveSkin(level, pending.payload);
        pending.updateSkin(resolution.skin(), resolution.future());
        if (pending.payload.hasSkinTexture() && pending.waitedTicks < SKIN_READY_TIMEOUT_TICKS && resolution.future() != null && !resolution.future().isDone()) {
            return SpawnAttempt.SKIN_NOT_READY;
        }
        return SpawnAttempt.SUCCESS;
    }

    private static SkinResolution resolveSkin(ClientLevel level, RagdollDeathSnapshot payload) {
        return resolveSkin(level, payload.playerId(), payload.playerName(), payload.skinTextureValue(), payload.skinTextureSignature());
    }

    private static SkinResolution resolveSkin(ClientLevel level, UUID playerId, String playerName, String skinTextureValue, String skinTextureSignature) {
        boolean hasSnapshotSkin = hasSkinTexture(skinTextureValue);
        GameProfile profile = skinProfile(playerId, playerName, skinTextureValue, skinTextureSignature);
        CompletableFuture<PlayerSkin> customFuture = playerName == null || playerName.isBlank()
                ? null : com.ragdoll.compat.client.CustomSkinLoaderCompat.resolve(profile);
        if (customFuture != null) {
            PlayerSkin customSkin = currentSkin(customFuture, null);
            if (customSkin != null && !isDefaultSkin(customSkin, playerId)) {
                return new SkinResolution(customSkin, customFuture);
            }
        }
        if (!hasSnapshotSkin) {
            PlayerSkin liveSkin = findLiveSkin(level, playerId);
            if (liveSkin != null) {
                return new SkinResolution(liveSkin, customFuture == null
                        ? CompletableFuture.completedFuture(liveSkin) : customFuture);
            }
        }
        if (!hasSnapshotSkin && (playerName == null || playerName.isBlank())) {
            PlayerSkin fallback = DefaultPlayerSkin.get(playerId);
            return new SkinResolution(fallback, CompletableFuture.completedFuture(fallback));
        }
        PlayerSkin fallback = DefaultPlayerSkin.get(profile);
        CompletableFuture<PlayerSkin> future = Minecraft.getInstance().getSkinManager().getOrLoad(profile);
        return new SkinResolution(currentSkin(future, fallback), future);
    }

    private static PlayerSkin currentSkin(CompletableFuture<PlayerSkin> future, PlayerSkin fallback) {
        try {
            PlayerSkin skin = future.getNow(fallback);
            return skin == null ? fallback : skin;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static GameProfile skinProfile(UUID playerId, String playerName, String skinTextureValue, String skinTextureSignature) {
        String safeName = playerName == null ? "" : playerName;
        if (hasSkinTexture(skinTextureValue)) {
            Property texture = skinTextureSignature == null || skinTextureSignature.isBlank() ? new Property("textures", skinTextureValue) : new Property("textures", skinTextureValue, skinTextureSignature);
            GameProfile profile = new GameProfile(playerId, safeName);
            profile.getProperties().put("textures", texture);
            return profile;
        }
        return new GameProfile(playerId, safeName);
    }

    private static boolean hasSkinTexture(String skinTextureValue) {
        return skinTextureValue != null && !skinTextureValue.isBlank();
    }

    /**
     * Re-resolves dynamic player textures after a world reload. Minecraft
     * releases downloaded skin textures when disconnecting, so a corpse must
     * not permanently keep the texture from the previous level.
     */
    private static PlayerSkin findLiveSkin(ClientLevel level, UUID playerId) {
        if (level == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(playerId);
        if (player instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkin();
        }
        return null;
    }

    public static void removeRagdoll(UUID ragdollId) {
        removeActiveRagdoll(ragdollId);
        removePendingRagdoll(ragdollId);
        CONTROLLED_PLAYERS.remove(ragdollId);
        INTERACTIVE_CONTROLLED_RAGDOLLS.remove(ragdollId);
        REMOTE_BODIES.remove(ragdollId);
        REMOTE_STATES.remove(ragdollId);
        HIDDEN_HEADS.remove(ragdollId);
        LIVE_PLAYER_RAGDOLLS.values().removeIf(ragdollId::equals);
        SLEEP_PLAYER_RAGDOLLS.values().removeIf(ragdollId::equals);
    }

    public static void removeRagdollWithParticles(UUID ragdollId) {
        removeActiveRagdoll(ragdollId, true);
        removePendingRagdoll(ragdollId);
        REMOTE_BODIES.remove(ragdollId);
        REMOTE_STATES.remove(ragdollId);
    }

    public static Map<UUID, RagdollPhysicsState> capturePlayerRagdollStates() {
        Map<UUID, RagdollPhysicsState> result = new LinkedHashMap<>();
        for (RagdollInstance ragdoll : RAGDOLLS) {
            RagdollPhysicsState state = ragdoll.unloadedState != null ? ragdoll.unloadedState : ragdoll.world.captureState();
            result.put(ragdoll.id, state);
        }
        return Map.copyOf(result);
    }

    public static void updatePlayerRagdollEquipment(UUID ragdollId, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, ItemStack mainHand, ItemStack offHand) {
        for (RagdollInstance ragdoll : RAGDOLLS) {
            if (ragdoll.id.equals(ragdollId)) {
                ragdoll.updateEquipment(helmet, chestplate, leggings, boots, mainHand, offHand);
                return;
            }
        }
        for (PendingRagdoll pending : PENDING_RAGDOLLS) {
            if (pending.payload.ragdollId().equals(ragdollId)) {
                pending.updateEquipment(helmet, chestplate, leggings, boots, mainHand, offHand);
                return;
            }
        }
    }

    public static void markTerrainDirty(ClientLevel level, BlockPos blockPos) {
        if (level == null || level != activeLevel || blockPos == null) {
            return;
        }
        for (RagdollInstance ragdoll : RAGDOLLS) {
            ragdoll.world.markTerrainDirty(blockPos);
        }
    }

    private static void removeActiveRagdoll(UUID ragdollId) {
        removeActiveRagdoll(ragdollId, false);
    }

    private static void removeActiveRagdoll(UUID ragdollId, boolean spawnParticles) {
        Iterator<RagdollInstance> iterator = RAGDOLLS.iterator();
        while (iterator.hasNext()) {
            RagdollInstance ragdoll = iterator.next();
            if (ragdoll.id.equals(ragdollId)) {
                if (activeGrab != null && activeGrab.ragdoll == ragdoll) {
                    releaseGrab();
                }
                if (spawnParticles) {
                    spawnDespawnParticles(Minecraft.getInstance(), ragdoll);
                }
                ragdoll.world.close();
                iterator.remove();
            }
        }
    }

    private static void removePendingRagdoll(UUID ragdollId) {
        PENDING_RAGDOLLS.removeIf(pending -> pending.payload.ragdollId().equals(ragdollId));
    }

    private static void closeAll() {
        com.ragdoll.compat.client.CustomSkinLoaderCompat.clear();
        activeGrab = null;
        for (RagdollInstance ragdoll : RAGDOLLS) {
            ragdoll.world.close();
        }
        RAGDOLLS.clear();
        PENDING_RAGDOLLS.clear();
        CONTROLLED_PLAYERS.clear();
        INTERACTIVE_CONTROLLED_RAGDOLLS.clear();
        LIVE_PLAYER_RAGDOLLS.clear();
        SLEEP_PLAYER_RAGDOLLS.clear();
        previousLiveTorso = null;
        REMOTE_BODIES.clear();
        REMOTE_STATES.clear();
        HIDDEN_HEADS.clear();
        skinRefreshCooldownTicks = 0;
        activeLevel = null;
    }

    private static final class ActiveGrab {
        private final RagdollInstance ragdoll;
        private final JoltPhysicsWorld.RagdollPart part;
        private double distance;
        private final double grabX;
        private final double grabY;
        private final double grabZ;

        private ActiveGrab(
                RagdollInstance ragdoll,
                JoltPhysicsWorld.RagdollPart part,
                double distance,
                double grabX,
                double grabY,
                double grabZ) {
            this.ragdoll = ragdoll;
            this.part = part;
            this.distance = distance;
            this.grabX = grabX;
            this.grabY = grabY;
            this.grabZ = grabZ;
        }
    }

    private record SkinResolution(PlayerSkin skin, CompletableFuture<PlayerSkin> future) {
    }

    private enum SpawnAttempt {

        SUCCESS("success"), SKIN_NOT_READY("skin textures are still loading"), TERRAIN_NOT_LOADED("terrain chunks are not loaded"), NO_COLLISION_BOXES("no collision boxes are available yet");

        private final String description;

        SpawnAttempt(String description) {
            this.description = description;
        }
    }

    private static final class PendingRagdoll {

        private RagdollDeathSnapshot payload;

        private final BlockPos origin;

        private final RagdollPhysicsState initialState;

        private final RagdollPhysicsSettings physicsSettings;

        private final RagdollInitialLimbPose initialLimbPose;

        private ItemStack helmet;

        private ItemStack chestplate;

        private ItemStack leggings;

        private ItemStack boots;

        private ItemStack mainHand;

        private ItemStack offHand;

        private ResourceLocation texture;

        private boolean slim;

        private ResourceLocation elytraTexture;

        private ResourceLocation capeTexture;

        private CompletableFuture<PlayerSkin> skinFuture;

        private int waitedTicks;

        private SpawnAttempt lastAttempt;

        private PendingRagdoll(RagdollDeathSnapshot payload, PlayerSkin skin, CompletableFuture<PlayerSkin> skinFuture, RagdollPhysicsState initialState, RagdollPhysicsSettings physicsSettings, RagdollInitialLimbPose initialLimbPose) {
            this.payload = payload;
            this.initialState = initialState;
            this.physicsSettings = physicsSettings;
            this.initialLimbPose = initialLimbPose;
            updateEquipment(payload.helmet(), payload.chestplate(), payload.leggings(), payload.boots(), payload.mainHand(), payload.offHand());
            this.origin = BlockPos.containing(payload.x(), payload.y(), payload.z());
            updateSkin(skin, skinFuture);
        }

        private void updateEquipment(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, ItemStack mainHand, ItemStack offHand) {
            this.helmet = helmet.copy();
            this.chestplate = chestplate.copy();
            this.leggings = leggings.copy();
            this.boots = boots.copy();
            this.mainHand = mainHand.copy();
            this.offHand = offHand.copy();
        }

        private void updateAppearance(RagdollDeathSnapshot snapshot) {
            payload = snapshot;
            updateEquipment(snapshot.helmet(), snapshot.chestplate(), snapshot.leggings(),
                    snapshot.boots(), snapshot.mainHand(), snapshot.offHand());
        }

        private void updateSkin(PlayerSkin skin, CompletableFuture<PlayerSkin> skinFuture) {
            this.texture = skin.texture();
            this.slim = skin.model() == PlayerSkin.Model.SLIM;
            this.elytraTexture = skin.elytraTexture();
            this.capeTexture = skin.capeTexture();
            this.skinFuture = skinFuture;
        }
    }

    private static final class RagdollInstance {

        private final UUID id;

        private final UUID playerId;

        private final String playerName;

        private final String skinTextureValue;

        private final String skinTextureSignature;

        private final JoltPhysicsWorld world;

        private final float modelScale;

        private final ResourceLocation texture;

        private final boolean slim;

        private final ResourceLocation elytraTexture;

        private final ResourceLocation capeTexture;

        private final int shownModelParts;

        private ItemStack helmet;

        private ItemStack chestplate;

        private ItemStack leggings;

        private ItemStack boots;

        private final boolean mainArmLeft;

        private ItemStack mainHand;

        private ItemStack offHand;

        private RagdollAccessorySnapshot accessories;

        private boolean simulationPaused;

        private RagdollPhysicsState unloadedState;

        private PlayerSkin resolvedSkin;

        private long ageTicks;

        private RagdollInstance(UUID id, UUID playerId, String playerName, String skinTextureValue, String skinTextureSignature, JoltPhysicsWorld world, float modelScale, ResourceLocation texture, boolean slim, ResourceLocation elytraTexture, ResourceLocation capeTexture, int shownModelParts, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, boolean mainArmLeft, ItemStack mainHand, ItemStack offHand, RagdollAccessorySnapshot accessories) {
            this.id = id;
            this.playerId = playerId;
            this.playerName = playerName;
            this.skinTextureValue = skinTextureValue;
            this.skinTextureSignature = skinTextureSignature;
            this.world = world;
            this.modelScale = modelScale;
            this.texture = texture;
            this.slim = slim;
            this.elytraTexture = elytraTexture;
            this.capeTexture = capeTexture;
            this.shownModelParts = shownModelParts;
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.mainArmLeft = mainArmLeft;
            this.mainHand = mainHand;
            this.offHand = offHand;
            this.accessories = accessories == null
                    ? RagdollAccessorySnapshot.EMPTY
                    : accessories;
        }

        private void updateEquipment(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, ItemStack mainHand, ItemStack offHand) {
            this.helmet = helmet.copy();
            this.chestplate = chestplate.copy();
            this.leggings = leggings.copy();
            this.boots = boots.copy();
            this.mainHand = mainHand.copy();
            this.offHand = offHand.copy();
        }

        private void updateAppearance(RagdollDeathSnapshot snapshot) {
            updateEquipment(snapshot.helmet(), snapshot.chestplate(), snapshot.leggings(),
                    snapshot.boots(), snapshot.mainHand(), snapshot.offHand());
            accessories = snapshot.accessories();
        }
    }

    public record RagdollRenderState(UUID owner, UUID playerId, float modelScale, ResourceLocation texture, boolean slim, ResourceLocation elytraTexture, ResourceLocation capeTexture, int shownModelParts, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, boolean mainArmLeft, ItemStack mainHand, ItemStack offHand, RagdollAccessorySnapshot accessories, float fadeAlpha, List<RagdollPartRenderState> parts) {
    }

}


