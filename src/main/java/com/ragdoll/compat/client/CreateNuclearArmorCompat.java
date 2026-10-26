package com.ragdoll.compat.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;

import java.lang.reflect.Field;
import java.util.List;

/** Isolates Create: Nuclear's custom armor roots for per-limb ragdoll rendering. */
final class CreateNuclearArmorCompat {
    private static final String MODEL =
            "net.nuclearteam.createnuclear.content.equipment.armor.AntiRadiationArmorModel";
    private static final List<String> PARTS = List.of(
            "head", "body", "right_arm", "left_arm", "right_leg", "left_leg",
            "right_boot", "left_boot");

    private CreateNuclearArmorCompat() {}

    static boolean prepare(HumanoidModel<?> model, EquipmentSlot slot, String part) {
        if (!model.getClass().getName().equals(MODEL)) return false;
        String selected = switch (part) {
            case "head" -> "head";
            case "body" -> "body";
            case "left_arm" -> "left_arm";
            case "right_arm" -> "right_arm";
            case "left_leg" -> slot == EquipmentSlot.FEET ? "left_boot" : "left_leg";
            case "right_leg" -> slot == EquipmentSlot.FEET ? "right_boot" : "right_leg";
            default -> null;
        };
        if (selected == null) return true;

        for (String name : PARTS) {
            ModelPart root = field(model, name);
            root.visible = name.equals(selected);
        }
        ModelPart selectedPart = field(model, selected);
        selectedPart.setPos(
                part.equals("left_arm") ? -1 : part.equals("right_arm") ? 1 : 0,
                part.equals("head") ? 4 : part.endsWith("arm") ? -4 : -6,
                0);
        return true;
    }

    private static ModelPart field(Object model, String name) {
        try {
            Field field = model.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (ModelPart) field.get(model);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw new IllegalStateException(
                    "Unsupported Create: Nuclear armor model field " + name, error);
        }
    }
}
