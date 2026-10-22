package com.asd.player.data.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.asd.player.L10n
import com.asd.player.R

/**
 * Servizio in primo piano acceso solo durante il login Google nel browser.
 *
 * Mentre l'utente è nel browser ASD sta in background, e Android congela i processi in
 * background: il server su 127.0.0.1 di [OwnGoogleClient] accetterebbe la connessione senza mai
 * rispondere, e il browser andrebbe in timeout. Con un servizio in primo piano il processo resta vivo.
 */
class DriveLoginService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, L10n.str(R.string.drive_login_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_asd)
            .setContentTitle("ASD")
            .setContentText(L10n.str(R.string.drive_login_wait))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "drive_login"
        private const val NOTIFICATION_ID = 4031

        /** Da chiamare con ASD in primo piano (il tocco su "Collega un account"). */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, DriveLoginService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DriveLoginService::class.java))
        }
    }
}
