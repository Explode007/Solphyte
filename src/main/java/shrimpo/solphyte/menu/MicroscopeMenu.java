package shrimpo.solphyte.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import shrimpo.solphyte.blockentity.MicroscopeBlockEntity;
import shrimpo.solphyte.registry.SolphyteBlock;
import shrimpo.solphyte.registry.SolphyteMenu;
import shrimpo.solphyte.registry.SolphyteTags;

public class MicroscopeMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerLevelAccess access;
    private final BlockPos bePos;
    @Nullable private final MicroscopeBlockEntity be;

    // client-synced
    private int syncedFound;
    private int seedLo;
    private int seedHi;

    public MicroscopeMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(bePosAware(id, playerInv,  buf.readBlockPos()));
    }

    private MicroscopeMenu(Object[] args) { this((int)args[0], (Inventory)args[1], (Container)args[2], (BlockPos)args[3]); }

    private static Object[] bePosAware(int id, Inventory playerInv, BlockPos pos) {
        return new Object[]{ id, playerInv, resolveContainer(playerInv, pos), pos };
    }

    public MicroscopeMenu(int id, Inventory playerInv, Container container, BlockPos pos) {
        super(SolphyteMenu.MICROSCOPE.get(), id);
        this.container = container;
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);
        this.bePos = pos;
        this.be = playerInv.player.level() != null && playerInv.player.level().getBlockEntity(pos) instanceof MicroscopeBlockEntity mb ? mb : null;

        // Input slot (container index 0) at (22,114)
        this.addSlot(new PlantInputSlot(this.container, 0, 22, 114));
        // Output slot (container index 4) at (220,115)
        this.addSlot(new OutputSlot(this.container, 4, 220, 115));

        // Player inventory aligned with the blitted generic_54 background at (x=40, y=198) relative to container origin.
        // Inside that 176x96 region, slots start at (8,14) for the first row and (8,72) for the hotbar.
        int invBgRelX = 40;
        int invBgRelY = 198;
        int invX = invBgRelX + 8;   // 48
        int invY = invBgRelY + 14;  // 212
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        int hotbarY = invY + 58; // 270
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, invX + col * 18, hotbarY));
        }

        // Sync found count and seed
        if (this.be != null) {
            this.addDataSlot(new DataSlot() {
                @Override public int get() { return be.getFoundCount(); }
                @Override public void set(int value) { syncedFound = value; }
            });
            this.addDataSlot(new DataSlot() {
                @Override public int get() { return be.getPuzzleSeedLo(); }
                @Override public void set(int value) { seedLo = value; }
            });
            this.addDataSlot(new DataSlot() {
                @Override public int get() { return be.getPuzzleSeedHi(); }
                @Override public void set(int value) { seedHi = value; }
            });
        } else {
            this.addDataSlot(new DataSlot() { private int v; @Override public int get() { return v; } @Override public void set(int value) { syncedFound = value; v = value; } });
            this.addDataSlot(new DataSlot() { private int v; @Override public int get() { return v; } @Override public void set(int value) { seedLo = value; v = value; } });
            this.addDataSlot(new DataSlot() { private int v; @Override public int get() { return v; } @Override public void set(int value) { seedHi = value; v = value; } });
        }
    }

    public BlockPos getBlockPos() { return bePos; }
    public int getFound() { return syncedFound; }
    public long getSeed() { return ((long)seedHi << 32) | (seedLo & 0xFFFFFFFFL); }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player.level() != null) {
            if (inv.player.level().getBlockEntity(pos) instanceof MicroscopeBlockEntity be) {
                return be;
            }
        }
        return new Container() {
            @Override public int getContainerSize() { return 5; }
            @Override public boolean isEmpty() { return true; }
            @Override public ItemStack getItem(int i) { return ItemStack.EMPTY; }
            @Override public ItemStack removeItem(int i, int c) { return ItemStack.EMPTY; }
            @Override public ItemStack removeItemNoUpdate(int i) { return ItemStack.EMPTY; }
            @Override public void setItem(int i, ItemStack s) {}
            @Override public void setChanged() {}
            @Override public boolean stillValid(Player p) { return false; }
            @Override public void clearContent() {}
        };
    }

    @Override
    public boolean stillValid(Player player) { return stillValid(this.access, player, SolphyteBlock.MICROSCOPE.get()); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            int beUiSlots = 2; // 0: input (container 0), 1: output (container 4)
            if (index < beUiSlots) {
                // from be to player
                if (!this.moveItemStackTo(slotStack, beUiSlots, this.slots.size(), true)) { return ItemStack.EMPTY; }
            } else {
                // from player to be input only (UI index 0)
                if (!this.moveItemStackTo(slotStack, 0, 1, false)) { return ItemStack.EMPTY; }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return itemstack;
    }

    private static class PlantInputSlot extends Slot {
        public PlantInputSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            // Strictly tag-driven acceptance; customize via data/solphyte/tags/items/microscope_inputs.json
            return stack.is(SolphyteTags.Items.MICROSCOPE_INPUTS);
        }
    }

    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
