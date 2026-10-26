package com.ragdoll.compat.client;

import java.util.ArrayList;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.GeoBone;

public final class GeckoArmorRegressionChecks {
    public static void main(String[] args) throws Exception {
        GeoBone[] roots = new GeoBone[8];
        for (int i = 0; i < roots.length; i++) roots[i] = new GeoBone(null, "root" + i, false, 0d, false, false);
        for (int pass = 0; pass < 3; pass++) {
            // The provider resets slot visibility for base, tint and glow passes.
            for (var root : roots) root.setHidden(false);
            GeckoArmorCompat.maskRoots(roots, EquipmentSlot.CHEST, "left_arm");
            for (int i = 0; i < roots.length; i++) {
                check(roots[i].isHidden() == (i != 2) && roots[i].isHidingChildren() == (i != 2),
                        "base/tint/glow must isolate an entire arm subtree");
            }
        }
        roots[2].setHidden(true);
        GeckoArmorCompat.maskRoots(roots, EquipmentSlot.CHEST, "left_arm");
        check(roots[2].isHidden(), "camouflage remains hidden");
        for (var root : roots) root.setHidden(false);
        GeckoArmorCompat.maskRoots(roots, EquipmentSlot.FEET, "right_leg");
        check(!roots[7].isHidden() && roots[5].isHidden(), "boots use boot bones instead of leggings");
        var bone = roots[0];
        bone.saveInitialSnapshot();
        bone.updatePosition(1, 2, 3);
        bone.updateRotation(0.2f, 0.3f, 0.4f);
        bone.updateScale(2, 3, 4);
        bone.setHidden(true);
        bone.setChildrenHidden(false);
        var reset = GeckoArmorCompat.class.getDeclaredMethod("saveAndReset", GeoBone.class, java.util.List.class);
        reset.setAccessible(true);
        var restores = new ArrayList<Runnable>();
        reset.invoke(null, bone, restores);
        check(bone.getPosX() == 0 && bone.getRotY() == 0 && bone.getScaleZ() == 1,
                "shared living-player animation is reset to bind pose");
        restores.forEach(Runnable::run);
        check(bone.getPosY() == 2 && bone.getRotY() == 0.3f && bone.getScaleZ() == 4
                && bone.isHidden() && !bone.isHidingChildren(), "shared bone transforms and visibility restored");
        System.out.println("Gecko armor regression checks passed (part masks, tint/glow passes, camouflage, bind pose restoration).");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
