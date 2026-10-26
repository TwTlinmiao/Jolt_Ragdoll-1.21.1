package earth.terrarium.adastra.client.models.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import java.util.List;
import java.util.Map;

/** Minimal fixture for the external suit's extra-root layout; never packaged. */
public final class SpaceSuitModel extends HumanoidModel<net.minecraft.world.entity.LivingEntity> {
    private final ModelPart visor = new ModelPart(List.of(), Map.of());
    private final ModelPart belt = new ModelPart(List.of(), Map.of());
    private final ModelPart rightBoot = new ModelPart(List.of(), Map.of());
    private final ModelPart leftBoot = new ModelPart(List.of(), Map.of());

    public SpaceSuitModel() {
        super(HumanoidModel.createMesh(CubeDeformation.NONE, 0).getRoot().bake(64, 64));
    }

    @Override public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);
        visor.visible = belt.visible = rightBoot.visible = leftBoot.visible = visible;
    }
}
