package shrimpo.solphyte.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import shrimpo.solphyte.menu.PressMenu;
import shrimpo.solphyte.network.SolphyteNetwork;
import shrimpo.solphyte.network.packet.PressCompleteC2SPacket;
import shrimpo.solphyte.registry.SolphyteItem;

public class PressScreen extends AbstractContainerScreen<PressMenu> {
    private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("solphyte", "textures/gui/press_gui.png");

    // Prompt state: require LMB or RMB, and maybe SHIFT
    private boolean requireLmb = true;
    private boolean requireRmb = false;
    private boolean requireShift = false;
    private long nextPromptChangeMs = 0L; // acts as "prompt ends at"

    // Timing helpers
    private static final long SWAP_IMMUNITY_MS = 300L;  // grace window after swap
    private static final long SWAP_FLASH_MS = 130L;      // quick bright flash at swap
    private static final long PRE_SWAP_WARN_MS = 400L;  // visual warning before swap
    private long swapImmunityUntilMs = 0L;
    private long swapFlashUntilMs = 0L;
    private long warnStartMs = 0L;

    // Progress 0..1
    private float progress = 0f;

    // Wrong-input feedback
    private long lastPenaltyMs = 0L;
    private long wrongFlashUntilMs = 0L;

    // Indicator layout
    private static final int IND_SPACING = 13;
    public static final int IND_W = 31;
    public static final int IND_H = 32;

    // Background UV offset inside press_gui texture
    private static final int BG_U = 40; // top-left u in texture
    private static final int BG_V = 14; // top-left v in texture

    private static final int GUI_X0 = 14, GUI_Y0 = 22, GUI_Y1 = 174;

    // Calibrated progress bar bounds relative to the GUI (BG_U/BG_V origin)
    private static final int BAR_LEFT = 79;   // 176 - 80 = 96 px from left
    private static final int BAR_RIGHT = 120; // ~120 px from left
    private static final int BAR_TOP = 9;    // 10 px from top
    private static final int BAR_BOTTOM = 134;// 134 px from top (bottom edge)

    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    public PressScreen(PressMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 256;
    }

    @Override
    protected void init() { super.init(); }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        gfx.blit(BG, x, y, BG_U, BG_V, this.imageWidth, this.imageHeight);

        drawProgressBar(gfx, x, y);

        drawButtonPresses(gfx, x + 159 - BG_U, y + 23 - BG_V);

