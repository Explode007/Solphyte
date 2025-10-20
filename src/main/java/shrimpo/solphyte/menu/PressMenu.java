package shrimpo.solphyte.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import shrimpo.solphyte.blockentity.PressBlockEntity;
import shrimpo.solphyte.registry.SolphyteBlock;
import shrimpo.solphyte.registry.SolphyteMenu;

public class PressMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerLevelAccess access;

    public PressMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, resolveContainer(playerInv, buf.readBlockPos()), buf.readBlockPos());
    }

    public PressMenu(int id, Inventory playerInv, Container container, BlockPos pos) {
        super(SolphyteMenu.PRESS.get(), id);
        this.container = container;
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);

        // Simple 2x2 for now
        int startX = 62;
        int startY = 17;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                this.addSlot(new Slot(this.container, row * 2 + col, startX + col * 18, startY + row * 18));
            }
        }

        // Player inventory
        int invY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        int hotbarY = invY + 58;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, hotbarY));
        }
    }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player.level() != null) {
            if (inv.player.level().getBlockEntity(pos) instanceof PressBlockEntity be) {
                return be;
            }
        }
        return new Container() {
            @Override public int getContainerSize() { return 4; }
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
    public boolean stillValid(Player player) { return stillValid(this.access, player, SolphyteBlock.PRESS.get()); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            int beSlots = 4;
            if (index < beSlots) {
                if (!this.moveItemStackTo(slotStack, beSlots, this.slots.size(), true)) { return ItemStack.EMPTY; }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, beSlots, false)) { return ItemStack.EMPTY; }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return itemstack;
    }
}

