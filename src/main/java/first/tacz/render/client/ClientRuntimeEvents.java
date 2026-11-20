package first.tacz.render.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "tacz_render_improvements", value = Dist.CLIENT)
public final class ClientRuntimeEvents {
    @SubscribeEvent
    public static void onRenderStart(RenderFrameEvent.Pre event) {
        RenderPipeline.INSTANCE.beginFrame();
    }

    @SubscribeEvent
    public static void onRenderEnd(RenderFrameEvent.Post event) {
        RenderPipeline.INSTANCE.endFrame();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RenderPipeline.INSTANCE.invalidate();
    }
}
