package fr.infuseting.tacz.item;

import fr.infuseting.tacz.client.MagazineLoadingHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientMagazineActionHelper {
    private ClientMagazineActionHelper() {}

    public static void startOrToggleUnloading(int slotIndex) {
        if (MagazineLoadingHandler.isActive()
                && MagazineLoadingHandler.isUnloading()
                && MagazineLoadingHandler.getContainerSlot() == slotIndex) {
            MagazineLoadingHandler.cancel();
        } else {
            MagazineLoadingHandler.startUnloading(slotIndex);
        }
    }

    public static void startOrToggleLoading(int slotIndex) {
        if (MagazineLoadingHandler.isActive()
                && !MagazineLoadingHandler.isUnloading()
                && MagazineLoadingHandler.getContainerSlot() == slotIndex) {
            MagazineLoadingHandler.cancel();
        } else {
            MagazineLoadingHandler.startLoading(slotIndex);
        }
    }

    public static void cancelLoading() {
        MagazineLoadingHandler.cancel();
    }

    public static void startOrToggleInHandUnloading() {
        if (MagazineLoadingHandler.isInHandActive() && MagazineLoadingHandler.isInHandUnloading()) {
            MagazineLoadingHandler.cancelInHand();
        } else {
            MagazineLoadingHandler.cancelInHand();
            MagazineLoadingHandler.startInHandUnloading();
        }
    }
}

