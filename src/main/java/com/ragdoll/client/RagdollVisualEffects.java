package com.ragdoll.client;

import com.ragdoll.client.physics.ClientPhysicsRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Visual suppression only; never changes effect durations, damage or physics. */
public final class RagdollVisualEffects {
    private RagdollVisualEffects() {}

    public static boolean hidden(Entity entity) {
        return entity instanceof Player
                && ClientPhysicsRuntime.isControlledPlayer(entity.getUUID());
    }

    public static boolean localHidden() {
        return hidden(Minecraft.getInstance().player);
    }
}
