package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ragdoll.compat.CompatibilityDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

/** Uses the same model, texture, tint and animation hooks as NeoForge armor. */
public final class RagdollArmorRenderer {
    private RagdollArmorRenderer() {}

    public static boolean hidesSkin(com.ragdoll.client.physics.ClientPhysicsRuntime.RagdollRenderState ragdoll,
                                    String part) {
        ItemStack stack = switch (part) {
            case "head" -> ragdoll.helmet();
            case "body", "left_arm", "right_arm" -> ragdoll.chestplate();
            case "left_leg", "right_leg" -> ragdoll.leggings();
            default -> ItemStack.EMPTY;
        };
        if (stack.isEmpty()) return false;
        try {
            Object extension = IClientItemExtensions.of(stack);
            Object gear = extension.getClass().getMethod("gearModel").invoke(extension);
            return gear != null
                    && gear.getClass().getName().equals("mekanism.client.render.armor.MekaSuitArmor");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static void render(LivingEntity corpse, ItemStack stack, EquipmentSlot slot,
                              String part, HumanoidModel<?> original, PoseStack pose,
                              MultiBufferSource buffers, int light) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) return;
        List<WearableModelState> saved = new ArrayList<>();
        pose.pushPose();
        try {
            saved.add(WearableModelState.capture(original));
            prepare(original, part);
            if (RagdollWearableAdapters.render(new RagdollWearableAdapters.Context(
                    stack, corpse, part, slot.getName(), 0, original, pose, buffers, light))) return;
            if (renderSpecialGear(corpse, stack, original, pose, buffers, light)) return;
            ImmersiveArmorCompat.render(corpse, stack, slot, part, original, pose, buffers, light);
            if (net.neoforged.fml.ModList.get().isLoaded("geckolib")
                    && GeckoArmorCompat.render(corpse, stack, slot, part, original, pose, buffers, light)) return;
            Model model = ClientHooks.getArmorModel(corpse, stack, slot, original);
            if (model instanceof HumanoidModel<?> humanoid && model != original) saved.add(WearableModelState.capture(humanoid));
            if (!(model instanceof HumanoidModel<?>)) CompatibilityDiagnostics.missing(
                    "armor/model", stack, "Non-humanoid armor model must honor the supplied part mask or provide a ragdoll adapter");
            IClientItemExtensions extension = IClientItemExtensions.of(stack);
            extension.setupModelAnimations(corpse, stack, slot, model, 0, 0, 0, 0, 0, 0);
            // A custom animation hook may reset visibility/limb poses. Apply the
            // corpse part selection after it, keeping the living renderer intact.
            if (model instanceof HumanoidModel<?> humanoid) {
                prepare(humanoid, part);
                CreateNuclearArmorCompat.prepare(humanoid, slot, part);
            }
            boolean spaceSuit = model instanceof HumanoidModel<?> humanoid && AdAstraArmorCompat.matches(humanoid);
            if (spaceSuit) AdAstraArmorCompat.prepare((HumanoidModel<?>) model, slot, part);
            boolean inner = slot == EquipmentSlot.LEGS;
            var layers = armor.getMaterial().value().layers();
            int fallback = extension.getDefaultDyeColor(stack);
            for (int i = 0; i < layers.size(); i++) {
                var layer = layers.get(i);
                int tint = extension.getArmorLayerTintColor(stack, corpse, layer, i, fallback);
                if (tint == 0) continue;
                var texture = ClientHooks.getArmorTexture(corpse, stack, layer, inner, slot);
                model.renderToBuffer(pose, buffers.getBuffer(spaceSuit ? RenderType.entityTranslucent(texture)
                                : RenderType.armorCutoutNoCull(texture)),
                        light, OverlayTexture.NO_OVERLAY, tint);
            }
            var trim = stack.get(DataComponents.TRIM);
            if (trim != null) {
                var sprite = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                        .getSprite(inner ? trim.innerTexture(armor.getMaterial()) : trim.outerTexture(armor.getMaterial()));
                model.renderToBuffer(pose, sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet(
                        trim.pattern().value().decal()))), light, OverlayTexture.NO_OVERLAY);
            }
            if (stack.hasFoil()) model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorEntityGlint()),
                    light, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException error) {
            CompatibilityDiagnostics.failure("armor/" + slot, stack, error);
        } finally {
            for (int i = saved.size() - 1; i >= 0; i--) saved.get(i).close();
            pose.popPose();
        }
    }

    /** Runs armor supplied through IClientItemExtensions custom gear models (e.g. Mekanism MekaSuit). */
    private static boolean renderSpecialGear(LivingEntity corpse, ItemStack stack,
                                             HumanoidModel<?> model, PoseStack pose,
                                             MultiBufferSource buffers, int light) {
        try {
            Object extension = IClientItemExtensions.of(stack);
            Method gearModelMethod;
            try {
                gearModelMethod = extension.getClass().getMethod("gearModel");
            } catch (NoSuchMethodException ignored) {
                return false;
            }
            Object gearModel = gearModelMethod.invoke(extension);
            if (gearModel == null) return false;
            Method render = gearModel.getClass().getMethod("render", HumanoidModel.class,
                    PoseStack.class, MultiBufferSource.class, int.class, int.class,
                    float.class, boolean.class, LivingEntity.class, ItemStack.class);
            render.invoke(gearModel, model, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                    0.0f, stack.hasFoil(), corpse, stack);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CompatibilityDiagnostics.failure("armor/special-gear", stack, error);
            return false;
        }
    }

    private static void prepare(HumanoidModel<?> model, String part) {
        model.attackTime = 0;
        model.swimAmount = 0;
        model.riding = model.young = model.crouching = false;
        model.leftArmPose = model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.setAllVisible(false);
        // Some armor implementations (including Spectrum) reset their own
        // visibility inside renderToBuffer. Keep other physical parts remote
        // even when that happens, as with third-party Curios models.
        for (var other : List.of(model.head, model.hat, model.body,
                model.leftArm, model.rightArm, model.leftLeg, model.rightLeg)) {
            other.y = 10000;
        }
        ModelPart selected = switch (part) {
            case "head" -> model.head;
            case "body" -> model.body;
            case "left_arm" -> model.leftArm;
            case "right_arm" -> model.rightArm;
            case "left_leg" -> model.leftLeg;
            case "right_leg" -> model.rightLeg;
            default -> throw new IllegalArgumentException(part);
        };
        selected.visible = true;
        selected.setRotation(0, 0, 0);
        float x = part.equals("left_arm") ? -1 : part.equals("right_arm") ? 1 : 0;
        float y = part.equals("head") ? 4 : part.endsWith("arm") ? -4 : -6;
        selected.setPos(x, y, 0);
        if (part.equals("head")) {
            model.hat.visible = true;
            model.hat.copyFrom(model.head);
        }
    }

}
