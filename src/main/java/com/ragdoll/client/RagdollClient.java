package com.ragdoll.client;

import com.ragdoll.RagdollMod;
import com.ragdoll.RagdollConfig;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import com.ragdoll.client.physics.JoltBootstrap;
import com.ragdoll.client.physics.RagdollInitialLimbPose;
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
    private static final String RAGDOLL_CATEGORY = "key.categories.ragdoll";
    private static final KeyMapping GRAB_KEY = new KeyMapping(
            "key.ragdoll.grab",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            RAGDOLL_CATEGORY);
    private static final KeyMapping LIVE_RAGDOLL_KEY = new KeyMapping(
            "key.ragdoll.liveToggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            RAGDOLL_CATEGORY);
    private static ModContainer configContainer;
    private static CameraType previousLiveRagdollCameraType;
    private static CameraType previousSleepRagdollCameraType;
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
        NeoForge.EVENT_BUS.addListener(RagdollClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(RagdollRenderer::onRenderLevel);
        RagdollNetwork.clientSpawn = packet ->
                ClientPhysicsRuntime.updateServerRagdollAppearance(
                        packet.toSnapshot(), packet.initialLimbPose(), packet.liveRagdoll());
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
        SleepRagdollTracker.tick(minecraft);
        updateLiveRagdollCameraType(minecraft);
        updateSleepRagdollCameraType(minecraft);
        updateSleepWakeInput(minecraft);
        if (minecraft.player != null && minecraft.screen == null
                && RagdollConfig.enabled() && RagdollConfig.liveRagdollEnabled()
                && LIVE_RAGDOLL_KEY.consumeClick()) {
            requestLiveRagdollToggle(minecraft);
        }
        ClientPhysicsRuntime.updateGrabInput(minecraft, GRAB_KEY.isDown());
        RagdollPhysicsGunClient.tick(minecraft);
        ClientPhysicsRuntime.tick(minecraft);
        ClientPhysicsRuntime.followLocalLiveRagdoll(minecraft);
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

    public static boolean isSleepRagdollCameraActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return RagdollConfig.enabled() && RagdollConfig.sleepRagdollReplaceScreen()
                && SleepRagdollTracker.isLocalSleepRagdollActive(minecraft);
    }

    public static boolean shouldReplaceSleepScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isSleeping()
                && RagdollConfig.enabled() && RagdollConfig.sleepRagdollReplaceScreen();
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
                CameraType requested = RagdollConfig.sleepRagdollCameraMode()
                        == RagdollConfig.SleepCameraMode.FIRST_PERSON
                        ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
                minecraft.options.setCameraType(requested);
            } else if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
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
                ? RagdollInitialLimbPose.captureCurrentPose(
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
        restoreLiveRagdollCameraType(Minecraft.getInstance());
        if (previousSleepRagdollCameraType != null) {
            Minecraft.getInstance().options.setCameraType(previousSleepRagdollCameraType);
            previousSleepRagdollCameraType = null;
        }
        sleepCameraLookInitialized = false;
        shiftWasDown = false;
        sleepWakeShiftWasDown = false;
    }

    private static void onInteractionKey(
            InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (event.isUseItem() && minecraft.player != null && !minecraft.player.isSleeping()) {
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
                && ClientPhysicsRuntime.isLiveRagdolled(minecraft.player.getUUID())) {
            event.setCanceled(true);
        }
    }

    public static boolean shouldHideOriginalDeath(LivingEntity entity) {
        return RagdollConfig.enabled() && entity != null
                && entity instanceof AbstractClientPlayer
                && (ClientPhysicsRuntime.isControlledPlayer(entity.getUUID())
                || entity.isDeadOrDying());
    }

}
