package fr.zeffut.mcwrapped.ui;

import fr.zeffut.mcwrapped.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Single entry point for every sound the Wrapped UI plays. Centralizes the volume scaling against
 * the user-configured {@code soundVolume} so changes apply globally without touching each card.
 */
public final class WrappedSounds {
    private WrappedSounds() {}

    /** Plays a UI sound at the given pitch and base volume, scaled by config volume (0–100). */
    public static void play(final Minecraft client, final SoundEvent sound, final float pitch, final float volume) {
        final float multiplier = ConfigManager.get().soundVolume / 100f;
        final float scaled = Math.max(0f, volume * multiplier);
        if (scaled <= 0f) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, scaled));
    }
}
