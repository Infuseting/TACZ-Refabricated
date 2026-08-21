package fr.infuseting.tacz.ammo;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represente une pile FILO (First-In, Last-Out) de munitions.
 */
public class AmmoStack {
    public static final String AMMO_STACK_TAG = "AmmoStack";

    public static class AmmoEntry {
        private ResourceLocation id;
        private int count;

        public AmmoEntry(ResourceLocation id, int count) {
            this.id = id;
            this.count = count;
        }

        public ResourceLocation getId() {
            return id;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    private final List<AmmoEntry> entries = new ArrayList<>();

    public List<AmmoEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty() || getTotalCount() == 0;
    }

    public int getTotalCount() {
        int total = 0;
        for (AmmoEntry entry : entries) {
            total += entry.count;
        }
        return total;
    }

    /**
     * Regarde le type de la cartouche au sommet de la pile (celle qui sera tiree en premier).
     */
    public ResourceLocation peek() {
        if (entries.isEmpty()) {
            return DefaultAssets.EMPTY_AMMO_ID;
        }
        return entries.get(entries.size() - 1).getId();
    }

    /**
     * Empile du nombre d'unite donnees pour l'id specifie (FILO - au sommet).
     */
    public void push(ResourceLocation ammoId, int count) {
        if (count <= 0 || ammoId == null || ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return;
        }
        if (!entries.isEmpty()) {
            AmmoEntry top = entries.get(entries.size() - 1);
            if (top.getId().equals(ammoId)) {
                top.setCount(top.getCount() + count);
                return;
            }
        }
        entries.add(new AmmoEntry(ammoId, count));
    }

    /**
     * Retire et retourne 1 cartouche du sommet de la pile (FILO).
     */
    public ResourceLocation pop() {
        if (entries.isEmpty()) {
            return DefaultAssets.EMPTY_AMMO_ID;
        }
        AmmoEntry top = entries.get(entries.size() - 1);
        ResourceLocation id = top.getId();
        top.setCount(top.getCount() - 1);
        if (top.getCount() <= 0) {
            entries.remove(entries.size() - 1);
        }
        return id;
    }

    /**
     * Retire et retourne jusqu'a `count` cartouches du sommet de la pile (FILO).
     * Les cartouches retirees sont retournees dans une sous-pile AmmoStack.
     */
    public AmmoStack pop(int count) {
        AmmoStack popped = new AmmoStack();
        int remainingToPop = count;
        while (remainingToPop > 0 && !entries.isEmpty()) {
            AmmoEntry top = entries.get(entries.size() - 1);
            int take = Math.min(remainingToPop, top.getCount());
            popped.push(top.getId(), take);
            top.setCount(top.getCount() - take);
            remainingToPop -= take;
            if (top.getCount() <= 0) {
                entries.remove(entries.size() - 1);
            }
        }
        return popped;
    }

    public void clear() {
        entries.clear();
    }

    public ListTag serializeNBT() {
        ListTag tagList = new ListTag();
        for (AmmoEntry entry : entries) {
            if (entry.count <= 0) continue;
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getId().toString());
            entryTag.putInt("count", entry.getCount());
            tagList.add(entryTag);
        }
        return tagList;
    }

    public void deserializeNBT(ListTag tagList) {
        entries.clear();
        if (tagList == null) return;
        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag entryTag = tagList.getCompound(i);
            if (entryTag.contains("id", Tag.TAG_STRING) && entryTag.contains("count", Tag.TAG_INT)) {
                ResourceLocation id = new ResourceLocation(entryTag.getString("id"));
                int count = entryTag.getInt("count");
                if (count > 0) {
                    push(id, count);
                }
            }
        }
    }

    /**
     * Lit AmmoStack a partir d'un ItemStack.
     */
    public static AmmoStack fromItemStack(ItemStack stack) {
        AmmoStack ammoStack = new AmmoStack();
        if (stack == null || stack.isEmpty()) {
            return ammoStack;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(AMMO_STACK_TAG, Tag.TAG_LIST)) {
            ammoStack.deserializeNBT(tag.getList(AMMO_STACK_TAG, Tag.TAG_COMPOUND));
        }
        return ammoStack;
    }

    /**
     * Sauvegarde AmmoStack dans le NBT d'un ItemStack.
     */
    public void saveToItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag tag = stack.getOrCreateTag();
        if (isEmpty()) {
            tag.remove(AMMO_STACK_TAG);
        } else {
            tag.put(AMMO_STACK_TAG, serializeNBT());
        }
    }

    /**
     * Teste si une munition donnee est compatible avec le calibre de reference.
     */
    public static boolean isAmmoCompatible(ResourceLocation requiredAmmoId, ResourceLocation testAmmoId) {
        if (requiredAmmoId == null || testAmmoId == null) return false;
        if (requiredAmmoId.equals(testAmmoId)) return true;
        if (requiredAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) return true;

        ResourceLocation parent1 = getParentOrSelf(requiredAmmoId);
        ResourceLocation parent2 = getParentOrSelf(testAmmoId);
        return parent1.equals(parent2);
    }

    public static ResourceLocation getParentOrSelf(ResourceLocation ammoId) {
        var dataOpt = TimelessAPI.getCommonAmmoData(ammoId);
        if (dataOpt.isPresent() && dataOpt.get().getParentAmmo() != null) {
            return dataOpt.get().getParentAmmo();
        }
        return ammoId;
    }
}
