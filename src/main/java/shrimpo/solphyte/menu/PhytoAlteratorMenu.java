package shrimpo.solphyte.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import shrimpo.solphyte.blockentity.PhytoAlteratorBlockEntity;
import shrimpo.solphyte.registry.SolphyteBlock;
import shrimpo.solphyte.registry.SolphyteMenu;

public class PhytoAlteratorMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerLevelAccess access;

    // Buffer-based ctor used by IForgeMenuType
    public PhytoAlteratorMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, resolveContainer(playerInv, buf.readBlockPos()), buf.readBlockPos());
    }

    // Director used by BE
    public PhytoAlteratorMenu(int id, Inventory playerInv, Container container, BlockPos pos) {
        super(SolphyteMenu.PHYTO_ALTERATOR.get(), id);
        this.container = container;
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);

        // 3x3 grid: indices 0-8
        int startX = 44; // centered-ish
        int startY = 17;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.container, row * 3 + col, startX + col * 18, startY + row * 18));
            }
        }

        // Player inventory 9x3
        int invY = 84;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        // Hotbar
        int hotbarY = invY + 58;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, hotbarY));
        }
    }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player.level() != null) {
            if (inv.player.level().getBlockEntity(pos) instanceof PhytoAlteratorBlockEntity be) {
                return be;
            }
        }
        // Fallback to a dummy container to avoid NPE if something goes wrong
        return new Container() {
            @Override public int getContainerSize() { return 9; }
            @Override public boolean isEmpty() { return true; }
            @Override public ItemStack getItem(int p_18941_) { return ItemStack.EMPTY; }
            @Override public ItemStack removeItem(int p_18942_, int p_18943_) { return ItemStack.EMPTY; }
            @Override public ItemStack removeItemNoUpdate(int p_18951_) { return ItemStack.EMPTY; }
            @Override public void setItem(int p_18944_, ItemStack p_18945_) {}
            @Override public void setChanged() {}
            @Override public boolean stillValid(Player p_18946_) { return false; }
            @Override public void clearContent() {}
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, SolphyteBlock.PHYTO_ALTERATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            int beSlots = 9;

            if (index < beSlots) {
                // Move from BE -> player inventory
                if (!this.moveItemStackTo(slotStack, beSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Move from player -> BE
                if (!this.moveItemStackTo(slotStack, 0, beSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}
