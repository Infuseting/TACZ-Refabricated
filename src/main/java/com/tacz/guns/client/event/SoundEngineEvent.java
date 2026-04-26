package com.tacz.guns.client.event;

import com.tacz.guns.client.resource.ClientAssetsManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sounds.SoundEngine;

@Environment(EnvType.CLIENT)
public class SoundEngineEvent {
    public static void onSoundEngineLoad(SoundEngine soundEngine) {
        ClientAssetsManager.INSTANCE.invalidateSoundBuffers();
    }
}
