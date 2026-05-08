package com.tacz.guns.loot;

import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;

import java.util.Arrays;
import java.util.List;

public class LootTableInjectorModifier {
    public static void init() {
        // Global Modifier
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            CommonAssetsManager manager = CommonAssetsManager.getInstance();
            if (manager == null) {
                return;
            }
            List<LootTableInjection> injections = manager.getLootTableInjections(id);
            if (injections.isEmpty()) {
                return;
            }
            for (LootTableInjection injection : injections) {
                tableBuilder.pools(Arrays.asList(injection.lootTable().pools));
            }
        });
    }
}