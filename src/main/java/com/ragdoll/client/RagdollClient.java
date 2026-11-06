package com.ragdoll.client;

import com.ragdoll.RagdollMod;
import com.ragdoll.RagdollConfig;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import com.ragdoll.client.physics.JoltBootstrap;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
import com.ragdoll.client.physics.RagdollPoseCapture;
import com.ragdoll.client.physics.RagdollPartRenderState;
import com.ragdoll.mixin.client.CameraRagdollAccessor;
import com.ragdoll.model.RagdollDeathSnapshot;
import com.ragdoll.network.RagdollNetwork;
import com.ragdoll.server.RagdollMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.AbstractClientPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.lwjgl.glfw.GLFW;

@Mod(value = RagdollMod.MOD_ID, dist = Dist.CLIENT)
public final class RagdollClient {
    private static boolean installed;
    private static float grabZoom = 1.0f;
    private static boolean grabMouseCaptured;
    private static boolean grabDragStarted;
    private static long grabMousePressedAtNanos;
    private static java.util.UUID pendingBodyClick;
    private static final String RAGDOLL_CATEGORY = "key.categories.ragdoll";
    private static final KeyMapping GRAB_KEY = new KeyMapping(
            "key.ragdoll.grab",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            RAGDOLL_CATEGORY);
    private static final KeyMapping LIVE_RAGDOLL_KEY = new KeyMapping(
            "key.ragdoll.liveToggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            RAGDOLL_CATEGORY);
    private static ModContainer configContainer;
    private static CameraType previousLiveRagdollCameraType;
    private static CameraType previousSleepRagdollCameraType;
    private static CameraType previousGrabCameraType;
    private static CameraType previousDeathCameraType;
    private static boolean shiftWasDown;
    private static boolean sleepWakeShiftWasDown;
    private static boolean sleepCameraLookInitialized;
    private static float sleepCameraYaw;
    private static float sleepCameraPitch;

    public RagdollClient(ModContainer modContainer) {
        install(modContainer);
    }

    private RagdollClient() {
    }

    public static void install(ModContainer modContainer) {
        if (installed) {
            return;
        }
        installed = true;
        configContainer = modContainer;
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new ConfigurationScreen(container, parent, RagdollConfigurationScreen::new));
        modContainer.getEventBus()
                .addListener(RagdollClient::onClientSetup);
        modContainer.getEventBus()
                .addListener(RagdollClient::onRegisterKeyMappings);
        modContainer.getEventBus()
                .addListener(RagdollClient::onRegisterMenuScreens);
        NeoForge.EVENT_BUS.addListener(RagdollClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(RagdollClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(RagdollClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(RagdollClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(RagdollClient::onComputeFovModifier);
        NeoForge.EVENT_BUS.addListener(RagdollClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(RagdollRenderer::onRenderLevel);
        RagdollNetwork.clientSpawn = packet ->
                ClientPhysicsRuntime.updateServerRagdollAppearance(
                        packet.toSnapshot(), packet.initialLimbPose(), packet.liveRagdoll());
        RagdollNetwork.clientDeathCamera = packet -> DeathRagdollCamera.target(packet.ragdollId());
        RagdollNetwork.clientState = packet ->
                ClientPhysicsRuntime.applySynchronizedState(
                        packet.ragdollId(),
                        packet.serverTimestampNanos(),
                        packet.sequence(),
                        packet.state());
        RagdollNetwork.clientRemove = packet ->
                ClientPhysicsRuntime.removeRagdollWithParticles(packet.ragdollId());
        RagdollNetwork.clientPhysicsGunSync =
                RagdollPhysicsGunClient::updateState;
        RagdollNetwork.clientLiveRagdollState = packet ->
                ClientPhysicsRuntime.setLiveRagdoll(
                        packet.playerId(), packet.ragdollId(), packet.active());
        RagdollNetwork.clientSleepRagdollState = packet ->
                ClientPhysicsRuntime.setSleepRagdoll(
                        packet.playerId(), packet.ragdollId(), packet.active());
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            JoltBootstrap.initialize();
        });
    }

    private static void onRegisterKeyMappings(
            RegisterKeyMappingsEvent event) {
        event.register(GRAB_KEY);
        event.register(LIVE_RAGDOLL_KEY);
    }

    private static void onRegisterMenuScreens(
            net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(RagdollMenus.CORPSE_LOOT.get(), CorpseLootScreen::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        DeathRagdollCamera.tick(minecraft);
        updateDeathPose(minecraft);
        SleepRagdollTracker.tick(minecraft);
        updateDeathRagdollCameraType(minecraft);
        updateLiveRagdollCameraType(minecraft);
        updateSleepRagdollCameraType(minecraft);
        updateSleepWakeInput(minecraft);
        if (minecraft.player != null && minecraft.screen == null
                && RagdollConfig.enabled() && RagdollConfig.liveRagdollEnabled()
                && LIVE_RAGDOLL_KEY.consumeClick()) {
            requestLiveRagdollToggle(minecraft);
        }
        updateGrabMouseHold();
        ClientPhysicsRuntime.updateGrabInput(minecraft,
                GRAB_KEY.isDown() && !RagdollPhysicsGunClient.isArmed(minecraft)
                        && !isLocalSleepInteractionLocked());
        RagdollPhysicsGunClient.tick(minecraft);
        updateGrabRagdollCameraType(minecraft);
        ClientPhysicsRuntime.tick(minecraft);
        ClientPhysicsRuntime.followLocalLiveRagdoll(minecraft);
    }

    public static boolean handleGrabMouseButton(int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (isLocalSleepInteractionLocked()) return false;
        if (action == GLFW.GLFW_PRESS && !grabMouseCaptured
                && minecraft.screen == null && minecraft.player != null
                && !RagdollPhysicsGunClient.isArmed(minecraft)
                && RagdollConfig.enabled()) {
            java.util.UUID body = ClientPhysicsRuntime.getLookedAtBodyRagdoll(minecraft);
            boolean hasGrabTarget = body != null
                    || ClientPhysicsRuntime.hasGrabTarget(minecraft);
            if (!hasGrabTarget) return false;
            grabMouseCaptured = true;
            pendingBodyClick = body;
            grabDragStarted = body == null;
            grabMousePressedAtNanos = System.nanoTime();
            GRAB_KEY.setDown(grabDragStarted);
            return true;
        }
        if (action == GLFW.GLFW_RELEASE && grabMouseCaptured) {
            if (pendingBodyClick != null && !grabDragStarted
                    && minecraft.player != null && minecraft.screen == null) {
                RagdollNetwork.openLoot(pendingBodyClick,
                        minecraft.player.isShiftKeyDown());
            }
            grabMouseCaptured = false;
            grabDragStarted = false;
            pendingBodyClick = null;
            GRAB_KEY.setDown(false);
            return true;
        }
        return false;
    }

    private static void updateGrabMouseHold() {
        if (isLocalSleepInteractionLocked()) {
            grabMouseCaptured = false;
            grabDragStarted = false;
            pendingBodyClick = null;
            GRAB_KEY.setDown(false);
            return;
        }
        if (!grabMouseCaptured || pendingBodyClick == null || grabDragStarted) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !RagdollConfig.enabled()) {
            grabMouseCaptured = false;
            pendingBodyClick = null;
            GRAB_KEY.setDown(false);
            return;
        }
        if (System.nanoTime() - grabMousePressedAtNanos >= 180_000_000L) {
            grabDragStarted = true;
            GRAB_KEY.setDown(true);
        }
    }

    private static void updateDeathPose(Minecraft minecraft) {
        var player = minecraft.player;
        if (!RagdollConfig.enabled() || player == null || !player.isAlive()
                || player.isSleeping() || minecraft.isPaused()
                || ClientPhysicsRuntime.isLiveRagdolled(player.getUUID())) return;
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
            var pose = RagdollPoseCapture.captureCurrentPose(player, playerRenderer);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.ragdoll.network.DeathPosePacket(pose));
        }
    }

    private static void updateLiveRagdollCameraType(Minecraft minecraft) {
        boolean active = minecraft.player != null
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID());
        if (active && RagdollConfig.liveRagdollCameraEnabled()) {
            if (previousLiveRagdollCameraType == null) {
                previousLiveRagdollCameraType = minecraft.options.getCameraType();
                CameraType requested = RagdollConfig.liveRagdollCameraMode()
                        == RagdollConfig.LiveCameraMode.FIRST_PERSON
                        ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
                if (minecraft.options.getCameraType() != requested) {
                    minecraft.options.setCameraType(requested);
                }
            } else if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            } else if (!isVanillaRagdollPerspective(minecraft.options.getCameraType())) {
                minecraft.options.setCameraType(liveRagdollCameraType());
            }
        } else {
            restoreLiveRagdollCameraType(minecraft);
        }
    }

    public static boolean isLiveRagdollCameraRestrictionActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && RagdollConfig.liveRagdollCameraEnabled()
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID());
    }

    public static boolean isRagdollPerspectiveRestricted() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!RagdollConfig.enabled() || minecraft.player == null) return false;
        return ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())
                || SleepRagdollTracker.isLocalSleepRagdollActive(minecraft)
                || DeathRagdollCamera.active()
                || ClientPhysicsRuntime.isGrabActive()
                || RagdollPhysicsGunClient.localGrabbedRagdollId(minecraft) != null;
    }

    public static CameraType restrictRagdollCameraType(CameraType requested) {
        if (!isRagdollPerspectiveRestricted()) return requested;
        if (requested == CameraType.THIRD_PERSON_FRONT) return CameraType.FIRST_PERSON;
        if (isVanillaRagdollPerspective(requested)) return requested;
        if (DeathRagdollCamera.active()) return CameraType.THIRD_PERSON_BACK;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())) {
            return liveRagdollCameraType();
        }
        if (isSleepRagdollCameraActive()) {
            return sleepRagdollCameraType();
        }
        CameraType saved = previousGrabCameraType;
        return saved == CameraType.FIRST_PERSON || saved == CameraType.THIRD_PERSON_BACK
                ? saved : CameraType.THIRD_PERSON_BACK;
    }

    public static void enforceRagdollCameraType() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || !isRagdollPerspectiveRestricted()) return;
        CameraType current = minecraft.options.getCameraType();
        CameraType allowed = restrictRagdollCameraType(current);
        if (current != allowed) minecraft.options.setCameraType(allowed);
    }

    public static void applyFinalRagdollCamera(
            net.minecraft.client.Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null
                || !isRagdollPerspectiveRestricted()) return;

        boolean deathCamera = DeathRagdollCamera.active();
        java.util.UUID ragdollId = null;
        boolean sleepCamera = false;
        boolean firstPerson;
        if (deathCamera) {
            firstPerson = false;
        } else if (ClientPhysicsRuntime.isLiveRagdolled(player.getUUID())) {
            ragdollId = ClientPhysicsRuntime.liveRagdollId(player.getUUID());
            firstPerson = RagdollConfig.liveRagdollCameraEnabled()
                    && RagdollConfig.liveRagdollCameraMode()
                    == RagdollConfig.LiveCameraMode.FIRST_PERSON;
        } else if (isSleepRagdollCameraActive()) {
            ragdollId = localSleepRagdollId();
            sleepCamera = ragdollId != null;
            firstPerson = RagdollConfig.sleepRagdollCameraMode()
                    == RagdollConfig.SleepCameraMode.FIRST_PERSON;
        } else {
            firstPerson = previousGrabCameraType == CameraType.FIRST_PERSON;
        }

        CameraRagdollAccessor accessor = (CameraRagdollAccessor) (Object) camera;
        accessor.ragdoll$setDetached(!firstPerson);

        if (deathCamera) {
            net.minecraft.world.phys.Vec3 anchor = DeathRagdollCamera.anchor(partialTick);
            accessor.ragdoll$setRotation(DeathRagdollCamera.yaw(), 20.0f, 0.0f);
            net.minecraft.world.phys.Vec3 position = ragdollThirdPersonPosition(
                    minecraft, player, camera, anchor, 4.0);
            accessor.ragdoll$setPosition(position.x, position.y, position.z);
            return;
        }

        if (ragdollId != null) {
            ClientPhysicsRuntime.setControlledHeadHidden(ragdollId, firstPerson);
            RagdollPartRenderState torso = ClientPhysicsRuntime.getPart(
                    ragdollId, "body", partialTick);
            if (torso != null) {
                net.minecraft.world.phys.Vec3 anchor =
                        new net.minecraft.world.phys.Vec3(torso.x(), torso.y(), torso.z());
                if (sleepCamera) {
                    accessor.ragdoll$setRotation(sleepCameraYaw, sleepCameraPitch, 0.0f);
                } else {
                    accessor.ragdoll$setRotation(
                            player.getViewYRot(partialTick), player.getXRot(), 0.0f);
                }

                if (!firstPerson) {
                    net.minecraft.world.phys.Vec3 position = ragdollThirdPersonPosition(
                            minecraft, player, camera, anchor, 4.0);
                    accessor.ragdoll$setPosition(position.x, position.y, position.z);
                } else {
                    RagdollPartRenderState head = ClientPhysicsRuntime.getPart(
                            ragdollId, "head", partialTick);
                    if (head != null) {
                        org.joml.Quaternionf rotation = new org.joml.Quaternionf(
                                head.rotationX(), head.rotationY(),
                                head.rotationZ(), head.rotationW()).normalize();
                        org.joml.Vector3f offset = rotation.transform(
                                new org.joml.Vector3f(0.0f, 0.05f, -0.325f));
                        net.minecraft.world.phys.Vec3 headPos =
                                new net.minecraft.world.phys.Vec3(head.x(), head.y(), head.z());
                        net.minecraft.world.phys.Vec3 eye = clipRagdollCamera(
                                minecraft, player, headPos,
                                headPos.add(offset.x, offset.y, offset.z));
                        accessor.ragdoll$setPosition(eye.x, eye.y, eye.z);
                        if (!sleepCamera) {
                            org.joml.Vector3f euler = rotation.getEulerAnglesYXZ(
                                    new org.joml.Vector3f());
                            accessor.ragdoll$setRotation(
                                    180.0f - (float) Math.toDegrees(euler.y),
                                    -(float) Math.toDegrees(euler.x),
                                    -(float) Math.toDegrees(euler.z));
                        }
                    } else if (sleepCamera) {
                        accessor.ragdoll$setPosition(anchor.x, anchor.y, anchor.z);
                    }
                }
                return;
            }
        }

        // Grab mode keeps the player's selected vanilla perspective, but does
        // not inherit a third-party camera's offset or rotation.
        accessor.ragdoll$setRotation(
                player.getViewYRot(partialTick), player.getXRot(), 0.0f);
        if (firstPerson) {
            net.minecraft.world.phys.Vec3 eye = player.getEyePosition(partialTick);
            accessor.ragdoll$setPosition(eye.x, eye.y, eye.z);
        } else {
            net.minecraft.world.phys.Vec3 eye = player.getEyePosition(partialTick);
            net.minecraft.world.phys.Vec3 position = ragdollThirdPersonPosition(
                    minecraft, player, camera, eye, 4.0);
            accessor.ragdoll$setPosition(position.x, position.y, position.z);
        }
    }

    private static net.minecraft.world.phys.Vec3 ragdollThirdPersonPosition(
            Minecraft minecraft, net.minecraft.world.entity.Entity entity,
            net.minecraft.client.Camera camera,
            net.minecraft.world.phys.Vec3 anchor, double distance) {
        org.joml.Vector3f look = camera.getLookVector();
        net.minecraft.world.phys.Vec3 desired = anchor.subtract(
                new net.minecraft.world.phys.Vec3(look.x(), look.y(), look.z()).scale(distance));
        return clipRagdollCamera(minecraft, entity, anchor, desired);
    }

    private static net.minecraft.world.phys.Vec3 clipRagdollCamera(
            Minecraft minecraft, net.minecraft.world.entity.Entity entity,
            net.minecraft.world.phys.Vec3 anchor,
            net.minecraft.world.phys.Vec3 desired) {
        var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(
                anchor, desired,
                net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return desired;
        net.minecraft.world.phys.Vec3 direction = desired.subtract(anchor).normalize();
        return hit.getLocation().subtract(direction.scale(0.05));
    }

    private static boolean isVanillaRagdollPerspective(CameraType type) {
        return type == CameraType.FIRST_PERSON
                || type == CameraType.THIRD_PERSON_BACK;
    }

    private static CameraType liveRagdollCameraType() {
        return RagdollConfig.liveRagdollCameraMode()
                == RagdollConfig.LiveCameraMode.FIRST_PERSON
                ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
    }

    private static CameraType sleepRagdollCameraType() {
        return RagdollConfig.sleepRagdollCameraMode()
                == RagdollConfig.SleepCameraMode.FIRST_PERSON
                ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
    }

    private static void updateDeathRagdollCameraType(Minecraft minecraft) {
        if (DeathRagdollCamera.active() && minecraft.options != null) {
            if (previousDeathCameraType == null) {
                previousDeathCameraType = minecraft.options.getCameraType();
            }
            if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (previousDeathCameraType != null && minecraft.options != null) {
            minecraft.options.setCameraType(previousDeathCameraType);
            previousDeathCameraType = null;
        }
    }

    private static void updateGrabRagdollCameraType(Minecraft minecraft) {
        boolean active = ClientPhysicsRuntime.isGrabActive()
                || RagdollPhysicsGunClient.localGrabbedRagdollId(minecraft) != null;
        if (active && minecraft.options != null) {
            if (previousGrabCameraType == null) {
                previousGrabCameraType = minecraft.options.getCameraType();
            }
            CameraType requested = previousGrabCameraType == CameraType.FIRST_PERSON
                    ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
            if (minecraft.options.getCameraType() != requested) {
                minecraft.options.setCameraType(requested);
            }
        } else if (previousGrabCameraType != null && minecraft.options != null) {
            minecraft.options.setCameraType(previousGrabCameraType);
            previousGrabCameraType = null;
        }
    }

    public static boolean isSleepRagdollCameraActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return RagdollConfig.enabled() && RagdollConfig.sleepRagdollReplaceScreen()
                && SleepRagdollTracker.isLocalSleepRagdollActive(minecraft);
    }

    public static boolean isLocalSleepInteractionLocked() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isSleeping();
    }

    public static boolean shouldReplaceSleepScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isSleeping()
                && RagdollConfig.enabled() && RagdollConfig.sleepRagdollReplaceScreen()
                && SleepRagdollTracker.isLocalSleepRagdollActive(minecraft);
    }

    public static java.util.UUID localSleepRagdollId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null
                : SleepRagdollTracker.ragdollId(minecraft.player.getUUID());
    }

    private static void updateSleepRagdollCameraType(Minecraft minecraft) {
        if (isSleepRagdollCameraActive()) {
            if (previousSleepRagdollCameraType == null) {
                previousSleepRagdollCameraType = minecraft.options.getCameraType();
                sleepCameraYaw = minecraft.player.getYRot();
                sleepCameraPitch = minecraft.player.getXRot();
                sleepCameraLookInitialized = true;
                CameraType requested = sleepRagdollCameraType();
                minecraft.options.setCameraType(requested);
            } else if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            } else if (!isVanillaRagdollPerspective(minecraft.options.getCameraType())) {
                minecraft.options.setCameraType(sleepRagdollCameraType());
            }
        } else if (previousSleepRagdollCameraType != null) {
            minecraft.options.setCameraType(previousSleepRagdollCameraType);
            previousSleepRagdollCameraType = null;
            sleepCameraLookInitialized = false;
        }
    }

    public static boolean captureSleepCameraTurn(
            net.minecraft.world.entity.Entity entity,
            double yawDelta, double pitchDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity != minecraft.player || !isSleepRagdollCameraActive()) return false;
        if (!sleepCameraLookInitialized) {
            sleepCameraYaw = entity.getYRot();
            sleepCameraPitch = entity.getXRot();
            sleepCameraLookInitialized = true;
        }
        sleepCameraYaw += (float) yawDelta * 0.15f;
        sleepCameraPitch = net.minecraft.util.Mth.clamp(
                sleepCameraPitch + (float) pitchDelta * 0.15f, -90.0f, 90.0f);
        return true;
    }

    public static float sleepCameraYaw() {
        return sleepCameraYaw;
    }

    public static float sleepCameraPitch() {
        return sleepCameraPitch;
    }

    private static void requestLiveRagdollToggle(Minecraft minecraft) {
        if (minecraft.player == null
                || ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())) {
            RagdollNetwork.toggleLiveRagdoll();
            return;
        }
        var player = minecraft.player;
        var movement = player.position().subtract(player.xOld, player.yOld, player.zOld);
        var velocity = player.getDeltaMovement();
        double movementHorizontalSqr = movement.x * movement.x + movement.z * movement.z;
        double velocityHorizontalSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        var horizontal = movementHorizontalSqr > velocityHorizontalSqr ? movement : velocity;
        double vertical = Math.abs(movement.y) > Math.abs(velocity.y) ? movement.y : velocity.y;
        double horizontalScale = player.onGround() ? 35.0 : 36.0;
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        RagdollInitialLimbPose limbPose = renderer instanceof
                net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer
                ? RagdollPoseCapture.captureCurrentPose(
                        player, playerRenderer) : null;
        RagdollNetwork.toggleLiveRagdoll(new com.ragdoll.network.LiveRagdollRequestPacket(
                limbPose, RagdollPlayerDeaths.initialPose(player),
                player.getX(), player.getY(), player.getZ(), player.yBodyRot,
                player.getXRot(),
                (float) (horizontal.x * horizontalScale), (float) (vertical * 9.0),
                (float) (horizontal.z * horizontalScale), player.onGround()));
    }

    private static void updateSleepWakeInput(Minecraft minecraft) {
        boolean shiftDown = InputConstants.isKeyDown(
                minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
        if (isSleepRagdollCameraActive() && shiftDown && !sleepWakeShiftWasDown
                && minecraft.player != null) {
            minecraft.player.connection.send(new ServerboundPlayerCommandPacket(
                    minecraft.player,
                    ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
        }
        sleepWakeShiftWasDown = shiftDown;
    }

    private static void restoreLiveRagdollCameraType(Minecraft minecraft) {
        if (previousLiveRagdollCameraType != null) {
            minecraft.options.setCameraType(previousLiveRagdollCameraType);
            previousLiveRagdollCameraType = null;
        }
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DeathRagdollCamera.reset();
        if (previousDeathCameraType != null) {
            Minecraft.getInstance().options.setCameraType(previousDeathCameraType);
            previousDeathCameraType = null;
        }
        restoreLiveRagdollCameraType(Minecraft.getInstance());
        if (previousSleepRagdollCameraType != null) {
            Minecraft.getInstance().options.setCameraType(previousSleepRagdollCameraType);
            previousSleepRagdollCameraType = null;
        }
        if (previousGrabCameraType != null) {
            Minecraft.getInstance().options.setCameraType(previousGrabCameraType);
            previousGrabCameraType = null;
        }
        sleepCameraLookInitialized = false;
        shiftWasDown = false;
        sleepWakeShiftWasDown = false;
    }

    private static void onInteractionKey(
            InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (grabMouseCaptured) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (minecraft.player != null
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (isLocalSleepInteractionLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (event.isUseItem() && minecraft.player != null && !minecraft.player.isSleeping()
                && !ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())) {
            SleepRagdollTracker.captureBedInteraction(minecraft, minecraft.player);
        }
        if (event.isUseItem()) {
            java.util.UUID ragdollId =
                    ClientPhysicsRuntime.getLookedAtBodyRagdoll(minecraft);
            if (ragdollId != null) {
                RagdollNetwork.openLoot(ragdollId,
                        minecraft.player != null && minecraft.player.isShiftKeyDown());
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
        }
        if (event.isAttack()
                && ClientPhysicsRuntime.attackLookedAtRagdoll(minecraft)) {
            event.setSwingHand(true);
            event.setCanceled(true);
        }
    }

    private static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player) return;
        var input = event.getInput();
        boolean shiftDown = input.shiftKeyDown;
        boolean liveRagdolled = ClientPhysicsRuntime.isLiveRagdolled(
                event.getEntity().getUUID());
        if (liveRagdolled && shiftDown && !shiftWasDown) {
            requestLiveRagdollToggle(minecraft);
        }
        shiftWasDown = shiftDown;
        if (!liveRagdolled && ClientPhysicsRuntime.isGrabActive()) {
            event.getEntity().setSprinting(false);
            if (!shiftDown) {
                input.forwardImpulse *= 0.3f;
                input.leftImpulse *= 0.3f;
            }
            return;
        }
        if (!liveRagdolled) return;
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    private static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && (DeathRagdollCamera.active()
                || ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID()))) {
            event.setCanceled(true);
        }
    }

    private static void onComputeFovModifier(
            net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean grabbing = event.getPlayer() == minecraft.player
                && ClientPhysicsRuntime.isGrabActive();
        float targetZoom = grabbing ? 0.85f : 1.0f;
        grabZoom += (targetZoom - grabZoom) * 0.18f;
        if (Math.abs(targetZoom - grabZoom) < 0.001f) grabZoom = targetZoom;
        event.setNewFovModifier(event.getNewFovModifier() * grabZoom);
    }

    public static boolean shouldHideOriginalDeath(LivingEntity entity) {
        return RagdollConfig.enabled() && entity != null
                && entity instanceof AbstractClientPlayer
                && (ClientPhysicsRuntime.isControlledPlayer(entity.getUUID())
                || entity.isDeadOrDying());
    }

}
