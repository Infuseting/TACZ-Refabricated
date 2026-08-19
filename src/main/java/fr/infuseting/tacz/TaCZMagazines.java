package fr.infuseting.tacz;

import fr.infuseting.tacz.command.MagazineCommands;
import fr.infuseting.tacz.config.FamilyConfigManager;
import fr.infuseting.tacz.config.GunOverrideConfig;
import fr.infuseting.tacz.config.MechanicsConfig;
import fr.infuseting.tacz.crafting.GunsmithIntegration;
import fr.infuseting.tacz.crafting.MagazineRecipeOverrides;
import fr.infuseting.tacz.item.MagazineRegistrar;
import fr.infuseting.tacz.item.SoundRegistrar;
import fr.infuseting.tacz.magazine.GunMagazineRepairEvents;
import fr.infuseting.tacz.magazine.MagazineFamilySystem;
import fr.infuseting.tacz.network.PacketHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class TaCZMagazines implements ModInitializer {
    public static final String MODID = "tacz";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] TaCZ Magazines mod initializing on Fabric...", MODID);

        // Register configs (via ForgeConfigAPIPort)
        FamilyConfigManager.register();
        GunOverrideConfig.register();
        MechanicsConfig.register();

        // Register items and sounds
        MagazineRegistrar.register();
        SoundRegistrar.register();

        // Register network packets
        PacketHandler.register();

        // Register server commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(MagazineCommands.register());
            LOGGER.info("[{}] Registered magazine commands", MODID);
        });

        // Register data reload listener
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(MagazineRecipeOverrides.INSTANCE);

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> onDatapackSync(player.server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerLoggedIn(handler.getPlayer()));

        // Register repair tick events
        GunMagazineRepairEvents.register();
    }

    private void onServerStarted(MinecraftServer server) {
        onDatapackSync(server);
    }

    public static void onDatapackSync(MinecraftServer server) {
        MagazineFamilySystem.discoverMagazineFamilies();
        GunOverrideConfig.apply();
        FamilyConfigManager.load();
        MagazineRegistrar.onDatapackSync();
        LOGGER.info("[{}] Magazine families discovered", MODID);

        if (server != null) {
            GunsmithIntegration.setup(server);
        }
    }

    private void onPlayerLoggedIn(net.minecraft.server.level.ServerPlayer player) {
        List<String> errors = new ArrayList<>();
        errors.addAll(GunOverrideConfig.getErrors());
        errors.addAll(FamilyConfigManager.getErrors());
        if (errors.isEmpty()) return;

        player.sendSystemMessage(Component.literal(
                "Â§c[TaCZMagazines] Config errors â€” fix and press F3+T to reload:"));
        for (String error : errors) {
            player.sendSystemMessage(Component.literal("Â§c  â€¢ " + error));
        }
    }
}

