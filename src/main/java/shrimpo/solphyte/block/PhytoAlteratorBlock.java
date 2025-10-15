package shrimpo.solphyte.block;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import shrimpo.solphyte.blockentity.PhytoAlteratorBlockEntity;

import javax.annotation.Nullable;
import java.util.EnumMap;

public class PhytoAlteratorBlock extends BaseEntityBlock {

    public PhytoAlteratorBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, Part.LEFT)
                .setValue(FACING, Direction.NORTH));
    }

    public enum Part implements net.minecraft.util.StringRepresentable { LEFT, RIGHT;
        @Override
        public String getSerializedName() { return name().toLowerCase(); }
    }
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING; // front of workstation

    private static final EnumMap<Direction, VoxelShape> LEFT_SHAPES = new EnumMap<>(Direction.class);
    private static final EnumMap<Direction, VoxelShape> RIGHT_SHAPES = new EnumMap<>(Direction.class);

    // Model elements (from the Blockbench JSON for phyto_alterator_half)
    private static final double[][] ELEMENTS = new double[][]{
            {1, 0, 2, 3, 10, 4},
            {1, 10, 2, 15, 11, 16},
            {2, 3, 3, 14, 4, 16},
            {13, 0, 2, 15, 10, 4}
    };

    static {
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            int rightAngle = switch (dir) {
                case NORTH -> 90;
                case EAST -> 180;
                case SOUTH -> 270;
                default -> 0;
            };
            int leftAngle = switch (dir) {
                case NORTH -> 270;
                case EAST -> 0;
                case SOUTH -> 90;
                default -> 180;
            };
            LEFT_SHAPES.put(dir, buildShape(leftAngle));
            RIGHT_SHAPES.put(dir, buildShape(rightAngle));
        }
    }

    private static VoxelShape buildShape(int angle) {
        VoxelShape s = Shapes.empty();
        for (double[] e : ELEMENTS) {
            double[] r = rotateBox(e[0], e[1], e[2], e[3], e[4], e[5], angle);
            s = Shapes.or(s, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
        }
        return s;
    }

    // Rotate a single element box by a Y rotation (0/90/180/270) around the block center XZ = 8.0
    private static double[] rotateBox(double x1, double y1, double z1, double x2, double y2, double z2, int angle) {
        double cx = 8.0, cz = 8.0;
        double[] xs = new double[4];
        double[] zs = new double[4];
        double[] rx = new double[]{x1 - cx, x1 - cx, x2 - cx, x2 - cx};
        double[] rz = new double[]{z1 - cz, z2 - cz, z1 - cz, z2 - cz};
        int a = ((angle % 360) + 360) % 360;
        for (int i = 0; i < 4; i++) {
            double nx, nz;
            if (a == 0) { nx = rx[i]; nz = rz[i]; }
            else if (a == 90) { nx = -rz[i]; nz = rx[i]; }
            else if (a == 180) { nx = -rx[i]; nz = -rz[i]; }
            else { nx = rz[i]; nz = -rx[i]; }
            xs[i] = nx + cx;
            zs[i] = nz + cz;
        }
        double nx1 = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double nx2 = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        double nz1 = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        double nz2 = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        return new double[]{nx1, y1, nz1, nx2, y2, nz2};
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
