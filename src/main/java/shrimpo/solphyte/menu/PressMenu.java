package shrimpo.solphyte.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import shrimpo.solphyte.blockentity.PressBlockEntity;
import shrimpo.solphyte.registry.SolphyteBlock;
import shrimpo.solphyte.registry.SolphyteItem;
import shrimpo.solphyte.registry.SolphyteMenu;

public class PressMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerLevelAccess access;
    private final BlockPos bePos;

    public PressMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, resolveContainer(playerInv, buf.readBlockPos()), buf.readBlockPos());
    }

    public PressMenu(int id, Inventory playerInv, Container container, BlockPos pos) {
        super(SolphyteMenu.PRESS.get(), id);
        this.container = container;
        this.access = ContainerLevelAccess.create(playerInv.player.level(), pos);
        this.bePos = pos;


        // Main input (container 0)
        this.addSlot(new Slot(this.container, 0, 48, 37) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(SolphyteItem.LUMINTHAE_FIBER.get());
            }
        });
        // Petri dish input (container 1)
        this.addSlot(new Slot(this.container, 1, 19, 37) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(SolphyteItem.PETRI_DISH.get().asItem());
            }
        });
        // Output (container 4)
        this.addSlot(new Slot(this.container, 4, 48, 97) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });

        // Player inventory
        int invX = 8;
        int invY = 158;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        int hotbarY = invY + 58;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, invX + col * 18, hotbarY));
        }
    }

    public BlockPos getBlockPos() { return bePos; }

    private static Container resolveContainer(Inventory inv, BlockPos pos) {
        if (inv.player.level() != null) {
            if (inv.player.level().getBlockEntity(pos) instanceof PressBlockEntity be) {
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
    public boolean stillValid(Player player) { return stillValid(this.access, player, SolphyteBlock.PRESS.get()); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            int beUiSlots = 3; // 0: main input, 1: petri input, 2: output (container 4)
            if (index < beUiSlots) {
                // from be to player
                if (!this.moveItemStackTo(slotStack, beUiSlots, this.slots.size(), true)) { return ItemStack.EMPTY; }
            } else {
                // from player to be inputs only (UI indices 0..1)
                if (!this.moveItemStackTo(slotStack, 0, 2, false)) { return ItemStack.EMPTY; }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return itemstack;
    }
}
