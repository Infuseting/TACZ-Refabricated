package fr.infuseting.tacz.item;

import fr.infuseting.tacz.TaCZMagazines;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

public final class SoundRegistrar {

    public static final SoundEvent MAG_LOADING_EVENT = SoundEvent.createVariableRangeEvent(
            new ResourceLocation(TaCZMagazines.MODID, "magloading"));
    public static final SoundEvent MAG_UNLOADING_EVENT = SoundEvent.createVariableRangeEvent(
            new ResourceLocation(TaCZMagazines.MODID, "magunloading"));

    public static final Supplier<SoundEvent> MAG_LOADING = () -> MAG_LOADING_EVENT;
    public static final Supplier<SoundEvent> MAG_UNLOADING = () -> MAG_UNLOADING_EVENT;

    private SoundRegistrar() {}

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, new ResourceLocation(TaCZMagazines.MODID, "magloading"), MAG_LOADING_EVENT);
        Registry.register(BuiltInRegistries.SOUND_EVENT, new ResourceLocation(TaCZMagazines.MODID, "magunloading"), MAG_UNLOADING_EVENT);
    }

    public static void playMagazineLoad(Entity entity) {
        playForEveryone(entity, MAG_LOADING_EVENT);
    }

    public static void playMagazineUnload(Entity entity) {
        playForEveryone(entity, MAG_UNLOADING_EVENT);
    }

    private static void playForEveryone(Entity entity, SoundEvent sound) {
        if (entity.level().isClientSide) {
            entity.level().playLocalSound(
                    entity.getX(), entity.getY(), entity.getZ(),
                    sound, SoundSource.PLAYERS, 1.0F, 1.0F, false);
        } else {
            // A Player's normal playSound call excludes that same player
            // because vanilla assumes their client already played it. These
            // transfers are packet-driven, so broadcast with no exclusion.
            entity.level().playSound(
                    null, entity.getX(), entity.getY(), entity.getZ(),
                    sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}

