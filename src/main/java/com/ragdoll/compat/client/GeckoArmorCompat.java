package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** Loaded only with GeckoLib present. Keeps the provider's textures and render layers. */
public final class GeckoArmorCompat {
    private record Selection(GeoArmorRenderer<?> renderer, EquipmentSlot slot, String part) {}
    private static final ThreadLocal<Selection> ACTIVE = new ThreadLocal<>();

    private GeckoArmorCompat() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean render(LivingEntity corpse, ItemStack stack, EquipmentSlot slot, String part,
                          HumanoidModel<?> original, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!(stack.getItem() instanceof GeoItem item)) return false;
        var model = GeoRenderProvider.of(stack).getGeoArmorRenderer(corpse, stack, slot, (HumanoidModel) original);
        if (!(model instanceof GeoArmorRenderer renderer)) return false;
        var previous = ACTIVE.get();
        List<Runnable> restores = new ArrayList<>();
        try (var ignored = WearableModelState.capture(renderer)) {
            renderer.prepForRender(corpse, stack, slot, original, buffers, 0, 0, 0, 0, 0);
            var geoModel = renderer.getGeoModel();
            var baked = geoModel.getBakedModel(geoModel.getModelResource(item, renderer));
            for (Object root : baked.topLevelBones()) saveAndReset((GeoBone) root, restores);
            configureQuantumDetails(renderer, stack);
            ACTIVE.set(new Selection(renderer, slot, part));
            var type = renderer.getRenderType(item, renderer.getTextureLocation(item), buffers, 0);
            if (type == null) return true;
            var buffer = ItemRenderer.getArmorFoilBuffer(buffers, type, stack.hasFoil());
            int color = renderer.getRenderColor(item, 0, light).argbInt();
            int overlay = OverlayTexture.NO_OVERLAY;
            pose.pushPose();
            try {
                renderer.preRender(pose, item, baked, buffers, buffer, false, 0, light, overlay, color);
                if (renderer.firePreRenderEvent(pose, baked, buffers, 0, light)) {
                    renderer.preApplyRenderLayers(pose, item, baked, type, buffers, buffer, 0, light, overlay);
                    // Re-render mode draws the bind pose without advancing shared living-player animation controllers.
                    renderer.actuallyRender(pose, item, baked, type, buffers, buffer, true, 0, light, overlay, color);
                    renderer.applyRenderLayers(pose, item, baked, type, buffers, buffer, 0, light, overlay);
                    renderer.postRender(pose, item, baked, buffers, buffer, false, 0, light, overlay, color);
                    renderer.firePostRenderEvent(pose, baked, buffers, 0, light);
                }
            } finally {
                pose.popPose();
            }
            renderer.renderFinal(pose, item, baked, buffers, buffer, 0, light, overlay, color);
        } finally {
            for (int i = restores.size() - 1; i >= 0; i--) restores.get(i).run();
            renderer.doPostRenderCleanup();
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
        return true;
    }

    private static void configureQuantumDetails(GeoArmorRenderer<?> renderer, ItemStack stack) {
        if (!renderer.getClass().getName().equals("net.pedroksl.advanced_ae.client.renderer.QuantumArmorRenderer")) return;
        try {
            // These child flags normally update during live-player inventory ticks.
            // A corpse uses its own saved upgrades and never ticks the live armor.
            boolean hud = (boolean) Class.forName("net.pedroksl.advanced_ae.xmod.iris.IrisPlugin")
                    .getMethod("isShaderPackInUse").invoke(null);
            var toggles = (java.util.Map<?, ?>) Class.forName("net.pedroksl.advanced_ae.common.definitions.AAEComponents")
                    .getField("UPGRADE_TOGGLE").get(null);
            Object strength = null;
            for (var entry : toggles.entrySet()) {
                if (entry.getKey() instanceof Enum<?> key && key.name().equals("STRENGTH")) strength = entry.getValue();
            }
            boolean blades = strength instanceof net.minecraft.core.component.DataComponentType<?> component && stack.has(component);
            renderer.getGeoModel().getBone("hud").ifPresent(bone -> bone.setHidden(!hud));
            renderer.getGeoModel().getBone("blade_left").ifPresent(bone -> bone.setHidden(!blades));
            renderer.getGeoModel().getBone("blade_right").ifPresent(bone -> bone.setHidden(!blades));
        } catch (ReflectiveOperationException | RuntimeException error) {
            com.ragdoll.compat.CompatibilityDiagnostics.failure("advanced_ae/details", stack, error);
        }
    }

    /** Also runs for tint/glow re-renders, whose preRender resets slot visibility. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void afterPreRender(Object candidate) {
        var selection = ACTIVE.get();
        if (selection == null || selection.renderer != candidate) return;
        GeoArmorRenderer renderer = selection.renderer;
        var model = renderer.getGeoModel();
        maskRoots(new GeoBone[]{renderer.getHeadBone(model), renderer.getBodyBone(model),
                renderer.getLeftArmBone(model), renderer.getRightArmBone(model),
                renderer.getLeftLegBone(model), renderer.getRightLegBone(model),
                renderer.getLeftBootBone(model), renderer.getRightBootBone(model)}, selection.slot, selection.part);
    }

    static void maskRoots(GeoBone[] roots, EquipmentSlot slot, String part) {
        int selected = switch (part) {
            case "head" -> 0;
            case "body" -> 1;
            case "left_arm" -> 2;
            case "right_arm" -> 3;
            case "left_leg" -> slot == EquipmentSlot.FEET ? 6 : 4;
            case "right_leg" -> slot == EquipmentSlot.FEET ? 7 : 5;
            default -> -1;
        };
        // Preserve the provider's camouflage and other visibility settings.
        for (int i = 0; i < roots.length; i++) {
            if (roots[i] != null && i != selected) roots[i].setHidden(true);
        }
    }

    private static void saveAndReset(GeoBone bone, List<Runnable> restores) {
        var saved = bone.saveSnapshot();
        boolean hidden = bone.isHidden(), childrenHidden = bone.isHidingChildren();
        restores.add(() -> {
            bone.updatePosition(saved.getOffsetX(), saved.getOffsetY(), saved.getOffsetZ());
            bone.updateRotation(saved.getRotX(), saved.getRotY(), saved.getRotZ());
            bone.updateScale(saved.getScaleX(), saved.getScaleY(), saved.getScaleZ());
            bone.setHidden(hidden);
            bone.setChildrenHidden(childrenHidden);
        });
        var initial = bone.getInitialSnapshot();
        if (initial != null) {
            bone.updatePosition(initial.getOffsetX(), initial.getOffsetY(), initial.getOffsetZ());
            bone.updateRotation(initial.getRotX(), initial.getRotY(), initial.getRotZ());
            bone.updateScale(initial.getScaleX(), initial.getScaleY(), initial.getScaleZ());
        }
        for (var child : bone.getChildBones()) saveAndReset(child, restores);
    }
}
