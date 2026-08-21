package fr.infuseting.tacz.item;

import fr.infuseting.tacz.TaCZMagazines;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Supplier;

public class MagazineRegistrar {

    private static final List<String> ALL_MAGAZINE_FAMILIES = new ArrayList<>();

    public static final MagazineItem MAGAZINE_ITEM = new MagazineItem(new Item.Properties().stacksTo(1));
    public static final Item TAB_ICON_ITEM = new Item(new Item.Properties());
    public static final GunCleaningKitItem CLEANING_KIT_ITEM = new GunCleaningKitItem(new Item.Properties().stacksTo(16));

    public static final Supplier<MagazineItem> MAGAZINE = () -> MAGAZINE_ITEM;
    public static final Supplier<Item> TAB_ICON = () -> TAB_ICON_ITEM;
    public static final Supplier<GunCleaningKitItem> CLEANING_KIT = () -> CLEANING_KIT_ITEM;

    public static final ResourceKey<CreativeModeTab> MAGAZINE_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            new ResourceLocation(TaCZMagazines.MODID, "magazines")
    );

    public static CreativeModeTab MAGAZINE_TAB;

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(TaCZMagazines.MODID, "magazine"), MAGAZINE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(TaCZMagazines.MODID, "tab_icon"), TAB_ICON_ITEM);
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(TaCZMagazines.MODID, "cleaning_kit"), CLEANING_KIT_ITEM);

        MAGAZINE_TAB = FabricItemGroup.builder()
                .title(Component.literal("TaCZ Magazines"))
                .icon(() -> new ItemStack(TAB_ICON_ITEM))
                .displayItems((params, output) -> {
                    output.accept(CLEANING_KIT_ITEM);
                    Set<String> addedFull = new HashSet<>();
                    Set<String> addedEmpty = new HashSet<>();

                    Map<String, Set<Integer>> baseFamiliesByAmmo = MagazineFamilySystem.getAllMagazineFamiliesWithCapacities();

                    List<String> sortedAmmoTypes = new ArrayList<>(baseFamiliesByAmmo.keySet());
                    sortedAmmoTypes.sort(String::compareToIgnoreCase);

                    for (String ammoType : sortedAmmoTypes) {
                        List<Integer> sortedCapacities = new ArrayList<>(baseFamiliesByAmmo.get(ammoType));
                        Collections.sort(sortedCapacities);

                        for (int capacity : sortedCapacities) {
                            String familyId = ammoType + "_" + capacity;
                            ResourceLocation ammoId = MagazineFamilySystem.getAmmoTypeForFamily(familyId);

                            // Full first, then empty
                            if (ammoId != null && addedFull.add(familyId)) {
                                output.accept(MagazineItem.createMagazineByFamily(MAGAZINE_ITEM, familyId, capacity, ammoId));
                            }
                            if (addedEmpty.add(familyId)) {
                                output.accept(MagazineItem.createMagazineByFamily(MAGAZINE_ITEM, familyId, 0));
                            }

                            // Extended mags (empty only) that belong to guns in this base family
                            for (String extFamilyId : MagazineFamilySystem.getExtendedFamiliesForBaseFamily(familyId)) {
                                if (addedEmpty.add(extFamilyId)) {
                                    output.accept(MagazineItem.createMagazineByFamily(MAGAZINE_ITEM, extFamilyId, 0));
                                }
                            }
                        }
                    }

                    // Isolated (solo) magazine families — each gun listed in [isolated_guns]
                    // gets its own group here, separate from the shared families above.
                    for (String soloFamilyId : MagazineFamilySystem.getIsolatedBaseFamilies()) {
                        ResourceLocation ammoId = MagazineFamilySystem.getAmmoTypeForFamily(soloFamilyId);
                        int capacity = MagazineFamilySystem.getCapacityForFamily(soloFamilyId);
                        if (ammoId != null && addedFull.add(soloFamilyId)) {
                            output.accept(MagazineItem.createMagazineByFamily(MAGAZINE_ITEM, soloFamilyId, capacity, ammoId));
                        }
                        if (addedEmpty.add(soloFamilyId)) {
                            output.accept(MagazineItem.createMagazineByFamily(MAGAZINE_ITEM, soloFamilyId, 0));
                        }
                        for (String extFamilyId : MagazineFamilySystem.getExtendedFamiliesForBaseFamily(soloFamilyId)) {
                            if (addedEmpty.add(extFamilyId)) {
                                output.accept(MagazineItem.createMagazineByFamily(MAGAZINE_ITEM, extFamilyId, 0));
                            }
                        }
                    }
                })
                .build();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAGAZINE_TAB_KEY, MAGAZINE_TAB);

        TaCZMagazines.LOGGER.info("[{}] Magazine registry initialized", TaCZMagazines.MODID);
    }

    public static void onDatapackSync() {
        ALL_MAGAZINE_FAMILIES.clear();
        ALL_MAGAZINE_FAMILIES.addAll(MagazineFamilySystem.getAllFamilies());

        TaCZMagazines.LOGGER.info("[{}] Registered {} magazine families for creative tab",
                TaCZMagazines.MODID, ALL_MAGAZINE_FAMILIES.size());
    }

    public static List<String> getAllMagazineFamilies() {
        return ALL_MAGAZINE_FAMILIES;
    }
}
