package com.ragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;

/** Native 26.2 submit-node renderer for Jolt collision parts. */
public final class RagdollRenderer26 {
    private static ModelPart cowRoot;
    private static ModelPart wardenRoot;

    private RagdollRenderer26() {
    }

    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float partialTick = minecraft.getDeltaTracker()
                .getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        double range = Math.max(
                16.0, minecraft.options.renderDistance().get() * 16.0);
        List<ClientPhysicsRuntime.RagdollRenderState> ragdolls =
                ClientPhysicsRuntime.getVisibleRagdollRenderStates(
                        partialTick,
                        bounds -> distanceToSqr(bounds, camera) <= range * range);

        for (ClientPhysicsRuntime.RagdollRenderState ragdoll : ragdolls) {
            for (ClientPhysicsRuntime.PartRenderState part : ragdoll.parts()) {
                submitPart(event, minecraft, camera, ragdoll, part);
            }
        }
    }

    private static void submitPart(
            SubmitCustomGeometryEvent event,
            Minecraft minecraft,
            Vec3 camera,
            ClientPhysicsRuntime.RagdollRenderState ragdoll,
            ClientPhysicsRuntime.PartRenderState part) {
        ModelPartSelection modelPart = selectVanillaPart(
                minecraft, ragdoll.entityTypeId(), part.name());
        if (modelPart != null) {
            submitVanillaPart(
                    event, minecraft, camera, ragdoll, part, modelPart);
            return;
        }

        float halfX = Math.max(0.025F, part.collisionHalfX());
        float halfY = Math.max(0.025F, part.collisionHalfY());
        float halfZ = Math.max(0.025F, part.collisionHalfZ());
        Identifier texture = ragdoll.texture();
        if (texture == null) {
            texture = Identifier.withDefaultNamespace(
                    "textures/entity/zombie/zombie.png");
        }
        BlockPos lightPosition = BlockPos.containing(
                part.x(), part.y(), part.z());
        int packedLight = LightCoordsUtil.pack(
                minecraft.level.getBrightness(
                        LightLayer.BLOCK, lightPosition),
                minecraft.level.getBrightness(
                        LightLayer.SKY, lightPosition));
        ModelPart.Cube cube = texturedCube(
                part.name(), halfX, halfY, halfZ);

        PoseStack pose = new PoseStack();
        pose.translate(
                part.x() - camera.x,
                part.y() - camera.y,
                part.z() - camera.z);
        pose.mulPose(new Quaternionf(
                part.rotationX(), part.rotationY(),
                part.rotationZ(), part.rotationW()));
        pose.translate(
                part.collisionOffsetX(),
                part.collisionOffsetY(),
                part.collisionOffsetZ());
        event.getSubmitNodeCollector().submitCustomGeometry(
                pose,
                RenderTypes.entityCutout(texture),
                (transform, consumer) -> cube.compile(
                        transform,
                        consumer,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        0xFFFFFFFF));
    }

    private static void submitVanillaPart(
            SubmitCustomGeometryEvent event,
            Minecraft minecraft,
            Vec3 camera,
            ClientPhysicsRuntime.RagdollRenderState ragdoll,
            ClientPhysicsRuntime.PartRenderState part,
            ModelPartSelection selection) {
        Identifier texture = ragdoll.texture();
        if (texture == null) {
            return;
        }
        BlockPos lightPosition = BlockPos.containing(
                part.x(), part.y(), part.z());
        int packedLight = LightCoordsUtil.pack(
                minecraft.level.getBrightness(
                        LightLayer.BLOCK, lightPosition),
                minecraft.level.getBrightness(
                        LightLayer.SKY, lightPosition));

        PoseStack pose = new PoseStack();
        pose.translate(
                part.x() - camera.x,
                part.y() - camera.y,
                part.z() - camera.z);
        pose.mulPose(new Quaternionf(
                part.rotationX(), part.rotationY(),
                part.rotationZ(), part.rotationW()));
        pose.scale(
                ragdoll.modelScale(),
                ragdoll.modelScale(),
                ragdoll.modelScale());
        pose.scale(-1.0F, -1.0F, 1.0F);

        event.getSubmitNodeCollector().submitCustomGeometry(
                pose,
                RenderTypes.entityCutout(texture),
                (transform, consumer) -> renderVanillaPart(
                        transform, consumer, packedLight, selection));
    }

    private static void renderVanillaPart(
            PoseStack.Pose ignored,
            VertexConsumer consumer,
            int packedLight,
            ModelPartSelection selection) {
        ModelPart part = selection.part();
        float oldX = part.x;
        float oldY = part.y;
        float oldZ = part.z;
        float oldXRot = part.xRot;
        float oldYRot = part.yRot;
        float oldZRot = part.zRot;
        boolean[] oldSkipDraw = null;
        try {
            if (selection.root() != null) {
                List<ModelPart> allParts = selection.root().getAllParts();
                oldSkipDraw = new boolean[allParts.size()];
                for (int index = 0; index < allParts.size(); ++index) {
                    oldSkipDraw[index] = allParts.get(index).skipDraw;
                    allParts.get(index).skipDraw = true;
                }
                for (ModelPart drawPart : selection.drawParts()) {
                    drawPart.skipDraw = false;
                }
            }
            part.setPos(
                    selection.offsetX(),
                    selection.offsetY(),
                    selection.offsetZ());
            part.setRotation(
                    selection.rotationX(),
                    selection.rotationY(),
                    selection.rotationZ());
            part.visible = true;
            PoseStack pose = new PoseStack();
            pose.last().pose().set(ignored.pose());
            pose.last().normal().set(ignored.normal());
            part.render(
                    pose,
                    consumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF);
        } finally {
            part.setPos(oldX, oldY, oldZ);
            part.setRotation(oldXRot, oldYRot, oldZRot);
            if (oldSkipDraw != null) {
                List<ModelPart> allParts = selection.root().getAllParts();
                for (int index = 0; index < allParts.size(); ++index) {
                    allParts.get(index).skipDraw = oldSkipDraw[index];
                }
            }
        }
    }

    private static ModelPartSelection selectVanillaPart(
            Minecraft minecraft,
            Identifier entityType,
            String partName) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType.toString()) {
            case "minecraft:cow", "minecraft:mooshroom" ->
                    selectCowPart(minecraft, partName);
            case "minecraft:warden" -> selectWardenPart(minecraft, partName);
            default -> null;
        };
    }

    private static ModelPartSelection selectCowPart(
            Minecraft minecraft,
            String name) {
        if (cowRoot == null) {
            cowRoot = minecraft.getEntityModels().bakeLayer(ModelLayers.COW);
        }
        return switch (name) {
            case "cow_head" -> selection(
                    cowRoot.getChild("head"), 0, 0, 3, 0, 0, 0);
            case "cow_body" -> selection(
                    cowRoot.getChild("body"), 0, -4, 2,
                    (float) (Math.PI / 2.0), 0, 0);
            case "cow_left_front_leg" -> selection(
                    cowRoot.getChild("left_front_leg"), 0, -6, 0, 0, 0, 0);
            case "cow_right_front_leg" -> selection(
                    cowRoot.getChild("right_front_leg"), 0, -6, 0, 0, 0, 0);
            case "cow_left_hind_leg" -> selection(
                    cowRoot.getChild("left_hind_leg"), 0, -6, 0, 0, 0, 0);
            case "cow_right_hind_leg" -> selection(
                    cowRoot.getChild("right_hind_leg"), 0, -6, 0, 0, 0, 0);
            default -> null;
        };
    }

    private static ModelPartSelection selectWardenPart(
            Minecraft minecraft,
            String name) {
        if (wardenRoot == null) {
            wardenRoot = minecraft.getEntityModels()
                    .bakeLayer(ModelLayers.WARDEN);
        }
        ModelPart bone = wardenRoot.getChild("bone");
        ModelPart body = bone.getChild("body");
        ModelPart head = body.getChild("head");
        return switch (name) {
            case "head" -> selection(
                    wardenRoot, head, 0, 8, 0,
                    head,
                    head.getChild("left_tendril"),
                    head.getChild("right_tendril"));
            case "body" -> selection(
                    wardenRoot, body, 0, 2.5F, -1.5F,
                    body,
                    body.getChild("left_ribcage"),
                    body.getChild("right_ribcage"));
            case "left_arm" -> selection(
                    wardenRoot, body.getChild("left_arm"), 0, -14, 0);
            case "right_arm" -> selection(
                    wardenRoot, body.getChild("right_arm"), 0, -14, 0);
            case "left_leg" -> selection(
                    wardenRoot, bone.getChild("left_leg"), -0.1F, -6.5F, 0);
            case "right_leg" -> selection(
                    wardenRoot, bone.getChild("right_leg"), 0.1F, -6.5F, 0);
            default -> null;
        };
    }

    private static ModelPartSelection selection(
            ModelPart part,
            float x, float y, float z,
            float xRot, float yRot, float zRot) {
        return new ModelPartSelection(
                null, part, x, y, z, xRot, yRot, zRot,
                new ModelPart[]{part});
    }

    private static ModelPartSelection selection(
            ModelPart root,
            ModelPart part,
            float x, float y, float z,
            ModelPart... drawParts) {
        return new ModelPartSelection(
                root, part, x, y, z, 0, 0, 0, drawParts);
    }

    private static ModelPart.Cube texturedCube(
            String partName,
            float halfX,
            float halfY,
            float halfZ) {
        int[] uv = humanoidUv(partName);
        float width = halfX * 32.0F;
        float height = halfY * 32.0F;
        float depth = halfZ * 32.0F;
        return new ModelPart.Cube(
                uv[0], uv[1],
                -width * 0.5F, -height * 0.5F, -depth * 0.5F,
                width, height, depth,
                0.0F, 0.0F, 0.0F,
                false,
                64.0F, 64.0F,
                Set.of(Direction.values()));
    }

    private static int[] humanoidUv(String partName) {
        return switch (partName) {
            case "head" -> new int[]{0, 0};
            case "body" -> new int[]{16, 16};
            case "right_arm" -> new int[]{40, 16};
            case "left_arm" -> new int[]{32, 48};
            case "right_leg" -> new int[]{0, 16};
            case "left_leg" -> new int[]{16, 48};
            default -> new int[]{0, 0};
        };
    }

    private static double distanceToSqr(AABB bounds, Vec3 position) {
        double x = Math.max(Math.max(bounds.minX - position.x, 0.0),
                position.x - bounds.maxX);
        double y = Math.max(Math.max(bounds.minY - position.y, 0.0),
                position.y - bounds.maxY);
        double z = Math.max(Math.max(bounds.minZ - position.z, 0.0),
                position.z - bounds.maxZ);
        return x * x + y * y + z * z;
    }

    private record ModelPartSelection(
            ModelPart root,
            ModelPart part,
            float offsetX,
            float offsetY,
            float offsetZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            ModelPart[] drawParts) {
    }
}
