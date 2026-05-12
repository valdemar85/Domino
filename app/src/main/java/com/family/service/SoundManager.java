package com.family.service;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

/**
 * Tiny wrapper around SoundPool for short game effects.
 *
 * Sound files are loaded by resource name lookup (getIdentifier) so the project
 * compiles cleanly with or without them. To enable real sounds, place these files
 * under app/src/main/res/raw/:
 *
 *   tile_play.ogg  — short tile clack (~150-300 ms)
 *   round_win.ogg  — short fanfare      (~1-2 s)
 *
 * Without custom files, falls back to AudioManager's built-in keyclick sounds so
 * the player still gets some auditory feedback.
 */
public class SoundManager {
    private final Context appContext;
    private SoundPool soundPool;
    private int tilePlaySoundId = 0;
    private int roundWinSoundId = 0;

    public SoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();
        tilePlaySoundId = loadIfPresent("tile_play");
        roundWinSoundId = loadIfPresent("round_win");
    }

    private int loadIfPresent(String name) {
        int resId = appContext.getResources().getIdentifier(name, "raw", appContext.getPackageName());
        if (resId == 0) return 0;
        return soundPool.load(appContext, resId, 1);
    }

    public void playTile() {
        if (soundPool == null) return;
        if (tilePlaySoundId != 0) {
            soundPool.play(tilePlaySoundId, 1f, 1f, 1, 0, 1f);
        } else {
            playSystemFallback(AudioManager.FX_KEYPRESS_STANDARD);
        }
    }

    public void playRoundWin() {
        if (soundPool == null) return;
        if (roundWinSoundId != 0) {
            soundPool.play(roundWinSoundId, 1f, 1f, 1, 0, 1f);
        } else {
            // No great built-in for "victory", but FX_KEYPRESS_RETURN is at least
            // a distinct chime — louder than the regular click.
            playSystemFallback(AudioManager.FX_KEYPRESS_RETURN);
        }
    }

    private void playSystemFallback(int effect) {
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.playSoundEffect(effect, 0.5f);
        }
    }

    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
