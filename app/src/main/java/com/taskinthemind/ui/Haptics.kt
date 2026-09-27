package com.taskinthemind.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short, crisp vibrations for touch moments, played on the motor directly.
 * Android's view haptics depend on the phone's "touch feedback" setting and
 * felt like a faint thud on some phones; these don't.
 */
object Haptics {
    /** A firm click: a menu opening, a tab lifting for reordering. */
    fun press(context: Context) = play(
        context,
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
    )

    /** A light tick: tabs swapping places while dragging. */
    fun tick(context: Context) = play(
        context,
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
    )

    private fun play(context: Context, effect: VibrationEffect) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (vibrator.hasVibrator()) vibrator.vibrate(effect)
        }
    }
}
