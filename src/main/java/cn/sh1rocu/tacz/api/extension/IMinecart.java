package cn.sh1rocu.tacz.api.extension;

import net.minecraft.world.entity.vehicle.AbstractMinecart;

public interface IMinecart {
    default boolean canBeRidden() {
        return ((AbstractMinecart) this).getMinecartType() == AbstractMinecart.Type.RIDEABLE;
    }
}
