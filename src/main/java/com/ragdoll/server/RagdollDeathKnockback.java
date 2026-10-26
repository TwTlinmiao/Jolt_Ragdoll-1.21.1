package com.ragdoll.server;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Initial ragdoll velocity contributed by the killing damage source. */
public final class RagdollDeathKnockback {
    private static final double HORIZONTAL_SPEED = 3.2;
    private static final double UPWARD_SPEED = 0.22;
    private static final double EXPLOSION_MIN_HORIZONTAL_SPEED = 4.0;
    private static final double EXPLOSION_MAX_HORIZONTAL_SPEED = 8.0;
    private static final double EXPLOSION_UPWARD_SPEED = 1.2;

    private RagdollDeathKnockback() {
    }

    public static Vec3 fromLastDamage(LivingEntity victim) {
        return fromSource(victim, victim.getLastDamageSource());
    }

    public static Vec3 fromSource(
            LivingEntity victim,
            DamageSource source) {
        if (source == null) {
            return Vec3.ZERO;
        }
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }
        Vec3 sourcePosition = explosion
                ? source.getSourcePosition()
                : attacker == null
                        ? source.getSourcePosition()
                        : attacker.position();
        if (sourcePosition == null) {
            return Vec3.ZERO;
        }
        Vec3 away = victim.position().subtract(sourcePosition);
        double horizontalLength = Math.sqrt(
                away.x * away.x + away.z * away.z);
        if (horizontalLength < 1.0e-5) {
            return Vec3.ZERO;
        }
        double horizontalSpeed = explosion
                ? Math.max(
                        EXPLOSION_MIN_HORIZONTAL_SPEED,
                        EXPLOSION_MAX_HORIZONTAL_SPEED
                                - horizontalLength * 0.8)
                : HORIZONTAL_SPEED;
        double upwardSpeed = explosion
                ? EXPLOSION_UPWARD_SPEED
                : UPWARD_SPEED;
        return new Vec3(
                away.x / horizontalLength * horizontalSpeed,
                upwardSpeed,
                away.z / horizontalLength * horizontalSpeed);
    }
}
