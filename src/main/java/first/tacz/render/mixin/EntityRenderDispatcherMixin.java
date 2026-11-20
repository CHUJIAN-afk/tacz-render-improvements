package first.tacz.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.tacz.render.client.RenderPipeline;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void tacz_render_improvements$enter(E entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource source, int light, CallbackInfo ci) {
        RenderPipeline.INSTANCE.enterEntity(source);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void tacz_render_improvements$leave(E entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource source, int light, CallbackInfo ci) {
        RenderPipeline.INSTANCE.leaveEntity();
    }
}
