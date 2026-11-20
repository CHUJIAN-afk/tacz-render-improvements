package first.tacz.render.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 模型只编译一次，骨骼编号、切线和纹理中点随局部几何常驻。
public final class GeometryCompiler implements VertexConsumer {
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private final Map<Vertex, Integer> unique = new HashMap<>();
    private final Vertex[] polygon = new Vertex[4];
    private int polygonSize;
    private int polygonIndex;
    private boolean pendingVertex;
    private float x, y, z, u, v, nx, ny, nz;
    private int color = -1, overlay, light;
    public int bone;
    public int discardedFaces;

    public GeometryCompiler(int polygonSize) {
        this.polygonSize = polygonSize;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (pendingVertex) endVertex();
        this.x = x;
        this.y = y;
        this.z = z;
        pendingVertex = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        color = red | green << 8 | blue << 16 | alpha << 24;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        overlay = u | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        light = u | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        nx = x;
        ny = y;
        nz = z;
        return this;
    }

    private void endVertex() {
        pendingVertex = false;
        polygon[polygonIndex++] = new Vertex(x, y, z, color, u, v, overlay, light, pack(nx, ny, nz, 0), 0, 0, 0, bone);
        if (polygonIndex == polygonSize) {
            Vertex a = polygon[0], b = polygon[1], c = polygon[2];
            float ex = b.x - a.x, ey = b.y - a.y, ez = b.z - a.z;
            float fx = c.x - a.x, fy = c.y - a.y, fz = c.z - a.z;
            float cx = ey * fz - ez * fy, cy = ez * fx - ex * fz, cz = ex * fy - ey * fx;
            boolean degenerate = cx * cx + cy * cy + cz * cz == 0;
            if (degenerate && polygonSize == 4) {
                Vertex d = polygon[3];
                fx = d.x - a.x;
                fy = d.y - a.y;
                fz = d.z - a.z;
                cx = ey * fz - ez * fy;
                cy = ez * fx - ex * fz;
                cz = ex * fy - ey * fx;
                degenerate = cx * cx + cy * cy + cz * cz == 0;
            }
            if (!degenerate) {
                float du1 = b.u - a.u, dv1 = b.v - a.v, du2 = c.u - a.u, dv2 = c.v - a.v;
                float determinant = du1 * dv2 - du2 * dv1;
                float inverse = determinant == 0 ? 1 : 1 / determinant;
                float tx = inverse * (dv2 * ex - dv1 * fx);
                float ty = inverse * (dv2 * ey - dv1 * fy);
                float tz = inverse * (dv2 * ez - dv1 * fz);
                float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (length > 0) {
                    tx /= length;
                    ty /= length;
                    tz /= length;
                }
                float bx = inverse * (-du2 * ex + du1 * fx);
                float by = inverse * (-du2 * ey + du1 * fy);
                float bz = inverse * (-du2 * ez + du1 * fz);
                float handedness = bx * (ty * nz - tz * ny) + by * (tz * nx - tx * nz) + bz * (tx * ny - ty * nx) < 0 ? -1 : 1;
                int tangent = pack(tx, ty, tz, handedness);
                float midU = 0, midV = 0;
                for (int i = 0; i < polygonSize; i++) {
                    midU += polygon[i].u;
                    midV += polygon[i].v;
                }
                int[] face = new int[polygonSize];
                for (int i = 0; i < polygonSize; i++) {
                    Vertex p = polygon[i];
                    Vertex vertex = new Vertex(p.x, p.y, p.z, p.color, p.u, p.v, p.overlay, p.light, p.normal,
                            midU / polygonSize, midV / polygonSize, tangent, p.bone);
                    Integer existing = unique.get(vertex);
                    if (existing == null) {
                        existing = vertices.size();
                        vertices.add(vertex);
                        unique.put(vertex, existing);
                    }
                    face[i] = existing;
                }
                indices.add(face[0]);
                indices.add(face[1]);
                indices.add(face[2]);
                if (polygonSize == 4) {
                    indices.add(face[2]);
                    indices.add(face[3]);
                    indices.add(face[0]);
                }
            } else {
                discardedFaces++;
            }
            polygonIndex = 0;
        }
    }

    public int indexCount() {
        if (pendingVertex) endVertex();
        return indices.size();
    }

    public Data finish() {
        if (pendingVertex) endVertex();
        if (polygonIndex != 0) {
            throw new IllegalStateException("Incomplete model polygon");
        }
        ByteBuffer data = ByteBuffer.allocateDirect(vertices.size() * 64).order(ByteOrder.nativeOrder());
        for (Vertex vertex : vertices) {
            data.putFloat(vertex.x).putFloat(vertex.y).putFloat(vertex.z).putInt(vertex.color);
            data.putFloat(vertex.u).putFloat(vertex.v).putInt(vertex.overlay).putInt(vertex.light).putInt(vertex.normal);
            data.putInt(0).putInt(0).putFloat(vertex.midU).putFloat(vertex.midV).putInt(vertex.tangent).putInt(vertex.bone).putInt(0);
        }
        data.flip();
        return new Data(data, indices.stream().mapToInt(Integer::intValue).toArray(), discardedFaces);
    }

    private int pack(float x, float y, float z, float w) {
        return ((int) (Math.max(-1, Math.min(1, x)) * 127) & 255)
                | ((int) (Math.max(-1, Math.min(1, y)) * 127) & 255) << 8
                | ((int) (Math.max(-1, Math.min(1, z)) * 127) & 255) << 16
                | ((int) (Math.max(-1, Math.min(1, w)) * 127) & 255) << 24;
    }


    private record Vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                          int normal, float midU, float midV, int tangent, int bone) {}

    public record Data(ByteBuffer vertices, int[] indices, int discardedFaces) {}
}
