package com.ragdoll.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Physics Gun item adapted from Velthoric's VxPhysicsGunItem.
 */
public final class PhysicsGunItem extends Item {

    public PhysicsGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        return InteractionResultHolder.pass(
                player.getItemInHand(hand));
    }
}
