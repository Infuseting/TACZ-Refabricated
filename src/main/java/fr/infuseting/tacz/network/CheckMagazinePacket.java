package fr.infuseting.tacz.network;

import com.tacz.guns.api.item.IGun;
import fr.infuseting.tacz.capability.GunMagazineCapability;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.SoundRegistrar;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CheckMagazinePacket {

    public CheckMagazinePacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static CheckMagazinePacket decode(FriendlyByteBuf buf) {
        return new CheckMagazinePacket();
    }

    public void handle(ServerPlayer player) {
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof IGun iGun) {
            GunMagazineCapability cap = GunMagazineCapability.of(held);
            if (cap.hasMagazine()) {
                ItemStack stored = cap.getStoredMagazine();
                if (stored.getItem() instanceof MagazineItem magItem) {
                    int currentAmmo = iGun.getCurrentAmmoCount(held);
                    magItem.setAmmoCount(stored, currentAmmo);
                    MagazineItem.setChecked(stored, true);
                    cap.setStoredMagazine(stored);
                }
            }
            SoundRegistrar.playMagazineLoad(player);
        } else if (held.getItem() instanceof MagazineItem magItem) {
            MagazineItem.setChecked(held, true);
            SoundRegistrar.playMagazineLoad(player);
        }
    }
}
