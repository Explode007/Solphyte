package shrimpo.solphyte.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.item.PlantSampleItem;
import shrimpo.solphyte.menu.MicroscopeMenu;
import shrimpo.solphyte.registry.SolphyteBlockEntity;
import shrimpo.solphyte.registry.SolphyteTags;
import shrimpo.solphyte.data.PlantMappings;

import java.util.Random;

public class MicroscopeBlockEntity extends BlockEntity implements Clearable, Container, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private int foundCount = 0; // client-synced via menu dataslot
    private long puzzleSeed = 0L;

    public MicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(SolphyteBlockEntity.MICROSCOPE.get(), pos, state);
    }

    public NonNullList<ItemStack> getItems() { return items; }

    public int getFoundCount() { return foundCount; }
    public void setFoundCount(int v) { this.foundCount = Math.max(0, v); setChanged(); }

    public long getPuzzleSeed() { return puzzleSeed; }
    public int getPuzzleSeedLo() { return (int)(puzzleSeed & 0xFFFFFFFFL); }
    public int getPuzzleSeedHi() { return (int)((puzzleSeed >>> 32) & 0xFFFFFFFFL); }
    public void setPuzzleSeed(long seed) { this.puzzleSeed = seed; setChanged(); }
    public void setPuzzleSeedParts(int lo, int hi) { this.puzzleSeed = ((long)hi << 32) | (lo & 0xFFFFFFFFL); setChanged(); }
    public void ensureSeed() { if (this.puzzleSeed == 0L) { this.puzzleSeed = new Random().nextLong(); setChanged(); } }
    public void resetPuzzle() { this.foundCount = 0; this.puzzleSeed = new Random().nextLong(); setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
        tag.putInt("FoundCount", this.foundCount);
        tag.putLong("PuzzleSeed", this.puzzleSeed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, this.items);
        if (tag.contains("FoundCount")) this.foundCount = tag.getInt("FoundCount");
        if (tag.contains("PuzzleSeed")) this.puzzleSeed = tag.getLong("PuzzleSeed");
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public ItemStack removeItem(int index, int count) { ItemStack s = ContainerHelper.removeItem(items, index, count); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int index) { ItemStack s = items.get(index); if (s.isEmpty()) return ItemStack.EMPTY; items.set(index, ItemStack.EMPTY); return s; }
    @Override public void setItem(int index, ItemStack stack) { items.set(index, stack); if (stack.getCount() > stack.getMaxStackSize()) stack.setCount(stack.getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0; }
    @Override public void clearContent() { for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY); }

    // Enforce allowed items for automation (hoppers etc.)
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == 0) return stack != null && !stack.isEmpty() && stack.is(SolphyteTags.Items.MICROSCOPE_INPUTS);
        if (index == 4) return false; // output only
        return false; // disallow unused internal slots
    }

    @Override public Component getDisplayName() { return Component.translatable("menu.solphyte.microscope"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { ensureSeed(); return new MicroscopeMenu(id, inv, this, this.worldPosition); }

    // Called by server when client reports a found sample
    public void onFoundOne() {
        if (this.level == null || this.level.isClientSide) return;
        if (!hasValidInput()) return;
        if (this.items.get(4).isEmpty()) {
            this.foundCount++;
            if (this.foundCount >= 5) {
                craftOutput();
                this.foundCount = 0;
                this.puzzleSeed = new Random().nextLong();
            }
            setChanged();
        }
    }

    private boolean hasValidInput() {
        ItemStack s = this.items.get(0);
        return isValidPlantInput(s);
    }

    private static boolean isValidPlantInput(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(SolphyteTags.Items.MICROSCOPE_INPUTS);
    }

    private void craftOutput() {
        if (!this.items.get(4).isEmpty()) return;
        ItemStack s = this.items.get(0);
        if (isValidPlantInput(s)) {
            ResourceLocation id = resolvePlantBlockId(s);
            if (id != null) {
                this.items.set(4, PlantSampleItem.of(id));
                s.shrink(1);
                if (s.getCount() <= 0) this.items.set(0, ItemStack.EMPTY);
            }
        }
    }

    private ResourceLocation resolvePlantBlockId(ItemStack stack) {
        Item it = stack.getItem();
        // 1) If a datapack mapping exists for this seed item, use it
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(it);
        if (itemId != null) {
            ResourceLocation mapped = PlantMappings.getMappedBlock(itemId);
            if (mapped != null) return mapped;
        }
        // 2) If the item is a BlockItem (via Block.byItem), use its block's ID directly (covers cactus, sugar cane, nether wart, etc.)
        Block b = Block.byItem(it);
        if (b != null && b != net.minecraft.world.level.block.Blocks.AIR) {
            return b.builtInRegistryHolder().key().location();
        }
        // 3) If it's an IPlantable (e.g., seeds), ask for its plant block state
        if (it instanceof IPlantable plantable) {
            if (this.level != null) {
                BlockState plantState = plantable.getPlant(this.level, this.worldPosition);
                Block pb = plantState.getBlock();
                if (pb != null) return pb.builtInRegistryHolder().key().location();
            }
        }
        return null;
    }
}
