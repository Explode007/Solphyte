package shrimpo.solphyte.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import shrimpo.solphyte.menu.PressMenu;
import shrimpo.solphyte.registry.SolphyteBlockEntity;

public class PressBlockEntity extends BlockEntity implements Clearable, Container, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);

    public PressBlockEntity(BlockPos pos, BlockState state) {
        super(SolphyteBlockEntity.PRESS.get(), pos, state);
    }

    public NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, this.items);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public ItemStack removeItem(int index, int count) { ItemStack s = ContainerHelper.removeItem(items, index, count); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int index) { ItemStack s = items.get(index); if (s.isEmpty()) return ItemStack.EMPTY; items.set(index, ItemStack.EMPTY); return s; }
    @Override public void setItem(int index, ItemStack stack) { items.set(index, stack); if (stack.getCount() > stack.getMaxStackSize()) stack.setCount(stack.getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0; }
    @Override public void clearContent() { for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY); }

    @Override public Component getDisplayName() { return Component.translatable("menu.solphyte.press"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new PressMenu(id, inv, this, this.worldPosition); }
}

