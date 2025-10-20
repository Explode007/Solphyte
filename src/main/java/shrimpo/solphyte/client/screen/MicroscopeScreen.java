package shrimpo.solphyte.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import shrimpo.solphyte.menu.MicroscopeMenu;
import shrimpo.solphyte.network.SolphyteNetwork;
import shrimpo.solphyte.network.packet.MicroscopeFoundC2SPacket;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

public class MicroscopeScreen extends AbstractContainerScreen<MicroscopeMenu> {
    private static final ResourceLocation BG = new ResourceLocation("solphyte", "textures/gui/microscope_gui.png");
    private static final ResourceLocation GENERIC_54 = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    // Pane placement within the container background (aligned to the big empty area on the GUI)
    private int paneX, paneY, paneW, paneH;
    // Virtual content size
    private final int contentW = 384;
    private final int contentH = 384;
    // Scroll offsets
    private int offX = 0;
    private int offY = 0;
    private boolean dragging = false;
    private int dragStartX, dragStartY, dragOrigOffX, dragOrigOffY;

    private long cachedSeed = 0L;
    private int cachedFound = 0;

    private static class Speck {
        int x, y;
        int color;
        boolean target;
        boolean found;
        int size;           // varied size per speck
        double phaseX, phaseY; // jitter phases
        double speedX, speedY; // radians per millisecond
        double amp;            // jitter amplitude in pixels
    }
    private final List<Speck> specks = new ArrayList<>();
    private final List<Integer> targetIndices = new ArrayList<>();
    private final BitSet localFound = new BitSet();

    public MicroscopeScreen(MicroscopeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        // Pin the GUI to the very top of the screen
        this.topPos = -35;
        // Align pane to the given coordinates in the microscope GUI texture
        this.paneX = this.leftPos + 54;
        this.paneY = this.topPos + 60;
        this.paneW = 181 - 54; // 127
        this.paneH = 185 - 60; // 125
        rebuildIfNeeded(true);
    }

    private void rebuildIfNeeded(boolean force) {
        long seed = this.menu.getSeed();
        boolean changed = force || seed != cachedSeed;
        if (changed) {
            this.cachedSeed = seed;
            this.cachedFound = this.menu.getFound();
            buildSpecks(seed);
            this.offX = 0;
            this.offY = 0;
            this.localFound.clear();
        }
    }

    private void buildSpecks(long seed) {
        this.specks.clear();
        this.targetIndices.clear();
        Random r = new Random(seed == 0 ? 1L : seed);
        int total = 300;
        int targets = 12; // more than 5 to make the search interesting
        for (int i = 0; i < total; i++) {
            Speck s = new Speck();
            s.x = r.nextInt(contentW);
            s.y = r.nextInt(contentH);
            int shade = 0x20 + r.nextInt(0x40);
            int g = 0x30 + r.nextInt(0x60);
            int b = 0x50 + r.nextInt(0x70);
            s.color = 0xFF000000 | (shade << 16) | (g << 8) | b;
            s.target = false;
            // varied sizes: non-targets small (1-3), targets slightly larger (2-4; will recolor when target set)
            s.size = 1 + r.nextInt(3);
            // subtle jitter values
            s.phaseX = r.nextDouble() * Math.PI * 2.0;
            s.phaseY = r.nextDouble() * Math.PI * 2.0;
            s.speedX = 0.002 + r.nextDouble() * 0.003; // rad/ms
            s.speedY = 0.002 + r.nextDouble() * 0.003; // rad/ms
            s.amp = 0.25 + r.nextDouble() * 0.75;      // 0.25..1.0 px
            this.specks.add(s);
        }
        // mark some as targets
        for (int i = 0; i < targets; i++) {
            int idx = r.nextInt(this.specks.size());
            if (this.specks.get(idx).target) { i--; continue; }
            this.specks.get(idx).target = true;
            // slightly larger target specks
            this.specks.get(idx).size = 2 + r.nextInt(3);
            this.targetIndices.add(idx);
        }
    }

