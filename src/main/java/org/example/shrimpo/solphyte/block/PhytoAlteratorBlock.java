package org.example.shrimpo.solphyte.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.example.shrimpo.solphyte.blockentity.PhytoAlteratorBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

/**
 * Two-block wide workstation. The block placed by the player becomes the LEFT part; it automatically
 * places the RIGHT part to the clockwise side (relative to facing). Only the LEFT part owns a BlockEntity
 * that stores materials (simple inventory for now). Breaking either part removes the other and drops inventory.
 * Basic interaction logic: right-click with item adds one to internal storage; empty hand extracts latest.
 */
public class PhytoAlteratorBlock extends BaseEntityBlock {
    public enum Part implements net.minecraft.util.StringRepresentable { LEFT, RIGHT;
        @Override
        public String getSerializedName() { return name().toLowerCase(); }
    }
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING; // front of workstation

    // Base boxes correspond to LEFT part with FACING = EAST (blockstate y=0). Other facings rotate clockwise.
    private static final AABB[] BASE_BOXES = new AABB[]{
            // pillar left front (from [1,0,2] to [3,10,4])
            new AABB(1/16.0, 0, 2/16.0, 3/16.0, 10/16.0, 4/16.0),
            // top slab (from [1,10,2] to [15,11,16])
            new AABB(1/16.0, 10/16.0, 2/16.0, 15/16.0, 11/16.0, 16/16.0),
            // mid plate (from [2,3,3] to [14,4,16])
            new AABB(2/16.0, 3/16.0, 3/16.0, 14/16.0, 4/16.0, 16/16.0),
            // pillar right front (from [13,0,2] to [15,10,4])
            new AABB(13/16.0, 0, 2/16.0, 15/16.0, 10/16.0, 4/16.0)
    };

    private static final EnumMap<Direction, VoxelShape> LEFT_SHAPES = new EnumMap<>(Direction.class);
    private static final EnumMap<Direction, VoxelShape> RIGHT_SHAPES = new EnumMap<>(Direction.class);

    // Base facing (the direction the unrotated model represents for the LEFT part). Adjust if hitboxes still misalign.
    private static final Direction BASE_FACING = Direction.EAST;

    static {
        // Precompute shapes for each horizontal direction
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            LEFT_SHAPES.put(dir, buildShape(dir, false));
            RIGHT_SHAPES.put(dir, buildShape(dir, true));
        }
    }

    private static VoxelShape buildShape(Direction facing, boolean rightPart) {
        VoxelShape shape = Shapes.empty();
        int facingSteps = rotationSteps(facing);
        int baseSteps = rotationSteps(BASE_FACING);
        int delta = (facingSteps - baseSteps + 4) % 4; // rotation needed from base to target
        int extraRot = rightPart ? 2 : 0; // add 180° for right part
        int total = (delta + extraRot) % 4;
        for (AABB box : BASE_BOXES) {
            AABB rotated = rotateAabb(box, total);
            shape = Shapes.or(shape, Shapes.create(rotated));
        }
        return shape.optimize();
    }

    // Map facing to rotation steps (0=NORTH,1=EAST,2=SOUTH,3=WEST)
    private static int rotationSteps(Direction facing) {
        return switch (facing) {
            case EAST -> 0;   // base orientation
            case SOUTH -> 1;  // 90° CW from base
            case WEST -> 2;   // 180°
            case NORTH -> 3;  // 270°
            default -> 0;
        };
    }

    // Rotate an AABB around block center (8,8,8) by 90° * steps clockwise
    private static AABB rotateAabb(AABB box, int steps) {
        if (steps == 0) return box;
        double minX = box.minX * 16.0;
        double minZ = box.minZ * 16.0;
        double maxX = box.maxX * 16.0;
        double maxZ = box.maxZ * 16.0;
        double[][] pts = new double[][]{{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}};
        double newMinX = 16, newMinZ = 16, newMaxX = 0, newMaxZ = 0;
        for (double[] p : pts) {
            double x = p[0];
            double z = p[1];
            for (int i = 0; i < steps; i++) { // rotate 90° clockwise about center (8,8)
                double relX = x - 8; double relZ = z - 8;
                double rx = 8 + relZ;          // x' = z
                double rz = 8 - relX;          // z' = -x
                x = rx; z = rz;
            }
            if (x < newMinX) newMinX = x;
            if (x > newMaxX) newMaxX = x;
            if (z < newMinZ) newMinZ = z;
            if (z > newMaxZ) newMaxZ = z;
        }
        return new AABB(newMinX/16.0, box.minY, newMinZ/16.0, newMaxX/16.0, box.maxY, newMaxZ/16.0);
    }

    public PhytoAlteratorBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, Part.LEFT)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite(); // face player
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        Direction right = facing.getClockWise();
        BlockPos otherPos = pos.relative(right);
        if (!level.getBlockState(otherPos).canBeReplaced(ctx)) { return null; }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == Part.LEFT) {
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();
            BlockPos otherPos = pos.relative(right);
            BlockState rightState = state.setValue(PART, Part.RIGHT);
            level.setBlock(otherPos, rightState, 3);
            level.blockUpdated(pos, this);
            level.blockUpdated(otherPos, this);
        }
    }

    private static BlockPos leftPos(BlockState state, BlockPos pos) {
        if (state.getValue(PART) == Part.LEFT) return pos;
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        return pos.relative(right.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        BlockPos expectedOther = state.getValue(PART) == Part.LEFT ? pos.relative(right) : pos.relative(right.getOpposite());
        if (neighborPos.equals(expectedOther) && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();
            BlockPos otherPos = state.getValue(PART) == Part.LEFT ? pos.relative(right) : pos.relative(right.getOpposite());
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this)) {
                level.destroyBlock(otherPos, false, player);
            }
            if (state.getValue(PART) == Part.LEFT) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof PhytoAlteratorBlockEntity pa) {
                    Containers.dropContents(level, pos, pa.getItems());
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext ctx) {
        Direction facing = state.getValue(FACING);
        boolean right = state.getValue(PART) == Part.RIGHT;
        return right ? RIGHT_SHAPES.get(facing) : LEFT_SHAPES.get(facing);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean right = state.getValue(PART) == Part.RIGHT;
        return right ? RIGHT_SHAPES.get(facing) : LEFT_SHAPES.get(facing);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(PART) == Part.LEFT ? new PhytoAlteratorBlockEntity(pos, state) : null; }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock() && state.getValue(PART) == Part.LEFT) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PhytoAlteratorBlockEntity pa) {
                Containers.dropContents(level, pos, pa.getItems());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS; // let server handle opening
        BlockPos leftPos = leftPos(state, pos);
        BlockEntity be = level.getBlockEntity(leftPos);
        if (be instanceof PhytoAlteratorBlockEntity pa) {
            NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, pa, buf -> buf.writeBlockPos(leftPos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) { return true; }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        if (adjacentState.getBlock() == this) {
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();
            boolean isLeft = state.getValue(PART) == Part.LEFT;
            if (isLeft && direction == right) return true;
            if (!isLeft && direction == right.getOpposite()) return true;
        }
        return super.skipRendering(state, adjacentState, direction);
    }
}
