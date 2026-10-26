package com.ragdoll.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

final class TwilightForestTrophyRenderer {
    private static final Set<String> TROPHIES = Set.of(
            "naga_trophy", "lich_trophy", "minoshroom_trophy", "hydra_trophy",
            "knight_phantom_trophy", "ur_ghast_trophy", "alpha_yeti_trophy",
            "snow_queen_trophy", "quest_ram_trophy");

    private TwilightForestTrophyRenderer() {}

    static boolean isTrophy(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("twilightforest") && TROPHIES.contains(id.getPath());
    }

    static void render(RagdollWearableAdapters.Context context) {
        render(context.stack(), context.corpse(), context.model().head,
                context.pose(), context.buffers(), context.light(), true);
    }

    static void renderHeadSlot(ItemStack stack, LivingEntity entity,
                               net.minecraft.client.model.HumanoidModel<?> model,
                               PoseStack pose, MultiBufferSource buffers, int light) {
        render(stack, entity, model.head, pose, buffers, light, true);
    }

    private static void render(ItemStack stack, LivingEntity entity,
                               net.minecraft.client.model.geom.ModelPart head,
                               PoseStack pose, MultiBufferSource buffers, int light,
                               boolean curiosScale) {
        pose.pushPose();
        try {
            head.translateAndRotate(pose);
            pose.translate(0.0, -0.25, 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            if (curiosScale) pose.scale(0.625f, -0.625f, -0.625f);
            Minecraft minecraft = Minecraft.getInstance();
            ItemInHandRenderer renderer = new ItemInHandRenderer(
                    minecraft, minecraft.getEntityRenderDispatcher(), minecraft.getItemRenderer());
            renderer.renderItem(entity, stack, ItemDisplayContext.HEAD, false, pose, buffers, light);
        } finally {
            pose.popPose();
        }
    }
}
