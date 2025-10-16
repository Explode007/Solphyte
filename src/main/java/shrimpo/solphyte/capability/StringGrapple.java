package shrimpo.solphyte.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = Solphyte.MODID)
public class StringGrapple {
    public static final Capability<StringGrapple> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private boolean active = false;
    private Vec3 anchor = Vec3.ZERO;
    private int startTick = 0;
    private double ropeLength = 0.0; // current allowed distance from anchor (rope slack)
    private int nextAvailableTick = 0; // server tick when grapple can be used again
    private boolean usingExistingNode = false; // true if current grapple used a pre-existing node
    private boolean lastUsingExistingNode = false; // snapshot at start, used when stopping to decide cooldown
    private int fallGraceEndTick = 0; // until this server tick, reduce/clear fall damage after release

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Vec3 getAnchor() { return anchor; }
    public void setAnchor(Vec3 anchor) { this.anchor = anchor; }

    public int getStartTick() { return startTick; }
    public void setStartTick(int startTick) { this.startTick = startTick; }

    public double getRopeLength() { return ropeLength; }
    public void setRopeLength(double ropeLength) { this.ropeLength = ropeLength; }

    public int getNextAvailableTick() { return nextAvailableTick; }
    public void setNextAvailableTick(int t) { this.nextAvailableTick = t; }

    public boolean isUsingExistingNode() { return usingExistingNode; }
    public void setUsingExistingNode(boolean v) { this.usingExistingNode = v; }

    public boolean getLastUsingExistingNode() { return lastUsingExistingNode; }
    public void setLastUsingExistingNode(boolean v) { this.lastUsingExistingNode = v; }

    public int getFallGraceEndTick() { return fallGraceEndTick; }
    public void setFallGraceEndTick(int t) { this.fallGraceEndTick = t; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putDouble("ax", anchor.x);
        tag.putDouble("ay", anchor.y);
        tag.putDouble("az", anchor.z);
        tag.putInt("start", startTick);
        tag.putDouble("rope", ropeLength);
        tag.putInt("cooldown", nextAvailableTick);
        tag.putBoolean("usingExisting", usingExistingNode);
        tag.putBoolean("lastUsingExisting", lastUsingExistingNode);
        tag.putInt("fallGraceEnd", fallGraceEndTick);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        active = tag.getBoolean("active");
        double ax = tag.getDouble("ax");
        double ay = tag.getDouble("ay");
        double az = tag.getDouble("az");
        anchor = new Vec3(ax, ay, az);
        startTick = tag.getInt("start");
        ropeLength = tag.contains("rope") ? tag.getDouble("rope") : 0.0;
        nextAvailableTick = tag.getInt("cooldown");
        usingExistingNode = tag.getBoolean("usingExisting");
        lastUsingExistingNode = tag.getBoolean("lastUsingExisting");
        fallGraceEndTick = tag.getInt("fallGraceEnd");
    }

    public static class Provider implements ICapabilityProvider, ICapabilitySerializable<CompoundTag> {
        private final StringGrapple instance = new StringGrapple();
        private final LazyOptional<StringGrapple> lazy = LazyOptional.of(() -> instance);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == CAPABILITY ? lazy.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() { return instance.serializeNBT(); }

        @Override
        public void deserializeNBT(CompoundTag nbt) { instance.deserializeNBT(nbt); }
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Object> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(Solphyte.MODID, "string_grapple"), new Provider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(CAPABILITY).ifPresent(oldCap -> {
            event.getEntity().getCapability(CAPABILITY).ifPresent(newCap -> newCap.deserializeNBT(oldCap.serializeNBT()));
        });
    }
}
