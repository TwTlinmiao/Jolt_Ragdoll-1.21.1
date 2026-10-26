package com.ragdoll.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
import java.util.Set;

/** Only applied when Accessories is installed; signatures do not link its classes. */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.impl.AccessoriesHolderImpl", remap = false)
public interface AccessoriesHolderAccessor {
    @Accessor("slotContainers") Map<String, ?> ragdoll$getContainers();
    @Accessor("validSlotTypes") Set<String> ragdoll$getValidTypes();
    @Accessor("validSlotTypes") void ragdoll$setValidTypes(Set<String> types);
}
