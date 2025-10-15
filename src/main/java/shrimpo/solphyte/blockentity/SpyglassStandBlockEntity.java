package shrimpo.solphyte.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.block.SpyglassStandBlock;
import shrimpo.solphyte.registry.SolphyteBlock;
import shrimpo.solphyte.registry.SolphyteBlockEntity;

public class SpyglassStandBlockEntity extends BlockEntity {

    public static final int STAIR_STEPS = 32; // required clear steps behind the stand

    // How many server ticks (20 ticks = 1s) to expose sand before conversion. Default 1200 = 60s.
    public static final int SAND_EXPOSURE_THRESHOLD = 1200; // 60s

    // Client-only visual state (not persisted)
    private float beamProgress;      // 0..1 eased in
    private float prevBeamProgress;  // for interpolation
    private int burnDelayTicks;      // counts up once fully active

    // Track the sand block being hit and exposure time
    private BlockPos lastTargetedSand = null;
    private int sandExposureTicks = 0;

    public SpyglassStandBlockEntity(BlockPos pos, BlockState state) {
        super(SolphyteBlockEntity.SPYGLASS_STAND.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SpyglassStandBlockEntity be) {
        // Ensure level non-null (shouldn't happen in normal ticking)
        if (level == null) return;

        Direction facing = state.getValue(SpyglassStandBlock.FACING);
        boolean shouldActive = isDayFunctional(level)
                && hasClearStaircaseBehind(level, pos, facing)
                && immediateFrontOk(level, pos, facing);

        // Client-side: handle visuals
        if (level.isClientSide) {
            be.prevBeamProgress = be.beamProgress;
            float speedIn = 0.05f;   // seconds ~1s to full (20 ticks)
            float speedOut = 0.10f;  // fade out faster
            if (shouldActive) {
                be.beamProgress = Math.min(1.0f, be.beamProgress + speedIn);
            } else {
                be.beamProgress = Math.max(0.0f, be.beamProgress - speedOut);
            }

            // Advance/reset burn delay once beam is essentially fully formed
            if (be.beamProgress >= 0.999f && shouldActive) {
                be.burnDelayTicks = Math.min(10000, be.burnDelayTicks + 1);
            } else {
                be.burnDelayTicks = 0;
            }
        }

        // Server-side: handle sand transformation
        if (!level.isClientSide && shouldActive) {
            // Mirror the renderer's aim: if there's a block directly in front, target that; otherwise target the floor in front-down
            BlockPos front = pos.relative(facing);
            BlockPos frontDown = front.below();
            BlockPos target = !level.isEmptyBlock(front) ? front : frontDown;
            BlockState targetState = level.getBlockState(target);
            boolean isSand = targetState.is(Blocks.SAND);
            if (isSand) {
                if (be.lastTargetedSand == null || !be.lastTargetedSand.equals(target)) {
                    be.lastTargetedSand = target;
                    be.sandExposureTicks = 1;
                } else {
                    be.sandExposureTicks++;
                }
                if (be.sandExposureTicks >= SAND_EXPOSURE_THRESHOLD) {
                    // Replace sand with solar_blasted_sand
                    level.setBlock(target, SolphyteBlock.SOLAR_BLASTED_SAND.get().defaultBlockState(), 3);
                    // Ensure the newly-placed block entity has the brush loot table so players can brush it
                    BlockEntity newBe = level.getBlockEntity(target);
                    if (newBe instanceof BrushableBlockEntity bbe) {
                        bbe.setLootTable(ResourceLocation.fromNamespaceAndPath(Solphyte.MODID, "archaeology/solar_blasted_sand"), level.getRandom().nextLong());
                    }

                    be.sandExposureTicks = 0;
                    be.lastTargetedSand = null;
                }
            } else {
                be.sandExposureTicks = 0;
                be.lastTargetedSand = null;
            }
        }
    }

    // Interpolated 0..1 progress for smooth scaling/alpha
    public float getBeamProgress(float partialTicks) {
        return this.prevBeamProgress + (this.beamProgress - this.prevBeamProgress) * partialTicks;
    }

    // Only start end-fire effects once beam is fully formed for a bit
    public boolean isBurningActive() {
        return this.beamProgress >= 0.999f && this.burnDelayTicks >= 10; // ~0.5s after full
    }

    public static boolean isDayFunctional(Level level) {
        if (level == null) return false;
        long t = level.getDayTime() % 24000L;
        if (t < 0) t += 24000L;
        return t < 12000L; // vanilla day roughly 0..12000
    }

    public static boolean hasClearStaircaseBehind(Level level, BlockPos pos, Direction facing) {
        Direction back = facing.getOpposite();
        for (int i = 1; i <= STAIR_STEPS; i++) {
            BlockPos check = pos.relative(back, i).above(i);
            if (!level.isEmptyBlock(check)) {
                return false;
            }
        }
        return true;
    }

    public static boolean immediateFrontOk(Level level, BlockPos pos, Direction facing) {
        BlockPos front = pos.relative(facing);
        BlockPos frontDown = front.below();
        // must have a floor block in front-down OR a sand/blasted-sand block directly in front on same level
        if (level.isEmptyBlock(front) && !level.isEmptyBlock(frontDown)) return true;
        BlockState frontState = level.getBlockState(front);
        return frontState.is(Blocks.SAND) || frontState.is(SolphyteBlock.SOLAR_BLASTED_SAND.get());
    }

    public static Vec3 beamStartLocal() {
        return new Vec3(0.5, 1.0 + (1.0/16.0), 0.5);
    }

    public static Vec3 beamEndLocal(Level level, BlockPos pos, Direction facing) {
        BlockPos front = pos.relative(facing);
        BlockPos frontDown = front.below();
        // If there's a block directly in front (e.g., sand or blasted sand), aim at its top center
        if (!level.isEmptyBlock(front)) {
            return new Vec3(front.getX() + 0.5 - pos.getX(), front.getY() + 1.0 - pos.getY(), front.getZ() + 0.5 - pos.getZ());
        }
        // Otherwise aim at the floor in front-down (default)
        return new Vec3(frontDown.getX() + 0.5 - pos.getX(), frontDown.getY() + 1.0 - pos.getY(), frontDown.getZ() + 0.5 - pos.getZ());
    }
}
