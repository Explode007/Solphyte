package shrimpo.solphyte.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class LuminthaeShotItem extends Item {
    private final Supplier<MobEffectInstance> effectSupplier;

    public LuminthaeShotItem(Properties properties, Supplier<MobEffectInstance> effectSupplier) {
        super(properties);
        this.effectSupplier = effectSupplier;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 24; // quick use
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK; // placeholder animation
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            MobEffectInstance inst = effectSupplier.get();
            if (inst != null) {
                entity.addEffect(new MobEffectInstance(inst));
            }
            if (entity instanceof Player player) {
                player.awardStat(Stats.ITEM_USED.get(this));
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            } else {
                stack.shrink(1);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MobEffectInstance inst = effectSupplier.get();
        if (inst != null) {
            Component effectName = Component.translatable(inst.getDescriptionId());
            int totalSeconds = Math.max(0, inst.getDuration() / 20);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String time = String.format("%d:%02d", minutes, seconds);
            tooltip.add(Component.translatable("tooltip.solphyte.applies_effect", effectName, time));
        }
    }
}
