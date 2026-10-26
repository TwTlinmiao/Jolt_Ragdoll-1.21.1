package com.ragdoll.compat.client;

import com.ragdoll.compat.AccessoryPartBindings;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Offline checks, including optional-mod absence; no running game or test framework required. */
public final class WearableRegressionChecks {
    public static void main(String[] args) throws Exception {
        for (String arm : new String[]{"left_arm", "right_arm"}) {
            check(AdvancedAeArmorCompat.hidesSkin(arm, false, false, true, false, false),
                    "quantum chest hides the base skin on both arms");
            check(!AdvancedAeArmorCompat.hidesSkin(arm, true, false, false, false, false),
                    "camouflaged or missing quantum chest preserves skin");
        }
        for (String leg : new String[]{"left_leg", "right_leg"}) {
            check(AdvancedAeArmorCompat.hidesSkin(leg, true, false, false, true, false),
                    "quantum leggings hide outer pants without boots");
            check(!AdvancedAeArmorCompat.hidesSkin(leg, false, false, false, true, false),
                    "bare feet preserve base legs");
            check(AdvancedAeArmorCompat.hidesSkin(leg, false, false, false, true, true),
                    "quantum leggings and boots replace base legs");
        }
        check(AdvancedAeArmorCompat.hidesSkin("head", true, true, false, false, false)
                && !AdvancedAeArmorCompat.hidesSkin("head", false, true, false, false, false),
                "quantum helmet hides hat but preserves head");
        check(AdvancedAeArmorCompat.hidesSkin("body", true, false, true, false, false)
                && !AdvancedAeArmorCompat.hidesSkin("body", false, false, true, false, false),
                "quantum chest hides jacket but preserves torso");
        var androidDetection = Class.forName("com.ragdoll.client.physics.JoltNativeLoader")
                .getDeclaredMethod("isAndroidRuntime", String.class, String.class);
        androidDetection.setAccessible(true);
        check((boolean) androidDetection.invoke(null, "linux", "Android-16"),
                "FCL Linux/Android system identification");
        check(!(boolean) androidDetection.invoke(null, "linux", "6.8.0"),
                "desktop Linux is not Android");
        check(CustomSkinLoaderCompat.resolve(new com.mojang.authlib.GameProfile(
                java.util.UUID.randomUUID(), "RagdollSkinCheck")) == null,
                "skin site compatibility remains optional without CustomSkinLoader");
        CustomSkinLoaderCompat.clear();
        check(AccessoryPartBindings.slotBelongsToPart("head", 0, "head", false), "head slot");
        check(!AccessoryPartBindings.slotBelongsToPart("head", 0, "body", false), "head must not duplicate on body");
        check(AccessoryPartBindings.slotBelongsToPart("ring", 0, "left_arm", false), "Curios paired limbs");
        check(AccessoryPartBindings.slotBelongsToPart("ring", 0, "right_arm", true), "Accessories right slot");
        check(!AccessoryPartBindings.slotBelongsToPart("ring", 0, "left_arm", true), "Accessories split hands");
        check(AccessoryPartBindings.slotBelongsToPart("ring", 1, "left_arm", true), "Accessories left slot");
        check(AccessoryPartBindings.slotBelongsToPart("feet", 0, "left_leg", false), "left footwear");
        check(AccessoryPartBindings.slotBelongsToPart("feet", 0, "right_leg", false), "right footwear");
        check(AccessoryPartBindings.slotBelongsToPart("unknown_slot", 0, "body", false), "unknown defaults to body");
        check(AccessoryPartBindings.slotBelongsToPart("earring", 0, "head", false), "earring attaches to head");
        check(AccessoryPartBindings.slotBelongsToPart("gear", 0, "body", false), "generic gear is not mistaken for ear");
        try {
            AccessoryPartBindings.register(ResourceLocation.withDefaultNamespace("stick"), Set.of("tail"));
            throw new AssertionError("invalid binding accepted");
        } catch (IllegalArgumentException expected) {}

        HumanoidModel<?> model = new HumanoidModel<>(HumanoidModel.createMesh(CubeDeformation.NONE, 0)
                .getRoot().bake(64, 64));
        model.leftArm.y = 17;
        model.leftArm.visible = false;
        model.leftArm.xScale = 1.25f;
        model.crouching = true;
        model.attackTime = 0.4f;
        model.leftArmPose = HumanoidModel.ArmPose.BLOCK;
        try {
            try (var ignored = WearableModelState.capture(model)) {
                model.leftArm.y = 10000;
                model.leftArm.visible = true;
                model.leftArm.xScale = 0;
                model.crouching = false;
                model.attackTime = 0;
                model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                throw new IllegalStateException("simulated wearable renderer failure");
            }
        } catch (IllegalStateException expected) {}
        check(model.leftArm.y == 17 && !model.leftArm.visible && model.leftArm.xScale == 1.25f,
                "part pose, visibility and scale restored after exception");
        check(model.crouching && model.attackTime == 0.4f && model.leftArmPose == HumanoidModel.ArmPose.BLOCK,
                "humanoid animation state restored after exception");
        var prepare = RagdollArmorRenderer.class.getDeclaredMethod("prepare", HumanoidModel.class, String.class);
        prepare.setAccessible(true);
        var suit = new earth.terrarium.adastra.client.models.armor.SpaceSuitModel();
        var extras = AdAstraArmorCompat.extraParts(suit);
        extras.get(0).y = 23;
        try (var ignored = WearableModelState.capture(suit)) {
            prepare.invoke(null, suit, "head");
            AdAstraArmorCompat.prepare(suit, net.minecraft.world.entity.EquipmentSlot.HEAD, "head");
            check(extras.get(0).visible && !extras.get(1).visible, "Ad Astra visor follows helmet selection");
            prepare.invoke(null, suit, "body");
            AdAstraArmorCompat.prepare(suit, net.minecraft.world.entity.EquipmentSlot.LEGS, "body");
            check(extras.get(1).visible && !suit.body.visible, "Ad Astra leggings use the extra belt root");
            prepare.invoke(null, suit, "right_leg");
            AdAstraArmorCompat.prepare(suit, net.minecraft.world.entity.EquipmentSlot.FEET, "right_leg");
            check(extras.get(2).visible && !extras.get(3).visible && !suit.rightLeg.visible,
                    "Ad Astra boots select one extra root without ordinary leggings");
            extras.get(0).y = -100;
        }
        check(extras.get(0).y == 23 && extras.stream().allMatch(p -> p.visible),
                "custom extra-root poses and visibility restored");
        try (var ignored = WearableModelState.capture(model)) {
            prepare.invoke(null, model, "left_arm");
            // Spectrum's renderToBuffer re-enables every part of its armor slot.
            model.setAllVisible(true);
            check(model.leftArm.y == -4 && model.leftArm.x == -1, "armor selected limb coordinates");
            check(model.body.y == 10000 && model.rightArm.y == 10000,
                    "self-unmasking custom armor cannot duplicate body or opposite limb");
            check(!model.crouching && model.attackTime == 0 && model.leftArmPose == HumanoidModel.ArmPose.EMPTY,
                    "armor rendering clears living animation state");
        }
        check(RagdollWearableAdapters.hasLayer("body") && !RagdollWearableAdapters.hasLayer("head"),
                "Spectrum cape bound to torso only without loading Spectrum");
        Class.forName("com.ragdoll.compat.RagdollAccessoryCapture");
        System.out.println("Regression checks passed (Android detection, wearable bindings/restoration, optional-mod isolation).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
