package shrimpo.solphyte.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import shrimpo.solphyte.modeldata.MutatedCropModelData;
import shrimpo.solphyte.registry.SolphyteBlockEntity;

public class MutatedCropBlockEntity extends BlockEntity {
    private ResourceLocation primary;
    private ResourceLocation secondary;

    public MutatedCropBlockEntity(BlockPos pos, BlockState state) {
        super(SolphyteBlockEntity.MUTATED_CROP.get(), pos, state);
    }

    public ResourceLocation getPrimaryId() { return primary; }
    public ResourceLocation getSecondaryId() { return secondary; }

    public void setPair(ResourceLocation primary, ResourceLocation secondary) {
        this.primary = primary;
        this.secondary = secondary;
        setChanged();
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (primary != null) tag.putString("Primary", primary.toString());
        if (secondary != null) tag.putString("Secondary", secondary.toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Primary")) primary = new ResourceLocation(tag.getString("Primary"));
        if (tag.contains("Secondary")) secondary = new ResourceLocation(tag.getString("Secondary"));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        requestModelDataUpdate();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt != null) {
            CompoundTag tag = pkt.getTag();
            if (tag != null) handleUpdateTag(tag);
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(MutatedCropModelData.PRIMARY, primary)
                .with(MutatedCropModelData.SECONDARY, secondary)
                .build();
    }
}
