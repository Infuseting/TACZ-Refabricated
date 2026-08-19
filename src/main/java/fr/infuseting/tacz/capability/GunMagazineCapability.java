package fr.infuseting.tacz.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;

public class GunMagazineCapability {

    public static final String TAG_KEY = "TaCZMag_StoredMagazine";

    // The ItemStack this capability is attached to â€” we write directly to its tag.
    private final ItemStack gunStack;

    public GunMagazineCapability(ItemStack gunStack) {
        this.gunStack = gunStack;
    }

    public static GunMagazineCapability of(ItemStack gunStack) {
        return new GunMagazineCapability(gunStack);
    }

    public static Optional<GunMagazineCapability> get(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GunMagazineCapability(gunStack));
    }

    public static void ifPresent(ItemStack gunStack, Consumer<GunMagazineCapability> consumer) {
        if (gunStack != null && !gunStack.isEmpty()) {
            consumer.accept(new GunMagazineCapability(gunStack));
        }
    }

    public ItemStack getStoredMagazine() {
        if (gunStack == null || gunStack.isEmpty()) return ItemStack.EMPTY;
        CompoundTag tag = gunStack.getTag();
        if (tag == null || !tag.contains(TAG_KEY)) return ItemStack.EMPTY;
        return ItemStack.of(tag.getCompound(TAG_KEY));
    }

    public void setStoredMagazine(ItemStack magazine) {
        if (gunStack == null || gunStack.isEmpty()) return;
        if (magazine == null || magazine.isEmpty()) {
            clearMagazine();
            return;
        }
        CompoundTag tag = gunStack.getOrCreateTag();
        tag.put(TAG_KEY, magazine.save(new CompoundTag()));
    }

    public boolean hasMagazine() {
        if (gunStack == null || gunStack.isEmpty()) return false;
        CompoundTag tag = gunStack.getTag();
        return tag != null && tag.contains(TAG_KEY);
    }

    public void clearMagazine() {
        if (gunStack == null || gunStack.isEmpty()) return;
        CompoundTag tag = gunStack.getTag();
        if (tag != null) tag.remove(TAG_KEY);
    }
}
