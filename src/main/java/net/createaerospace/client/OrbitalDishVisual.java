package net.createaerospace.client;

import java.util.List;

import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createaerospace.CreateAerospace;
import net.createaerospace.relay.OrbitalDishEntity;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * The orbital dish as Flywheel instances: the tower, gimbal and reflector meshes go to the GPU once and each dish only
 * moves two transforms when it turns, instead of re-emitting every quad each frame through the block entity renderer.
 */
public final class OrbitalDishVisual extends AbstractBlockEntityVisual<OrbitalDishEntity> implements SimpleDynamicVisual, LightUpdatedVisual {
    private static final SimpleMaterial MATERIAL = SimpleMaterial.builder()
        .texture(CreateAerospace.id("textures/entity/plain.png"))
        .cutout(CutoutShaders.ONE_TENTH)
        .backfaceCulling(false)
        .build();
    private static final Model TOWER = model(OrbitalDishRenderer.TOWER);
    private static final Model GIMBAL = model(OrbitalDishRenderer.GIMBAL);
    private static final Model REFLECTOR = model(OrbitalDishRenderer.REFLECTOR);

    private final TransformedInstance tower, gimbal, reflector;
    private float bearing = Float.NaN, elevation = Float.NaN;

    public OrbitalDishVisual(VisualizationContext context, OrbitalDishEntity dish, float partialTick) {
        super(context, dish, partialTick);
        tower = instancerProvider().instancer(InstanceTypes.TRANSFORMED, TOWER).createInstance();
        gimbal = instancerProvider().instancer(InstanceTypes.TRANSFORMED, GIMBAL).createInstance();
        reflector = instancerProvider().instancer(InstanceTypes.TRANSFORMED, REFLECTOR).createInstance();
        tower.setIdentityTransform().translate(visualPos.getX() + .5f, visualPos.getY(), visualPos.getZ() + .5f).setChanged();
        aim();
    }

    public static void register() {
        SimpleBlockEntityVisualizer.builder(CreateAerospace.DISH_BE.get())
            .factory(OrbitalDishVisual::new)
            .skipVanillaRender(dish -> true)
            .apply();
    }

    @Override
    public void beginFrame(DynamicVisual.Context context) {
        aim();
    }

    /** Only touches the instances when the dish has turned. */
    private void aim() {
        float b = blockEntity.bearing(), e = blockEntity.elevation();
        if (b == bearing && e == elevation) return;
        bearing = b;
        elevation = e;
        gimbal.setIdentityTransform()
            .translate(visualPos.getX() + .5f, visualPos.getY() + OrbitalDishRenderer.PIVOT, visualPos.getZ() + .5f)
            .rotate(Axis.YP.rotationDegrees(-b));
        reflector.setTransform(gimbal.pose).rotate(Axis.XP.rotationDegrees(e));
        gimbal.setChanged();
        reflector.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(tower, gimbal, reflector);
    }

    @Override
    public void collectCrumblingInstances(java.util.function.Consumer<dev.engine_room.flywheel.api.instance.Instance> consumer) {
        consumer.accept(tower);
    }

    @Override
    protected void _delete() {
        tower.delete();
        gimbal.delete();
        reflector.delete();
    }

    private static Model model(List<OrbitalDishRenderer.Quad> quads) {
        return new SimpleModel(List.of(new Model.ConfiguredMesh(MATERIAL, new Mesh(quads))));
    }

    /** The renderer's quads, written once into Flywheel's vertex buffer. */
    private record Mesh(List<OrbitalDishRenderer.Quad> quads, Vector4fc boundingSphere) implements QuadMesh {
        Mesh(List<OrbitalDishRenderer.Quad> quads) { this(quads, sphere(quads)); }

        @Override
        public int vertexCount() { return quads.size() * 4; }

        @Override
        public void write(MutableVertexList out) {
            int i = 0;
            for (var quad : quads) {
                Vec3[] corners = {quad.a(), quad.b(), quad.c(), quad.d()};
                for (int k = 0; k < 4; k++, i++) {
                    out.x(i, (float) corners[k].x); out.y(i, (float) corners[k].y); out.z(i, (float) corners[k].z);
                    out.r(i, (quad.color() >> 16 & 255) / 255f); out.g(i, (quad.color() >> 8 & 255) / 255f); out.b(i, (quad.color() & 255) / 255f); out.a(i, 1);
                    out.u(i, k == 1 || k == 2 ? 1 : 0); out.v(i, k >= 2 ? 1 : 0);
                    out.overlay(i, OverlayTexture.NO_OVERLAY); out.light(i, 0);
                    out.normalX(i, (float) quad.normal().x); out.normalY(i, (float) quad.normal().y); out.normalZ(i, (float) quad.normal().z);
                }
            }
        }

        private static Vector4fc sphere(List<OrbitalDishRenderer.Quad> quads) {
            double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (var q : quads) for (Vec3 p : new Vec3[]{q.a(), q.b(), q.c(), q.d()}) {
                min[0] = Math.min(min[0], p.x); min[1] = Math.min(min[1], p.y); min[2] = Math.min(min[2], p.z);
                max[0] = Math.max(max[0], p.x); max[1] = Math.max(max[1], p.y); max[2] = Math.max(max[2], p.z);
            }
            float cx = (float) (min[0] + max[0]) / 2, cy = (float) (min[1] + max[1]) / 2, cz = (float) (min[2] + max[2]) / 2;
            float r = (float) Math.sqrt(Math.pow(max[0] - cx, 2) + Math.pow(max[1] - cy, 2) + Math.pow(max[2] - cz, 2));
            return new Vector4f(cx, cy, cz, r);
        }
    }
}
