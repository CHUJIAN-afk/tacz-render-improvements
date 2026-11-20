package first.tacz.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.ExternalGunItemVboRenderer;
import com.tacz.guns.client.renderer.item.StaticGunRenderResult;
import first.tacz.render.client.ItemGunRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExternalGunItemVboRenderer.class, remap = false)
public abstract class ExternalGunItemVboRendererMixin {
    @Inject(method = "tryRender", at = @At("HEAD"), cancellable = true)
    private static void tacz_render_improvements$instance(ItemStack stack, ItemDisplayContext context, boolean useLod,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfoReturnable<StaticGunRenderResult> cir) {
        StaticGunRenderResult result = ItemGunRenderer.INSTANCE.submit(stack, context, useLod, pose, buffers, light, overlay);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
