package com.ragdoll.mixin.client;

import com.ragdoll.client.physics.ServerRagdollManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Common-side hook: server physics must observe committed block changes too. */
@Mixin(LevelChunk.class)
abstract class LevelChunkTerrainMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void ragdoll$invalidateServerTerrain(
            BlockPos pos, BlockState state, boolean moving,
            CallbackInfoReturnable<BlockState> callback) {
        // Null means no change was committed. Notify after the write so the
        // next physics tick sees the new collision shape, including air.
        if (callback.getReturnValue() != null
                && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level) {
            ServerRagdollManager.markTerrainDirty(level, pos);
        }
    }
}
