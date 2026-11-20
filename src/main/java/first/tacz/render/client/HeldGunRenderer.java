package first.tacz.render.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.UnshadedVertexConsumer;
import first.tacz.render.TaczRenderImprovements;
import first.tacz.render.mixin.RenderStateShardAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import static org.lwjgl.opengl.GL15.*;

public final class HeldGunRenderer {
    public static final HeldGunRenderer INSTANCE = new HeldGunRenderer();
    private final Map<BedrockModel, Layout> models = new IdentityHashMap<>();
    private final String path = System.getProperty("tacz_render_improvements.heldPath", "partial");
    private final boolean rigid = !path.equals("baseline");
    public final boolean partial = path.equals("partial");
    public long modelBytes;
    public long uploads;
    public long bones;
    public long discardedFaces;

    public VertexConsumer bind(BedrockModel model, MultiBufferSource source, RenderType type, ItemDisplayContext context) {
        if (context.firstPerson() && TaczRenderImprovements.CONFIG.optimizeHeldGuns.get()
                && RenderPipeline.INSTANCE.available()
                && source instanceof MultiBufferSource.BufferSource && type.format() == DefaultVertexFormat.NEW_ENTITY
                && type.mode() == VertexFormat.Mode.QUADS
                && ((RenderStateShardAccessor) type).tacz_render_improvements$name().startsWith("entity_cutout")) {
            RenderPipeline.Layer layer = RenderPipeline.INSTANCE.layer(source, type);
            if (!layer.unsupported) {
                Layout layout = models.computeIfAbsent(model, Layout::new);
                layout.lastUsed = RenderPipeline.INSTANCE.frame;
                if (layout.prepare()) {
                    layer.mark(source);
                    HeldBatch batch = layout.acquire(layer);
                    return new TrackedConsumer(source, layer.consumer, batch);
                }
            }
        }
        return source.getBuffer(type);
    }

    public boolean submit(BedrockPart part, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
                          float red, float green, float blue, float alpha) {
        UnshadedVertexConsumer wrapper = consumer instanceof UnshadedVertexConsumer value ? value : null;
        if (wrapper != null) consumer = wrapper.getDelegate();
        boolean captured = false;
        if (consumer instanceof TrackedConsumer tracked) {
            HeldBatch batch = tracked.activeBatch();
            Integer slot = batch.layout.slots.get(part);
            Integer poseSlot = batch.layout.poseSlots.get(part);
            if (poseSlot != null && batch.written.get(poseSlot) || slot != null && batch.visible.get(slot)) {
                batch = batch.layout.acquire(batch.layer);
                tracked.batch = batch;
            }
            if (slot != null) {
                batch.poses.put(poseSlot, pose, wrapper == null ? pose.normal() : wrapper.createNormalMatrix(),
                        light, overlay, red, green, blue, alpha, batch.identifiers, wrapper != null);
                batch.written.set(poseSlot);
                batch.visible.set(slot);
                batch.order.add((int) slot);
                bones++;
                captured = true;
            } else if (part.cubes.isEmpty()) {
                if (poseSlot != null) {
                    batch.poses.put(poseSlot, pose, wrapper == null ? pose.normal() : wrapper.createNormalMatrix(),
                            light, overlay, red, green, blue, alpha, batch.identifiers, wrapper != null);
                    batch.written.set(poseSlot);
                }
                captured = true;
            }
        }
        return captured;
    }

    public boolean submitRigid(BedrockPart part, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
                               float red, float green, float blue, float alpha) {
        boolean captured = false;
        UnshadedVertexConsumer wrapper = consumer instanceof UnshadedVertexConsumer value ? value : null;
        if (wrapper != null) consumer = wrapper.getDelegate();
        if (consumer instanceof TrackedConsumer tracked) {
            HeldBatch batch = tracked.activeBatch();
            Leaf leaf = batch.layout.leaves.get(part);
            if (leaf != null) {
                if (part.visible) {
                    int slot = batch.layout.slots.get(part);
                    if (batch.visible.get(slot)) {
                        batch = batch.layout.acquire(batch.layer);
                        tracked.batch = batch;
                    }
                    int poseSlot = batch.layout.poseSlots.get(leaf.parent);
                    if (!batch.written.get(poseSlot)) {
                        batch.poses.put(poseSlot, pose, wrapper == null ? pose.normal() : wrapper.createNormalMatrix(),
                                light, overlay, red, green, blue, alpha, batch.identifiers, wrapper != null);
                        batch.written.set(poseSlot);
                        bones++;
                    }
                    batch.visible.set(slot);
                    batch.order.add(slot);
                }
                captured = true;
            }
        }
        return captured;
    }

