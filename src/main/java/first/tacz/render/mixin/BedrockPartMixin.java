package first.tacz.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import first.tacz.render.client.HeldGunRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockPart.class, remap = false)
public abstract class BedrockPartMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void tacz_render_improvements$submitRigid(PoseStack pose, ItemDisplayContext context, VertexConsumer consumer,
            int light, int overlay, float red, float green, float blue, float alpha, MultiBufferSource buffers, CallbackInfo ci) {
        if (HeldGunRenderer.INSTANCE.submitRigid((BedrockPart) (Object) this, pose.last(), consumer, light, overlay, red, green, blue, alpha)) {
            ci.cancel();
        }
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void tacz_render_improvements$submitBone(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
            float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (HeldGunRenderer.INSTANCE.submit((BedrockPart) (Object) this, pose, consumer, light, overlay, red, green, blue, alpha)) {
            ci.cancel();
        }
    }
}
