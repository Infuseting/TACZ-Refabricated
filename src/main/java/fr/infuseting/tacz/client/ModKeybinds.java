package fr.infuseting.tacz.client;

import com.mojang.blaze3d.platform.InputConstants;
import committee.nova.mkb.api.IKeyBinding;
import committee.nova.mkb.keybinding.KeyConflictContext;
import committee.nova.mkb.keybinding.KeyModifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ModKeybinds {

    public static final KeyMapping UNLOAD_MAG = new KeyMapping(
            "key.taczmagazines.unload_mag",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.getCategory()
    );

    public static final KeyMapping CHECK_MAG = new KeyMapping(
            "key.taczmagazines.check_mag",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.getCategory()
    );

    public static final KeyMapping FAST_RELOAD = new KeyMapping(
            "key.taczmagazines.fast_reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.getCategory()
    );

    public static final KeyMapping UNJAM = new KeyMapping(
            "key.taczmagazines.unjam",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.getCategory()
    );

    public static final KeyMapping TOGGLE_SAFE = new KeyMapping(
            "key.taczmagazines.toggle_safe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.getCategory()
    );

    public static void register() {
        if (UNLOAD_MAG instanceof IKeyBinding mkb) {
            mkb.setKeyConflictContext(KeyConflictContext.IN_GAME);
            mkb.setKeyModifierAndCode(KeyModifier.CONTROL, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
        }
        if (CHECK_MAG instanceof IKeyBinding mkb) {
            mkb.setKeyConflictContext(KeyConflictContext.IN_GAME);
            mkb.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
        }
        if (FAST_RELOAD instanceof IKeyBinding mkb) {
            mkb.setKeyConflictContext(KeyConflictContext.IN_GAME);
            mkb.setKeyModifierAndCode(KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
        }
        if (UNJAM instanceof IKeyBinding mkb) {
            mkb.setKeyConflictContext(KeyConflictContext.IN_GAME);
        }
        if (TOGGLE_SAFE instanceof IKeyBinding mkb) {
            mkb.setKeyConflictContext(KeyConflictContext.IN_GAME);
        }
        KeyBindingHelper.registerKeyBinding(UNLOAD_MAG);
        KeyBindingHelper.registerKeyBinding(CHECK_MAG);
        KeyBindingHelper.registerKeyBinding(FAST_RELOAD);
        KeyBindingHelper.registerKeyBinding(UNJAM);
        KeyBindingHelper.registerKeyBinding(TOGGLE_SAFE);
    }

    public static boolean matchesUnloadKey(int key, int scanCode) {
        if (UNLOAD_MAG instanceof IKeyBinding mkb) {
            InputConstants.Key inputKey = InputConstants.getKey(key, scanCode);
            if (mkb.isActiveAndMatches(inputKey)) {
                return true;
            }
            if (UNLOAD_MAG.matches(key, scanCode) && mkb.getKeyModifier() == KeyModifier.CONTROL && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                return true;
            }
            if (UNLOAD_MAG.matches(key, scanCode) && mkb.getKeyModifier() == KeyModifier.NONE && !net.minecraft.client.gui.screens.Screen.hasControlDown() && !net.minecraft.client.gui.screens.Screen.hasShiftDown() && !net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                return true;
            }
        }
        return UNLOAD_MAG.matches(key, scanCode) && net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    public static boolean matchesCheckKey(int key, int scanCode) {
        if (CHECK_MAG instanceof IKeyBinding mkb) {
            InputConstants.Key inputKey = InputConstants.getKey(key, scanCode);
            if (mkb.isActiveAndMatches(inputKey)) {
                return true;
            }
            if (CHECK_MAG.matches(key, scanCode) && mkb.getKeyModifier() == KeyModifier.SHIFT && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                return true;
            }
            if (CHECK_MAG.matches(key, scanCode) && mkb.getKeyModifier() == KeyModifier.NONE && !net.minecraft.client.gui.screens.Screen.hasControlDown() && !net.minecraft.client.gui.screens.Screen.hasShiftDown() && !net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                return true;
            }
        }
        return CHECK_MAG.matches(key, scanCode) && net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    public static boolean matchesFastReloadKey(int key, int scanCode) {
        if (FAST_RELOAD instanceof IKeyBinding mkb) {
            InputConstants.Key inputKey = InputConstants.getKey(key, scanCode);
            if (mkb.isActiveAndMatches(inputKey)) {
                return true;
            }
            if (FAST_RELOAD.matches(key, scanCode) && mkb.getKeyModifier() == KeyModifier.ALT && net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                return true;
            }
            if (FAST_RELOAD.matches(key, scanCode) && mkb.getKeyModifier() == KeyModifier.NONE && !net.minecraft.client.gui.screens.Screen.hasControlDown() && !net.minecraft.client.gui.screens.Screen.hasShiftDown() && !net.minecraft.client.gui.screens.Screen.hasAltDown()) {
                return true;
            }
        }
        return FAST_RELOAD.matches(key, scanCode) && net.minecraft.client.gui.screens.Screen.hasAltDown();
    }
}

