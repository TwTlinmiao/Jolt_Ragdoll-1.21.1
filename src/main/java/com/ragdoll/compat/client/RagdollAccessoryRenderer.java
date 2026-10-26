package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.model.RagdollAccessorySnapshot;
import com.ragdoll.compat.RagdollAccessoryCapture;
import com.ragdoll.compat.CompatibilityDiagnostics;
import com.ragdoll.mixin.client.LivingEntityRendererAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Dispatches stored accessory snapshots to optional client renderers. */
public final class RagdollAccessoryRenderer {
    private static boolean skinLayersReflectionInitialized;
    private static Method getInjectedSkinMesh;
    private static Method getInjectedSkinOffset;
    private static Method setInjectedSkinMesh;
    private static Method setupSkinLayers;
    private static final Method[] skinMeshGetters = new Method[6];
    private static Field skinLayersConfig;
    private static Field enableHat;
    private static Field enableJacket;
    private static Field enableLeftSleeve;
    private static Field enableRightSleeve;
    private static Field enableLeftPants;
    private static Field enableRightPants;

    private RagdollAccessoryRenderer() {
    }

    public static boolean replacesHeadSkin(ItemStack headItem, RagdollAccessorySnapshot snapshot) {
        if (TwilightForestTrophyRenderer.isTrophy(headItem)) return true;
        if (snapshot == null) return false;
        return containsTrophy(snapshot.curiosItems())
                || containsTrophy(snapshot.curiosCosmeticItems())
                || containsTrophy(snapshot.accessoriesItems())
                || containsTrophy(snapshot.accessoriesCosmeticItems());
    }

    private static boolean containsTrophy(java.util.Map<String, java.util.List<ItemStack>> items) {
        return items.values().stream().flatMap(java.util.Collection::stream)
                .anyMatch(TwilightForestTrophyRenderer::isTrophy);
    }

    public static void render(
            RagdollAccessorySnapshot snapshot,
            String partName,
            LivingEntity context,
            PlayerRenderer parent,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light,
            float partialTick,
            boolean renderSkinLayers) {
        if (!(context instanceof Player player)
                || !(parent instanceof LivingEntityRendererAccessor accessor)) {
            return;
        }
        boolean hasAccessories =
                snapshot != null && !snapshot.isEmpty();
        boolean hasSkinLayers = renderSkinLayers
                && ModList.get().isLoaded("skinlayers3d");
        boolean hasCatBurgerFollowers = !renderSkinLayers && partName.equals("body")
                && ModList.get().isLoaded("catburger");
        // Goggles, skulls and other wearable items in the vanilla helmet slot
        // are drawn by CustomHeadLayer, not the armor or Curios renderer.
        ItemStack headItem = context.getItemBySlot(EquipmentSlot.HEAD);
        boolean hasHeadItem = !renderSkinLayers && partName.equals("head")
                && !headItem.isEmpty() && !(headItem.getItem() instanceof ArmorItem)
                && !headItem.getItem().getClass().getName()
                .equals("quek.undergarden.item.DenizenMaskItem");
        boolean hasAdapters = !renderSkinLayers && RagdollWearableAdapters.hasLayer(partName);
        if (!hasAccessories && !hasSkinLayers && !hasHeadItem && !hasAdapters
                && !hasCatBurgerFollowers) {
            return;
        }
        // Optional layers derive their overlay from the live player. A corpse
        // must not inherit that player's persistent death tint (or later damage).
        int previousHurtTime = context.hurtTime;
        int previousDeathTime = context.deathTime;
        int previousTickCount = context.tickCount;
        float previousAttackAnim = context.attackAnim;
        float previousAttackAnimO = context.oAttackAnim;
        context.hurtTime = 0;
        context.deathTime = 0;
        // Accessory renderers may read the entity clock directly rather than
        // the age parameter. Freeze both clocks while drawing a corpse.
        context.tickCount = 0;
        context.attackAnim = 0;
        context.oAttackAnim = 0;
        try (AutoCloseable ignored = hasAccessories
                ? RagdollAccessoryCapture.mirrorForRendering(
                player, snapshot, partName)
                : () -> {
                }) {
            if (hasCatBurgerFollowers) {
                CatBurgerRagdollCompat.render(context, poseStack, buffers, light);
            }
            // Dispatch saved stacks directly: an unregistered corpse context
            // need not have the same capability slot layout as a live player.
            if (hasAccessories && !renderSkinLayers && partName.equals("body")
                    && ModList.get().isLoaded("sophisticatedbackpacks")) {
                SophisticatedBackpackRenderer.render(snapshot, context, parent, poseStack, buffers, light);
            }
            if (hasAccessories && ModList.get().isLoaded("curios")) {
                CuriosRagdollRenderer.render(snapshot, partName, context, parent,
                        poseStack, buffers, light, 0.0f);
            }
            if (hasAccessories && ModList.get().isLoaded("accessories")) {
                AccessoriesRagdollRenderer.render(snapshot, partName, context, parent,
                        poseStack, buffers, light, 0.0f);
            }
            for (Object object : accessor.ragdoll$getLayers()) {
                if (!(object instanceof RenderLayer layer)) {
                    continue;
                }
                boolean skinLayer =
                        hasSkinLayers && isSkinLayer(layer);
                boolean headLayer = hasHeadItem && layer instanceof CustomHeadLayer;
                boolean adapterLayer = hasAdapters && RagdollWearableAdapters.matchesLayer(layer, partName);
                if (!skinLayer && !headLayer && !adapterLayer) {
                    continue;
                }
                if (headLayer && TwilightForestTrophyRenderer.isTrophy(headItem)) {
                    TwilightForestTrophyRenderer.renderHeadSlot(
                            headItem, context, parent.getModel(), poseStack, buffers, light);
                    continue;
                }
                poseStack.pushPose();
                try (var state = WearableModelState.capture(parent.getModel());
                     var adapterState = RagdollWearableAdapters.prepareLayer(layer, context, poseStack)) {
                    layer.render(
                            poseStack,
                            buffers,
                            light,
                            context,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f);
                } catch (Exception error) {
                    CompatibilityDiagnostics.failure("layer/" + layer.getClass().getName(), headItem, error);
                } finally {
                    poseStack.popPose();
                }
            }
        } catch (Exception error) {
            CompatibilityDiagnostics.failure("optional-layers/" + partName, headItem, error);
        } finally {
            context.hurtTime = previousHurtTime;
            context.deathTime = previousDeathTime;
            context.tickCount = previousTickCount;
            context.attackAnim = previousAttackAnim;
            context.oAttackAnim = previousAttackAnimO;
        }
    }

