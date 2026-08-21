package fr.infuseting.tacz.client;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.InputExtraCheck;
import fr.infuseting.tacz.firemode.SafeFireModeHandler;
import fr.infuseting.tacz.network.PacketHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class ClientSafeHandler {

    private ClientSafeHandler() {
    }

    public static void onKeyPress(InputEvent.Key event) {
        if (!InputExtraCheck.isInGame()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (ModKeybinds.TOGGLE_SAFE.matches(event.getKey(), event.getScanCode())) {
            performToggleSafe();
        }
    }

    public static void onMousePress(InputEvent.MouseButton.Post event) {
        if (!InputExtraCheck.isInGame()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (ModKeybinds.TOGGLE_SAFE.matchesMouse(event.getButton())) {
            performToggleSafe();
        }
    }

    public static boolean performToggleSafe() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return false;
        }

        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof IGun)) {
            return false;
        }

        GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
        if (display != null) {
            SoundPlayManager.playFireSelectSound(player, display);
            AnimationStateMachine<?> anim = display.getAnimationStateMachine();
            if (anim != null) {
                anim.trigger(GunAnimationConstant.INPUT_FIRE_SELECT);
            }
        }

        SafeFireModeHandler.toggleSafe(gun);
        AttachmentPropertyManager.postChangeEvent(player, gun);
        PacketHandler.sendToggleSafe();
        return true;
    }
}
