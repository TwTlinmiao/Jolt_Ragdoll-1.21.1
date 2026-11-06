package com.ragdoll.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraRagdollAccessor {
    @Invoker("setPosition")
    void ragdoll$setPosition(double x, double y, double z);

    @Invoker("setRotation")
    void ragdoll$setRotation(float yaw, float pitch, float roll);

    @Accessor("detached")
    void ragdoll$setDetached(boolean detached);
}
