package com.stylemirror.feature.overlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.stylemirror.feature.overlay.R
import com.stylemirror.feature.overlay.ui.BubbleHost
import com.stylemirror.feature.overlay.ui.BubbleStyle

/**
 * Foreground service that owns the floating bubble's lifetime. While running,
 * a [BubbleHost] keeps a [androidx.compose.ui.platform.ComposeView] attached
 * to [android.view.WindowManager] at TYPE_APPLICATION_OVERLAY layer.
 *
 * **Why foreground**
 *
 * Android kills cached background services aggressively, especially on
 * Chinese OEMs (MIUI, EMUI, ColorOS) — see
 * docs/p1c-permission-setup.md "常见卡点". A foreground service with a
 * persistent (low-importance) notification is the only reliable way to keep
 * the bubble alive while the user is in another app.
 *
 * **specialUse foreground type (Android 14+)**
 *
 * Google rejects the obvious-sounding types (camera / dataSync / mediaPlayback)
 * for an overlay assistant. `specialUse` plus a documented subtype is the
 * sanctioned path; we declare it both as the runtime
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] and as a manifest
 * `<property>` carrying the human-readable subtype.
 */
class FloatingBubbleService : Service() {
    private var bubbleHost: BubbleHost? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startInForeground()
        bubbleHost =
            BubbleHost(
                context = this,
                onClick = ::onBubbleClicked,
            ).also { it.show(BubbleStyle.CIRCLE) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        bubbleHost?.hide()
        bubbleHost = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onBubbleClicked() {
        // T30.6 will route this into OverlayCandidateController to fetch
        // candidates from the latest snapshot. For T30.5 the click only logs.
        android.util.Log.d(TAG, "bubble clicked (placeholder)")
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.p1c_foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.p1c_foreground_service_channel_description)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent()
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.p1c_foreground_notification_title))
            .setContentText(getString(R.string.p1c_foreground_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val TAG = "StyleMirrorOverlay"
        private const val CHANNEL_ID = "stylemirror.overlay.bubble"
        private const val NOTIFICATION_ID = 1042

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
