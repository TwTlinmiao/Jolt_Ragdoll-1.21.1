package com.ragdoll.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ragdoll.RagdollConfig;
import com.ragdoll.RagdollMod;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import com.ragdoll.client.physics.RagdollPartRenderState;
import com.ragdoll.compat.client.RagdollAccessoryRenderer;
import com.ragdoll.compat.client.RagdollArmorRenderer;
import com.ragdoll.mixin.client.LivingEntityRendererAccessor;
import com.ragdoll.mixin.client.PlayerRendererAccessor;
import com.ragdoll.item.RagdollItems;
import com.ragdoll.network.RagdollPhysicsGunSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders the original Jolt part transforms through the 1.21.1 level pipeline. */
public final class RagdollRenderer {
    private static final ByteBufferBuilder SHARED_BUFFER = new ByteBufferBuilder(786432);
    private static final Map<RenderType, ByteBufferBuilder> FIXED_BUFFERS = new LinkedHashMap<>();
    private static PlayerModel<?> wideModel;
    private static PlayerModel<?> slimModel;
    private static HumanoidModel<?> outerArmor;
    private static HumanoidModel<?> innerArmor;
    private static ModelPart vanillaLeftElytraWing;
    private static ModelPart vanillaRightElytraWing;
    private static ModelPart vanillaCloakModel;

    static {
        for (RenderType type : List.of(Sheets.cutoutBlockSheet(), Sheets.translucentItemSheet(),
                RenderType.glint(), RenderType.glintTranslucent(), RenderType.entityGlint(),
                RenderType.armorEntityGlint(), Sheets.armorTrimsSheet(false),
                Sheets.armorTrimsSheet(true), RenderType.lines())) {
            FIXED_BUFFERS.put(type, new ByteBufferBuilder(262144));
        }
    }

