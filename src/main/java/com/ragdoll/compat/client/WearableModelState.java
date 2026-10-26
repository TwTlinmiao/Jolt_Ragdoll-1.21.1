package com.ragdoll.compat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Protect shared player/custom armor models from another wearable's animation hook. */
final class WearableModelState implements AutoCloseable {
    private final List<Runnable> restores = new ArrayList<>();

    static WearableModelState capture(HumanoidModel<?> model) {
        var state = new WearableModelState();
        var seen = new IdentityHashMap<ModelPart, Boolean>();
        var roots = new ArrayList<>(List.of(model.head, model.hat, model.body,
                model.leftArm, model.rightArm, model.leftLeg, model.rightLeg));
        if (model instanceof PlayerModel<?> player) roots.addAll(List.of(player.jacket,
                player.leftSleeve, player.rightSleeve, player.leftPants, player.rightPants));
        roots.addAll(AdAstraArmorCompat.extraParts(model));
        for (var root : roots) root.getAllParts().forEach(part -> {
            if (seen.put(part, true) != null) return;
            PartPose pose = part.storePose();
            boolean visible = part.visible, skip = part.skipDraw;
            float x = part.xScale, y = part.yScale, z = part.zScale;
            state.restores.add(() -> {
                part.loadPose(pose);
                part.visible = visible;
                part.skipDraw = skip;
                part.xScale = x; part.yScale = y; part.zScale = z;
            });
        });
        float attack = model.attackTime, swim = model.swimAmount;
        boolean riding = model.riding, young = model.young, crouching = model.crouching;
        var left = model.leftArmPose;
        var right = model.rightArmPose;
        state.restores.add(() -> {
            model.attackTime = attack; model.swimAmount = swim;
            model.riding = riding; model.young = young; model.crouching = crouching;
            model.leftArmPose = left; model.rightArmPose = right;
        });
        return state;
    }

    @Override public void close() {
        for (int i = restores.size() - 1; i >= 0; i--) restores.get(i).run();
    }
}
