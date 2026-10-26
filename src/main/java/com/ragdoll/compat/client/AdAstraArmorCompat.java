package com.ragdoll.compat.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;

/** Space suits keep the visor, belt and footwear outside the humanoid roots. */
final class AdAstraArmorCompat {
    private static final String MODEL = "earth.terrarium.adastra.client.models.armor.SpaceSuitModel";
    private static final ClassValue<List<Field>> EXTRA = new ClassValue<>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (!current.getName().equals(MODEL)) continue;
                List<Field> fields = new ArrayList<>();
                for (String name : List.of("visor", "belt", "rightBoot", "leftBoot")) {
                    try {
                        Field field = current.getDeclaredField(name);
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported Ad Astra space suit model", error);
                    }
                }
                return List.copyOf(fields);
            }
            return List.of();
        }
    };

    static List<ModelPart> extraParts(HumanoidModel<?> model) {
        return EXTRA.get(model.getClass()).stream().map(field -> get(field, model)).toList();
    }

    static boolean matches(HumanoidModel<?> model) {
        return !EXTRA.get(model.getClass()).isEmpty();
    }

    static void prepare(HumanoidModel<?> model, EquipmentSlot slot, String part) {
        var extra = extraParts(model);
        if (extra.isEmpty()) return;
        // The suit copies poses from the supplied parent during rendering.
        // Keep its extra roots visible only on their owning physical body part.
        extra.get(0).visible = slot == EquipmentSlot.HEAD && part.equals("head");
        extra.get(1).visible = slot == EquipmentSlot.LEGS && part.equals("body");
        extra.get(2).visible = slot == EquipmentSlot.FEET && part.equals("right_leg");
        extra.get(3).visible = slot == EquipmentSlot.FEET && part.equals("left_leg");
        if (slot == EquipmentSlot.FEET) {
            model.leftLeg.visible = model.rightLeg.visible = false;
        }
        if (slot == EquipmentSlot.LEGS) model.body.visible = false;
    }

    private static ModelPart get(Field field, Object model) {
        try { return (ModelPart) field.get(model); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
}
