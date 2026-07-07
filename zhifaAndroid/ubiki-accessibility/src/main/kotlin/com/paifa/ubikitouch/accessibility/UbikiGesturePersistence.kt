package com.paifa.ubikitouch.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object UbikiGesturePersistence {
    const val ACTION_RECOVER_GESTURE = "com.paifa.ubikitouch.action.RECOVER_GESTURE"

    fun startForeground(service: Service) {
        ensureChannel(service)
        val notification = serviceNotification(service)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            service.startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    fun startKeepAliveForeground(service: Service) {
        ensureChannel(service)
        val notification = serviceNotification(service)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                KEEP_ALIVE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            service.startForeground(KEEP_ALIVE_NOTIFICATION_ID, notification)
        }
    }

    fun stopForeground(service: Service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            service.stopForeground(true)
        }
    }

    fun notifyRecoveryReminder(context: Context, reason: String) {
        ensureChannel(context)
        val notification = recoveryNotification(context, reason)
        runCatching {
            NotificationManagerCompat.from(context).notify(RECOVERY_NOTIFICATION_ID, notification)
        }
    }

    fun startKeepAliveService(context: Context) {
        scheduleRecoveryWatchdog(context)
        val intent = Intent(context, UbikiGestureKeepAliveService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun handleRecoveryBroadcast(context: Context, reason: String) {
        if (!isAccessibilityServiceEnabled(context)) {
            cancelRecoveryWatchdog(context)
            if (reason != ACTION_RECOVER_GESTURE) {
                notifyRecoveryReminder(context, reason)
            }
            return
        }
        scheduleRecoveryWatchdog(context)
        runCatching {
            startKeepAliveService(context.applicationContext)
        }.onFailure { error ->
            Log.w(TAG, "failed to recover keep alive service reason=$reason", error)
            if (reason != ACTION_RECOVER_GESTURE) {
                notifyRecoveryReminder(context, reason)
            }
        }
        UbikiAccessibilityService.instance?.requestOverlayRecoveryCheck()
    }

    fun scheduleRecoveryWatchdog(
        context: Context,
        delayMs: Long = RECOVERY_WATCHDOG_INTERVAL_MS
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = recoveryWatchdogPendingIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(MIN_RECOVERY_WATCHDOG_DELAY_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
        Log.d(TAG, "scheduled recovery watchdog delayMs=$delayMs")
    }

    fun cancelRecoveryWatchdog(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(recoveryWatchdogPendingIntent(context))
        Log.d(TAG, "cancelled recovery watchdog")
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val component = ComponentName(context.packageName, UbikiAccessibilityService::class.java.name)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices
            .split(':')
            .any { enabled -> enabled.equals(component.flattenToString(), ignoreCase = true) }
    }

    private fun serviceNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ubiki_ic_persistent_gesture)
            .setContentTitle(context.getString(R.string.ubiki_persistent_notification_title))
            .setContentText(context.getString(R.string.ubiki_persistent_notification_text))
            .setContentIntent(openAppSettingsPendingIntent(context, REQUEST_APP_SETTINGS_SERVICE))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun recoveryNotification(context: Context, reason: String): Notification {
        val text = context.getString(R.string.ubiki_recovery_notification_text)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ubiki_ic_persistent_gesture)
            .setContentTitle(context.getString(R.string.ubiki_recovery_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$reason"))
            .setContentIntent(openAccessibilitySettingsPendingIntent(context, REQUEST_ACCESSIBILITY_CONTENT))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                0,
                context.getString(R.string.ubiki_recovery_notification_action_accessibility),
                openAccessibilitySettingsPendingIntent(context, REQUEST_ACCESSIBILITY_ACTION)
            )
            .addAction(
                0,
                context.getString(R.string.ubiki_recovery_notification_action_app_settings),
                openAppSettingsPendingIntent(context, REQUEST_APP_SETTINGS_ACTION)
            )
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ubiki_persistent_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                description = context.getString(R.string.ubiki_persistent_notification_text)
            }
        )
    }

    private fun recoveryWatchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_RECOVER_GESTURE).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_RECOVERY_WATCHDOG,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAccessibilitySettingsPendingIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppSettingsPendingIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val CHANNEL_ID = "gesture_persistence"
    private const val FOREGROUND_NOTIFICATION_ID = 42010
    private const val RECOVERY_NOTIFICATION_ID = 42011
    private const val KEEP_ALIVE_NOTIFICATION_ID = 42012
    private const val REQUEST_APP_SETTINGS_SERVICE = 43010
    private const val REQUEST_ACCESSIBILITY_CONTENT = 43011
    private const val REQUEST_ACCESSIBILITY_ACTION = 43012
    private const val REQUEST_APP_SETTINGS_ACTION = 43013
    private const val REQUEST_RECOVERY_WATCHDOG = 43014
    private const val RECOVERY_WATCHDOG_INTERVAL_MS = 15_000L
    private const val MIN_RECOVERY_WATCHDOG_DELAY_MS = 1_000L
    private const val TAG = "GesturePersistence"
}

fun gesturePersistenceUsesForegroundNotification(): Boolean = true

fun gesturePersistencePostsBootRecoveryNotification(): Boolean = true

fun gesturePersistenceCanOnlyRestoreAfterAccessibilityIsEnabled(): Boolean = true

fun gesturePersistenceUsesRecoveryWatchdog(): Boolean = true
