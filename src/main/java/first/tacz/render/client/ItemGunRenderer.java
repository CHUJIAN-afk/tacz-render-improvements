package first.tacz.render.client;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.block.DisplayGunGeometry;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.renderer.external.ExternalGunEffects;
import com.tacz.guns.client.renderer.item.DisplayItemRenderContext;
import com.tacz.guns.client.renderer.item.ItemPreviewRenderContext;
import com.tacz.guns.client.renderer.item.StaticGunRenderResult;
import com.tacz.guns.compat.iris.IrisCompat;
import first.tacz.render.TaczRenderImprovements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemGunRenderer {
    public static final ItemGunRenderer INSTANCE = new ItemGunRenderer();
    private final Map<Key, Model> models = new LinkedHashMap<>(16, 0.75f, true);
    public long modelBytes;
    public long uploads;
    public long submitted;

    public StaticGunRenderResult submit(ItemStack stack, ItemDisplayContext context, boolean useLod, PoseStack pose,
                                         MultiBufferSource source, int light, int overlay) {
        StaticGunRenderResult result = null;
        if (TaczRenderImprovements.CONFIG.optimizeItemGuns.get() && RenderPipeline.INSTANCE.available()
                && RenderPipeline.INSTANCE.owner(source) instanceof MultiBufferSource.BufferSource
                && !ItemPreviewRenderContext.isPreview() && !DisplayItemRenderContext.isActive()
                && (context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            ExternalGunAppearance.Resolution resolution = ExternalGunAppearance.resolve(stack, false);
            if (resolution.variants() != null) {
                ExternalGunAppearance.Snapshot appearance = resolution.variants().select(useLod || IrisCompat.isRenderShadow());
                Key key = new Key(appearance.key(), context);
                Model model = models.get(key);
                if (model == null) {
                    GeometryCollector collector = new GeometryCollector();
                    if (DisplayGunGeometry.capture(appearance, context, true, collector)) {
                        List<Part> parts = new ArrayList<>();
                        for (var entry : collector.snapshot().entrySet()) {
                            GeometryCompiler compiler = new GeometryCompiler(entry.getKey().mode().primitiveLength);
                            entry.getValue().emitTo(compiler);
                            GpuMesh mesh = new GpuMesh(compiler.finish());
                            parts.add(new Part(entry.getKey().material(), mesh));
                            modelBytes += mesh.bytes();
                            uploads++;
                        }
                        model = new Model(parts);
                        models.put(key, model);
                    }
                }
                boolean supported = model != null && !model.parts.isEmpty();
                if (supported) {
                    for (Part part : model.parts) supported &= !RenderPipeline.INSTANCE.layer(source, part.material).unsupported;
                }
                if (supported) {
                    model.lastUsed = RenderPipeline.INSTANCE.frame;
                    long identifiers = RenderPipeline.INSTANCE.identifiers();
                    for (Part part : model.parts) {
                        RenderPipeline.Layer layer = RenderPipeline.INSTANCE.layer(source, part.material);
                        ItemBatch batch = part.batches.get(layer);
                        if (batch == null) {
                            batch = new ItemBatch(part.mesh);
                            part.batches.put(layer, batch);
                        }
                        if (batch.poses.count == 0) layer.pending.add(batch);
                        batch.poses.put(batch.poses.count, pose.last(), pose.last().normal(), light, overlay, 1, 1, 1, 1, identifiers, false);
                        layer.mark(source);
                    }
                    submitted++;
                    if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                        ExternalGunEffects.renderThirdPerson(appearance, pose, source, light, overlay);
                    }
                    result = new StaticGunRenderResult(StaticGunRenderResult.Status.SUBMITTED, appearance.useLod());
                }
            }
        }
        return result;
    }

    public void invalidate() {
        for (Model model : models.values()) {
            for (Part part : model.parts) {
                part.mesh.close();
                for (ItemBatch batch : part.batches.values()) batch.poses.close();
            }
        }
        models.clear();
        modelBytes = 0;
    }

    public void trim(long budget) {
        var iterator = models.entrySet().iterator();
        while (modelBytes > budget && iterator.hasNext()) {
            Model model = iterator.next().getValue();
            if (model.lastUsed < RenderPipeline.INSTANCE.frame) {
                for (Part part : model.parts) {
                    modelBytes -= part.mesh.bytes();
                    part.mesh.close();
                    for (ItemBatch batch : part.batches.values()) batch.poses.close();
                }
                iterator.remove();
            }
        }
    }

    private record Key(ExternalGunAppearance.GeometryKey appearance, ItemDisplayContext context) {}
    private final class Model {
        private final List<Part> parts;
        private long lastUsed;

        private Model(List<Part> parts) {
            this.parts = parts;
        }
    }

    private final class Part {
        private final RenderType material;
        private final GpuMesh mesh;
        private final Map<RenderPipeline.Layer, ItemBatch> batches = new HashMap<>();

        private Part(RenderType material, GpuMesh mesh) {
            this.material = material;
            this.mesh = mesh;
        }
    }

    private final class ItemBatch implements RenderPipeline.Batch {
        private final GpuMesh mesh;
        private final PoseBuffer poses = new PoseBuffer(16);

        private ItemBatch(GpuMesh mesh) {
            this.mesh = mesh;
        }

        @Override
        public void draw() {
            RenderPipeline.INSTANCE.draw(mesh, mesh.indexBuffer, mesh.data.indices(), poses, 1);
        }

        @Override
        public void release() {
            poses.count = 0;
        }
    }
}
