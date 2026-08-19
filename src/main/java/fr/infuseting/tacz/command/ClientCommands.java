package fr.infuseting.tacz.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import fr.infuseting.tacz.debug.GunBoneDebugger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class ClientCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("magazine")
                .then(ClientCommandManager.literal("bonedebug")
                    .executes(ctx -> {
                        String result = GunBoneDebugger.run();
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("Â§e[Magazine Debug] Â§f" + result));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );
    }
}

