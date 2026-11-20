package first.tacz.render.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderInstance.class)
public interface ShaderInstanceAccessor {
    @Accessor("lastProgramId")
    static void tacz_render_improvements$program(int program) { throw new AssertionError(); }
}
