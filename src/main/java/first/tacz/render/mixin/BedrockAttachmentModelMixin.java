package first.tacz.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import first.tacz.render.client.HeldGunRenderer;
import first.tacz.render.client.RenderPipeline;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class BedrockAttachmentModelMixin {
    @Redirect(method = "renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;FLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true))
    private VertexConsumer tacz_render_improvements$partial(MultiBufferSource source, RenderType material, PoseStack pose,
            ItemDisplayContext context, RenderType type, int light, int overlay, List<BedrockPart> path, float alpha, MultiBufferSource buffers) {
        return HeldGunRenderer.INSTANCE.partial
                ? HeldGunRenderer.INSTANCE.bind((BedrockModel) (Object) this, source, type, context) : source.getBuffer(type);
    }

    @Redirect(method = "renderTempPart(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILjava/util/List;FLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/util/RenderHelper;endBatch(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void tacz_render_improvements$flushPartial(MultiBufferSource source, RenderType type) {
        RenderPipeline.INSTANCE.endBatch(source, type);
    }
}
