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

/** Keeps user-initiated turns alive; never restarts commands after process death. */
class ExecutionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
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
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "codexr_execution"
        private const val STOP = "com.example.codexmobile.STOP"
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ExecutionService::class.java))
        fun stop(context: Context) { context.stopService(Intent(context, ExecutionService::class.java)) }
    }
}
