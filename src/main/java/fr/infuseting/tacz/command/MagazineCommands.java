package fr.infuseting.tacz.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.infuseting.tacz.capability.GunMagazineCapability;
import fr.infuseting.tacz.item.MagazineItem;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class MagazineCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("magazine")
                .then(Commands.literal("eject")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack mainHand = player.getMainHandItem();

                            if (!(mainHand.getItem() instanceof IGun iGun)) {
                                player.sendSystemMessage(Component.literal("Â§cYou must be holding a gun!"));
                                return 0;
                            }

                            GunMagazineCapability magCap = GunMagazineCapability.of(mainHand);
                            if (magCap.hasMagazine()) {
                                ItemStack storedMag = magCap.getStoredMagazine();

                                ResourceLocation gunId = iGun.getGunId(mainHand);
                                TimelessAPI.getCommonGunIndex(gunId).ifPresent(gunIndex -> {
                                    if (storedMag.getItem() instanceof MagazineItem magItem) {
                                        int currentAmmoInGun = iGun.getCurrentAmmoCount(mainHand);
                                        int currentAmmoInMag = magItem.getAmmoCount(storedMag);

                                        ResourceLocation gunAmmoId = gunIndex.getGunData().getAmmoId();
                                        if (currentAmmoInGun > 0 && !gunAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                                            magItem.setAmmoId(storedMag, gunAmmoId);
                                        }

                                        magItem.setAmmoCount(storedMag, currentAmmoInMag + currentAmmoInGun);
                                    }

                                    String magInfo = "Empty Magazine";
                                    if (storedMag.getItem() instanceof MagazineItem magItem) {
                                        int ammoCount = magItem.getAmmoCount(storedMag);
                                        magInfo = "Magazine (" + ammoCount + " rounds)";
                                    }

                                    iGun.setCurrentAmmoCount(mainHand, 0);
                                    magCap.clearMagazine();

                                    boolean added = player.getInventory().add(storedMag);
                                    if (!added) {
                                        player.drop(storedMag, false);
                                    }

                                    player.sendSystemMessage(Component.literal("Â§aEjected: " + magInfo));
                                });
                            } else {
                                player.sendSystemMessage(Component.literal("Â§eNo magazine in gun!"));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );
    }
}
