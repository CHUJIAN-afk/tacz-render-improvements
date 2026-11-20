package first.tacz.render.client;

import first.tacz.render.mixin.ShaderInstanceAccessor;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.system.MemoryStack;
import org.slf4j.LoggerFactory;
import static org.lwjgl.opengl.GL43.*;

// 原程序负责光影材质与目标绑定，派生程序只增加骨骼或实例输入。
public final class ShaderBridge {
    public final Map<Long, Program> programs = new HashMap<>();
    private final Set<Long> failed = new HashSet<>();
    private final ShaderTransform transform = new ShaderTransform();
    private final String kernel;

    public ShaderBridge() {
        try (var stream = ShaderBridge.class.getResourceAsStream("/assets/tacz_render_improvements/shaders/geometry.glsl")) {
            if (stream == null) throw new IOException("Missing geometry shader");
            kernel = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public Program get(ShaderInstance shader, int mode) {
        long key = (long) shader.getId() << 1 | mode;
        Program program = programs.get(key);
        if (program == null && !failed.contains(key)) {
            try {
                program = new Program(shader.getId(), mode);
                programs.put(key, program);
            } catch (RuntimeException exception) {
                failed.add(key);
                LoggerFactory.getLogger("TaczRenderImprovements").warn("Using native geometry for shader {} mode {}", shader.getName(), mode, exception);
            }
        }
        return program;
    }

    public void invalidate() {
        for (Program program : programs.values()) glDeleteProgram(program.id);
        programs.clear();
        failed.clear();
    }

    public final class Program {
        public final int id;
        public final int binding;
        public final Map<String, String> aliases;
        private final List<Uniform> uniforms = new ArrayList<>();

        private Program(int source, int mode) {
            int[] attached = new int[glGetProgrami(source, GL_ATTACHED_SHADERS)];
            glGetAttachedShaders(source, new int[1], attached);
            String vertexSource = null;
            for (int shader : attached) {
                int type = glGetShaderi(shader, GL_SHADER_TYPE);
                if (type == GL_VERTEX_SHADER) vertexSource = glGetShaderSource(shader);
                if (type == GL_TESS_CONTROL_SHADER || type == GL_TESS_EVALUATION_SHADER) {
                    throw new IllegalArgumentException("Tessellated geometry requires native submissions");
                }
            }
            if (vertexSource == null) throw new IllegalStateException("Missing vertex shader source");
            int blocks = glGetProgramInterfacei(source, GL_SHADER_STORAGE_BLOCK, GL_ACTIVE_RESOURCES);
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < blocks; i++) {
                int[] value = new int[1];
                glGetProgramResourceiv(source, GL_SHADER_STORAGE_BLOCK, i, new int[]{GL_BUFFER_BINDING}, new int[1], value);
                used.add(value[0]);
            }
            int selected = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) - 1;
            while (used.contains(selected)) selected--;
            if (selected < 0) throw new IllegalStateException("No free storage binding");
            binding = selected;
            ShaderTransform.Result result = transform.transform(vertexSource, binding, mode, kernel);
            aliases = result.aliases();
            id = glCreateProgram();
            int vertex = glCreateShader(GL_VERTEX_SHADER);
            try (MemoryStack memory = MemoryStack.stackPush()) {
                glShaderSource(vertex, result.source());
                glCompileShader(vertex);
                if (glGetShaderi(vertex, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException(glGetShaderInfoLog(vertex));
                glAttachShader(id, vertex);
                for (int shader : attached) {
                    if (glGetShaderi(shader, GL_SHADER_TYPE) != GL_VERTEX_SHADER) glAttachShader(id, shader);
                }
                IntBuffer size = memory.mallocInt(1), type = memory.mallocInt(1);
                Set<Integer> attributes = new HashSet<>();
                for (int i = 0; i < glGetProgrami(source, GL_ACTIVE_ATTRIBUTES); i++) {
                    String name = glGetActiveAttrib(source, i, 256, size, type);
                    int location = glGetAttribLocation(source, name);
                    attributes.add(location);
                    glBindAttribLocation(id, location, name);
                }
                int bone = glGetInteger(GL_MAX_VERTEX_ATTRIBS) - 1;
                while (attributes.contains(bone)) bone--;
                if (bone < 0) throw new IllegalStateException("No free bone attribute");
                glBindAttribLocation(id, bone, "tr_Bone");
                int outputs = glGetProgramInterfacei(source, GL_PROGRAM_OUTPUT, GL_ACTIVE_RESOURCES);
                for (int i = 0; i < outputs; i++) {
                    String name = glGetProgramResourceName(source, GL_PROGRAM_OUTPUT, i);
                    int location = glGetFragDataLocation(source, name);
                    if (location >= 0) glBindFragDataLocation(id, location, name);
                }
                glLinkProgram(id);
                if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(id));
                for (int i = 0; i < glGetProgrami(source, GL_ACTIVE_UNIFORM_BLOCKS); i++) {
                    String name = glGetActiveUniformBlockName(source, i);
                    int block = glGetUniformBlockIndex(id, name);
                    if (block != GL_INVALID_INDEX) glUniformBlockBinding(id, block, glGetActiveUniformBlocki(source, i, GL_UNIFORM_BLOCK_BINDING));
                }
                for (int i = 0; i < blocks; i++) {
                    String name = glGetProgramResourceName(source, GL_SHADER_STORAGE_BLOCK, i);
                    int block = glGetProgramResourceIndex(id, GL_SHADER_STORAGE_BLOCK, name);
                    int[] value = new int[1];
                    glGetProgramResourceiv(source, GL_SHADER_STORAGE_BLOCK, i, new int[]{GL_BUFFER_BINDING}, new int[1], value);
                    if (block != GL_INVALID_INDEX) glShaderStorageBlockBinding(id, block, value[0]);
                }
                for (int i = 0; i < glGetProgrami(source, GL_ACTIVE_UNIFORMS); i++) {
                    String name = glGetActiveUniform(source, i, 256, size, type);
                    for (int element = 0; element < size.get(0); element++) {
                        String uniform = size.get(0) > 1 ? name.replace("[0]", "[" + element + "]") : name;
                        int original = glGetUniformLocation(source, uniform), target = glGetUniformLocation(id, uniform);
                        if (original >= 0 && target >= 0) uniforms.add(new Uniform(original, target, type.get(0)));
                    }
                }
            } catch (RuntimeException exception) {
                glDeleteProgram(id);
                throw exception;
            } finally {
                glDeleteShader(vertex);
            }
        }

        public void apply(ShaderInstance source) {
            ShaderInstanceAccessor.tacz_render_improvements$program(-1);
            source.apply();
            glUseProgram(id);
            try (MemoryStack memory = MemoryStack.stackPush()) {
                FloatBuffer floats = memory.mallocFloat(16);
                IntBuffer integers = memory.mallocInt(16);
                for (Uniform uniform : uniforms) uniform.copy(source.getId(), floats, integers);
            }
        }
    }

    private record Uniform(int source, int target, int type) {
        private void copy(int program, FloatBuffer floats, IntBuffer integers) {
            floats.clear();
            integers.clear();
            switch (type) {
                case GL_FLOAT, GL_FLOAT_VEC2, GL_FLOAT_VEC3, GL_FLOAT_VEC4, GL_FLOAT_MAT2, GL_FLOAT_MAT3, GL_FLOAT_MAT4,
                        GL_FLOAT_MAT2x3, GL_FLOAT_MAT2x4, GL_FLOAT_MAT3x2, GL_FLOAT_MAT3x4, GL_FLOAT_MAT4x2, GL_FLOAT_MAT4x3 -> {
                    glGetUniformfv(program, source, floats);
                    switch (type) {
                        case GL_FLOAT -> glUniform1fv(target, floats.limit(1));
                        case GL_FLOAT_VEC2 -> glUniform2fv(target, floats.limit(2));
                        case GL_FLOAT_VEC3 -> glUniform3fv(target, floats.limit(3));
                        case GL_FLOAT_VEC4 -> glUniform4fv(target, floats.limit(4));
                        case GL_FLOAT_MAT2 -> glUniformMatrix2fv(target, false, floats.limit(4));
                        case GL_FLOAT_MAT3 -> glUniformMatrix3fv(target, false, floats.limit(9));
                        case GL_FLOAT_MAT2x3 -> glUniformMatrix2x3fv(target, false, floats.limit(6));
                        case GL_FLOAT_MAT2x4 -> glUniformMatrix2x4fv(target, false, floats.limit(8));
                        case GL_FLOAT_MAT3x2 -> glUniformMatrix3x2fv(target, false, floats.limit(6));
                        case GL_FLOAT_MAT3x4 -> glUniformMatrix3x4fv(target, false, floats.limit(12));
                        case GL_FLOAT_MAT4x2 -> glUniformMatrix4x2fv(target, false, floats.limit(8));
                        case GL_FLOAT_MAT4x3 -> glUniformMatrix4x3fv(target, false, floats.limit(12));
                        default -> glUniformMatrix4fv(target, false, floats.limit(16));
                    }
                }
                case GL_UNSIGNED_INT, GL_UNSIGNED_INT_VEC2, GL_UNSIGNED_INT_VEC3, GL_UNSIGNED_INT_VEC4 -> {
                    glGetUniformuiv(program, source, integers);
                    switch (type) {
                        case GL_UNSIGNED_INT_VEC2 -> glUniform2uiv(target, integers.limit(2));
                        case GL_UNSIGNED_INT_VEC3 -> glUniform3uiv(target, integers.limit(3));
                        case GL_UNSIGNED_INT_VEC4 -> glUniform4uiv(target, integers.limit(4));
                        default -> glUniform1uiv(target, integers.limit(1));
                    }
                }
                default -> {
                    glGetUniformiv(program, source, integers);
                    switch (type) {
                        case GL_INT_VEC2, GL_BOOL_VEC2 -> glUniform2iv(target, integers.limit(2));
                        case GL_INT_VEC3, GL_BOOL_VEC3 -> glUniform3iv(target, integers.limit(3));
                        case GL_INT_VEC4, GL_BOOL_VEC4 -> glUniform4iv(target, integers.limit(4));
                        default -> glUniform1iv(target, integers.limit(1));
                    }
                }
            }
        }
    }
}
