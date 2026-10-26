package com.ragdoll.mixin.client;

import com.ragdoll.compat.client.GeckoArmorCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoArmorRenderer", remap = false)
public abstract class GeoArmorRendererMixin {
    @Inject(method = "preRender", at = @At("RETURN"), remap = false)
    private void ragdoll$maskPhysicalPart(CallbackInfo ci) {
        GeckoArmorCompat.afterPreRender(this);
    }
}
