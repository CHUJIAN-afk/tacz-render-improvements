package first.tacz.render;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class RenderConfig {
    public final ModConfigSpec spec;
    public final ModConfigSpec.BooleanValue optimizeItemGuns;
    public final ModConfigSpec.BooleanValue optimizeHeldGuns;
    public final ModConfigSpec.IntValue meshBudgetMiB;

    public RenderConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        optimizeItemGuns = builder.comment("Tacz物品枪械渲染与实例化")
                .translation("tacz_render_improvements.config.optimizeItemGuns").define("optimizeItemGuns", true);
        optimizeHeldGuns = builder.comment("Tacz手持渲染优化")
                .translation("tacz_render_improvements.config.optimizeHeldGuns").define("optimizeHeldGuns", true);
        meshBudgetMiB = builder.comment("共享网格显存预算，当前帧正在使用的模型保留至绘制结束")
                .defineInRange("meshBudgetMiB", 256, 16, 2048);
        spec = builder.build();
    }
}
