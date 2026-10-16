package com.verityball.client;

import com.verityball.VerityState;
import com.verityball.VerityVariant;
import com.verityball.entity.VerityEntity;
import com.verityball.network.ModNetwork;
import com.verityball.network.SetStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Menú que se abre al hacer clic derecho sobre Verity: variante, gestos e interruptores (seguirme / mirarme / arcoíris). */
public class VerityMenuScreen extends Screen {
    private static final int W = 150, H = 20, GAP = 4;

    private final VerityEntity verity;
    /** Variante mostrada (se actualiza al instante, sin cerrar el menú). */
    private VerityVariant shownVariant;
    /** Para reconstruir el menú cuando el servidor cambia algo (estado, seguir, mirar...). */
    private int lastSignature;

    public VerityMenuScreen(VerityEntity verity) {
        super(Component.translatable("gui.verityball.title"));
        this.verity = verity;
        this.shownVariant = verity.getVariant();
        this.lastSignature = signature();
    }

    private int signature() {
        int s = verity.getStateId();
        s = s * 2 + (verity.isFollowing() ? 1 : 0);
        s = s * 2 + (verity.isLookingAtPlayers() ? 1 : 0);
        s = s * 2 + (verity.isRainbow() ? 1 : 0);
        return s * 31 + verity.getVariant().id;
    }

    private static MutableComponent toggleLabel(String key, boolean on) {
        MutableComponent label = Component.translatable(key)
                .append(Component.literal(on ? ": " : ": "))
                .append(Component.translatable(on ? "gui.verityball.on" : "gui.verityball.off"));
        return label.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private int panelTop(int rows) {
        int total = 34 + (rows + 2) * (H + GAP) + 14;
        return Math.max(8, this.height / 2 - total / 2);
    }

    @Override
    protected void init() {
        VerityVariant.Type type = shownVariant.type;
        List<VerityState> gestures = new ArrayList<>();
        for (VerityState st : VerityState.values()) {
            if (st != VerityState.IDLE && st.isAvailableFor(type)) gestures.add(st);
        }
        gestures.add(VerityState.IDLE); // "Detener" al final
        int rows = (gestures.size() + 1) / 2;

        final int left = this.width / 2 - W - GAP / 2;
        final int right = this.width / 2 + GAP / 2;
        int y = panelTop(rows) + 34;

        // Selector de variante: no cierra el menú
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> changeVariant(shownVariant.previous()))
                .bounds(left, y, 20, H).build());
        this.addRenderableWidget(Button.builder(variantName(shownVariant), b -> changeVariant(shownVariant.next()))
                .bounds(left + 24, y, 2 * W + GAP - 48, H).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> changeVariant(shownVariant.next()))
                .bounds(right + W - 20, y, 20, H).build());
        y += H + GAP + 6;

        // Gestos
        for (int i = 0; i < gestures.size(); i++) {
            VerityState st = gestures.get(i);
            MutableComponent label = Component.translatable("gui.verityball.state." + st.key);
            if (verity.getState() == st) label = label.withStyle(ChatFormatting.GREEN);
            int x = (i % 2 == 0) ? left : right;
            int bw = W;
            // si el último queda solo en su fila, ocupa todo el ancho
            if (i == gestures.size() - 1 && i % 2 == 0) bw = 2 * W + GAP;
            this.addRenderableWidget(Button.builder(label, b -> send(st.id, true))
                    .bounds(x, y + (i / 2) * (H + GAP), bw, H).build());
        }
        y += rows * (H + GAP) + 6;

        // Interruptores: Seguirme / Mirarme (y Arcoíris en la esfera)
        boolean rainbowAvailable = !type.equals(VerityVariant.Type.FEM);
        int cols = rainbowAvailable ? 3 : 2;
        int tw = (2 * W + GAP - (cols - 1) * GAP) / cols;
        int x0 = left;
        this.addRenderableWidget(Button.builder(toggleLabel("gui.verityball.follow", verity.isFollowing()),
                b -> send(SetStatePacket.TOGGLE_FOLLOW, false)).bounds(x0, y, tw, H).build());
        x0 += tw + GAP;
        this.addRenderableWidget(Button.builder(toggleLabel("gui.verityball.look", verity.isLookingAtPlayers()),
                b -> send(SetStatePacket.TOGGLE_LOOK, false)).bounds(x0, y, tw, H).build());
        if (rainbowAvailable) {
            x0 += tw + GAP;
            this.addRenderableWidget(Button.builder(toggleLabel("gui.verityball.rainbow", verity.isRainbow()),
                    b -> send(SetStatePacket.TOGGLE_RAINBOW, false)).bounds(x0, y, tw, H).build());
        }
    }

    private static Component variantName(VerityVariant v) {
        return Component.translatable("variant.verityball." + v.key).withStyle(s -> s.withColor(v.color == 0x2A2A2A ? 0xBBBBBB : v.color));
    }

    private void changeVariant(VerityVariant v) {
        this.shownVariant = v;
        ModNetwork.CHANNEL.sendToServer(new SetStatePacket(verity.getId(), SetStatePacket.SET_VARIANT_BASE + v.id));
        this.rebuildWidgets(); // los gestos disponibles dependen del tipo de modelo
    }

    private void send(int action, boolean close) {
        ModNetwork.CHANNEL.sendToServer(new SetStatePacket(verity.getId(), action));
        if (close) this.onClose();
    }

    @Override
    public void tick() {
        if (!verity.isAlive()) {
            this.onClose();
            return;
        }
        // el servidor confirma los cambios con unos ticks de retraso: refrescar los textos
        int sig = signature();
        if (sig != lastSignature) {
            lastSignature = sig;
            this.shownVariant = verity.getVariant();
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int rows = 0;
        for (VerityState st : VerityState.values()) if (st != VerityState.IDLE && st.isAvailableFor(shownVariant.type)) rows++;
        rows = (rows + 2) / 2;
        int top = panelTop(rows);
        int pw = 2 * W + GAP + 24;
        int px = this.width / 2 - pw / 2;
        int total = 34 + (rows + 2) * (H + GAP) + 14;
        g.fill(px, top, px + pw, top + total, 0xB0101018);
        g.renderOutline(px, top, pw, total, 0xFF000000 | shownVariant.color);
        g.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFED905);
        g.drawCenteredString(this.font, Component.translatable("gui.verityball.hint.menu"),
                this.width / 2, top + total - 12, 0xA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
