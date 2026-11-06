package com.ragdoll.mixin.client;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Common-side access to vanilla explosion geometry. */
@Mixin(Explosion.class)
public interface ExplosionAccessor {
    @Accessor("x") double ragdoll$x();
    @Accessor("y") double ragdoll$y();
    @Accessor("z") double ragdoll$z();
    @Accessor("radius") float ragdoll$radius();
}
