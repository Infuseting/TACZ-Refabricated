package fr.infuseting.tacz.client;

import fr.infuseting.tacz.client.tooltip.MagazineTooltipRenderer;
import fr.infuseting.tacz.command.ClientCommands;
import fr.infuseting.tacz.item.MagazineRegistrar;
import fr.infuseting.tacz.tooltip.MagazineTooltipData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Environment(EnvType.CLIENT)
public class TaCZMagazinesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register custom item renderer
        BuiltinItemRendererRegistry.INSTANCE.register(MagazineRegistrar.MAGAZINE.get(), MagazineItemRenderer.getInstance());

        // Register keybinds
        ModKeybinds.register();

        // Register client commands
        ClientCommands.register();

        // Register loading handler input and mouse events
        MagazineLoadingHandler.register();

        // Register unjam input events
        cn.sh1rocu.tacz.api.event.InputEvent.Key.EVENT.register(ClientUnjamHandler::onKeyPress);
        cn.sh1rocu.tacz.api.event.InputEvent.MouseButton.Post.EVENT.register(ClientUnjamHandler::onMousePress);

        // Register client tick listeners
        ClientTickEvents.END_CLIENT_TICK.register(ClientReloadKeyHandler::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(MagazineLoadingHandler::onClientTick);

        // Register HUD overlays
        HudRenderCallback.EVENT.register(InHandLoadingOverlay::render);
        HudRenderCallback.EVENT.register(MagazineAmmoHudOverlay::render);
        HudRenderCallback.EVENT.register(MagazineSelectorOverlay::render);

        // Register screen overlay for in-container magazine loading
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenEvents.afterRender(screen).register(MagazineLoadingOverlay::onScreenRender));

        // Register custom tooltip renderer
        TooltipComponentCallback.EVENT.register(data ->
                data instanceof MagazineTooltipData tooltipData ? new MagazineTooltipRenderer(tooltipData) : null);
    }
}

