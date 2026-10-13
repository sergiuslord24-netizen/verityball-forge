package com.verityball.client;

import com.verityball.VerityState;
import com.verityball.entity.VerityEntity;
import com.verityball.network.ModNetwork;
import com.verityball.network.SetStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Menú que se abre al hacer clic derecho sobre Verity. */
public class VerityMenuScreen extends Screen {
    private final VerityEntity verity;

    public VerityMenuScreen(VerityEntity verity) {
        super(Component.translatable("gui.verityball.title"));
        this.verity = verity;
    }

    @Override
    protected void init() {
        final int w = 150, h = 20, gap = 4;
        final int left = this.width / 2 - w - gap / 2;
        final int right = this.width / 2 + gap / 2;
        final int top = this.height / 2 - (4 * (h + gap)) / 2;

        VerityState[] order = {
                VerityState.TALK, VerityState.ITS_COMING, VerityState.DANCE, VerityState.IDLE,
                VerityState.FACE1, VerityState.FACE2, VerityState.FACE3
        };

        for (int i = 0; i < order.length; i++) {
            VerityState st = order[i];
            int col = i < 4 ? 0 : 1;
            int row = i < 4 ? i : i - 4;
            MutableComponent label = Component.translatable("gui.verityball.state." + st.key);
            if (verity.getState() == st) label = label.withStyle(ChatFormatting.GREEN);
            this.addRenderableWidget(Button.builder(label, b -> send(st.id))
                    .bounds(col == 0 ? left : right, top + row * (h + gap), w, h).build());
        }

        MutableComponent rainbow = Component.translatable("gui.verityball.rainbow")
                .append(Component.literal(verity.isRainbow() ? ": ON" : ": OFF"));
        if (verity.isRainbow()) rainbow = rainbow.withStyle(ChatFormatting.GREEN);
        this.addRenderableWidget(Button.builder(rainbow, b -> send(SetStatePacket.TOGGLE_RAINBOW))
                .bounds(right, top + 3 * (h + gap), w, h).build());
    }

    private void send(int action) {
        ModNetwork.CHANNEL.sendToServer(new SetStatePacket(verity.getId(), action));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 70, 0xFED905);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
