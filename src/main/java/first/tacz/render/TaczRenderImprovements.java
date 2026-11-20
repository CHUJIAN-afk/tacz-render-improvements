package first.tacz.render;

import first.tacz.render.client.RenderConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "tacz_render_improvements", dist = Dist.CLIENT)
public final class TaczRenderImprovements {
    public static final RenderConfig CONFIG = new RenderConfig();

    public TaczRenderImprovements(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CONFIG.spec);
        container.registerExtensionPoint(IConfigScreenFactory.class, (ignored, parent) -> new RenderConfigScreen(parent));
    }
}