        drawIndicatorGlows(gfx, x + 159 - BG_U, y + 23 - BG_V);
    }


    private void drawProgressBar(GuiGraphics gfx, int baseX, int baseY) {
        int barWidth = BAR_RIGHT - BAR_LEFT;
        int barHeight = BAR_BOTTOM - BAR_TOP;

        int filled = Math.max(0, Math.min(barHeight, Math.round(progress * barHeight)));
        if (filled <= 0) return;

        int destX = baseX + BAR_LEFT;
        int destY = baseY + BAR_TOP;

        int srcU = 0;
        for (int i = 0; i < filled; i++) {
            int srcV = 10 + ((i & 1) == 0 ? 0 : 1);
            gfx.blit(BG, destX, destY + i, srcU, srcV, barWidth, 1);
        }
    }


    private void drawIndicatorGlows(GuiGraphics gfx, int originX, int originY) {
        long now = System.currentTimeMillis();
        float phase = (float) Math.sin((now % 1000L) / 1000f * (float) (Math.PI * 2));
        int a = (int) (0x66 + (0x99 - 0x66) * (0.5f + 0.5f * phase));
        int col = (a << 24) | 0xFFFFFF;

        int firstRowY = originY;
        if (requireLmb) gfx.fill(originX, originY, originX + IND_W, originY + IND_H, col);
        originY += IND_H + IND_SPACING;
        if (requireRmb) gfx.fill(originX, originY, originX + IND_W, originY + IND_H, col);
        originY += IND_H + IND_SPACING;
        if (requireShift) gfx.fill(originX, originY, originX + IND_W, originY + IND_H, col);

        int totalH = (IND_H * 3) + (IND_SPACING * 2);
        int boxLeft = originX - 1;
        int boxTop = firstRowY - 1;
        int boxRight = originX + IND_W + 1;
        int boxBottom = boxTop + totalH + 2;

        // Pre-swap warning: pulsing yellow border
        if (now >= warnStartMs && now < nextPromptChangeMs) {
            int yellow = (0xAA << 24) | 0xFFFF00;
            // slight pulse thickness by phase
            int t = (phase > 0 ? 2 : 1);
            for (int i = 0; i < t; i++) {
                gfx.fill(boxLeft - i, boxTop - i, boxRight + i, boxTop + 1 - i, yellow);           // top
                gfx.fill(boxLeft - i, boxBottom - 1 + i, boxRight + i, boxBottom + i, yellow);     // bottom
                gfx.fill(boxLeft - i, boxTop, boxLeft + 1 - i, boxBottom, yellow);                 // left
                gfx.fill(boxRight - 1 + i, boxTop, boxRight + i, boxBottom, yellow);               // right
            }
        }

        // Swap flash overlay (brief white flash on swap)
        if (now < swapFlashUntilMs) {
            int white = (0x66 << 24) | 0xFFFFFF;
            gfx.fill(boxLeft, boxTop, boxRight, boxBottom, white);
        }

        // Wrong-input flash overlay
        if (now < wrongFlashUntilMs) {
            int redCol = (0x88 << 24) | 0xFF0000; // translucent red
            gfx.fill(boxLeft, boxTop, boxRight, boxBottom, redCol);
        }
    }

    private void drawButtonPresses(GuiGraphics gfx, int originX, int originY) {
        int srcU = BG_U + (originX - this.leftPos) + 61;
        int srcV = BG_V + (originY - this.topPos);

        boolean lmbHeld = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean rmbHeld = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean shiftHeld = hasShiftDown();


        if (lmbHeld) {
            gfx.blit(BG, originX, originY, srcU, srcV, IND_W +1, IND_H+1);
        }
        originY += IND_H + IND_SPACING;
        srcV += IND_H + IND_SPACING;

        // Row 1: RMB
        if (rmbHeld) {
            gfx.blit(BG, originX, originY, srcU, srcV, IND_W+1, IND_H+1);
        }

        originY += IND_H + IND_SPACING;
        srcV += IND_H + IND_SPACING;

        // Row 2: SHIFT
        if (shiftHeld) {
            gfx.blit(BG, originX, originY, srcU, srcV, IND_W+1, IND_H+1);
        }
    }
    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(this.font, this.title, BG_U + 4, BG_V + 2, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        long now = System.currentTimeMillis();
        if (now >= nextPromptChangeMs) pickNewPrompt(now);

        boolean rmbHeld = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean lmbHeld = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean shiftHeld = hasShiftDown();

        boolean inImmunity = now < swapImmunityUntilMs;

        // Wrong input = pressing any control that is NOT required by the current prompt
        boolean wrongActive = (!requireLmb && lmbHeld) || (!requireRmb && rmbHeld) || (!requireShift && shiftHeld);

        // During immunity, ignore wrongActive for meeting condition, but still require core inputs
        boolean meetsCore = (!requireLmb || lmbHeld) && (!requireRmb || rmbHeld) && (!requireShift || shiftHeld);
        boolean meets = meetsCore && (inImmunity || !wrongActive);

        boolean mouseInside = lastMouseX >= this.leftPos + GUI_X0 && lastMouseX <= this.leftPos + imageWidth
                && lastMouseY >= this.topPos + GUI_Y0 && lastMouseY <= this.topPos + GUI_Y1;
        // Only allow progress if the mouse is inside the intended minigame area and not over a slot, and valid input present
        boolean overSlot = this.hoveredSlot != null;

        boolean hasValidInput = false;
        if (!this.menu.slots.isEmpty() && this.menu.slots.get(0) != null && this.menu.slots.get(0).hasItem()) {
            hasValidInput = this.menu.slots.get(0).getItem().is(SolphyteItem.LUMINTHAE_FIBER.get());
        }
        boolean outputOccupied = this.menu.slots.size() > 2 && this.menu.slots.get(2) != null && this.menu.slots.get(2).hasItem();

        // Apply wrong-input punishment if actively pressing wrong controls (but not during immunity)
        if (!inImmunity && hasValidInput && !outputOccupied && mouseInside && !overSlot && wrongActive) {
            // cooldown to avoid spam while holding
            if (now - lastPenaltyMs >= 250L) {
                progress = Math.max(0f, progress - 0.18f);
                lastPenaltyMs = now;
                wrongFlashUntilMs = now + 140L;
                // play a short "bad" sound locally
                if (Minecraft.getInstance().player != null) {
                    var player = Minecraft.getInstance().player;
                    var level = player.level();
                    level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                            SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.7f, false);
                }
            }
        }

        float rateUp = 0.0125f;
        float rateDown = 0.0060f;
        // Prevent progress during immunity to give adjust buffer; allow decay but no punishment above
        if (!inImmunity && hasValidInput && !outputOccupied && meets && mouseInside && !overSlot) {
            progress = Math.min(1f, progress + rateUp);
        } else {
            progress = Math.max(0f, progress - rateDown);
        }

        if (progress >= 1f && hasValidInput && !outputOccupied) {
            SolphyteNetwork.sendToServer(new PressCompleteC2SPacket(this.menu.getBlockPos()));
            progress = 0f;
            // Notify server to complete the press action at this block position
        }
    }

    private void pickNewPrompt(long nowMs) {
        java.util.Random r = new java.util.Random();
        boolean lmb = r.nextBoolean();
        boolean rmb = !lmb;
        boolean shf = r.nextInt(4) == 0;

        this.requireLmb = lmb;
        this.requireRmb = rmb;
        this.requireShift = shf;
        long delay = 1500 + r.nextInt(1501);
        this.nextPromptChangeMs = nowMs + delay;
        this.warnStartMs = nowMs + Math.max(0L, delay - PRE_SWAP_WARN_MS);
        this.swapImmunityUntilMs = nowMs + SWAP_IMMUNITY_MS;
        this.swapFlashUntilMs = nowMs + SWAP_FLASH_MS;

        // subtle swap click
        if (Minecraft.getInstance().player != null) {
            var player = Minecraft.getInstance().player;
            var level = player.level();
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25f, 1.2f, false);
        }
    }

    private static boolean isMouseDown(int glfwButton) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) return false;
        return GLFW.glfwGetMouseButton(window, glfwButton) == GLFW.GLFW_PRESS;
    }
}
