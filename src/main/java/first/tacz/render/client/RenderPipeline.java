package first.tacz.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.resource.ClientIndexManager;
import first.tacz.render.mixin.ShaderInstanceAccessor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL43.*;

public final class RenderPipeline {
    public static final RenderPipeline INSTANCE = new RenderPipeline();
    public final ShaderBridge shaders = new ShaderBridge();
    private final Map<MultiBufferSource, Map<RenderType, Layer>> layers = new IdentityHashMap<>();
    private final ArrayDeque<MultiBufferSource> entitySources = new ArrayDeque<>();
    private long revision = -1;
    private Object pipeline;
    private Object captured;
    private Object pipelineManager;
    private Method pipelineGetter, getEntity, getBlock, getItem;
    private boolean irisChecked;
    private int poseBuffer;
    private Layer drawingLayer;
    public long uploadedBytes;
    public long heldDraws;
    public long itemDraws;
    public long instances;
    public long fallbackDraws;
    public long frame;

    public boolean available() {
        return GL.getCapabilities().OpenGL43;
    }

    public void beginFrame() {
        frame++;
        var target = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        if (available() && !target.isStencilEnabled()) {
            target.enableStencil();
        }
        if (!irisChecked) {
            irisChecked = true;
            for (String namespace : List.of("net.irisshaders.iris.", "net.coderbot.iris.")) {
                try {
                    Class<?> iris = Class.forName(namespace + "Iris");
                    pipelineManager = iris.getMethod("getPipelineManager").invoke(null);
                    pipelineGetter = pipelineManager.getClass().getMethod("getPipelineNullable");
                    Class<?> state = Class.forName(namespace + "uniforms.CapturedRenderingState");
                    captured = state.getField("INSTANCE").get(null);
                    getEntity = state.getMethod("getCurrentRenderedEntity");
                    getBlock = state.getMethod("getCurrentRenderedBlockEntity");
                    getItem = state.getMethod("getCurrentRenderedItem");
                    break;
                } catch (ClassNotFoundException ignored) {
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot initialize Oculus bridge", exception);
                }
            }
        }
        Object current = null;
        if (pipelineGetter != null) {
            try {
                current = pipelineGetter.invoke(pipelineManager);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
        long currentRevision = ClientIndexManager.resourceRevision();
        if (revision != currentRevision || pipeline != current) {
            invalidate();
            revision = currentRevision;
            pipeline = current;
        }
    }

    public long identifiers() {
        long result = 0;
        if (captured != null && pipeline != null) {
            try {
                result = ((int) getEntity.invoke(captured) & 65535L)
                        | ((int) getBlock.invoke(captured) & 65535L) << 16
                        | ((int) getItem.invoke(captured) & 65535L) << 32;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
        return result;
    }

    public void endFrame() {
        long budget = first.tacz.render.TaczRenderImprovements.CONFIG.meshBudgetMiB.get() * 1024L * 1024;
        if (HeldGunRenderer.INSTANCE.modelBytes + ItemGunRenderer.INSTANCE.modelBytes > budget) {
            HeldGunRenderer.INSTANCE.trim(Math.max(0, budget - ItemGunRenderer.INSTANCE.modelBytes));
            ItemGunRenderer.INSTANCE.trim(Math.max(0, budget - HeldGunRenderer.INSTANCE.modelBytes));
        }
    }

    public Layer layer(MultiBufferSource source, RenderType material) {
        return layers.computeIfAbsent(owner(source), ignored -> new IdentityHashMap<>())
                .computeIfAbsent(material, ignored -> new Layer(material));
    }

    public MultiBufferSource owner(MultiBufferSource source) {
        return source instanceof MultiBufferSource.BufferSource || entitySources.isEmpty() ? source : entitySources.peekLast();
    }

    public void enterEntity(MultiBufferSource source) {
        entitySources.addLast(owner(source));
    }

    public void leaveEntity() {
        entitySources.removeLast();
    }

    public void endBatch(MultiBufferSource source, RenderType material) {
        Map<RenderType, Layer> materials = layers.get(source);
        Layer layer = materials == null ? null : materials.get(material);
        RenderType target = layer == null || layer.pending.isEmpty() ? material : layer;
        if (source instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch(target == null ? material : target);
        }
    }

    public void draw(GpuMesh mesh, int indices, int[] drawIndices, PoseBuffer poses, int mode) {
        ShaderInstance source = RenderSystem.getShader();
        ShaderBridge.Program program = source == null ? null : shaders.get(source, mode);
        if (program != null && drawIndices.length > 0 && poses.count > 0) {
            int previousArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int previousArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int previousBuffer = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
            int previousBinding = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, program.binding);
            long previousOffset = previousBinding == 0 ? 0 : glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, program.binding);
            long previousLength = previousBinding == 0 ? 0 : glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, program.binding);
            if (poseBuffer == 0) poseBuffer = glGenBuffers();
            try {
                glUseProgram(source.getId());
                program.apply(source);
                poses.data.position(0).limit(poses.count * 176);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, poseBuffer);
                glBufferData(GL_SHADER_STORAGE_BUFFER, poses.data, GL_STREAM_DRAW);
                poses.data.clear();
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, program.binding, poseBuffer);
                mesh.bind(program, indices);
                glDrawElementsInstanced(GL_TRIANGLES, drawIndices.length, GL_UNSIGNED_INT, 0, mode == 0 ? 1 : poses.count);
                uploadedBytes += (long) poses.count * 176;
                if (mode == 0) {
                    heldDraws++;
                } else {
                    itemDraws++;
                    instances += poses.count;
                }
            } finally {
                if (previousBinding != 0 && previousLength > 0) {
                    glBindBufferRange(GL_SHADER_STORAGE_BUFFER, program.binding, previousBinding, previousOffset, previousLength);
                } else {
                    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, program.binding, previousBinding);
                }
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, previousBuffer);
                glBindVertexArray(previousArray);
                glBindBuffer(GL_ARRAY_BUFFER, previousArrayBuffer);
                glUseProgram(source.getId());
                ShaderInstanceAccessor.tacz_render_improvements$program(-1);
                source.clear();
            }
        } else if (drawIndices.length > 0 && poses.count > 0) {
            if (drawingLayer != null) drawingLayer.unsupported = true;
            fallback(mesh, drawIndices, poses, mode);
        }
    }

    private void fallback(GpuMesh mesh, int[] drawIndices, PoseBuffer poses, int mode) {
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(262144)) {
            BufferBuilder builder = new BufferBuilder(buffer, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
            Matrix4f matrix = new Matrix4f();
            Vector3f position = new Vector3f();
            for (int instance = 0; instance < (mode == 0 ? 1 : poses.count); instance++) {
                for (int vertex : drawIndices) {
                    int at = vertex * 64;
                    int bone = mode == 0 ? mesh.data.vertices().getInt(at + 56) : instance;
                    int pose = bone * 176;
                    matrix.set(pose, poses.data);
                    position.set(mesh.data.vertices().getFloat(at), mesh.data.vertices().getFloat(at + 4), mesh.data.vertices().getFloat(at + 8)).mulPosition(matrix);
                    int color = mesh.data.vertices().getInt(at + 12);
                    float nx = mesh.data.vertices().get(at + 32) / 127f;
                    float ny = mesh.data.vertices().get(at + 33) / 127f;
                    float nz = mesh.data.vertices().get(at + 34) / 127f;
                    int light = poses.data.getInt(pose + 144) | poses.data.getInt(pose + 148) << 16;
                    int overlay = poses.data.getInt(pose + 152) | poses.data.getInt(pose + 156) << 16;
                    builder.addVertex(position.x, position.y, position.z)
                            .setColor((int) ((color & 255) * poses.data.getFloat(pose + 128)),
                            (int) ((color >>> 8 & 255) * poses.data.getFloat(pose + 132)),
                            (int) ((color >>> 16 & 255) * poses.data.getFloat(pose + 136)),
                            (int) ((color >>> 24) * poses.data.getFloat(pose + 140)))
                            .setUv(mesh.data.vertices().getFloat(at + 16), mesh.data.vertices().getFloat(at + 20)).setOverlay(overlay).setLight(light).setNormal(
                            nx * poses.data.getFloat(pose + 64) + ny * poses.data.getFloat(pose + 80) + nz * poses.data.getFloat(pose + 96),
                            nx * poses.data.getFloat(pose + 68) + ny * poses.data.getFloat(pose + 84) + nz * poses.data.getFloat(pose + 100),
                            nx * poses.data.getFloat(pose + 72) + ny * poses.data.getFloat(pose + 88) + nz * poses.data.getFloat(pose + 104));
                }
            }
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }
        fallbackDraws++;
    }

    public void invalidate() {
        for (var materials : layers.values()) {
            for (Layer layer : materials.values()) layer.release();
        }
        layers.clear();
        HeldGunRenderer.INSTANCE.invalidate();
        ItemGunRenderer.INSTANCE.invalidate();
        shaders.invalidate();
        if (poseBuffer != 0) {
            glDeleteBuffers(poseBuffer);
            poseBuffer = 0;
        }
    }

    public interface Batch {
        void draw();
        void release();
    }

    public final class Layer extends RenderType {
        public final RenderType material;
        public final List<Batch> pending = new ArrayList<>();
        public VertexConsumer consumer;
        private boolean marked;
        public boolean unsupported;

        private Layer(RenderType material) {
            super("tacz_render_improvements:" + material, material.format(), material.mode(), material.bufferSize(),
                    material.affectsCrumbling(), false, material::setupRenderState, material::clearRenderState);
            this.material = material;
            if (captured != null) {
                String prefix = captured.getClass().getName().startsWith("net.coderbot") ? "net.coderbot" : "net.irisshaders";
                try {
                    Class<?> holder = Class.forName(prefix + ".batchedentityrendering.impl.BlendingStateHolder");
                    Class<?> transparency = Class.forName(prefix + ".batchedentityrendering.impl.TransparencyType");
                    holder.getMethod("setTransparencyType", transparency).invoke(this, holder.getMethod("getTransparencyType").invoke(material));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }

        public void mark(MultiBufferSource source) {
            consumer = source.getBuffer(this);
            if (!marked) {
                marked = true;
                for (int i = 0; i < material.mode().primitiveLength; i++) {
                    consumer.addVertex(0, 0, 0).setColor(255, 255, 255, 0).setUv(0, 0)
                            .setOverlay(655360).setLight(0).setNormal(0, 1, 0);
                }
            }
        }

        @Override
        public void clearRenderState() {
            try {
                drawingLayer = this;
                for (Batch batch : pending) batch.draw();
            } finally {
                drawingLayer = null;
                release();
                super.clearRenderState();
            }
        }

        public void release() {
            for (Batch batch : pending) batch.release();
            pending.clear();
            marked = false;
        }
    }
}