    public void invalidate() {
        for (Layout layout : models.values()) layout.close();
        models.clear();
        modelBytes = 0;
    }

    public void trim(long budget) {
        while (modelBytes > budget) {
            var oldest = models.entrySet().stream().filter(entry -> entry.getValue().lastUsed < RenderPipeline.INSTANCE.frame)
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().lastUsed)).orElse(null);
            if (oldest == null) break;
            oldest.getValue().close();
            models.remove(oldest.getKey());
        }
    }

    private final class Layout {
        private final Map<BedrockPart, Integer> slots = new IdentityHashMap<>();
        private final Map<BedrockPart, Integer> poseSlots = new IdentityHashMap<>();
        private final Map<BedrockPart, Leaf> leaves = new IdentityHashMap<>();
        private final List<Leaf> leafChecks = new ArrayList<>();
        private final List<Integer> starts = new ArrayList<>();
        private final List<Integer> counts = new ArrayList<>();
        private final LinkedHashMap<IntArrayList, Indices> visibleIndices = new LinkedHashMap<>(16, 0.75f, true);
        private final ArrayDeque<HeldBatch> pool = new ArrayDeque<>();
        private final CompletableFuture<GeometryCompiler.Data> compilation;
        private GpuMesh mesh;
        private long lastUsed;

        private Layout(BedrockModel model) {
            List<BedrockPart> roots = new ArrayList<>(model.getShouldRender());
            compilation = CompletableFuture.supplyAsync(() -> {
                GeometryCompiler compiler = new GeometryCompiler(4);
                PoseStack.Pose identity = new PoseStack().last();
                ArrayDeque<BedrockPart> pending = new ArrayDeque<>(roots);
                Map<BedrockPart, BedrockPart> parents = new IdentityHashMap<>();
                while (!pending.isEmpty()) {
                    BedrockPart part = pending.removeFirst();
                    pending.addAll(part.children);
                    for (BedrockPart child : part.children) parents.put(child, part);
                    if (!part.cubes.isEmpty() && !slots.containsKey(part)) {
                        slots.put(part, slots.size());
                        BedrockPart owner = part;
                        PoseStack.Pose local = identity;
                        if (rigid && part.name == null && parents.containsKey(part) && part.getClass() == BedrockPart.class
                                && part.children.isEmpty() && !part.illuminated
                                && part.xScale == 1 && part.yScale == 1 && part.zScale == 1) {
                            owner = parents.get(part);
                            Leaf leaf = new Leaf(part, owner);
                            leaves.put(part, leaf);
                            leafChecks.add(leaf);
                            local = leaf.pose;
                        }
                        compiler.bone = poseSlots.computeIfAbsent(owner, ignored -> poseSlots.size());
                        int start = compiler.indexCount();
                        starts.add(start);
                        for (var cube : part.cubes) cube.compile(local, compiler, 0, 0, 1, 1, 1, 1);
                        counts.add(compiler.indexCount() - start);
                    }
                }
                return compiler.finish();
            }, net.minecraft.Util.backgroundExecutor());
        }

        private boolean prepare() {
            if (mesh == null && compilation.isDone()) {
                GeometryCompiler.Data data = compilation.join();
                mesh = new GpuMesh(data);
                modelBytes += mesh.bytes();
                discardedFaces += data.discardedFaces();
                uploads++;
            }
            boolean valid = mesh != null;
            if (valid) {
                for (int i = 0; i < leafChecks.size(); i++) {
                    if (!leafChecks.get(i).matches()) {
                        valid = false;
                        break;
                    }
                }
            }
            return valid;
        }

        private HeldBatch acquire(RenderPipeline.Layer layer) {
            HeldBatch batch = pool.pollFirst();
            if (batch == null) batch = new HeldBatch(this);
            batch.layer = layer;
            batch.active = true;
            batch.identifiers = RenderPipeline.INSTANCE.identifiers();
            layer.pending.add(batch);
            return batch;
        }

        private void close() {
            compilation.cancel(false);
            if (mesh != null) {
                modelBytes -= mesh.bytes();
                mesh.close();
            }
            for (Indices indices : visibleIndices.values()) {
                glDeleteBuffers(indices.buffer);
                modelBytes -= (long) indices.values.length * 4;
            }
            visibleIndices.clear();
            for (HeldBatch batch : pool) batch.poses.close();
            pool.clear();
        }
    }

    private final class HeldBatch implements RenderPipeline.Batch {
        private final Layout layout;
        private final PoseBuffer poses;
        private final BitSet visible = new BitSet();
        private final BitSet written = new BitSet();
        private final IntArrayList order = new IntArrayList();
        private RenderPipeline.Layer layer;
        private long identifiers;
        private boolean active;

        private HeldBatch(Layout layout) {
            this.layout = layout;
            poses = new PoseBuffer(layout.poseSlots.size());
        }

        @Override
        public void draw() {
            if (!visible.isEmpty()) {
                Indices selected = layout.visibleIndices.get(order);
                if (selected == null) {
                    int size = 0;
                    for (int i = 0; i < order.size(); i++) size += layout.counts.get(order.getInt(i));
                    int[] indices = new int[size];
                    int at = 0;
                    for (int i = 0; i < order.size(); i++) {
                        int part = order.getInt(i);
                        int count = layout.counts.get(part);
                        System.arraycopy(layout.mesh.data.indices(), layout.starts.get(part), indices, at, count);
                        at += count;
                    }
                    selected = new Indices(layout.mesh.uploadIndices(indices), indices);
                    layout.visibleIndices.put(order.clone(), selected);
                    modelBytes += (long) indices.length * 4;
                    if (layout.visibleIndices.size() > 32) {
                        var iterator = layout.visibleIndices.entrySet().iterator();
                        Indices removed = iterator.next().getValue();
                        glDeleteBuffers(removed.buffer);
                        modelBytes -= (long) removed.values.length * 4;
                        iterator.remove();
                    }
                }
                RenderPipeline.INSTANCE.draw(layout.mesh, selected.buffer, selected.values, poses, 0);
            }
        }

        @Override
        public void release() {
            visible.clear();
            written.clear();
            order.clear();
            poses.count = 0;
            active = false;
            layout.pool.addLast(this);
        }
    }

    private record Indices(int buffer, int[] values) {}

    private static final class Leaf {
        private final BedrockPart parent;
        private final BedrockPart part;
        private final PoseStack.Pose pose;
        private final float x, y, z, rx, ry, rz, ox, oy, oz, qx, qy, qz, qw;

        private Leaf(BedrockPart part, BedrockPart parent) {
            this.parent = parent;
            this.part = part;
            x = part.x; y = part.y; z = part.z;
            rx = part.xRot; ry = part.yRot; rz = part.zRot;
            ox = part.offsetX; oy = part.offsetY; oz = part.offsetZ;
            qx = part.additionalQuaternion.x; qy = part.additionalQuaternion.y;
            qz = part.additionalQuaternion.z; qw = part.additionalQuaternion.w;
            PoseStack stack = new PoseStack();
            part.translateAndRotateAndScale(stack);
            pose = stack.last();
        }

        private boolean matches() {
            return part.x == x && part.y == y && part.z == z
                    && part.xRot == rx && part.yRot == ry && part.zRot == rz
                    && part.offsetX == ox && part.offsetY == oy && part.offsetZ == oz
                    && part.additionalQuaternion.x == qx && part.additionalQuaternion.y == qy
                    && part.additionalQuaternion.z == qz && part.additionalQuaternion.w == qw
                    && part.xScale == 1 && part.yScale == 1 && part.zScale == 1
                    && !part.illuminated && part.children.isEmpty();
        }
    }

    private final class TrackedConsumer implements VertexConsumer {
        private final MultiBufferSource source;
        private VertexConsumer delegate;
        private HeldBatch batch;

        private TrackedConsumer(MultiBufferSource source, VertexConsumer delegate, HeldBatch batch) {
            this.source = source;
            this.delegate = delegate;
            this.batch = batch;
        }

        private HeldBatch activeBatch() {
            if (!batch.active) {
                batch.layer.mark(source);
                delegate = batch.layer.consumer;
                batch = batch.layout.acquire(batch.layer);
            }
            return batch;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { activeBatch(); delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
