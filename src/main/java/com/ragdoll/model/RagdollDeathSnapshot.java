package com.ragdoll.model;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record RagdollDeathSnapshot(
        UUID ragdollId,
        UUID playerId,
        String playerName,
        float modelScale,
        String skinTextureValue,
        String skinTextureSignature,
        double x,
        double y,
        double z,
        float yaw,
        RagdollInitialPose initialPose,
        float xRot,
        float velocityX,
        float velocityY,
        float velocityZ,
        int shownModelParts,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        boolean mainArmLeft,
        ItemStack mainHand,
        ItemStack offHand,
        RagdollAccessorySnapshot accessories) {
    public static final float DEFAULT_MODEL_SCALE = 1.0f;
    public static final float MAX_MODEL_SCALE = 6.0f;

    public RagdollDeathSnapshot {
        playerName = playerName == null ? "" : playerName;
        modelScale = Float.isFinite(modelScale)
                ? Math.clamp(modelScale, 0.25f, MAX_MODEL_SCALE)
                : DEFAULT_MODEL_SCALE;
        skinTextureValue = skinTextureValue == null ? "" : skinTextureValue;
        skinTextureSignature = skinTextureSignature == null ? "" : skinTextureSignature;
        accessories = accessories == null
                ? RagdollAccessorySnapshot.EMPTY
                : accessories;
    }

    public RagdollDeathSnapshot(
            UUID ragdollId,
            UUID playerId,
            String playerName,
            float modelScale,
            String skinTextureValue,
            String skinTextureSignature,
            double x,
            double y,
            double z,
            float yaw,
            RagdollInitialPose initialPose,
            float xRot,
            float velocityX,
            float velocityY,
            float velocityZ,
            int shownModelParts,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            boolean mainArmLeft,
            ItemStack mainHand,
            ItemStack offHand) {
        this(
                ragdollId,
                playerId,
                playerName,
                modelScale,
                skinTextureValue,
                skinTextureSignature,
                x,
                y,
                z,
                yaw,
                initialPose,
                xRot,
                velocityX,
                velocityY,
                velocityZ,
                shownModelParts,
                helmet,
                chestplate,
                leggings,
                boots,
                mainArmLeft,
                mainHand,
                offHand,
                RagdollAccessorySnapshot.EMPTY);
    }

    public boolean hasSkinTexture() {
        return !skinTextureValue.isBlank();
    }

    public RagdollDeathSnapshot withInitialPose(RagdollInitialPose pose) {
        return new RagdollDeathSnapshot(ragdollId, playerId, playerName, modelScale,
                skinTextureValue, skinTextureSignature, x, y, z, yaw, pose, xRot,
                velocityX, velocityY, velocityZ, shownModelParts, helmet, chestplate,
                leggings, boots, mainArmLeft, mainHand, offHand, accessories);
    }

    public RagdollDeathSnapshot withTransform(double x, double y, double z,
                                              float yaw, float pitch) {
        return new RagdollDeathSnapshot(ragdollId, playerId, playerName, modelScale,
                skinTextureValue, skinTextureSignature, x, y, z, yaw, initialPose,
                pitch, velocityX, velocityY, velocityZ, shownModelParts, helmet,
                chestplate, leggings, boots, mainArmLeft, mainHand, offHand, accessories);
    }
}
