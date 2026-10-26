package com.ragdoll;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Map;

public final class RagdollConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .translation("ragdoll.configuration.enabled")
            .comment("Enable ragdoll spawning, simulation, rendering and interaction.")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue SLEEP_RAGDOLL;
    public static final ModConfigSpec.BooleanValue SLEEP_RAGDOLL_REPLACE_SCREEN;
    public static final ModConfigSpec.EnumValue<SleepCameraMode> SLEEP_RAGDOLL_CAMERA_MODE;

    public static final ModConfigSpec.BooleanValue LIVE_RAGDOLL;
    public static final ModConfigSpec.BooleanValue LIVE_RAGDOLL_CAMERA_ENABLED;
    public static final ModConfigSpec.EnumValue<LiveCameraMode> LIVE_RAGDOLL_CAMERA_MODE;
    public static final ModConfigSpec.BooleanValue CORPSE_RAGDOLL_ENABLED;
    public static final ModConfigSpec.BooleanValue CORPSE_OWNER_ONLY;

    static {
        BUILDER.push("sleepRagdoll");
        SLEEP_RAGDOLL = BUILDER
                .translation("ragdoll.configuration.sleepRagdoll.enabled")
                .comment("Turn the player into a physics ragdoll when they enter a bed.")
                .define("enabled", false);
        SLEEP_RAGDOLL_REPLACE_SCREEN = BUILDER
                .translation("ragdoll.configuration.sleepRagdoll.replaceScreen")
                .comment("Replace the vanilla sleeping screen with a ragdoll camera.")
                .define("replaceScreen", true);
        SLEEP_RAGDOLL_CAMERA_MODE = BUILDER
                .translation("ragdoll.configuration.sleepRagdoll.cameraMode")
                .comment("Initial camera perspective while sleeping as a ragdoll.")
                .defineEnum("cameraMode", SleepCameraMode.THIRD_PERSON);
        BUILDER.pop();

        BUILDER.push("livePlayerRagdoll");
        LIVE_RAGDOLL = BUILDER
                .translation("ragdoll.configuration.livePlayerRagdoll.enabled")
                .comment("Allow players to temporarily become physics ragdolls with the toggle key.")
                .define("enabled", false);
        LIVE_RAGDOLL_CAMERA_ENABLED = BUILDER
                .translation("ragdoll.configuration.livePlayerRagdoll.cameraEnabled")
                .comment("Attach the camera to the live ragdoll while ragdolled.")
                .define("cameraEnabled", true);
        LIVE_RAGDOLL_CAMERA_MODE = BUILDER
                .translation("ragdoll.configuration.livePlayerRagdoll.cameraMode")
                .comment("Camera perspective used while controlling a live ragdoll.")
                .defineEnum("cameraMode", LiveCameraMode.FIRST_PERSON);
        BUILDER.pop();

        BUILDER.push("corpseRagdoll");
        CORPSE_RAGDOLL_ENABLED = BUILDER
                .translation("ragdoll.configuration.corpseRagdoll.enabled")
                .comment("Allow players to keep and retrieve items from their ragdoll corpse.")
                .define("enabled", true);
        CORPSE_OWNER_ONLY = BUILDER
                .translation("ragdoll.configuration.corpseRagdoll.ownerOnly")
                .comment("Only allow the owner of a ragdoll corpse container to open it.")
                .define("ownerOnly", true);
        BUILDER.pop();
    }

    public static final ModConfigSpec.IntValue DESPAWN_SECONDS = BUILDER
            .translation("ragdoll.configuration.despawnSeconds")
            .comment("How many seconds a player ragdoll remains. Zero keeps it permanently.")
            .defineInRange("despawnSeconds", 8, 0, 120);
    public static final ModConfigSpec.IntValue MAX_RAGDOLLS = BUILDER
            .translation("ragdoll.configuration.maxRagdolls")
            .comment("Maximum number of player ragdolls retained in a world.")
            .defineInRange("maxRagdolls", 16, 1, 64);
    public static final ModConfigSpec.BooleanValue SELF_COLLISION = BUILDER
            .translation("ragdoll.configuration.selfCollision")
            .comment("When disabled, only directly connected body parts ignore each other; other pairs still collide.")
            .define("selfCollision", true);
    public static final ModConfigSpec.BooleanValue RENDER_CAPE = BUILDER
            .translation("ragdoll.configuration.renderCape")
            .comment("Render the saved player cape on ragdolls.")
            .define("renderCape", false);
    public static final ModConfigSpec.BooleanValue RENDER_3D_SKIN = BUILDER
            .translation("ragdoll.configuration.render3dSkin")
            .comment("Render 3D Skin Layers meshes on ragdolls when the mod is installed.")
            .define("render3dSkin", true);
    public static final ModConfigSpec.BooleanValue RENDER_HD_3D_SKIN = BUILDER
            .translation("ragdoll.configuration.renderHd3dSkin")
            .comment("Generate a separate 3D outer shell for HD ragdoll skins. Disabled by default.")
            .define("renderHd3dSkin", false);

    private static final Map<String, CollisionSettings> COLLISION_SETTINGS = Map.of(
            "head", new CollisionSettings(0.5, 0.25, 0.5, 0.0, 0.0, 0.0),
            "body", new CollisionSettings(0.375, 0.5625, 0.25, 0.0, -0.0625, 0.0),
            "left_arm", new CollisionSettings(0.125, 0.625, 0.25, 0.0, 0.0, 0.0),
            "right_arm", new CollisionSettings(0.125, 0.625, 0.25, 0.0, 0.0, 0.0),
            "left_leg", new CollisionSettings(0.125, 0.625, 0.25, 0.0, 0.0, 0.0),
            "right_leg", new CollisionSettings(0.125, 0.625, 0.25, 0.0, 0.0, 0.0));
    public static final ModConfigSpec SPEC = BUILDER.build();

    private RagdollConfig() {}

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static boolean sleepRagdollEnabled() {
        return SLEEP_RAGDOLL.get();
    }

    public static boolean sleepRagdollReplaceScreen() {
        return sleepRagdollEnabled() && SLEEP_RAGDOLL_REPLACE_SCREEN.get();
    }

    public static SleepCameraMode sleepRagdollCameraMode() {
        return SLEEP_RAGDOLL_CAMERA_MODE.get();
    }

    public static boolean liveRagdollEnabled() {
        return LIVE_RAGDOLL.get();
    }

    public static boolean liveRagdollCameraEnabled() {
        return LIVE_RAGDOLL_CAMERA_ENABLED.get();
    }

    public static LiveCameraMode liveRagdollCameraMode() {
        return LIVE_RAGDOLL_CAMERA_MODE.get();
    }

    public static boolean corpseRagdollEnabled() {
        return CORPSE_RAGDOLL_ENABLED.get();
    }

    public static boolean corpseOwnerOnly() {
        return CORPSE_OWNER_ONLY.get();
    }

    public static void setEnabled(boolean value) {
        ENABLED.set(value);
        SPEC.save();
    }

    public static long despawnTicks() { return DESPAWN_SECONDS.getAsInt() * 20L; }
    public static int maxRagdolls() { return MAX_RAGDOLLS.getAsInt(); }
    public static boolean selfCollisionEnabled() {
        return SELF_COLLISION.get();
    }

    public static boolean renderCape() {
        return RENDER_CAPE.get();
    }

    public static boolean render3dSkin() {
        return skinLayersAvailable() && RENDER_3D_SKIN.get();
    }

    public static boolean skinLayersAvailable() {
        return net.neoforged.fml.ModList.get().isLoaded("skinlayers3d");
    }

    public static boolean renderHd3dSkin() {
        return render3dSkin() && RENDER_HD_3D_SKIN.get();
    }

    public static boolean showCollisionBoxes() {
        return false;
    }

    public static void setShowCollisionBoxes(boolean value) {
        // Collision visualization is intentionally no longer configurable.
    }

    public static float collisionBoxLineWidth() {
        return 2.0f;
    }

    public static double collisionValue(String part, String axis) {
        CollisionSettings settings = collisionSettings(part);
        return switch (axis) {
            case "sizeX" -> settings.sizeX();
            case "sizeY" -> settings.sizeY();
            case "sizeZ" -> settings.sizeZ();
            case "offsetX" -> settings.offsetX();
            case "offsetY" -> settings.offsetY();
            case "offsetZ" -> settings.offsetZ();
            default -> throw new IllegalArgumentException("Unknown collision axis: " + axis);
        };
    }

    public static void setCollisionValue(String part, String axis, double value) {
        collisionSettings(part);
    }

    public static void resetCollisionPart(String part) {
        CollisionSettings settings = collisionSettings(part);
    }

    private static CollisionSettings collisionSettings(String part) {
        CollisionSettings settings = COLLISION_SETTINGS.get(part);
        if (settings == null) {
            throw new IllegalArgumentException("Unknown player ragdoll part: " + part);
        }
        return settings;
    }

    public enum LiveCameraMode { FIRST_PERSON, THIRD_PERSON }
    public enum SleepCameraMode { FIRST_PERSON, THIRD_PERSON }

    public static CollisionBox collisionBox(String part) {
        CollisionSettings settings = collisionSettings(part);
        return new CollisionBox(settings.sizeX(), settings.sizeY(), settings.sizeZ(),
                settings.offsetX(), settings.offsetY(), settings.offsetZ());
    }


    public static double collisionScale(double modelScale) {
        return Double.isFinite(modelScale) ? Math.max(0.01, modelScale) : 1.0;
    }

    public record CollisionBox(double sizeX, double sizeY, double sizeZ,
                               double positionOffsetX, double positionOffsetY, double positionOffsetZ) {
        public float halfX() { return safeHalf(sizeX); }
        public float halfY() { return safeHalf(sizeY); }
        public float halfZ() { return safeHalf(sizeZ); }
        public float offsetX() { return (float) positionOffsetX; }
        public float offsetY() { return (float) positionOffsetY; }
        public float offsetZ() { return (float) positionOffsetZ; }
        private static float safeHalf(double size) { return (float) Math.max(0.01, size * 0.5); }
    }

    private record CollisionSettings(double sizeX, double sizeY, double sizeZ,
                                     double offsetX, double offsetY, double offsetZ) {}
}
