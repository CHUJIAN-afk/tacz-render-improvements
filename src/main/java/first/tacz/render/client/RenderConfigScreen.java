package first.tacz.render.client;

import first.tacz.render.TaczRenderImprovements;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RenderConfigScreen extends Screen {
    private final Screen parent;
    private Checkbox droppedGuns;
    private Checkbox heldGuns;

    public RenderConfigScreen(Screen parent) {
        super(Component.translatable("tacz_render_improvements.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        droppedGuns = addRenderableWidget(Checkbox.builder(Component.translatable("tacz_render_improvements.config.optimizeItemGuns"), font)
                .pos(left, this.height / 2 - 42).selected(TaczRenderImprovements.CONFIG.optimizeItemGuns.get()).build());
        heldGuns = addRenderableWidget(Checkbox.builder(Component.translatable("tacz_render_improvements.config.optimizeHeldGuns"), font)
                .pos(left, this.height / 2 - 12).selected(TaczRenderImprovements.CONFIG.optimizeHeldGuns.get()).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(left, this.height / 2 + 32, 310, 20).build());
    }

    private void saveAndClose() {
        TaczRenderImprovements.CONFIG.optimizeItemGuns.set(droppedGuns.selected());
        TaczRenderImprovements.CONFIG.optimizeHeldGuns.set(heldGuns.selected());
        TaczRenderImprovements.CONFIG.spec.save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
