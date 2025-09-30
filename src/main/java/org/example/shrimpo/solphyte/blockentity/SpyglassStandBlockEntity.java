package org.example.shrimpo.solphyte.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.example.shrimpo.solphyte.registry.SolphyteBlockEntity;

public class SpyglassStandBlockEntity extends BlockEntity {

    public static final int STAIR_STEPS = 32; // required clear steps behind the stand

    // Client-only visual state (not persisted)
    private float beamProgress;      // 0..1 eased in
    private float prevBeamProgress;  // for interpolation
    private int burnDelayTicks;      // counts up once fully active

    public SpyglassStandBlockEntity(BlockPos pos, BlockState state) {
        super(SolphyteBlockEntity.SPYGLASS_STAND.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SpyglassStandBlockEntity be) {
        // Animate only on client; server does nothing
        if (level == null || !level.isClientSide) return;

        boolean shouldActive = isDayFunctional(level)
                && hasClearStaircaseBehind(level, pos, state.getValue(org.example.shrimpo.solphyte.block.SpyglassStandBlock.FACING))
                && immediateFrontOk(level, pos, state.getValue(org.example.shrimpo.solphyte.block.SpyglassStandBlock.FACING));

        be.prevBeamProgress = be.beamProgress;
        float speedIn = 0.05f;   // seconds ~1s to full (20 ticks)
        float speedOut = 0.10f;  // fade out faster
        if (shouldActive) {
            be.beamProgress = Math.min(1.0f, be.beamProgress + speedIn);
        } else {
            be.beamProgress = Math.max(0.0f, be.beamProgress - speedOut);
        }

        if (be.beamProgress >= 1.0f && shouldActive) {
            // Wait a short time before enabling burn effects
            be.burnDelayTicks = Math.min(be.burnDelayTicks + 1, 40); // cap
        } else {
            be.burnDelayTicks = 0;
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
        // must be air directly in front
        if (!level.isEmptyBlock(front)) return false;
        // must have a floor block in front-down
        return !level.isEmptyBlock(frontDown);
    }

    public static Vec3 beamStartLocal() {
        return new Vec3(0.5, 1.0 + (1.0/16.0), 0.5);
    }

    public static Vec3 beamEndLocal(BlockPos pos, Direction facing) {
        BlockPos frontDown = pos.relative(facing).below();
        // local to block origin
        return new Vec3(frontDown.getX() + 0.5 - pos.getX(), frontDown.getY() + 1.0 - pos.getY(), frontDown.getZ() + 0.5 - pos.getZ());
    }
}
