package com.example.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.MainActivity
import com.example.R
import java.lang.ref.WeakReference

/**
 * Coordinates MediaProjection lifecycle between [YouTubeAudioCaptureService] and [DawAudioEngine].
 */
object YouTubeCaptureBridge {
    @Volatile
    private var engineRef: WeakReference<DawAudioEngine>? = null

    fun registerEngine(engine: DawAudioEngine) {
        engineRef = WeakReference(engine)
    }

    internal fun getEngine(): DawAudioEngine? = engineRef?.get()

    fun startCaptureService(context: Context, resultCode: Int, data: Intent) {
        val intent = Intent(context, YouTubeAudioCaptureService::class.java).apply {
            action = YouTubeAudioCaptureService.ACTION_START
            putExtra(YouTubeAudioCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(YouTubeAudioCaptureService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopCaptureService(context: Context) {
        getEngine()?.stopYouTubeAudioCapture()
        context.stopService(Intent(context, YouTubeAudioCaptureService::class.java))
    }
}

/**
 * Foreground service required on Android 10+ (and strictly enforced on Android 14+) to hold
 * a MediaProjection session for AudioPlaybackCapture of YouTube WebView streams into the DAW.
 */
class YouTubeAudioCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanupProjection()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        val notification = buildNotification()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
            val resultData = intent?.let {
                IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java)
            }
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                startAudioCapture(resultCode, resultData)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startAudioCapture(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        cleanupProjection()

        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        val projection = manager.getMediaProjection(resultCode, data) ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                YouTubeCaptureBridge.getEngine()?.stopYouTubeAudioCapture()
                mediaProjection = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
        projectionCallback = callback
        mediaProjection = projection

        val started = YouTubeCaptureBridge.getEngine()?.startYouTubeAudioCapture(projection) == true
        if (!started) {
            cleanupProjection()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupProjection() {
        YouTubeCaptureBridge.getEngine()?.stopYouTubeAudioCapture()
        val proj = mediaProjection
        val cb = projectionCallback
        mediaProjection = null
        projectionCallback = null
        if (proj != null) {
            if (cb != null) {
                runCatching { proj.unregisterCallback(cb) }
            }
            runCatching { proj.stop() }
        }
    }

    override fun onDestroy() {
        cleanupProjection()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube DAW Audio Capture",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Menangkap audio stream YouTube ke Mixer & Sampler DAW"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, YouTubeAudioCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Spatial DAW — YouTube Audio Stream")
            .setContentText("Audio YouTube sedang mengalir ke Analyzer & Sampler DAW")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Hentikan", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_YT_CAPTURE"
        const val ACTION_STOP = "com.example.action.STOP_YT_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "youtube_audio_capture"
        private const val NOTIFICATION_ID = 4108
    }
}
