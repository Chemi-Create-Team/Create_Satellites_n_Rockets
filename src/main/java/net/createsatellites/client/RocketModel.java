package net.createsatellites.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createsatellites.entity.RocketEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Body, payload fairing, nose, four fins and a nozzle. The 128x128 texture holds each box unwrapped at its texOffs: body (0,0), fairing (0,64), nose (48,64), the two fin shapes (64,0) and (64,20), nozzle (64,44). */
public class RocketModel extends EntityModel<RocketEntity> {
    private final ModelPart root;

    public RocketModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition p = mesh.getRoot();
        PartPose feet = PartPose.offset(0, 24, 0);
        p.addOrReplaceChild("nozzle", CubeListBuilder.create().texOffs(64, 44).addBox(-3, -5, -3, 6, 5, 6), feet);
        p.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -53, -6, 12, 48, 12), feet);
        p.addOrReplaceChild("fairing", CubeListBuilder.create().texOffs(0, 64).addBox(-5, -69, -5, 10, 16, 10), feet);
        p.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(48, 64).addBox(-3, -79, -3, 6, 10, 6), feet);
        p.addOrReplaceChild("fin_east", CubeListBuilder.create().texOffs(64, 0).addBox(6, -19, -1, 6, 14, 2), feet);
        p.addOrReplaceChild("fin_west", CubeListBuilder.create().texOffs(64, 0).addBox(-12, -19, -1, 6, 14, 2), feet);
        p.addOrReplaceChild("fin_south", CubeListBuilder.create().texOffs(64, 20).addBox(-1, -19, 6, 2, 14, 6), feet);
        p.addOrReplaceChild("fin_north", CubeListBuilder.create().texOffs(64, 20).addBox(-1, -19, -12, 2, 14, 6), feet);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(RocketEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {}

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        root.render(ps, vc, light, overlay, color);
    }
}
