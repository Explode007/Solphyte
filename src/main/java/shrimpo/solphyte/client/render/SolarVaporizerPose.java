package shrimpo.solphyte.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.item.SolarVaporizerItem;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SolarVaporizerPose {

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!player.isUsingItem()) return;
        ItemStack using = player.getUseItem();
        if (using.isEmpty() || !(using.getItem() instanceof SolarVaporizerItem)) return;

        // Force bow-like arm poses in third person only. This event runs for world render, not first-person hand renderer.
        HumanoidModel<?> model = event.getRenderer().getModel();
        if (model == null) return;

        // Apply bow-and-arrow arm poses to both arms to mimic aiming
        model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
    }
}

