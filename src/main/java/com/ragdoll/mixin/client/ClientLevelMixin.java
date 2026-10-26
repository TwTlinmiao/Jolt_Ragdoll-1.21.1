package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void ragdoll$markTerrainDirty(
            BlockPos blockPos,
            BlockState oldState,
            BlockState newState,
            CallbackInfo callbackInfo) {
        ClientPhysicsRuntime.markTerrainDirty(
                (ClientLevel) (Object) this,
                blockPos);
    }
}
