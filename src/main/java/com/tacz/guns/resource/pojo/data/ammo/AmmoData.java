package com.tacz.guns.resource.pojo.data.ammo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class AmmoData {
    @SerializedName("parent_ammo")
    @Nullable
    private ResourceLocation parentAmmo;

    @SerializedName("damage")
    @Nullable
    private Float damage;

    @SerializedName("speed")
    @Nullable
    private Float speed;

    @SerializedName("knockback")
    @Nullable
    private Float knockback;

    @SerializedName("friction")
    @Nullable
    private Float friction;

    @SerializedName("pierce")
    @Nullable
    private Integer pierce;

    @SerializedName("tracer_color")
    @Nullable
    private String tracerColor;

    @Nullable
    public ResourceLocation getParentAmmo() {
        return parentAmmo;
    }

    @Nullable
    public Float getDamage() {
        return damage;
    }

    @Nullable
    public Float getSpeed() {
        return speed;
    }

    @Nullable
    public Float getKnockback() {
        return knockback;
    }

    @Nullable
    public Float getFriction() {
        return friction;
    }

    @Nullable
    public Integer getPierce() {
        return pierce;
    }

    @Nullable
    public String getTracerColor() {
        return tracerColor;
    }
}
