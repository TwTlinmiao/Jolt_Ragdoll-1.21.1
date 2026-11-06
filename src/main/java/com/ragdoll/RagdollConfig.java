package com.ragdoll;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RagdollConfig {
    private static final String LEGACY_CONFIG_FILE = "ragdoll-common.toml";
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
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
    private static final ModConfigSpec.BooleanValue SERVER_VALUES_MIGRATED;
    private static final ModConfigSpec.BooleanValue CLIENT_VALUES_MIGRATED;

    static {
        SERVER_BUILDER.push("sleepRagdoll");
        SLEEP_RAGDOLL = SERVER_BUILDER
                .translation("ragdoll.configuration.sleepRagdoll.enabled")
                .comment("Turn the player into a physics ragdoll when they enter a bed.")
                .define("enabled", false);
        SERVER_BUILDER.pop();

        CLIENT_BUILDER.push("sleepRagdoll");
        SLEEP_RAGDOLL_REPLACE_SCREEN = CLIENT_BUILDER
                .translation("ragdoll.configuration.sleepRagdoll.replaceScreen")
                .comment("Replace the vanilla sleeping screen with a ragdoll camera.")
                .define("replaceScreen", true);
        SLEEP_RAGDOLL_CAMERA_MODE = CLIENT_BUILDER
                .translation("ragdoll.configuration.sleepRagdoll.cameraMode")
                .comment("Initial camera perspective while sleeping as a ragdoll.")
                .defineEnum("cameraMode", SleepCameraMode.THIRD_PERSON);
        CLIENT_BUILDER.pop();

        SERVER_BUILDER.push("livePlayerRagdoll");
        LIVE_RAGDOLL = SERVER_BUILDER
                .translation("ragdoll.configuration.livePlayerRagdoll.enabled")
                .comment("Allow players to temporarily become physics ragdolls with the toggle key.")
                .define("enabled", false);
        SERVER_BUILDER.pop();

        CLIENT_BUILDER.push("livePlayerRagdoll");
        LIVE_RAGDOLL_CAMERA_ENABLED = CLIENT_BUILDER
                .translation("ragdoll.configuration.livePlayerRagdoll.cameraEnabled")
                .comment("Attach the camera to the live ragdoll while ragdolled.")
                .define("cameraEnabled", true);
        LIVE_RAGDOLL_CAMERA_MODE = CLIENT_BUILDER
                .translation("ragdoll.configuration.livePlayerRagdoll.cameraMode")
                .comment("Camera perspective used while controlling a live ragdoll.")
                .defineEnum("cameraMode", LiveCameraMode.FIRST_PERSON);
        CLIENT_BUILDER.pop();

        SERVER_BUILDER.push("corpseRagdoll");
        CORPSE_RAGDOLL_ENABLED = SERVER_BUILDER
                .translation("ragdoll.configuration.corpseRagdoll.enabled")
                .comment("Allow players to keep and retrieve items from their ragdoll corpse.")
                .define("enabled", true);
        CORPSE_OWNER_ONLY = SERVER_BUILDER
                .translation("ragdoll.configuration.corpseRagdoll.ownerOnly")
                .comment("Only allow the owner of a ragdoll corpse container to open it.")
                .define("ownerOnly", true);
        SERVER_BUILDER.pop();
    }

    public static final ModConfigSpec.IntValue DESPAWN_SECONDS = SERVER_BUILDER
            .translation("ragdoll.configuration.despawnSeconds")
            .comment("How many seconds a player ragdoll remains. Zero keeps it permanently.")
            .defineInRange("despawnSeconds", 8, 0, 120);
    public static final ModConfigSpec.IntValue MAX_RAGDOLLS = SERVER_BUILDER
            .translation("ragdoll.configuration.maxRagdolls")
            .comment("Maximum number of player ragdolls retained in a world.")
            .defineInRange("maxRagdolls", 16, 1, 64);
    public static final ModConfigSpec.BooleanValue SELF_COLLISION = SERVER_BUILDER
            .translation("ragdoll.configuration.selfCollision")
            .comment("When disabled, only directly connected body parts ignore each other; other pairs still collide.")
            .define("selfCollision", true);
    public static final ModConfigSpec.BooleanValue RENDER_CAPE = CLIENT_BUILDER
            .translation("ragdoll.configuration.renderCape")
            .comment("Render the saved player cape on ragdolls.")
            .define("renderCape", false);
    public static final ModConfigSpec.BooleanValue RENDER_3D_SKIN = CLIENT_BUILDER
            .translation("ragdoll.configuration.render3dSkin")
            .comment("Render 3D Skin Layers meshes on ragdolls when the mod is installed.")
            .define("render3dSkin", true);
    public static final ModConfigSpec.BooleanValue RENDER_HD_3D_SKIN = CLIENT_BUILDER
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
    static {
        SERVER_VALUES_MIGRATED = SERVER_BUILDER
                .define("_legacyCommonValuesMigrated", false);
        CLIENT_VALUES_MIGRATED = CLIENT_BUILDER
                .define("_legacyCommonValuesMigrated", false);
    }

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private RagdollConfig() {}

    public static boolean enabled() {
        return serverValue(ENABLED, true);
    }

    public static boolean sleepRagdollEnabled() {
        return serverValue(SLEEP_RAGDOLL, false);
    }

    public static boolean sleepRagdollReplaceScreen() {
        return clientValue(SLEEP_RAGDOLL_REPLACE_SCREEN, true);
    }

    public static SleepCameraMode sleepRagdollCameraMode() {
        return clientValue(SLEEP_RAGDOLL_CAMERA_MODE, SleepCameraMode.THIRD_PERSON);
    }

    public static boolean liveRagdollEnabled() {
        return serverValue(LIVE_RAGDOLL, false);
    }

    public static boolean liveRagdollCameraEnabled() {
        return clientValue(LIVE_RAGDOLL_CAMERA_ENABLED, true);
    }

    public static LiveCameraMode liveRagdollCameraMode() {
        return clientValue(LIVE_RAGDOLL_CAMERA_MODE, LiveCameraMode.FIRST_PERSON);
    }

    public static boolean corpseRagdollEnabled() {
        return serverValue(CORPSE_RAGDOLL_ENABLED, true);
    }

    public static boolean corpseOwnerOnly() {
        return serverValue(CORPSE_OWNER_ONLY, true);
    }

    public static void setEnabled(boolean value) {
        ENABLED.set(value);
        SERVER_SPEC.save();
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (!"ragdoll".equals(config.getModId())) return;
        if (config.getType() == ModConfig.Type.CLIENT && !CLIENT_VALUES_MIGRATED.get()) {
            migrateLegacyValues(false);
            CLIENT_VALUES_MIGRATED.set(true);
            CLIENT_SPEC.save();
        }
    }

    public static void migrateLegacyServerValues() {
        if (SERVER_VALUES_MIGRATED.get()) return;
        migrateLegacyValues(true);
        SERVER_VALUES_MIGRATED.set(true);
        SERVER_SPEC.save();
    }

    private static void migrateLegacyValues(boolean serverValues) {
        Path legacyPath = FMLPaths.CONFIGDIR.get().resolve(LEGACY_CONFIG_FILE);
        if (!Files.isRegularFile(legacyPath)) return;
        try (CommentedFileConfig legacy = CommentedFileConfig.builder(legacyPath).sync().build()) {
            legacy.load();
            if (serverValues) {
                copyLegacy(legacy, "enabled", ENABLED);
                copyLegacy(legacy, "sleepRagdoll.enabled", SLEEP_RAGDOLL);
                copyLegacy(legacy, "livePlayerRagdoll.enabled", LIVE_RAGDOLL);
                copyLegacy(legacy, "corpseRagdoll.enabled", CORPSE_RAGDOLL_ENABLED);
                copyLegacy(legacy, "corpseRagdoll.ownerOnly", CORPSE_OWNER_ONLY);
                copyLegacy(legacy, "despawnSeconds", DESPAWN_SECONDS);
                copyLegacy(legacy, "maxRagdolls", MAX_RAGDOLLS);
                copyLegacy(legacy, "selfCollision", SELF_COLLISION);
            } else {
                copyLegacy(legacy, "sleepRagdoll.replaceScreen", SLEEP_RAGDOLL_REPLACE_SCREEN);
                copyLegacyEnum(legacy, "sleepRagdoll.cameraMode", SLEEP_RAGDOLL_CAMERA_MODE);
                copyLegacy(legacy, "livePlayerRagdoll.cameraEnabled", LIVE_RAGDOLL_CAMERA_ENABLED);
                copyLegacyEnum(legacy, "livePlayerRagdoll.cameraMode", LIVE_RAGDOLL_CAMERA_MODE);
                copyLegacy(legacy, "renderCape", RENDER_CAPE);
                copyLegacy(legacy, "render3dSkin", RENDER_3D_SKIN);
                copyLegacy(legacy, "renderHd3dSkin", RENDER_HD_3D_SKIN);
            }
        } catch (RuntimeException exception) {
            com.ragdoll.RagdollMod.LOGGER.warn("Could not migrate legacy ragdoll config values", exception);
        }
    }

    private static <T> void copyLegacy(CommentedFileConfig legacy, String path,
                                       ModConfigSpec.ConfigValue<T> target) {
        Object value = legacy.get(path);
        if (value != null) {
            try {
                @SuppressWarnings("unchecked") T typed = (T) value;
                target.set(typed);
            } catch (ClassCastException ignored) {
                RagdollMod.LOGGER.warn("Ignoring legacy config value with invalid type: {}", path);
            }
        }
    }

    private static <E extends Enum<E>> void copyLegacyEnum(
            CommentedFileConfig legacy, String path,
            ModConfigSpec.EnumValue<E> target) {
        Object value = legacy.get(path);
        if (value instanceof String name) {
            try {
                target.set(Enum.valueOf(target.get().getDeclaringClass(), name));
            } catch (IllegalArgumentException exception) {
                RagdollMod.LOGGER.warn("Ignoring unknown legacy config value for {}: {}", path, name);
            }
        }
    }

    public static long despawnTicks() {
        return serverValue(DESPAWN_SECONDS, 8) * 20L;
    }
    public static int maxRagdolls() {
        return serverValue(MAX_RAGDOLLS, 16);
    }
    public static boolean selfCollisionEnabled() {
        return serverValue(SELF_COLLISION, true);
    }

    public static boolean renderCape() {
        return clientValue(RENDER_CAPE, false);
    }

    public static boolean render3dSkin() {
        return skinLayersAvailable() && clientValue(RENDER_3D_SKIN, true);
    }

    public static boolean skinLayersAvailable() {
        return net.neoforged.fml.ModList.get().isLoaded("skinlayers3d");
    }

    public static boolean renderHd3dSkin() {
        return render3dSkin() && clientValue(RENDER_HD_3D_SKIN, false);
    }

    private static boolean serverValue(ModConfigSpec.BooleanValue value,
                                       boolean defaultValue) {
        return SERVER_SPEC.isLoaded() ? value.get() : defaultValue;
    }

    private static int serverValue(ModConfigSpec.IntValue value, int defaultValue) {
        return SERVER_SPEC.isLoaded() ? value.get() : defaultValue;
    }

    private static boolean clientValue(ModConfigSpec.BooleanValue value,
                                       boolean defaultValue) {
        return CLIENT_SPEC.isLoaded() ? value.get() : defaultValue;
    }

    private static <T> T clientValue(ModConfigSpec.ConfigValue<T> value, T defaultValue) {
        return CLIENT_SPEC.isLoaded() ? value.get() : defaultValue;
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
