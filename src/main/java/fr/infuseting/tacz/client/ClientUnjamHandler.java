package fr.infuseting.tacz.client;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.util.InputExtraCheck;
import fr.infuseting.tacz.network.PacketHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class ClientUnjamHandler {

    private static long lastUnjamTimestamp = 0;
    private static final long UNJAM_COOLDOWN_MS = 1500;

    private ClientUnjamHandler() {
    }

    public static void onKeyPress(InputEvent.Key event) {
        if (!InputExtraCheck.isInGame()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (ModKeybinds.UNJAM.matches(event.getKey(), event.getScanCode())) {
            performUnjam();
        }
    }

    public static void onMousePress(InputEvent.MouseButton.Post event) {
        if (!InputExtraCheck.isInGame()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (ModKeybinds.UNJAM.matchesMouse(event.getButton())) {
            performUnjam();
        }
    }

    public static boolean performUnjam() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastUnjamTimestamp < UNJAM_COOLDOWN_MS) {
            return false;
        }

        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }

        if (!iGun.isJammed(gun)) {
            return false;
        }

        lastUnjamTimestamp = now;

        GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
        if (display != null) {
            SoundPlayManager.playBoltSound(player, display);
            AnimationStateMachine<?> anim = display.getAnimationStateMachine();
            if (anim != null) {
                anim.trigger(GunAnimationConstant.INPUT_BOLT);
            }
        }

        // Optimistically clear jam flag client-side for HUD
        iGun.setJammed(gun, false);
        PacketHandler.sendUnjamGun();
        return true;
    }
}
