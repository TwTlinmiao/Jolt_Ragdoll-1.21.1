package com.ragdoll.client;

import com.ragdoll.item.RagdollItems;
import com.ragdoll.RagdollConfig;
import com.ragdoll.network.RagdollNetwork;
import com.ragdoll.network.RagdollPhysicsGunActionPacket;
import com.ragdoll.network.RagdollPhysicsGunSyncPacket;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client input and synchronized state for the Physics Gun.
 *
 * <p>The interaction flow follows Velthoric: raw left/right mouse input,
 * wheel distance adjustment, and Tab-held rotation mode.</p>
 */
public final class RagdollPhysicsGunClient {

    private static final Map<UUID, RagdollPhysicsGunSyncPacket.GrabData>
            ACTIVE_GRABS = new LinkedHashMap<>();

    private static final Set<UUID> TRYING_PLAYERS =
            new LinkedHashSet<>();

    private static boolean leftDown;

    private static boolean rotationMode;

    private RagdollPhysicsGunClient() {
    }

    public static void tick(Minecraft minecraft) {
        if (!RagdollConfig.enabled()) {
            if (leftDown || rotationMode || isLocalPlayerTrying(minecraft)
                    || isLocalPlayerGrabbing(minecraft)) {
                stopGrabAttempt();
            }
            resetLocalState();
            ACTIVE_GRABS.clear();
            TRYING_PLAYERS.clear();
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            resetLocalState();
            ACTIVE_GRABS.clear();
            TRYING_PLAYERS.clear();
            return;
        }
        if ((minecraft.screen != null || !isArmed(minecraft))
                && (leftDown
                || isLocalPlayerTrying(minecraft)
                || isLocalPlayerGrabbing(minecraft))) {
            stopGrabAttempt();
        }
    }

    public static boolean handleMouseButton(
            int button,
            int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetLocalState();
            return false;
        }
        if (minecraft.screen != null) {
            if (isArmed(minecraft)
                    && (leftDown
                    || isLocalPlayerTrying(minecraft)
                    || isLocalPlayerGrabbing(minecraft))) {
                stopGrabAttempt();
            }
            return false;
        }
        if (!isArmed(minecraft)) {
            if (leftDown
                    || isLocalPlayerTrying(minecraft)
                    || isLocalPlayerGrabbing(minecraft)) {
                stopGrabAttempt();
            }
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                leftDown = true;
                send(RagdollPhysicsGunActionPacket.Action
                        .START_GRAB_ATTEMPT);
            } else if (action == GLFW.GLFW_RELEASE) {
                stopGrabAttempt();
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (action == GLFW.GLFW_PRESS) {
                send(RagdollPhysicsGunActionPacket.Action
                        .FREEZE_OBJECT);
            }
            return true;
        }
        return false;
    }

    public static boolean handleMouseScroll(double delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isArmed(minecraft)
                || minecraft.screen != null
                || !leftDown
                || !Double.isFinite(delta)) {
            return false;
        }
        RagdollNetwork.sendPhysicsGun(
                new RagdollPhysicsGunActionPacket(
                        RagdollPhysicsGunActionPacket.Action
                                .UPDATE_SCROLL,
                        (float) delta,
                        0.0f));
        return true;
    }

    public static boolean handleRotationKey(
            long window,
            int key,
            int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()
                || key != GLFW.GLFW_KEY_TAB
                || minecraft.screen != null
                || !isArmed(minecraft)
                || !isLocalPlayerGrabbing(minecraft)) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS && !rotationMode) {
            rotationMode = true;
            send(RagdollPhysicsGunActionPacket.Action
                    .START_ROTATION_MODE);
        } else if (action == GLFW.GLFW_RELEASE
                && rotationMode) {
            rotationMode = false;
            send(RagdollPhysicsGunActionPacket.Action
                    .STOP_ROTATION_MODE);
        }
        return true;
    }

    public static boolean interceptMouseTurn(
            double deltaX,
            double deltaY) {
        if (!RagdollConfig.enabled()) {
            return false;
        }
        if (!rotationMode) {
            return false;
        }
        if (deltaX != 0.0 || deltaY != 0.0) {
            RagdollNetwork.sendPhysicsGun(
                    new RagdollPhysicsGunActionPacket(
                            RagdollPhysicsGunActionPacket.Action
                                    .UPDATE_ROTATION,
                            (float) deltaX,
                            (float) deltaY));
        }
        return true;
    }

    public static void updateState(
            RagdollPhysicsGunSyncPacket packet) {
        ACTIVE_GRABS.clear();
        for (RagdollPhysicsGunSyncPacket.GrabData grab
                : packet.grabs()) {
            ACTIVE_GRABS.put(grab.playerId(), grab);
        }
        TRYING_PLAYERS.clear();
        TRYING_PLAYERS.addAll(packet.tryingPlayers());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && !ACTIVE_GRABS.containsKey(
                minecraft.player.getUUID())) {
            rotationMode = false;
        }
    }

    public static Map<UUID, RagdollPhysicsGunSyncPacket.GrabData>
    activeGrabs() {
        return Map.copyOf(ACTIVE_GRABS);
    }

    public static Set<UUID> tryingPlayers() {
        return Set.copyOf(TRYING_PLAYERS);
    }

    public static boolean isArmed(Minecraft minecraft) {
        return RagdollConfig.enabled() && minecraft.player != null
                && minecraft.level != null
                && (minecraft.player.getMainHandItem().is(
                RagdollItems.PHYSICS_GUN.get())
                || minecraft.player.getOffhandItem().is(
                RagdollItems.PHYSICS_GUN.get()));
    }

    private static boolean isLocalPlayerGrabbing(
            Minecraft minecraft) {
        return minecraft.player != null
                && ACTIVE_GRABS.containsKey(
                minecraft.player.getUUID());
    }

    private static boolean isLocalPlayerTrying(
            Minecraft minecraft) {
        return minecraft.player != null
                && TRYING_PLAYERS.contains(
                minecraft.player.getUUID());
    }

    private static void stopGrabAttempt() {
        send(RagdollPhysicsGunActionPacket.Action
                .STOP_GRAB_ATTEMPT);
        leftDown = false;
        rotationMode = false;
    }

    private static void resetLocalState() {
        leftDown = false;
        rotationMode = false;
    }

    private static void send(
            RagdollPhysicsGunActionPacket.Action action) {
        RagdollNetwork.sendPhysicsGun(
                new RagdollPhysicsGunActionPacket(action));
    }
}
