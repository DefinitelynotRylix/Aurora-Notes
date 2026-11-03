package com.aurora.notes.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Reliable in-app haptic feedback helper for Aurora Notes.
 *
 * Uses the app's explicit `android.permission.VIBRATE` permission via the direct
 * [Vibrator.vibrate] API (without `USAGE_TOUCH` filtering, which many Android OEMs
 * silently block when system "Touch vibration" is turned off), combined with view
 * haptic feedback constants.
 */
object HapticHelper {

    fun triggerHaptic(
        view: View?,
        context: Context,
        enabled: Boolean = true,
        durationMs: Long = 30L
    ) {
        if (!enabled) return

        // 1. Direct hardware vibrator pulse using android.permission.VIBRATE.
        // Calling vibrate(VibrationEffect) without USAGE_TOUCH ensures the vibration motor
        // fires whenever the user has enabled Haptic Feedback inside Aurora Notes Settings,
        // even if system-wide touch haptics are muted in OS settings.
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }

        // 2. Secondary View-level haptic actuation
        if (view != null) {
            try {
                view.isHapticFeedbackEnabled = true
                @Suppress("DEPRECATION")
                val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, flags)
            } catch (_: Exception) {
            }
        }
    }
}
