package first.tacz.render.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import org.joml.Matrix3f;
import org.lwjgl.system.MemoryUtil;

public final class PoseBuffer implements AutoCloseable {
    public ByteBuffer data;
    public int count;

    public PoseBuffer(int capacity) {
        data = MemoryUtil.memAlloc(Math.max(1, capacity) * 176);
    }

    public void put(int index, PoseStack.Pose pose, Matrix3f normal, int light, int overlay,
                    float red, float green, float blue, float alpha, long identifiers, boolean unshaded) {
        if ((index + 1) * 176 > data.capacity()) {
            data = MemoryUtil.memRealloc(data, Math.max((index + 1) * 176, data.capacity() * 2));
        }
        int at = index * 176;
        pose.pose().get(at, data);
        data.putFloat(at + 64, normal.m00()).putFloat(at + 68, normal.m01()).putFloat(at + 72, normal.m02()).putFloat(at + 76, 0);
        data.putFloat(at + 80, normal.m10()).putFloat(at + 84, normal.m11()).putFloat(at + 88, normal.m12()).putFloat(at + 92, 0);
        data.putFloat(at + 96, normal.m20()).putFloat(at + 100, normal.m21()).putFloat(at + 104, normal.m22()).putFloat(at + 108, 0);
        data.putFloat(at + 112, unshaded ? 1 : 0).putFloat(at + 116, 0).putFloat(at + 120, 0).putFloat(at + 124, 1);
        data.putFloat(at + 128, red).putFloat(at + 132, green).putFloat(at + 136, blue).putFloat(at + 140, alpha);
        data.putInt(at + 144, light & 65535).putInt(at + 148, light >>> 16);
        data.putInt(at + 152, overlay & 65535).putInt(at + 156, overlay >>> 16);
        data.putInt(at + 160, (short) identifiers).putInt(at + 164, (short) (identifiers >>> 16));
        data.putInt(at + 168, (short) (identifiers >>> 32)).putInt(at + 172, 0);
        count = Math.max(count, index + 1);
    }

    @Override
    public void close() {
        MemoryUtil.memFree(data);
    }
}
