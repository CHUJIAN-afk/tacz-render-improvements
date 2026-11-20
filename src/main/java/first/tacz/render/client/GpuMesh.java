package first.tacz.render.client;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL43.*;

public final class GpuMesh implements AutoCloseable {
    public final GeometryCompiler.Data data;
    public final int vertexBuffer;
    public final int indexBuffer;
    private final Map<Integer, Integer> arrays = new HashMap<>();

    public GpuMesh(GeometryCompiler.Data data) {
        this.data = data;
        int previous = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        vertexBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, data.vertices(), GL_STATIC_DRAW);
        indexBuffer = uploadIndices(data.indices());
        glBindBuffer(GL_ARRAY_BUFFER, previous);
    }

    public int uploadIndices(int[] indices) {
        int previous = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int buffer = glGenBuffers();
        IntBuffer values = MemoryUtil.memAllocInt(indices.length);
        values.put(indices).flip();
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, values, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, previous);
        MemoryUtil.memFree(values);
        return buffer;
    }

    public void bind(ShaderBridge.Program program, int indices) {
        Integer array = arrays.get(program.id);
        if (array == null) {
            array = glGenVertexArrays();
            arrays.put(program.id, array);
            glBindVertexArray(array);
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            for (var attribute : program.aliases.entrySet()) {
                String semantic = attribute.getKey();
                int location = glGetAttribLocation(program.id, attribute.getValue());
                if (location >= 0) {
                    switch (semantic) {
                        case "Position" -> glVertexAttribPointer(location, 3, GL_FLOAT, false, 64, 0);
                        case "Color" -> glVertexAttribPointer(location, 4, GL_UNSIGNED_BYTE, true, 64, 12);
                        case "UV0" -> glVertexAttribPointer(location, 2, GL_FLOAT, false, 64, 16);
                        case "UV1" -> glVertexAttribIPointer(location, 2, GL_SHORT, 64, 24);
                        case "UV2" -> glVertexAttribIPointer(location, 2, GL_SHORT, 64, 28);
                        case "Normal" -> glVertexAttribPointer(location, 3, GL_BYTE, true, 64, 32);
                        case "Entity" -> glVertexAttribIPointer(location, 3, GL_SHORT, 64, 36);
                        case "at_tangent" -> glVertexAttribPointer(location, 4, GL_BYTE, true, 64, 52);
                        default -> throw new IllegalArgumentException(semantic);
                    }
                    glEnableVertexAttribArray(location);
                }
            }
            int midpoint = glGetAttribLocation(program.id, "mc_midTexCoord");
            if (midpoint >= 0) {
                glVertexAttribPointer(midpoint, 2, GL_FLOAT, false, 64, 44);
                glEnableVertexAttribArray(midpoint);
            }
            int bone = glGetAttribLocation(program.id, "tr_Bone");
            if (bone >= 0) {
                glVertexAttribIPointer(bone, 1, GL_INT, 64, 56);
                glEnableVertexAttribArray(bone);
            }
        } else {
            glBindVertexArray(array);
        }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
    }

    public long bytes() {
        return data.vertices().remaining() + (long) data.indices().length * 4;
    }

    @Override
    public void close() {
        for (int array : arrays.values()) glDeleteVertexArrays(array);
        arrays.clear();
        glDeleteBuffers(vertexBuffer);
        glDeleteBuffers(indexBuffer);
    }
}
