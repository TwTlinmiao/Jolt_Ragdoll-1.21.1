package com.ragdoll.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** An unregistered render context with skin/mesh state owned by one corpse. */
final class RagdollSkinPlayer extends RemotePlayer {
    private static final Cache<UUID, RagdollSkinPlayer> PLAYERS = CacheBuilder.newBuilder()
            .maximumSize(64).expireAfterAccess(30, TimeUnit.SECONDS).build();
    private PlayerSkin savedSkin;
    private int shownParts;
    private HumanoidArm savedMainArm = HumanoidArm.RIGHT;

    private RagdollSkinPlayer(ClientLevel level, UUID id) {
        super(level, new GameProfile(id, "Ragdoll"));
    }

    static RagdollSkinPlayer forRagdoll(Minecraft minecraft,
                                       ClientPhysicsRuntime.RagdollRenderState ragdoll) {
        RagdollSkinPlayer player = PLAYERS.getIfPresent(ragdoll.owner());
        if (player == null || player.level() != minecraft.level) {
            // A distinct UUID also isolates third-party animation caches from
            // both the living owner and other corpses of that owner.
            player = new RagdollSkinPlayer(minecraft.level, ragdoll.owner());
            PLAYERS.put(ragdoll.owner(), player);
        }
        player.savedSkin = new PlayerSkin(ragdoll.texture(), null, ragdoll.capeTexture(),
                ragdoll.elytraTexture(), ragdoll.slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false);
        player.shownParts = ragdoll.shownModelParts();
        player.setItemSlot(EquipmentSlot.HEAD, appearance(ragdoll, EquipmentSlot.HEAD, ragdoll.helmet()));
        player.setItemSlot(EquipmentSlot.CHEST, appearance(ragdoll, EquipmentSlot.CHEST, ragdoll.chestplate()));
        player.setItemSlot(EquipmentSlot.LEGS, appearance(ragdoll, EquipmentSlot.LEGS, ragdoll.leggings()));
        player.setItemSlot(EquipmentSlot.FEET, appearance(ragdoll, EquipmentSlot.FEET, ragdoll.boots()));
        player.setItemSlot(EquipmentSlot.MAINHAND, ragdoll.mainHand().copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, ragdoll.offHand().copy());
        player.savedMainArm = ragdoll.mainArmLeft() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        player.tickCount = 0;
        player.attackAnim = player.oAttackAnim = 0;
        player.swinging = false;
        player.stopUsingItem();
        if (!ragdoll.parts().isEmpty()) {
            var part = ragdoll.parts().getFirst();
            player.setPos(part.x(), part.y(), part.z());
        }
        player.xo = player.xOld = player.xCloakO = player.xCloak = player.getX();
        player.yo = player.yOld = player.yCloakO = player.yCloak = player.getY();
        player.zo = player.zOld = player.zCloakO = player.zCloak = player.getZ();
        player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 0;
        player.walkDist = player.walkDistO = player.bob = player.oBob = 0;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        return player;
    }

    private static net.minecraft.world.item.ItemStack appearance(
            ClientPhysicsRuntime.RagdollRenderState ragdoll, EquipmentSlot slot,
            net.minecraft.world.item.ItemStack fallback) {
        var cosmetic = com.ragdoll.compat.client.RagdollAccessoryRenderer.cosmeticArmorOverride(ragdoll.accessories(), slot);
        return (cosmetic == null ? fallback : cosmetic).copy();
    }

    @Override
    public HumanoidArm getMainArm() {
        return savedMainArm;
    }

    @Override
    public PlayerSkin getSkin() {
        return savedSkin != null ? savedSkin : super.getSkin();
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return (shownParts & part.getMask()) != 0;
    }
}