    private RagdollRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!RagdollConfig.enabled()) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double range = Math.max(16, minecraft.options.renderDistance().get() * 16);
        List<ClientPhysicsRuntime.RagdollRenderState> ragdolls =
                ClientPhysicsRuntime.getVisibleRagdollRenderStates(partialTick,
                        bounds -> distanceToSqr(bounds, camera) <= range * range);
        boolean hasPhysicsGunBeams =
                !RagdollPhysicsGunClient.activeGrabs().isEmpty()
                        || !RagdollPhysicsGunClient
                        .tryingPlayers().isEmpty();
        if (ragdolls.isEmpty() && !hasPhysicsGunBeams) {
            return;
        }
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediateWithBuffers(
                new LinkedHashMap<>(FIXED_BUFFERS), SHARED_BUFFER);
        PoseStack pose = event.getPoseStack();
        try {
            for (var ragdoll : ragdolls) {
                for (var part : ragdoll.parts()) {
                    int light = LevelRenderer.getLightColor(minecraft.level,
                            BlockPos.containing(part.x(), part.y(), part.z()));
                    renderPart(minecraft, pose, buffers, camera, ragdoll, part, light, partialTick);
                }
            }
            renderPhysicsGunBeams(
                    minecraft, pose, buffers, camera, partialTick);
        } catch (RuntimeException exception) {
            RagdollMod.LOGGER.error("Unable to render player ragdoll", exception);
        } finally {
            buffers.endBatch();
        }
    }

    private static void renderPart(Minecraft minecraft, PoseStack pose, MultiBufferSource buffers,
                                   Vec3 camera, ClientPhysicsRuntime.RagdollRenderState ragdoll,
                                   RagdollPartRenderState part, int light, float partialTick) {
        pose.pushPose();
        try {
            pose.translate(part.x() - camera.x, part.y() - camera.y, part.z() - camera.z);
            pose.mulPose(new Quaternionf(part.rotationX(), part.rotationY(),
                    part.rotationZ(), part.rotationW()));
            if (RagdollConfig.showCollisionBoxes()) {
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                        part.collisionOffsetX() - part.collisionHalfX(),
                        part.collisionOffsetY() - part.collisionHalfY(),
                        part.collisionOffsetZ() - part.collisionHalfZ(),
                        part.collisionOffsetX() + part.collisionHalfX(),
                        part.collisionOffsetY() + part.collisionHalfY(),
                        part.collisionOffsetZ() + part.collisionHalfZ(),
                        1.0f, 0.35f, 0.2f, 0.85f);
            }
            if (ragdoll.slim() && "left_arm".equals(part.name())) {
                pose.translate(0.5f / 16.0f * ragdoll.modelScale(), 0, 0);
            } else if (ragdoll.slim() && "right_arm".equals(part.name())) {
                pose.translate(-0.5f / 16.0f * ragdoll.modelScale(), 0, 0);
            }
            float scale = ragdoll.modelScale() * Math.max(0, ragdoll.fadeAlpha());
            pose.scale(scale, scale, scale);
            pose.scale(-1, -1, 1);

            PlayerModel<?> model = model(ragdoll.slim());
            PartSelection selection = select(model, part.name(), ragdoll.slim());
            if (selection == null) {
                return;
            }
            RagdollAccessoryRenderer.clearInjectedSkinLayer(selection.base());
            RagdollAccessoryRenderer.clearInjectedSkinLayer(selection.outer());
            // Preserve the texture's alpha, including partially transparent skin pixels.
            VertexConsumer skin = buffers.getBuffer(RenderType.entityTranslucent(ragdoll.texture()));
            boolean trophyReplacesHead = "head".equals(part.name())
                    && RagdollAccessoryRenderer.replacesHeadSkin(ragdoll.helmet(), ragdoll.accessories());
            boolean mekSuitReplacesSkin = RagdollArmorRenderer.hidesSkin(ragdoll, part.name());
            boolean hideBase = trophyReplacesHead || mekSuitReplacesSkin
                    || com.ragdoll.compat.client.AdvancedAeArmorCompat.hidesSkin(ragdoll, part.name(), false);
            boolean hideOuter = trophyReplacesHead || mekSuitReplacesSkin
                    || com.ragdoll.compat.client.AdvancedAeArmorCompat.hidesSkin(ragdoll, part.name(), true);
            if (!hideBase) draw(selection.base(), selection.x(), selection.y(), pose, skin, light, 0xFFFFFFFF);
            boolean render3dSkinLayer =
                    !hideOuter && canRenderSkinLayers(minecraft, ragdoll, part.name());
            boolean hdSkinLayer = !hideOuter && RagdollConfig.render3dSkin()
                    && RagdollConfig.renderHd3dSkin()
                    && (ragdoll.shownModelParts() & selection.toggle().getMask()) != 0
                    && HdSkinLayerRenderer.render(ragdoll.texture(), part.name(), ragdoll.slim(),
                            selection.outer(), selection.x(), selection.y(), pose, skin, light);
            boolean hideVanillaOuterLayer =
                    render3dSkinLayer || hdSkinLayer;
            if ((ragdoll.shownModelParts()
                    & selection.toggle().getMask()) != 0
                    && !hideOuter && !hideVanillaOuterLayer) {
                draw(selection.outer(), selection.x(), selection.y(), pose, skin, light, 0xFFFFFFFF);
            }
            renderArmor(pose, buffers, ragdoll, part.name(), light);
            renderHeldItem(minecraft, pose, buffers, ragdoll, part.name(), light);
            renderOptionalModLayers(minecraft, pose, buffers, ragdoll, part.name(), light, partialTick);
            if ("body".equals(part.name())) {
                renderBack(pose, buffers, ragdoll, light);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void renderPhysicsGunBeams(
            Minecraft minecraft,
            PoseStack pose,
            MultiBufferSource buffers,
            Vec3 camera,
            float partialTick) {
        if (minecraft.level == null) {
            return;
        }
        VertexConsumer lines =
                buffers.getBuffer(RenderType.lines());
        Map<java.util.UUID, RagdollPhysicsGunSyncPacket.GrabData>
                activeGrabs =
                RagdollPhysicsGunClient.activeGrabs();
        for (var entry
                : activeGrabs.entrySet()) {
            var player = minecraft.level.getPlayerByUUID(entry.getKey());
            if (player == null) {
                continue;
            }
            var grab = entry.getValue();
            Vec3 end = ClientPhysicsRuntime.getRagdollLocalPoint(
                    grab.ragdollId(),
                    grab.partName(),
                    grab.localX(),
                    grab.localY(),
                    grab.localZ(),
                    partialTick);
            if (end == null) {
                continue;
            }
            Vec3 look = physicsGunLook(
                    minecraft, player, partialTick);
            Vec3 start = physicsGunTip(
                    minecraft, player, look, partialTick);
            renderPhysicsGunBeam(
                    pose, lines, camera, start, end, look);
        }

        for (java.util.UUID playerId
                : RagdollPhysicsGunClient.tryingPlayers()) {
            if (activeGrabs.containsKey(playerId)) {
                continue;
            }
            var player = minecraft.level.getPlayerByUUID(playerId);
            if (player == null) {
                continue;
            }
            Vec3 look = physicsGunLook(
                    minecraft, player, partialTick);
            Vec3 traceStart = player.getEyePosition(partialTick);
            Vec3 traceEnd = traceStart.add(look.scale(100.0));
            BlockHitResult blockHit = minecraft.level.clip(
                    new ClipContext(
                            traceStart,
                            traceEnd,
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            player));
            Vec3 end = blockHit.getType() == HitResult.Type.MISS
                    ? traceEnd : blockHit.getLocation();
            Vec3 start = physicsGunTip(
                    minecraft, player, look, partialTick);
            renderPhysicsGunBeam(
                    pose, lines, camera, start, end, look);
        }
    }

    private static Vec3 physicsGunLook(
            Minecraft minecraft,
            net.minecraft.world.entity.player.Player player,
            float partialTick) {
        if (player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()) {
            org.joml.Vector3f look =
                    minecraft.gameRenderer.getMainCamera()
                            .getLookVector();
            return new Vec3(
                    look.x(), look.y(), look.z()).normalize();
        }
        return player.getViewVector(partialTick).normalize();
    }

    private static Vec3 physicsGunTip(
            Minecraft minecraft,
            net.minecraft.world.entity.player.Player player,
            Vec3 look,
            float partialTick) {
        boolean mainHand = player.getMainHandItem().is(
                RagdollItems.PHYSICS_GUN.get());
        double handSide = mainHand ? 1.0 : -1.0;
        if (player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()) {
            var camera =
                    minecraft.gameRenderer.getMainCamera();
            org.joml.Vector3f cameraUp = camera.getUpVector();
            Vec3 up = new Vec3(
                    cameraUp.x(), cameraUp.y(), cameraUp.z())
                    .normalize();
            Vec3 right = look.cross(up).normalize();
            return camera.getPosition()
                    .add(look.scale(0.5))
                    .add(right.scale(0.3 * handSide))
                    .add(up.scale(-0.15));
        }
        Vec3 up = player.getUpVector(partialTick).normalize();
        Vec3 right = look.cross(up).normalize();
        return player.getEyePosition(partialTick)
                .add(look.scale(0.3))
                .add(right.scale(-0.35 * handSide))
                .add(up.scale(-0.3));
    }

    private static void renderPhysicsGunBeam(
            PoseStack pose,
            VertexConsumer lines,
            Vec3 camera,
            Vec3 start,
            Vec3 end,
            Vec3 look) {
        double distance = start.distanceTo(end);
        if (distance < 1.0e-5) {
            return;
        }
        Vec3 firstControl =
                start.add(look.scale(distance * 0.3));
        Vec3 secondControl = end.add(
                start.subtract(end).normalize()
                        .scale(distance * 0.3));
        Vec3 previous = start;
        for (int segment = 1; segment <= 20; ++segment) {
            double t = segment / 20.0;
            Vec3 next = cubicBezier(
                    start,
                    firstControl,
                    secondControl,
                    end,
                    t);
            Vec3 normal = next.subtract(previous).normalize();
            lines.addVertex(
                            pose.last(),
                            (float) (previous.x - camera.x),
                            (float) (previous.y - camera.y),
                            (float) (previous.z - camera.z))
                    .setColor(248, 73, 3, 220)
                    .setNormal(
                            pose.last(),
                            (float) normal.x,
                            (float) normal.y,
                            (float) normal.z);
            lines.addVertex(
                            pose.last(),
                            (float) (next.x - camera.x),
                            (float) (next.y - camera.y),
                            (float) (next.z - camera.z))
                    .setColor(248, 73, 3, 220)
                    .setNormal(
                            pose.last(),
                            (float) normal.x,
                            (float) normal.y,
                            (float) normal.z);
            previous = next;
        }
    }

    private static Vec3 cubicBezier(
            Vec3 first,
            Vec3 firstControl,
            Vec3 secondControl,
            Vec3 last,
            double t) {
        double inverse = 1.0 - t;
        return first.scale(inverse * inverse * inverse)
                .add(firstControl.scale(
                        3.0 * inverse * inverse * t))
                .add(secondControl.scale(
                        3.0 * inverse * t * t))
                .add(last.scale(t * t * t));
    }

    private static PlayerModel<?> model(boolean slim) {
        if (slim) {
            if (slimModel == null) {
                slimModel = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, true)
                        .getRoot().bake(64, 64), true);
            }
            return slimModel;
        }
        if (wideModel == null) {
            wideModel = new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, false)
                    .getRoot().bake(64, 64), false);
        }
        return wideModel;
    }

    private static PartSelection select(PlayerModel<?> model, String part, boolean slim) {
        return switch (part) {
            case "head" -> new PartSelection(model.head, model.hat, PlayerModelPart.HAT, 0, 4);
            case "body" -> new PartSelection(model.body, model.jacket, PlayerModelPart.JACKET, 0, -6);
            case "left_arm" -> new PartSelection(model.leftArm, model.leftSleeve,
                    PlayerModelPart.LEFT_SLEEVE, slim ? -0.5f : -1, -4);
            case "right_arm" -> new PartSelection(model.rightArm, model.rightSleeve,
                    PlayerModelPart.RIGHT_SLEEVE, slim ? 0.5f : 1, -4);
            case "left_leg" -> new PartSelection(model.leftLeg, model.leftPants,
                    PlayerModelPart.LEFT_PANTS_LEG, 0, -6);
            case "right_leg" -> new PartSelection(model.rightLeg, model.rightPants,
                    PlayerModelPart.RIGHT_PANTS_LEG, 0, -6);
            default -> null;
        };
    }

    private static void draw(ModelPart part, float x, float y, PoseStack pose,
                             VertexConsumer buffer, int light, int color) {
        part.visible = true;
        part.setPos(x, y, 0);
        part.setRotation(0, 0, 0);
        part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, color);
    }

    private static void renderArmor(PoseStack pose, MultiBufferSource buffers,
                                    ClientPhysicsRuntime.RagdollRenderState ragdoll,
                                    String partName, int light) {
        ItemStack helmet = armorStack(ragdoll, EquipmentSlot.HEAD, ragdoll.helmet());
        ItemStack chestplate = armorStack(ragdoll, EquipmentSlot.CHEST, ragdoll.chestplate());
        ItemStack leggings = armorStack(ragdoll, EquipmentSlot.LEGS, ragdoll.leggings());
        ItemStack boots = armorStack(ragdoll, EquipmentSlot.FEET, ragdoll.boots());
        switch (partName) {
            case "head" -> armorPiece(ragdoll, pose, buffers, helmet, false, partName, light);
            case "body" -> {
                armorPiece(ragdoll, pose, buffers, chestplate, false, partName, light);
                armorPiece(ragdoll, pose, buffers, leggings, true, partName, light);
            }
            case "left_arm", "right_arm" ->
                    armorPiece(ragdoll, pose, buffers, chestplate, false, partName, light);
            case "left_leg", "right_leg" -> {
                armorPiece(ragdoll, pose, buffers, leggings, true, partName, light);
                armorPiece(ragdoll, pose, buffers, boots, false, partName, light);
            }
            default -> {}
        }
    }

    private static boolean canRenderSkinLayers(
            Minecraft minecraft,
            ClientPhysicsRuntime.RagdollRenderState ragdoll,
            String partName) {
        if (!RagdollConfig.render3dSkin()
                || !net.neoforged.fml.ModList.get()
                .isLoaded("skinlayers3d")) {
            return false;
        }
        if (minecraft.level == null) {
            return false;
        }
        return RagdollAccessoryRenderer.replacesVanillaSkinLayer(
                partName, RagdollSkinPlayer.forRagdoll(minecraft, ragdoll), ragdoll.slim());
    }

    private static ItemStack armorStack(
            ClientPhysicsRuntime.RagdollRenderState ragdoll,
            EquipmentSlot slot,
            ItemStack fallback) {
        ItemStack override = RagdollAccessoryRenderer.cosmeticArmorOverride(
                ragdoll.accessories(), slot);
        return override == null ? fallback : override;
    }

    private static void armorPiece(ClientPhysicsRuntime.RagdollRenderState ragdoll,
                                   PoseStack pose, MultiBufferSource buffers, ItemStack stack,
                                   boolean inner, String partName, int light) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var corpse = RagdollSkinPlayer.forRagdoll(minecraft, ragdoll);
        try (var ignored = com.ragdoll.compat.RagdollAccessoryCapture.mirrorForRendering(
                corpse, ragdoll.accessories(), partName)) {
            RagdollArmorRenderer.render(corpse, stack, armor.getEquipmentSlot(),
                    partName, armorModel(inner), pose, buffers, light);
        } catch (Exception error) {
            com.ragdoll.compat.CompatibilityDiagnostics.failure("armor/snapshot", stack, error);
        }
    }

    private static HumanoidModel<?> armorModel(boolean inner) {
        if (innerArmor == null) {
            var roots = LayerDefinitions.createRoots();
            innerArmor = new HumanoidModel<>(roots.get(ModelLayers.PLAYER_INNER_ARMOR).bakeRoot());
            outerArmor = new HumanoidModel<>(roots.get(ModelLayers.PLAYER_OUTER_ARMOR).bakeRoot());
        }
        return inner ? innerArmor : outerArmor;
    }

    private static void renderHeldItem(Minecraft minecraft, PoseStack pose, MultiBufferSource buffers,
                                       ClientPhysicsRuntime.RagdollRenderState ragdoll,
                                       String partName, int light) {
        boolean left = "left_arm".equals(partName);
        if (!left && !"right_arm".equals(partName)) {
            return;
        }
        ItemStack stack = ragdoll.mainArmLeft() == left
                ? ragdoll.mainHand()
                : ragdoll.offHand();
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate((left ? -1 : 1) / 16.0f, -4.0f / 16.0f, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate((left ? -1 : 1) / 16.0f, 0.125f, -0.625f);
        minecraft.getItemRenderer().renderStatic(null, stack,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left, pose, buffers, minecraft.level, light, OverlayTexture.NO_OVERLAY,
                ragdoll.owner().hashCode());
        pose.popPose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderOptionalModLayers(Minecraft minecraft, PoseStack pose, MultiBufferSource buffers,
                                                ClientPhysicsRuntime.RagdollRenderState ragdoll,
                                                String partName, int light, float partialTick) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        AbstractClientPlayer player =
                RagdollSkinPlayer.forRagdoll(minecraft, ragdoll);
        renderOptionalModLayersForPlayer(minecraft, pose, buffers, ragdoll,
                partName, light, partialTick, player, false);
        if (RagdollConfig.render3dSkin() && minecraft.level != null
                && !com.ragdoll.compat.client.AdvancedAeArmorCompat.hidesSkin(ragdoll, partName, true)
                && net.neoforged.fml.ModList.get().isLoaded("skinlayers3d")) {
            renderOptionalModLayersForPlayer(minecraft, pose, buffers, ragdoll,
                    partName, light, partialTick,
                    RagdollSkinPlayer.forRagdoll(minecraft, ragdoll), true);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderOptionalModLayersForPlayer(Minecraft minecraft, PoseStack pose,
            MultiBufferSource buffers, ClientPhysicsRuntime.RagdollRenderState ragdoll,
            String partName, int light, float partialTick, AbstractClientPlayer player,
            boolean renderSkinLayers) {
        EntityRenderer<? super AbstractClientPlayer> renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)
                || !(playerRenderer instanceof LivingEntityRendererAccessor accessor)) {
            return;
        }
        PlayerModel model = playerRenderer.getModel();
        PartSelection selection = select(model, partName, ragdoll.slim());
        if (selection == null) {
            return;
        }
        AccessoryModelState modelState = AccessoryModelState.capture(model);
        ModelPart[] mainParts = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        float[] savedY = new float[mainParts.length];
        try {
            ((PlayerRendererAccessor) playerRenderer)
                    .ragdoll$setModelProperties(player);
            model.attackTime = 0.0f;
            model.riding = false;
            model.young = false;
            model.prepareMobModel(player, 0.0f, 0.0f, 0.0f);
            model.setupAnim(
                    player,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f);
            // Optional renderers often copy every limb, not just the visible
            // one. Remove held-item poses and idle rotations from all limbs.
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            model.crouching = false;
            model.swimAmount = 0;
            for (ModelPart modelPart : new ModelPart[]{model.head, model.hat,
                    model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg,
                    model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants}) {
                modelPart.setRotation(0, 0, 0);
            }
            for (int i = 0; i < mainParts.length; ++i) {
                savedY[i] = mainParts[i].y;
                if (mainParts[i] != selection.base()) {
                    mainParts[i].y += 10000.0f;
                    mainParts[i].visible = false;
                }
            }
            selection.base().visible = true;
            selection.base().setPos(
                    selection.x(), selection.y(), 0.0f);
            selection.base().setRotation(0.0f, 0.0f, 0.0f);
            RagdollAccessoryRenderer.render(
                    renderSkinLayers ? null : ragdoll.accessories(),
                    partName,
                    player,
                    playerRenderer,
                    pose,
                    buffers,
                    light,
                    partialTick,
                    renderSkinLayers);
            if (renderSkinLayers) {
                PlayerModel<?> vanillaModel = model(ragdoll.slim());
                PartSelection vanillaSelection =
                        select(vanillaModel, partName, ragdoll.slim());
                if (vanillaSelection == null) {
                    return;
                }
                vanillaSelection.outer().setPos(
                        vanillaSelection.x(),
                        vanillaSelection.y(),
                        0.0f);
                vanillaSelection.outer().setRotation(
                        0.0f, 0.0f, 0.0f);
                RagdollAccessoryRenderer.renderInjectedSkinLayer(
                        selection.outer(),
                        vanillaSelection.outer(),
                        player,
                        pose,
                        buffers,
                        light);
            }
        } finally {
            modelState.restore(model);
        }
    }

    private static void renderBack(PoseStack pose, MultiBufferSource buffers,
                                   ClientPhysicsRuntime.RagdollRenderState ragdoll, int light) {
        if (ragdoll.chestplate().is(Items.ELYTRA)
                || isMekanismHdpeElytra(ragdoll.chestplate())) {
            ResourceLocation texture = isMekanismHdpeElytra(ragdoll.chestplate())
                    ? ResourceLocation.fromNamespaceAndPath("mekanism", "textures/entity/hdpe_elytra.png")
                    : ragdoll.elytraTexture() != null ? ragdoll.elytraTexture()
                    : ragdoll.capeTexture() != null ? ragdoll.capeTexture()
                    : ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
            pose.pushPose();
            pose.translate(0, -6.0f / 16.0f, 0.125f);
            VertexConsumer buffer = net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(
                    buffers, RenderType.armorCutoutNoCull(texture), ragdoll.chestplate().hasFoil());
            renderVanillaElytra(pose, buffer, light);
            pose.popPose();
        } else if (RagdollConfig.renderCape()
                && ragdoll.capeTexture() != null
                && (ragdoll.shownModelParts() & PlayerModelPart.CAPE.getMask()) != 0) {
            pose.pushPose();
            pose.translate(0, -6.0f / 16.0f, 0.125f);
            pose.mulPose(Axis.XP.rotationDegrees(6.0f));
            pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            ModelPart cloak = getVanillaCloakModel();
            cloak.visible = true;
            cloak.setPos(0.0f, 0.0f, 0.0f);
            cloak.setRotation(0.0f, 0.0f, 0.0f);
            cloak.xScale = 1.0f;
            cloak.yScale = 1.0f;
            cloak.zScale = 1.0f;
            cloak.render(pose, buffers.getBuffer(RenderType.entityTranslucent(ragdoll.capeTexture())),
                    light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static boolean isMekanismHdpeElytra(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("mekanism") && id.getPath().equals("hdpe_elytra");
    }

    private static ModelPart getVanillaCloakModel() {
        if (vanillaCloakModel == null) {
            vanillaCloakModel = PlayerModel.createMesh(CubeDeformation.NONE, false)
                    .getRoot().bake(64, 64).getChild("cloak");
        }
        return vanillaCloakModel;
    }

    private static void ensureVanillaElytraModel() {
        if (vanillaLeftElytraWing != null) {
            return;
        }
        ModelPart root = ElytraModel.createLayer().bakeRoot();
        vanillaLeftElytraWing = root.getChild("left_wing");
        vanillaRightElytraWing = root.getChild("right_wing");
    }

    private static void renderVanillaElytra(PoseStack pose, VertexConsumer buffer, int light) {
        ensureVanillaElytraModel();
        resetElytraWing(vanillaLeftElytraWing, 5.0f, -(float) Math.PI / 12.0f);
        resetElytraWing(vanillaRightElytraWing, -5.0f, (float) Math.PI / 12.0f);
        vanillaLeftElytraWing.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        vanillaRightElytraWing.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    private static void resetElytraWing(ModelPart wing, float x, float zRotation) {
        wing.visible = true;
        wing.setPos(x, 0.0f, 0.0f);
        wing.setRotation((float) Math.PI / 12.0f, 0.0f, zRotation);
        wing.xScale = 1.0f;
        wing.yScale = 1.0f;
        wing.zScale = 1.0f;
    }
    private static double distanceToSqr(AABB bounds, Vec3 position) {
        double x = Math.max(Math.max(bounds.minX - position.x, 0), position.x - bounds.maxX);
        double y = Math.max(Math.max(bounds.minY - position.y, 0), position.y - bounds.maxY);
        double z = Math.max(Math.max(bounds.minZ - position.z, 0), position.z - bounds.maxZ);
        return x * x + y * y + z * z;
    }

    private record PartSelection(ModelPart base, ModelPart outer,
                                 PlayerModelPart toggle, float x, float y) {}

    private record ModelPartState(
            ModelPart part,
            PartPose pose,
            boolean visible,
            boolean skipDraw) {
        private static ModelPartState capture(ModelPart part) {
            return new ModelPartState(
                    part, part.storePose(), part.visible, part.skipDraw);
        }

        private void restore() {
            part.loadPose(pose);
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }

    private record AccessoryModelState(
            List<ModelPartState> parts,
            float attackTime,
            boolean riding,
            boolean young,
            boolean crouching,
            float swimAmount,
            HumanoidModel.ArmPose leftArmPose,
            HumanoidModel.ArmPose rightArmPose) {
        private static AccessoryModelState capture(PlayerModel<?> model) {
            return new AccessoryModelState(
                    List.of(
                            ModelPartState.capture(model.head),
                            ModelPartState.capture(model.hat),
                            ModelPartState.capture(model.body),
                            ModelPartState.capture(model.jacket),
                            ModelPartState.capture(model.leftArm),
                            ModelPartState.capture(model.leftSleeve),
                            ModelPartState.capture(model.rightArm),
                            ModelPartState.capture(model.rightSleeve),
                            ModelPartState.capture(model.leftLeg),
                            ModelPartState.capture(model.leftPants),
                            ModelPartState.capture(model.rightLeg),
                            ModelPartState.capture(model.rightPants)),
                    model.attackTime,
                    model.riding,
                    model.young,
                    model.crouching,
                    model.swimAmount,
                    model.leftArmPose,
                    model.rightArmPose);
        }

        private void restore(PlayerModel<?> model) {
            for (ModelPartState part : parts) {
                part.restore();
            }
            model.attackTime = attackTime;
            model.riding = riding;
            model.young = young;
            model.crouching = crouching;
            model.swimAmount = swimAmount;
            model.leftArmPose = leftArmPose;
            model.rightArmPose = rightArmPose;
        }
    }
}


