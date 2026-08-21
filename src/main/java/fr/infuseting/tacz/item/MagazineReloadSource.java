package fr.infuseting.tacz.item;

import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import cn.sh1rocu.tacz.util.itemhandler.IItemHandlerModifiable;
import fr.infuseting.tacz.config.MechanicsConfig;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// Resolves reload magazines from inventory, ammo boxes, or creative boxes.
public final class MagazineReloadSource {
    private MagazineReloadSource() {}

    public static ItemStack extract(IItemHandler inventory, ItemStack gun, int selectedSlot) {
        ItemStack selected = extractSelected(inventory, gun, selectedSlot);
        if (!selected.isEmpty()) return selected;

        if (MechanicsConfig.PREFER_PLAYER_INVENTORY.get()) {
            ItemStack direct = extractBestDirect(inventory, gun);
            return direct.isEmpty() ? extractBestBoxed(inventory, gun) : direct;
        }

        ItemStack boxed = extractBestBoxed(inventory, gun);
        return boxed.isEmpty() ? extractBestDirect(inventory, gun) : boxed;
    }

    public static boolean hasUsableMagazine(IItemHandler inventory, ItemStack gun) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (isUsableDirect(stack, gun)) return true;
            if (isAllTypeCreativeBox(stack)) return true;
            if (!AmmoBoxMagazineStorage.peekBestCompatible(stack, gun).isEmpty()) return true;
        }
        return false;
    }

    public static ItemStack createFullMagazineForGun(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) return ItemStack.EMPTY;
        ResourceLocation gunId = iGun.getGunId(gun);
        String family = MagazineFamilySystem.getFamilyForGun(gunId);
        if (family == null) return ItemStack.EMPTY;

        ResourceLocation ammoId = MagazineFamilySystem.getAmmoTypeForFamily(family);
        if (ammoId == null) return ItemStack.EMPTY;
        int capacity = MagazineFamilySystem.getCapacityForFamily(family);
        return MagazineItem.createMagazineByFamily(
                MagazineRegistrar.MAGAZINE.get(), family, capacity, ammoId);
    }

    public static ItemStack createCreativeReloadMagazine(IItemHandler inventory, ItemStack gun,
                                                         int selectedSlot) {
        ItemStack magazine = ItemStack.EMPTY;
        if (selectedSlot >= 0 && selectedSlot < inventory.getSlots()) {
            ItemStack selected = inventory.getStackInSlot(selectedSlot);
            if (isUsableDirect(selected, gun)) {
                magazine = selected.copyWithCount(1);
            } else if (!isAllTypeCreativeBox(selected)) {
                magazine = AmmoBoxMagazineStorage.peekBestCompatible(selected, gun);
            }
        }

        if (!(magazine.getItem() instanceof MagazineItem magItem)) {
            return createFullMagazineForGun(gun);
        }

        String family = MagazineItem.getMagazineFamilyId(magazine);
        ResourceLocation ammoId = family == null
                ? null
                : MagazineFamilySystem.getAmmoTypeForFamily(family);
        if (ammoId == null) return createFullMagazineForGun(gun);

        magItem.setAmmoId(magazine, ammoId);
        magItem.setAmmoCount(magazine, MagazineItem.getMaxCapacity(magazine));
        return magazine;
    }

    private static ItemStack extractSelected(IItemHandler inventory, ItemStack gun, int slot) {
        if (slot < 0 || slot >= inventory.getSlots()) return ItemStack.EMPTY;
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.getItem() instanceof MagazineItem magItem && magItem.isAmmoBoxOfGun(gun, stack)) {
            return inventory.extractItem(slot, 1, false);
        }
        if (isAllTypeCreativeBox(stack)) return createFullMagazineForGun(gun);

        ItemStack result = AmmoBoxMagazineStorage.extractBestCompatible(stack, gun);
        if (!result.isEmpty() && inventory instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        }
        return result;
    }

    private static ItemStack extractBestDirect(IItemHandler inventory, ItemStack gun) {
        int bestCheckedSlot = -1;
        int bestCheckedAmmo = 0;
        int firstUncheckedSlot = -1;

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!isUsableDirect(stack, gun)) continue;
            MagazineItem magItem = (MagazineItem) stack.getItem();
            boolean checked = MagazineItem.isChecked(stack);
            int ammo = magItem.getAmmoCount(stack);

            if (checked) {
                if (ammo > bestCheckedAmmo) {
                    bestCheckedAmmo = ammo;
                    bestCheckedSlot = i;
                }
            } else {
                if (firstUncheckedSlot < 0) {
                    firstUncheckedSlot = i;
                }
            }
        }

        int targetSlot = bestCheckedSlot >= 0 ? bestCheckedSlot : firstUncheckedSlot;
        return targetSlot < 0 ? ItemStack.EMPTY : inventory.extractItem(targetSlot, 1, false);
    }

    private static ItemStack extractBestBoxed(IItemHandler inventory, ItemStack gun) {
        int bestCheckedBoxSlot = -1;
        int bestCheckedAmmo = 0;
        int firstUncheckedBoxSlot = -1;

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack box = inventory.getStackInSlot(i);
            if (isAllTypeCreativeBox(box)) {
                return createFullMagazineForGun(gun);
            }

            ItemStack magazine = AmmoBoxMagazineStorage.peekBestCompatible(box, gun);
            if (!(magazine.getItem() instanceof MagazineItem magItem)) continue;
            boolean checked = MagazineItem.isChecked(magazine);
            int ammo = magItem.getAmmoCount(magazine);

            if (checked) {
                if (ammo > bestCheckedAmmo) {
                    bestCheckedAmmo = ammo;
                    bestCheckedBoxSlot = i;
                }
            } else {
                if (firstUncheckedBoxSlot < 0) {
                    firstUncheckedBoxSlot = i;
                }
            }
        }

        int targetBoxSlot = bestCheckedBoxSlot >= 0 ? bestCheckedBoxSlot : firstUncheckedBoxSlot;
        if (targetBoxSlot < 0) return ItemStack.EMPTY;

        ItemStack box = inventory.getStackInSlot(targetBoxSlot);
        ItemStack result = AmmoBoxMagazineStorage.extractBestCompatible(box, gun);
        if (inventory instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(targetBoxSlot, box);
        }
        return result;
    }

    private static boolean isUsableDirect(ItemStack stack, ItemStack gun) {
        if (!(stack.getItem() instanceof MagazineItem magItem)) return false;
        return magItem.isAmmoBoxOfGun(gun, stack) && magItem.getAmmoCount(stack) > 0;
    }

    private static boolean isAllTypeCreativeBox(ItemStack stack) {
        return AmmoBoxMagazineStorage.isExternalAmmoBox(stack)
                && stack.getItem() instanceof IAmmoBox box
                && box.isAllTypeCreative(stack);
    }
}

