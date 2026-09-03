package com.example.codexmobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Keeps user-initiated turns alive; never restarts commands after process death. */
class ExecutionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: ExecutionOverlay
    private var appVisible = true
    private var overlayDismissedForTurn = false
    private var typingPreviously = false

    override fun onCreate() {
        super.onCreate()
        activeService = this
        appVisible = applicationVisible
        val controller = (application as CodexApplication).chatController
        overlay = ExecutionOverlay(
            context = this,
            onStop = controller::stopExecution,
            onFollowUp = controller::sendMessage,
            onDismiss = { overlayDismissedForTurn = true },
        )
        controller.isTyping.onEach { typing ->
            if (typing && !typingPreviously) overlayDismissedForTurn = false
            typingPreviously = typing
            refreshOverlay()
        }.launchIn(serviceScope)
        controller.streamingText.onEach { refreshOverlay() }.launchIn(serviceScope)
        controller.phase.onEach { refreshOverlay() }.launchIn(serviceScope)
        controller.attachmentStatus.onEach { refreshOverlay() }.launchIn(serviceScope)
        controller.messages.onEach { refreshOverlay() }.launchIn(serviceScope)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Active CodexR work", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            (application as CodexApplication).chatController.stopExecution()
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ExecutionService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_work_notification)
            .setContentTitle("CodexR is working")
            .setContentText("Processing continues in the background. Tap to return.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Stop", stop).build()
        try {
            ServiceCompat.startForeground(this, 7, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CodexR:Execution")
                    .apply { acquire(6 * 60 * 60 * 1000L) }
            }
        } catch (error: Exception) {
            (application as CodexApplication).chatController.stopExecution("Background execution unavailable: ${error.message}")
            stopSelf()
        }
        // A turn may finish before Android delivers this start request.
        if (!(application as CodexApplication).chatController.hasActiveWork()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as CodexApplication).chatController.stopExecution("Android's background time limit was reached. Return to CodexR to continue.")
        stopSelf()
    }

    override fun onDestroy() {
        overlay.hide()
        serviceScope.cancel()
        if (activeService === this) activeService = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onAppVisibilityChanged(visible: Boolean) {
        appVisible = visible
        refreshOverlay()
    }

    private fun refreshOverlay() {
        if (!::overlay.isInitialized) return
        val controller = (application as CodexApplication).chatController
        if (appVisible || !controller.hasActiveWork() || overlayDismissedForTurn) {
            overlay.hide()
            return
        }
        val latest = controller.streamingText.value.takeIf { it.isNotBlank() }
            ?: controller.attachmentStatus.value?.takeIf { it.isNotBlank() }
            ?: controller.phase.value.takeIf { it.isNotBlank() }
            ?: controller.messages.value.lastOrNull()?.content
            ?: "Processing…"
        overlay.showOrUpdate(latest)
    }

    companion object {
        private const val CHANNEL = "codexr_execution"
        private const val STOP = "com.example.codexmobile.STOP"
        @Volatile private var applicationVisible = true
        @Volatile private var activeService: ExecutionService? = null
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ExecutionService::class.java))
        fun stop(context: Context) { context.stopService(Intent(context, ExecutionService::class.java)) }
        fun setApplicationVisible(visible: Boolean) {
            applicationVisible = visible
            activeService?.onAppVisibilityChanged(visible)
        }
    }
}
