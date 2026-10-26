package com.ragdoll.mixin.client;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityFallFlyingAccessor {
    @Invoker("setSharedFlag")
    void ragdoll$setSharedFlag(int flag, boolean value);
}
