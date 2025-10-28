package appeng.client.gui.me.common;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;

public class ContentToast implements Toast {
    private static final long TIME_VISIBLE = 2500;
    private static final int TITLE_COLOR = 0xFF500050;
    private static final int TEXT_COLOR = 0xFF000000;

    protected final AEKey what;
    protected final List<FormattedCharSequence> lines = new ObjectArrayList<>();
    protected int height;
    protected boolean dirty = true;

    public ContentToast(AEKey icon) {
        this.what = icon;

    }
    public int slotCount() {
        update();
        return Toast.super.slotCount();
    }
    protected Component getTitle() {
        return Component.empty();
    }

    protected void calculateHeight() {
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        height = Toast.super.height() + (lines.size() - 1) * font.lineHeight;
    }

    protected void addInfoLines(List<FormattedCharSequence> lines) {
    }

    private void update() {
        if (dirty) {
            var text = new ObjectArrayList<FormattedCharSequence>();
            addInfoLines(text);
            this.lines.clear();
            this.lines.addAll(text);
            calculateHeight();
            dirty = false;
        }
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        update();

        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;

        // stretch the middle
        guiGraphics.blit(TEXTURE, 0, 0, 0, 32, this.width(), 8);
        int middleHeight = height - 16;
        for (var middleY = 0; middleY < middleHeight; middleY += 16) {
            var tileHeight = Math.min(middleHeight - middleY, 16);
            guiGraphics.blit(TEXTURE, 0, 8 + middleY, 0, 32 + 8, this.width(), tileHeight);
        }
        guiGraphics.blit(TEXTURE, 0, height - 8, 0, 32 + 32 - 8, this.width(), 8);
        guiGraphics.drawString(toastComponent.getMinecraft().font, getTitle(), 30, 7,
                TITLE_COLOR, false);
        var lineY = 18;
        for (var line : lines) {
            guiGraphics.drawString(toastComponent.getMinecraft().font, line, 30, lineY, TEXT_COLOR, false);
            lineY += font.lineHeight;
        }
        AEKeyRendering.drawInGui(minecraft, guiGraphics, 8, 8, what);

        return timeSinceLastVisible >= TIME_VISIBLE ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }

    @Override
    public int height() {
        return height;
    }
}
