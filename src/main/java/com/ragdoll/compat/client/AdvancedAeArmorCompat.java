package com.ragdoll.compat.client;

import com.ragdoll.compat.CompatibilityDiagnostics;
import com.ragdoll.client.physics.ClientPhysicsRuntime.RagdollRenderState;
import java.lang.reflect.Method;
import net.minecraft.world.item.ItemStack;

/** Mirrors Advanced AE's player skin masking, using the corpse's equipment and camouflage. */
public final class AdvancedAeArmorCompat {
    private static final String ARMORS = "net.pedroksl.advanced_ae.common.items.armors.";
    private record Visibility(Method method) {}
    private static final ClassValue<Visibility> VISIBILITY = new ClassValue<>() {
        protected Visibility computeValue(Class<?> type) {
            try {
                return new Visibility(type.getMethod("isVisible", ItemStack.class));
            } catch (NoSuchMethodException error) {
                return new Visibility(null);
            }
        }
    };

    private AdvancedAeArmorCompat() {}

    public static boolean hidesSkin(RagdollRenderState ragdoll, String part, boolean outer) {
        return hidesSkin(part, outer, visible(ragdoll.helmet(), "QuantumHelmet"),
                visible(ragdoll.chestplate(), "QuantumChestplate"),
                visible(ragdoll.leggings(), "QuantumLeggings"), visible(ragdoll.boots(), "QuantumBoots"));
    }

    static boolean hidesSkin(String part, boolean outer, boolean helmet, boolean chest,
                             boolean leggings, boolean boots) {
        return switch (part) {
            case "head" -> outer && helmet;
            case "body" -> outer && chest;
            case "left_arm", "right_arm" -> chest;
            case "left_leg", "right_leg" -> leggings && (outer || boots);
            default -> false;
        };
    }

    private static boolean visible(ItemStack stack, String itemType) {
        if (stack.isEmpty()) return false;
        Class<?> type = stack.getItem().getClass();
        boolean matches = false;
        for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            if (parent.getName().equals(ARMORS + itemType)) { matches = true; break; }
        }
        if (!matches) return false;
        Method method = VISIBILITY.get(type).method();
        if (method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(stack.getItem(), stack));
        } catch (ReflectiveOperationException | RuntimeException error) {
            CompatibilityDiagnostics.failure("advanced_ae/visibility", stack, error);
            return false;
        }
    }
}
