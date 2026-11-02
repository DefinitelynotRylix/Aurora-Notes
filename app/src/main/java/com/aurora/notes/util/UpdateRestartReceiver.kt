package com.aurora.notes.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.aurora.notes.MainActivity

class UpdateRestartReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_COMMIT = "com.aurora.notes.ACTION_INSTALL_COMMIT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LocalLogger.security("UPDATE", "Package replaced successfully — forcing automatic app restart into new version")
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra("extra_updated_restarted", true)
            }
            context.startActivity(launchIntent)
            return
        }

        if (action == ACTION_INSTALL_COMMIT) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirmIntent != null) {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmIntent)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    LocalLogger.security("UPDATE", "Self-update installation succeeded — restarting Aurora Notes")
                    Toast.makeText(context, "Update installed! Restarting Aurora Notes...", Toast.LENGTH_SHORT).show()
                    AppUpdateManager.forceRestartApp(context)
                }
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation cancelled or failed"
                    LocalLogger.error("UPDATE", "PackageInstaller status $status: $msg")
                }
            }
        }
    }
}
