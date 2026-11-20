package first.tacz.render.client;

import first.tacz.render.TaczRenderImprovements;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "tacz_render_improvements", value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> RenderPipeline.INSTANCE.invalidate());
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        RenderPipeline.INSTANCE.invalidate();
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == TaczRenderImprovements.CONFIG.spec) {
            Minecraft.getInstance().execute(() -> RenderPipeline.INSTANCE.invalidate());
        }
    }
}