    private static boolean isAccessoryLayer(RenderLayer<?, ?> layer) {
        String name = layer.getClass().getName();
        return name.equals(
                "top.theillusivec4.curios.client.render.CuriosLayer")
                || name.equals(
                "io.wispforest.accessories.client.AccessoriesRenderLayer");
    }

    private static boolean isSkinLayer(RenderLayer<?, ?> layer) {
        return layer.getClass().getName().equals(
                "dev.tr7zw.skinlayers.renderlayers."
                        + "CustomLayerFeatureRenderer");
    }

    public static void renderInjectedSkinLayer(
            ModelPart sourcePart,
            ModelPart targetPart,
            AbstractClientPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int light) {
        if (!ModList.get().isLoaded("skinlayers3d")
                || !copyInjectedSkinMesh(sourcePart, targetPart)) {
            return;
        }
        boolean previousVisible = targetPart.visible;
        targetPart.visible = true;
        try {
            targetPart.render(
                    poseStack,
                    buffers.getBuffer(RenderType.entityTranslucent(
                            player.getSkin().texture(), true)),
                    light,
                    OverlayTexture.NO_OVERLAY);
        } finally {
            targetPart.visible = previousVisible;
            clearInjectedSkinMesh(targetPart);
        }
    }

    private static boolean copyInjectedSkinMesh(
            ModelPart sourcePart,
            ModelPart targetPart) {
        initializeSkinLayersReflection();
        if (getInjectedSkinMesh == null
                || getInjectedSkinOffset == null
                || setInjectedSkinMesh == null) {
            return false;
        }
        try {
            Object mesh = getInjectedSkinMesh.invoke(sourcePart);
            Object offset = getInjectedSkinOffset.invoke(sourcePart);
            if (mesh == null || offset == null) {
                return false;
            }
            setInjectedSkinMesh.invoke(targetPart, mesh, offset);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void clearInjectedSkinMesh(ModelPart part) {
        if (setInjectedSkinMesh == null) {
            return;
        }
        try {
            setInjectedSkinMesh.invoke(part, null, null);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static void clearInjectedSkinLayer(ModelPart part) {
        if (!ModList.get().isLoaded("skinlayers3d")) {
            return;
        }
        initializeSkinLayersReflection();
        clearInjectedSkinMesh(part);
    }

    public static boolean replacesVanillaSkinLayer(
            String partName, AbstractClientPlayer player, boolean slim) {
        if (!ModList.get().isLoaded("skinlayers3d")) {
            return false;
        }
        initializeSkinLayersReflection();
        Field option = switch (partName) {
            case "head" -> enableHat;
            case "body" -> enableJacket;
            case "left_arm" -> enableLeftSleeve;
            case "right_arm" -> enableRightSleeve;
            case "left_leg" -> enableLeftPants;
            case "right_leg" -> enableRightPants;
            default -> null;
        };
        if (skinLayersConfig == null || option == null) {
            return false;
        }
        try {
            Object config = skinLayersConfig.get(null);
            if (config == null || !option.getBoolean(config)
                    || setupSkinLayers == null) {
                return false;
            }
            // HD textures cannot produce a mesh: preserve their vanilla outer layer.
            if (!Boolean.TRUE.equals(setupSkinLayers.invoke(null, player, player, slim))) {
                return false;
            }
            int index = switch (partName) {
                case "head" -> 0;
                case "body" -> 1;
                case "left_arm" -> 2;
                case "right_arm" -> 3;
                case "left_leg" -> 4;
                case "right_leg" -> 5;
                default -> -1;
            };
            return index >= 0 && skinMeshGetters[index] != null
                    && skinMeshGetters[index].invoke(player) != null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static synchronized void initializeSkinLayersReflection() {
        if (skinLayersReflectionInitialized) {
            return;
        }
        skinLayersReflectionInitialized = true;
        try {
            Class<?> injector = Class.forName(
                    "dev.tr7zw.skinlayers.accessor.ModelPartInjector");
            getInjectedSkinMesh = injector.getMethod("getInjectedMesh");
            getInjectedSkinOffset = injector.getMethod("getOffsetProvider");
            Class<?> mesh = Class.forName(
                    "dev.tr7zw.skinlayers.api.Mesh");
            Class<?> offset = Class.forName(
                    "dev.tr7zw.skinlayers.api.OffsetProvider");
            setInjectedSkinMesh = injector.getMethod(
                    "setInjectedMesh", mesh, offset);
            Class<?> modBase = Class.forName(
                    "dev.tr7zw.skinlayers.versionless.ModBase");
            skinLayersConfig = modBase.getField("config");
            Class<?> config = Class.forName(
                    "dev.tr7zw.skinlayers.versionless.config.Config");
            enableHat = config.getField("enableHat");
            enableJacket = config.getField("enableJacket");
            enableLeftSleeve = config.getField("enableLeftSleeve");
            enableRightSleeve = config.getField("enableRightSleeve");
            enableLeftPants = config.getField("enableLeftPants");
            enableRightPants = config.getField("enableRightPants");
            Class<?> settings = Class.forName(
                    "dev.tr7zw.skinlayers.accessor.PlayerSettings");
            setupSkinLayers = Class.forName("dev.tr7zw.skinlayers.SkinUtil")
                    .getMethod("setup3dLayers", AbstractClientPlayer.class,
                            settings, boolean.class);
            String[] getters = {"getHeadMesh", "getTorsoMesh", "getLeftArmMesh",
                    "getRightArmMesh", "getLeftLegMesh", "getRightLegMesh"};
            for (int i = 0; i < getters.length; ++i) {
                skinMeshGetters[i] = settings.getMethod(getters[i]);
            }
        } catch (ReflectiveOperationException ignored) {
            getInjectedSkinMesh = null;
            getInjectedSkinOffset = null;
            setInjectedSkinMesh = null;
            skinLayersConfig = null;
        }
    }

    public static ItemStack cosmeticArmorOverride(
            RagdollAccessorySnapshot snapshot, EquipmentSlot slot) {
        if (snapshot == null
                || snapshot.isEmpty()
                || !ModList.get().isLoaded("accessories")) {
            return null;
        }
        return AccessoriesRagdollRenderer.cosmeticArmorOverride(
                snapshot, slot);
    }
}
