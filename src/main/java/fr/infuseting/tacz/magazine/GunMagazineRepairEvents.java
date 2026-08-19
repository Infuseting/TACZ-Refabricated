package fr.infuseting.tacz.magazine;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class GunMagazineRepairEvents {

    private GunMagazineRepairEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GunMagazineRepairEvents::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : player.getInventory().items) {
                GunMagazineInitializer.ensureMagazineForLoadedGun(stack);
            }
            for (ItemStack stack : player.getInventory().offhand) {
                GunMagazineInitializer.ensureMagazineForLoadedGun(stack);
            }
            for (ItemStack stack : player.getInventory().armor) {
                GunMagazineInitializer.ensureMagazineForLoadedGun(stack);
            }
        }
    }
}