    private static int jitterX(Speck s, long nowMs) {
        return (int)Math.round(Math.sin(s.phaseX + nowMs * s.speedX) * s.amp);
    }
    private static int jitterY(Speck s, long nowMs) {
        return (int)Math.round(Math.cos(s.phaseY + nowMs * s.speedY) * s.amp);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // Draw microscope GUI background at the pinned position
        gfx.blit(BG, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Draw specks within viewport bounds
        long now = System.currentTimeMillis();
        int viewX0 = offX, viewY0 = offY, viewX1 = offX + paneW, viewY1 = offY + paneH;
        for (int i = 0; i < specks.size(); i++) {
            Speck s = specks.get(i);
            if (s.x >= viewX0 && s.x < viewX1 && s.y >= viewY0 && s.y < viewY1) {
                int jx = jitterX(s, now);
                int jy = jitterY(s, now);
                int sx = paneX + (s.x - offX) + jx;
                int sy = paneY + (s.y - offY) + jy;
                int size = s.target ? Math.max(s.size, 2) : s.size;
                int col = s.target ? (s.found ? 0xFF44CC44 : 0xFFCC4444) : s.color;
                gfx.fill(sx, sy, sx + size, sy + size, col);
            }
        }

        // Minimal scrollbars beside the pane
        int px0 = paneX, py0 = paneY, px1 = paneX + paneW, py1 = paneY + paneH;
        int barX = px1 + 1;
        int barY0 = py0;
        int barY1 = py1;
        gfx.fill(barX, barY0, barX + 2, barY1, 0x40202020);
        int thumbH = Math.max(6, paneH * paneH / contentH);
        int thumbY = barY0 + (paneH - thumbH) * offY / Math.max(1, contentH - paneH);
        gfx.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0x60606060);
        int hbarY = py1 + 1;
        gfx.fill(px0, hbarY, px1, hbarY + 2, 0x40202020);
        int thumbW = Math.max(6, paneW * paneW / contentW);
        int thumbX = px0 + (paneW - thumbW) * offX / Math.max(1, contentW - paneW);
        gfx.fill(thumbX, hbarY, thumbX + thumbW, hbarY + 2, 0x60606060);

        // Draw the default player inventory background 120px right and 200px down from microscope origin
        int invBgX = this.leftPos + 40;
        int invBgY = this.topPos + 198;
        gfx.blit(GENERIC_54, invBgX, invBgY, 0, 126, 176, 96);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // Title near top-left of the microscope GUI (relative to container origin)
        gfx.drawString(this.font, this.title, 8, 6, 0x404040, false);

        // Player inventory label aligned to the blitted inventory background (relative offsets)
        int inventoryLabelX = 40 + 8;   // invBg relative X + label inset
        int inventoryLabelY = 198 + 2;  // invBg relative Y + label inset
        gfx.drawString(this.font, this.playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        // Found counter on top of the draggable pane (convert absolute pane to relative)
        int fx = (this.paneX - this.leftPos) + 2;
        int fy = (this.paneY - this.topPos) - 10; // shift 5px higher than before
        gfx.drawString(this.font, Component.literal("Found: " + Math.min(this.menu.getFound(), 5) + "/5"), fx, fy, 0xFF777777, false);

        // Instructions anchored to the bottom-right of the microscope GUI (relative to container origin)
        int rightInset = 8;
        int bottomInset = 8;
        int lineStep = 10; // spacing between lines
        boolean hasInput = this.menu.slots.get(0).hasItem();
        boolean hasOutput = this.menu.slots.get(1).hasItem();
        if (!hasInput) {
            String[] lines = {"Put crops", "or seeds", "to begin"};
            int yStart = this.imageHeight - 60 - bottomInset - lines.length * lineStep;
            for (int i = 0; i < lines.length; i++) {
                String s = lines[i];
                int x = this.imageWidth - rightInset - this.font.width(s);
                int y = yStart + i * lineStep;
                gfx.drawString(this.font, Component.literal(s), x, y, 0xFF777777, false);
            }
        } else if (hasInput && !hasOutput) {
            String[] lines = {"Click Red", "specks", "to progress"};
            int yStart = this.imageHeight - 60 - bottomInset - lines.length * lineStep;
            for (int i = 0; i < lines.length; i++) {
                String s = lines[i];
                int x = this.imageWidth - rightInset - this.font.width(s);
                int y = yStart + i * lineStep;
                gfx.drawString(this.font, Component.literal(s), x, y, 0xFF777777, false);
            }
        } else if (hasOutput) {
            String[] lines = {"Take Sample", "from output"};
            int yStart = this.imageHeight - 60 - bottomInset - lines.length * lineStep; // align with the same anchor as "Put..."
            for (int i = 0; i < lines.length; i++) {
                String s = lines[i];
                int x = this.imageWidth - rightInset - this.font.width(s);
                int y = yStart + i * lineStep;
                gfx.drawString(this.font, Component.literal(s), x, y, 0xFF77CC77, false);
            }
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (this.cachedSeed != this.menu.getSeed()) {
            rebuildIfNeeded(true);
        }
        this.cachedFound = this.menu.getFound();

        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Start dragging with right mouse inside pane
        if (button == 1 && inPane(mouseX, mouseY)) {
            this.dragging = true;
            this.dragStartX = (int)mouseX;
            this.dragStartY = (int)mouseY;
            this.dragOrigOffX = offX;
            this.dragOrigOffY = offY;
            return true;
        }
        // Left click: attempt to find a target
        if (button == 0 && inPane(mouseX, mouseY)) {
            boolean hasInput = this.menu.slots.get(0).hasItem();
            boolean hasOutput = this.menu.slots.get(1).hasItem();
            if (!hasInput || hasOutput) return true; // consume click

            long now = System.currentTimeMillis();
            int lx = (int)mouseX - paneX + offX;
            int ly = (int)mouseY - paneY + offY;
            int foundIdx = -1;
            int bestDist = Integer.MAX_VALUE;
            for (int idx : targetIndices) {
                Speck s = specks.get(idx);
                if (s.found) continue;
                int jx = jitterX(s, now);
                int jy = jitterY(s, now);
                int dx = (s.x + jx) - lx;
                int dy = (s.y + jy) - ly;
                int d2 = dx*dx + dy*dy;
                int clickRadius = s.size + 4; // radius scales with apparent size
                if (d2 < bestDist && d2 <= clickRadius * clickRadius) { bestDist = d2; foundIdx = idx; }
            }
            if (foundIdx >= 0) {
                Speck s = specks.get(foundIdx);
                s.found = true;
                localFound.set(foundIdx);
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
                SolphyteNetwork.sendToServer(new MicroscopeFoundC2SPacket(this.menu.getBlockPos()));
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 1) {
            int dx = (int)mouseX - dragStartX;
            int dy = (int)mouseY - dragStartY;
            setOffsets(dragOrigOffX - dx, dragOrigOffY - dy);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inPane(mouseX, mouseY)) {
            setOffsets(offX, (int)(offY - delta * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean inPane(double mx, double my) {
        return mx >= paneX && mx < paneX + paneW && my >= paneY && my < paneY + paneH;
    }

    private void setOffsets(int nx, int ny) {
        this.offX = clamp(nx, 0, Math.max(0, contentW - paneW));
        this.offY = clamp(ny, 0, Math.max(0, contentH - paneH));
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
