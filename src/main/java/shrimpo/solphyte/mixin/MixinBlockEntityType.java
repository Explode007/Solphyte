package shrimpo.solphyte.mixin;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import shrimpo.solphyte.registry.SolphyteBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(BlockEntityType.class)
public class MixinBlockEntityType {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void solphyte$includeSolarBlastedSand(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state != null && state.is(SolphyteBlock.SOLAR_BLASTED_SAND.get())) {
            cir.setReturnValue(true);
        }
    }
}

