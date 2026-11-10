package uk.nktnet.webviewkiosk.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import java.lang.ref.WeakReference
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.utils.webview.BackgroundMediaPlaybackController

/**
 * WebView owns playback; this media session exposes controls for the actual
 * HTMLMediaElements rather than pretending to be an independent native player. No
 * media URL is transferred out of the WebView.
 */
class BackgroundMediaPlaybackService : Service() {
    companion object {
        private const val ACTION_START = "uk.nktnet.webviewkiosk.action.MEDIA_START"
        private const val ACTION_PLAY = "uk.nktnet.webviewkiosk.action.MEDIA_PLAY"
        private const val ACTION_PAUSE = "uk.nktnet.webviewkiosk.action.MEDIA_PAUSE"
        private const val ACTION_STOP = "uk.nktnet.webviewkiosk.action.MEDIA_STOP"
        private const val TAG = "BackgroundMediaPlayback"
        private var activeService: WeakReference<BackgroundMediaPlaybackService>? =
            null

        fun startIntent(context: Context): Intent =
            Intent(context, BackgroundMediaPlaybackService::class.java)
                .setAction(ACTION_START)

        /** Called on the main thread by the WebView's playback state bridge. */
        fun updatePlaying(playing: Boolean) {
            activeService?.get()?.updatePlayback(playing)
        }
    }

    private lateinit var mediaSession: MediaSession
    private var playing = false
    private var foregroundStarted = false

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        CustomNotificationManager.init(this)
        mediaSession =
            MediaSession(this, TAG).apply {
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            BackgroundMediaPlaybackController
                                .resumeFromNotification()
                        }

                        override fun onPause() {
                            BackgroundMediaPlaybackController.pauseFromNotification()
                        }

                        override fun onStop() {
                            BackgroundMediaPlaybackController.stopFromNotification()
                        }
                    }
                )
                setMetadata(
                    MediaMetadata.Builder()
                        .putString(
                            MediaMetadata.METADATA_KEY_TITLE,
                            getString(R.string.notification_background_media_title),
                        )
                        .putString(
                            MediaMetadata.METADATA_KEY_ARTIST,
                            getString(R.string.app_name),
                        )
                        .build()
                )
                isActive = true
            }
        activeService = WeakReference(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BackgroundMediaPlaybackController.stopFromNotification()
            stopSelf()
            return START_NOT_STICKY
        }
        if (
            !UserSettings(this).backgroundMediaPlayback ||
                !BackgroundMediaPlaybackController.isPlaybackRequested()
        ) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> {
                BackgroundMediaPlaybackController.pauseFromNotification()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                BackgroundMediaPlaybackController.resumeFromNotification()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            else -> {
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }
        playing = BackgroundMediaPlaybackController.isPlaying()
        updateSessionState()
        try {
            ServiceCompat.startForeground(
                this,
                CustomNotificationType.BACKGROUND_MEDIA_PLAYBACK,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                },
            )
            foregroundStarted = true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to promote playback service", error)
            BackgroundMediaPlaybackController.stopFromNotification()
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun updatePlayback(currentlyPlaying: Boolean) {
        if (playing == currentlyPlaying) {
            return
        }
        playing = currentlyPlaying
        updateSessionState()
        if (foregroundStarted) {
            CustomNotificationManager.updateServiceNotification(
                this,
                CustomNotificationType.BACKGROUND_MEDIA_PLAYBACK,
                buildNotification(),
            )
        }
    }

    private fun updateSessionState() {
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (playing) {
                        PlaybackState.STATE_PLAYING
                    } else {
                        PlaybackState.STATE_PAUSED
                    },
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) {
                        1f
                    } else {
                        0f
                    },
                )
                .build()
        )
    }

    private fun actionIntent(action: String, requestCode: Int) =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, BackgroundMediaPlaybackService::class.java)
                .setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val openIntent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, MainActivity::class.java)
        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleAction =
            if (playing) {
                ACTION_PAUSE
            } else {
                ACTION_PLAY
            }
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(
                    this,
                    CustomNotificationChannel.BackgroundMediaPlayback.ID,
                )
            } else {
                @Suppress("DEPRECATION") Notification.Builder(this)
            }
        builder
            .setSmallIcon(R.drawable.outline_circle_notifications_24)
            .setContentTitle(getString(R.string.notification_background_media_title))
            .setContentText(
                getString(
                    if (playing) {
                        R.string.notification_background_media_text
                    } else {
                        R.string.notification_background_media_paused
                    }
                )
            )
            .setContentIntent(openPendingIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setOngoing(true)
            .addAction(
                if (playing) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                },
                getString(
                    if (playing) {
                        R.string.notification_background_media_pause
                    } else {
                        R.string.notification_background_media_play
                    }
                ),
                actionIntent(
                    toggleAction,
                    if (playing) {
                        1
                    } else {
                        2
                    },
                ),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_background_media_stop),
                actionIntent(ACTION_STOP, 3),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
        // Notification.Builder has no setSilent() (that is an AndroidX API).
        // The channel is low-importance on O+; on older devices we request no
        // sound, vibration, or default alerts in the builder above.
        return builder.build()
    }

    override fun onDestroy() {
        foregroundStarted = false
        if (activeService?.get() === this) {
            activeService = null
        }
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }
}
