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
import com.stylemirror.feature.overlay.candidate.OverlayCandidateController
import com.stylemirror.feature.overlay.config.OverlayConfigStore
import com.stylemirror.feature.overlay.ui.BubbleHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service that owns the floating bubble's lifetime. While
 * running, a [BubbleHost] keeps a [androidx.compose.ui.platform.ComposeView]
 * attached to [android.view.WindowManager] at TYPE_APPLICATION_OVERLAY layer
 * and an [OverlayCandidateController] mediates between Soul snapshots and
 * the candidate panel UI.
 *
 * **Why foreground**
 *
 * Android kills cached background services aggressively, especially on
 * Chinese OEMs (MIUI, EMUI, ColorOS) — see
 * docs/p1c-permission-setup.md "常见卡点". A foreground service with a
 * persistent (low-importance) notification is the only reliable way to
 * keep the bubble alive while the user is in another app.
 *
 * **specialUse foreground type (Android 14+)**
 *
 * Google rejects the obvious-sounding types (camera / dataSync / mediaPlayback)
 * for an overlay assistant. `specialUse` plus a documented subtype is the
 * sanctioned path; we declare it both as the runtime
 * [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] and as a manifest
 * `<property>` carrying the human-readable subtype.
 *
 * **Controller wiring**
 *
 * The controller depends on `CandidateGenerator` from feature-realtime,
 * which is built up via Hilt in the app module. To keep feature-overlay
 * itself Hilt-free, the app module installs a [controllerFactory] at
 * `StyleMirrorApp.onCreate` time. This service then asks the factory for a
 * controller scoped to its own CoroutineScope; if no factory is installed
 * we still come up (so foreground notification rules are respected) but
 * tap-to-trigger is a no-op. That can only happen if the user starts the
 * service before the app has finished initializing — practically rare,
 * always recoverable by re-toggling the switch in Settings.
 */
class FloatingBubbleService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bubbleHost: BubbleHost? = null
    private var controller: OverlayCandidateController? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startInForeground()
        val factory = controllerFactory
        if (factory == null) {
            android.util.Log.w(TAG, "controllerFactory not installed; bubble will be inert")
            return
        }
        val ctrl = factory(serviceScope).also { controller = it }
        val style = OverlayConfigStore(this).bubbleStyle
        bubbleHost = BubbleHost(context = this, controller = ctrl).also { it.show(style) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    override fun onDestroy() {
        bubbleHost?.hide()
        bubbleHost = null
        controller?.dismiss()
        controller = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

        /**
         * Install once from `StyleMirrorApp.onCreate`. The factory is called
         * each time the service starts and is given a `CoroutineScope` tied
         * to that service's lifetime; the controller's launched coroutines
         * are cancelled when the service stops.
         */
        @Volatile
        var controllerFactory: ((CoroutineScope) -> OverlayCandidateController)? = null

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
