package fr.infuseting.tacz.network;

import fr.infuseting.tacz.ammo.AmmoStack;
import fr.infuseting.tacz.item.MagazineAmmoSource;
import fr.infuseting.tacz.item.MagazineItem;
import fr.infuseting.tacz.item.SoundRegistrar;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

// Transfers exactly one bullet to or from a magazine slot. Fired by the client loading-session ticker in tick-based mode.
public class BulletTransferPacket {

    private final int inventorySlot;
    private final boolean unload;

    public BulletTransferPacket(int inventorySlot, boolean unload) {
        this.inventorySlot = inventorySlot;
        this.unload        = unload;
    }

    public int getInventorySlot() {
        return inventorySlot;
    }

    public boolean isUnload() {
        return unload;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(inventorySlot);
        buf.writeBoolean(unload);
    }

    public static BulletTransferPacket decode(FriendlyByteBuf buf) {
        return new BulletTransferPacket(buf.readVarInt(), buf.readBoolean());
    }

    public void handle(ServerPlayer player) {
        if (player == null || !RateLimiter.canProcess(player)) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null
                || inventorySlot < 0
                || inventorySlot >= player.getInventory().items.size()) return;

        ItemStack mag = player.getInventory().getItem(inventorySlot);
        if (mag.isEmpty() || !(mag.getItem() instanceof MagazineItem magItem)) return;

        ItemStack extras = ItemStack.EMPTY;
        if (mag.getCount() > 1) {
            extras = mag.split(mag.getCount() - 1);
        }

        if (unload) {
            handleUnload(player, menu, mag, magItem);
        } else {
            handleLoad(player, menu, mag, magItem);
        }

        if (!extras.isEmpty()) {
            if (!player.getInventory().add(extras)) player.drop(extras, false);
        }

        player.getInventory().setChanged();
        menu.broadcastChanges();
    }

    // â”€â”€ Load one bullet: cursor â†’ magazine â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private static void handleLoad(ServerPlayer player, AbstractContainerMenu menu,
                                   ItemStack mag, MagazineItem magItem) {
        ItemStack cursor = menu.getCarried();

        String familyId = MagazineItem.getMagazineFamilyId(mag);
        if (familyId == null) return;
        ResourceLocation familyAmmo = MagazineFamilySystem.getAmmoTypeForFamily(familyId);
        if (familyAmmo == null) return;

        boolean creative = player.getAbilities().instabuild;
        ResourceLocation heldAmmoId = creative
                ? familyAmmo
                : MagazineAmmoSource.compatibleAmmoId(cursor, familyAmmo);
        if (heldAmmoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(heldAmmoId)) return;

        int maxCap  = MagazineItem.getMaxCapacity(mag);
        int current = magItem.getAmmoCount(mag);
        if (current >= maxCap) return;

        AmmoStack ammoStack = AmmoStack.fromItemStack(mag);
        ammoStack.push(heldAmmoId, 1);
        ammoStack.saveToItemStack(mag);

        if (!creative) MagazineAmmoSource.consume(cursor, 1);

        menu.setCarried(cursor);
        SoundRegistrar.playMagazineLoad(player);
    }

    // ── Unload one bullet: magazine → cursor/inventory ───────────────────────

    private static void handleUnload(ServerPlayer player, AbstractContainerMenu menu,
                                     ItemStack mag, MagazineItem magItem) {
        AmmoStack ammoStack = AmmoStack.fromItemStack(mag);
        if (ammoStack.isEmpty()) return;

        ResourceLocation poppedId = ammoStack.pop();
        ammoStack.saveToItemStack(mag);

        ItemStack cursor = menu.getCarried();
        ItemStack bullet = AmmoItemBuilder.create().setId(poppedId).setCount(1).build();

        if (cursor.isEmpty()) {
            menu.setCarried(bullet);
        } else if (cursor.getItem() == bullet.getItem()
                && ItemStack.isSameItemSameTags(cursor, bullet)
                && cursor.getCount() < cursor.getMaxStackSize()) {
            cursor.grow(1);
            menu.setCarried(cursor);
        } else {
            if (!player.getInventory().add(bullet)) {
                player.drop(bullet, false);
            }
        }
        SoundRegistrar.playMagazineUnload(player);
    }
}

