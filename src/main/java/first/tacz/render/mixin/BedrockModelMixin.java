package first.tacz.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import first.tacz.render.client.HeldGunRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BedrockModel.class, remap = false)
public abstract class BedrockModelMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true))
    private VertexConsumer tacz_render_improvements$bindHeldModel(MultiBufferSource source, RenderType type, PoseStack pose,
            ItemDisplayContext context, RenderType material, int light, int overlay, float red, float green, float blue, float alpha, MultiBufferSource buffers) {
        return HeldGunRenderer.INSTANCE.bind((BedrockModel) (Object) this, source, type, context);
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/RenderHelper;endBatch(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void tacz_render_improvements$flushMaterial(MultiBufferSource source, RenderType type) {
        first.tacz.render.client.RenderPipeline.INSTANCE.endBatch(source, type);
    }
}
