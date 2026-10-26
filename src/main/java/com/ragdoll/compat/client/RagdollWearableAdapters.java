package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.compat.AccessoryPartBindings;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only escape hatch for items that cannot use a framework's standard renderer. */
public final class RagdollWearableAdapters {
    private static final Map<ResourceLocation, Adapter> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> LAYERS = new ConcurrentHashMap<>();
    private static boolean optionalIntegrationsInitialized;
    private static final Set<String> PARTS = Set.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg");
    private static final String SPECTRUM_CAPE = "de.dafuqs.spectrum.render.armor.BedrockCapeRenderer$BedrockCapeLayer";
    private static final String NORTHSTAR_SPACE_SUIT_LAYER =
            "com.lightning.northstar.client.renderer.armor.SpaceSuitLayerRenderer";
    private static final String DEEPER_DARKER_WARDEN_HELMET_LAYER =
            "com.kyanite.deeperdarker.client.render.WardenHelmetRenderer";
    private static final String DEEPER_DARKER_SOUL_ELYTRA_LAYER =
            "com.kyanite.deeperdarker.client.render.SoulElytraRenderer";
    private static final String UNDERGARDEN_DENIZEN_MASK_LAYER =
            "quek.undergarden.client.render.layer.DenizenMaskLayer";
    static {
        registerLayer(SPECTRUM_CAPE, Set.of("body"));
    }

    private RagdollWearableAdapters() {}

    private static synchronized void initializeOptionalIntegrations() {
        if (optionalIntegrationsInitialized || ModList.get() == null) return;
        optionalIntegrationsInitialized = true;
        if (ModList.get().isLoaded("northstar")) {
            registerLayer(NORTHSTAR_SPACE_SUIT_LAYER, Set.of("head"));
        }
        if (ModList.get().isLoaded("deeperdarker")) {
            registerLayer(DEEPER_DARKER_WARDEN_HELMET_LAYER, Set.of("head"));
            registerLayer(DEEPER_DARKER_SOUL_ELYTRA_LAYER, Set.of("body"));
        }
        if (ModList.get().isLoaded("undergarden")) {
            registerLayer(UNDERGARDEN_DENIZEN_MASK_LAYER, Set.of("head"));
        }
        if (ModList.get().isLoaded("create_sa")) {
            String root = "net.mcreator.createstuffadditions.client.renderer.";
            for (String material : Set.of("Andesite", "Brass", "Copper", "Netherite")) {
                registerLayer(root + "exoskeleton." + material + "ExoskeletonArmorRenderer", Set.of("body"));
                registerLayer(root + "jetpack." + material + "JetpackArmorRenderer", Set.of("body"));
            }
        }
        if (ModList.get().isLoaded("twilightforest")) {
            AccessoryPartBindings.register(
                    ResourceLocation.fromNamespaceAndPath("twilightforest", "charm_of_keeping_1"), Set.of("right_leg"));
            AccessoryPartBindings.register(
                    ResourceLocation.fromNamespaceAndPath("twilightforest", "charm_of_keeping_2"), Set.of("right_leg"));
            AccessoryPartBindings.register(
                    ResourceLocation.fromNamespaceAndPath("twilightforest", "charm_of_keeping_3"), Set.of("right_leg"));
            for (String item : Set.of("naga_trophy", "lich_trophy", "minoshroom_trophy",
                    "hydra_trophy", "knight_phantom_trophy", "ur_ghast_trophy",
                    "alpha_yeti_trophy", "snow_queen_trophy", "quest_ram_trophy",
                    "cicada", "firefly", "moonworm", "skeleton_skull_candle",
                    "wither_skeleton_skull_candle", "zombie_skull_candle",
                    "creeper_skull_candle", "player_skull_candle", "piglin_skull_candle")) {
                AccessoryPartBindings.register(
                        ResourceLocation.fromNamespaceAndPath("twilightforest", item), Set.of("head"));
            }
            for (String item : Set.of("naga_trophy", "lich_trophy", "minoshroom_trophy",
                    "hydra_trophy", "knight_phantom_trophy", "ur_ghast_trophy",
                    "alpha_yeti_trophy", "snow_queen_trophy", "quest_ram_trophy")) {
                register(ResourceLocation.fromNamespaceAndPath("twilightforest", item),
                        Set.of("head"), TwilightForestTrophyRenderer::render);
            }
        }
    }

    /** Replaces the Curios, Accessories or armor renderer for this item. */
    public static void register(ResourceLocation item, Set<String> parts, Adapter adapter) {
        AccessoryPartBindings.register(item, parts);
        ITEMS.put(item, java.util.Objects.requireNonNull(adapter));
    }

    /** Opt in a known player layer; never automatically execute every mod layer. */
    public static void registerLayer(String className, Set<String> parts) {
        if (parts.isEmpty() || !PARTS.containsAll(parts)) throw new IllegalArgumentException("Unknown parts: " + parts);
        LAYERS.put(className, Set.copyOf(parts));
    }

    static boolean hasLayer(String part) {
        initializeOptionalIntegrations();
        return LAYERS.values().stream().anyMatch(parts -> parts.contains(part));
    }

    static boolean matchesLayer(Object layer, String part) {
        initializeOptionalIntegrations();
        return LAYERS.getOrDefault(layer.getClass().getName(), Set.of()).contains(part);
    }

    static AutoCloseable prepareLayer(Object layer, LivingEntity corpse, PoseStack pose)
            throws ReflectiveOperationException {
        if (!layer.getClass().getName().equals(SPECTRUM_CAPE)) return () -> {};
        // Spectrum's cape uses full player coordinates, and its elytra flag is
        // global. Isolate that flag from whichever living entity rendered last.
        var context = Class.forName("de.dafuqs.spectrum.render.RenderingContext");
        var field = context.getField("isElytraRendered");
        boolean previous = field.getBoolean(null);
        field.setBoolean(null, corpse.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .getItem() instanceof net.minecraft.world.item.ElytraItem);
        pose.translate(0, -6.0 / 16.0, 0);
        return () -> field.setBoolean(null, previous);
    }

    static boolean has(ItemStack stack) {
        return ITEMS.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static boolean render(Context context) {
        var adapter = ITEMS.get(BuiltInRegistries.ITEM.getKey(context.stack().getItem()));
        if (adapter == null) return false;
        if (!AccessoryPartBindings.belongsToPart(context.stack(), context.slot(), context.index(),
                context.part(), false)) return true;
        adapter.render(context);
        return true;
    }

    @FunctionalInterface public interface Adapter { void render(Context context); }

    /** Model and pose are already relative to the selected physical body part; all clocks are frozen. */
    public record Context(ItemStack stack, LivingEntity corpse, String part, String slot,
                          int index, HumanoidModel<?> model, PoseStack pose,
                          MultiBufferSource buffers, int light) {}
}
