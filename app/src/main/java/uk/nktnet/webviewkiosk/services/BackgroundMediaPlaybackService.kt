package uk.nktnet.webviewkiosk.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ServiceCompat
import java.lang.ref.WeakReference
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.CustomNotificationChannel
import uk.nktnet.webviewkiosk.managers.CustomNotificationManager
import uk.nktnet.webviewkiosk.managers.CustomNotificationType
import uk.nktnet.webviewkiosk.utils.webview.BackgroundMediaPlaybackController
import uk.nktnet.webviewkiosk.utils.webview.MediaPlaybackInfo

/**
 * Android transport and artwork for the existing WebView player. No website media
 * URLs or streams are copied to a native media engine. Capability flags are supplied
 * by the active WebView document, not by the notification UI.
 */
class BackgroundMediaPlaybackService : Service() {
    companion object {
        private const val ACTION_START = "uk.nktnet.webviewkiosk.action.MEDIA_START"
        private const val ACTION_PLAY = "uk.nktnet.webviewkiosk.action.MEDIA_PLAY"
        private const val ACTION_PAUSE = "uk.nktnet.webviewkiosk.action.MEDIA_PAUSE"
        private const val ACTION_NEXT = "uk.nktnet.webviewkiosk.action.MEDIA_NEXT"
        private const val ACTION_PREVIOUS =
            "uk.nktnet.webviewkiosk.action.MEDIA_PREVIOUS"
        private const val ACTION_REWIND =
            "uk.nktnet.webviewkiosk.action.MEDIA_REWIND"
        private const val ACTION_FORWARD =
            "uk.nktnet.webviewkiosk.action.MEDIA_FORWARD"
        private const val ACTION_SPEED = "uk.nktnet.webviewkiosk.action.MEDIA_SPEED"
        private const val ACTION_STOP = "uk.nktnet.webviewkiosk.action.MEDIA_STOP"
        private const val TAG = "BackgroundMediaPlayback"
        private var activeService: WeakReference<BackgroundMediaPlaybackService>? =
            null

        fun startIntent(context: Context): Intent =
            Intent(context, BackgroundMediaPlaybackService::class.java)
                .setAction(ACTION_START)

        /** Called on the main thread by WebView's JS bridge. */
        fun updatePlaying(playing: Boolean) {
            activeService?.get()?.updatePlayback(playing)
        }

        internal fun updateMediaInfo(info: MediaPlaybackInfo) {
            activeService?.get()?.updateInfo(info)
        }
    }

    private lateinit var mediaSession: MediaSession
    private val artworkLoader = MediaArtworkLoader()
    private var info = MediaPlaybackInfo()
    private var playing = false
    private var foregroundStarted = false
    private var artwork: Bitmap? = null

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

                        override fun onSkipToPrevious() {
                            if (info.canPrevious) {
                                BackgroundMediaPlaybackController
                                    .previousFromNotification()
                            }
                        }

                        override fun onSkipToNext() {
                            if (info.canNext) {
                                BackgroundMediaPlaybackController
                                    .nextFromNotification()
                            }
                        }

                        override fun onSeekTo(position: Long) {
                            if (info.canSeek) {
                                BackgroundMediaPlaybackController
                                    .seekFromNotification(position)
                            }
                        }

                        override fun onRewind() {
                            if (info.canSeek) {
                                BackgroundMediaPlaybackController
                                    .rewindFromNotification()
                            }
                        }

                        override fun onFastForward() {
                            if (info.canSeek) {
                                BackgroundMediaPlaybackController
                                    .forwardFromNotification()
                            }
                        }

                        override fun onCustomAction(
                            action: String,
                            extras: Bundle?,
                        ) {
                            when (action) {
                                ACTION_REWIND -> onRewind()
                                ACTION_FORWARD -> onFastForward()
                                ACTION_SPEED -> {
                                    if (info.canChangeSpeed) {
                                        BackgroundMediaPlaybackController
                                            .speedFromNotification()
                                    }
                                }
                                ACTION_STOP -> onStop()
                            }
                        }
                    }
                )
                isActive = true
            }
        activeService = WeakReference(this)
        publishMetadata()
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
            ACTION_PLAY -> BackgroundMediaPlaybackController.resumeFromNotification()
            ACTION_PAUSE -> BackgroundMediaPlaybackController.pauseFromNotification()
            ACTION_NEXT -> BackgroundMediaPlaybackController.nextFromNotification()
            ACTION_PREVIOUS ->
                BackgroundMediaPlaybackController.previousFromNotification()
            ACTION_REWIND ->
                BackgroundMediaPlaybackController.rewindFromNotification()
            ACTION_FORWARD ->
                BackgroundMediaPlaybackController.forwardFromNotification()
            ACTION_SPEED -> BackgroundMediaPlaybackController.speedFromNotification()
            ACTION_START -> Unit
            else -> {
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }
        if (!foregroundStarted) {
            playing = BackgroundMediaPlaybackController.isPlaying()
            BackgroundMediaPlaybackController.currentMediaInfo()?.let(::updateInfo)
            publishPlaybackState()
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
        }
        return START_NOT_STICKY
    }

    private fun updatePlayback(currentlyPlaying: Boolean) {
        if (playing == currentlyPlaying) {
            return
        }
        playing = currentlyPlaying
        publishPlaybackState()
        refreshNotification()
    }

    private fun updateInfo(value: MediaPlaybackInfo) {
        if (info == value) {
            return
        }
        val refresh = info.presentationChanged(value)
        // On Android 13+, the flat notification uses a regular progress bar.
        // Repaint only on a whole-percent change, not on every WebView time tick.
        val progressChanged =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                flatMediaProgress(info) != flatMediaProgress(value)
        val artworkChanged = info.artworkUrl != value.artworkUrl
        info = value
        publishPlaybackState()
        if (artworkChanged) {
            artwork = null
            artworkLoader.load(
                value.artworkUrl,
                BackgroundMediaPlaybackController.currentPageHost(),
            ) {
                if (info.artworkUrl == value.artworkUrl) {
                    artwork = it
                    publishMetadata()
                    refreshNotification()
                }
            }
        }
        if (refresh) {
            publishMetadata()
        }
        if (refresh || progressChanged) {
            refreshNotification()
        }
    }

    private fun publishMetadata() {
        val builder =
            MediaMetadata.Builder()
                .putString(
                    MediaMetadata.METADATA_KEY_TITLE,
                    info.title.ifBlank {
                        getString(R.string.notification_background_media_title)
                    },
                )
                .putString(
                    MediaMetadata.METADATA_KEY_ARTIST,
                    info.artist.ifBlank { getString(R.string.app_name) },
                )
        if (info.durationMs > 0) {
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, info.durationMs)
        }
        artwork?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        mediaSession.setMetadata(builder.build())
    }

    private fun publishPlaybackState() {
        var actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP
        val builder = PlaybackState.Builder()
        if (info.canPrevious) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (info.canNext) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        // Android 13+ uses the session's custom actions instead of the
        // notification's action list. Expose Stop there as well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.addCustomAction(
                PlaybackState.CustomAction.Builder(
                        ACTION_STOP,
                        getString(R.string.notification_background_media_stop),
                        android.R.drawable.ic_menu_close_clear_cancel,
                    )
                    .build()
            )
        }
        if (info.canSeek) {
            actions =
                actions or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_REWIND or
                    PlaybackState.ACTION_FAST_FORWARD
            builder.addCustomAction(
                PlaybackState.CustomAction.Builder(
                        ACTION_REWIND,
                        getString(R.string.notification_background_media_rewind),
                        android.R.drawable.ic_media_rew,
                    )
                    .build()
            )
            builder.addCustomAction(
                PlaybackState.CustomAction.Builder(
                        ACTION_FORWARD,
                        getString(R.string.notification_background_media_forward),
                        android.R.drawable.ic_media_ff,
                    )
                    .build()
            )
        }
        if (info.canChangeSpeed) {
            builder.addCustomAction(
                PlaybackState.CustomAction.Builder(
                        ACTION_SPEED,
                        getString(
                            R.string.notification_background_media_speed,
                            info.speed.toString(),
                        ),
                        android.R.drawable.ic_menu_manage,
                    )
                    .build()
            )
        }
        mediaSession.setPlaybackState(
            builder
                .setActions(actions)
                .setState(
                    if (playing) {
                        PlaybackState.STATE_PLAYING
                    } else {
                        PlaybackState.STATE_PAUSED
                    },
                    info.positionMs.takeIf { it >= 0 }
                        ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) {
                        info.speed
                    } else {
                        0f
                    },
                )
                .build()
        )
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, BackgroundMediaPlaybackService::class.java)
                .setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Suppress("DEPRECATION")
    internal fun buildNotification(
        flatLayout: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ): Notification {
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
            .setContentTitle(
                info.title.ifBlank {
                    getString(R.string.notification_background_media_title)
                }
            )
            .setContentText(
                info.artist.ifBlank {
                    if (playing) {
                        getString(
                            R.string.notification_background_media_text,
                            getString(R.string.app_name),
                        )
                    } else {
                        getString(R.string.notification_background_media_paused)
                    }
                }
            )
            .setContentIntent(openPendingIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setOngoing(true)
        artwork?.let(builder::setLargeIcon)

        if (flatLayout && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // The Android 13+ media card draws its own animated squiggle and
            // exposes no API to make that seekbar straight. Use a decorated
            // notification with a flat progress indicator instead. Keep the
            // MediaSession active for headset / external transport controls.
            val compact =
                flatMediaView(R.layout.notification_media_compact, openPendingIntent)
            val expanded =
                flatMediaView(
                    R.layout.notification_media_expanded,
                    openPendingIntent,
                )
            builder
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
            return builder.build()
        }

        var nextIndex = 0
        val compact = mutableListOf<Int>()
        fun button(
            icon: Int,
            label: Int,
            action: String,
            compactView: Boolean = false,
        ) {
            builder.addAction(
                icon,
                getString(label),
                actionIntent(action, nextIndex + 1),
            )
            if (compactView) {
                compact.add(nextIndex)
            }
            nextIndex++
        }
        if (info.canPrevious) {
            button(
                android.R.drawable.ic_media_previous,
                R.string.notification_background_media_previous,
                ACTION_PREVIOUS,
                compactView = true,
            )
        }
        button(
            if (playing) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            },
            if (playing) {
                R.string.notification_background_media_pause
            } else {
                R.string.notification_background_media_play
            },
            if (playing) {
                ACTION_PAUSE
            } else {
                ACTION_PLAY
            },
            compactView = true,
        )
        if (info.canNext) {
            button(
                android.R.drawable.ic_media_next,
                R.string.notification_background_media_next,
                ACTION_NEXT,
                compactView = true,
            )
        }
        if (info.canSeek && nextIndex < 4) {
            button(
                android.R.drawable.ic_media_rew,
                R.string.notification_background_media_rewind,
                ACTION_REWIND,
            )
        }
        if (info.canSeek && nextIndex < 4) {
            button(
                android.R.drawable.ic_media_ff,
                R.string.notification_background_media_forward,
                ACTION_FORWARD,
            )
        }
        if (info.canChangeSpeed && nextIndex < 4) {
            button(
                android.R.drawable.ic_menu_manage,
                R.string.notification_background_media_speed_title,
                ACTION_SPEED,
            )
        }
        button(
            android.R.drawable.ic_menu_close_clear_cancel,
            R.string.notification_background_media_stop,
            ACTION_STOP,
            compactView = compact.size < 3,
        )
        builder.setStyle(
            Notification.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(*compact.toIntArray())
        )
        return builder.build()
    }

    private fun flatMediaView(layout: Int, openIntent: PendingIntent): RemoteViews {
        val views = RemoteViews(packageName, layout)
        views.setTextViewText(
            R.id.notification_media_title,
            info.title.ifBlank {
                getString(R.string.notification_background_media_title)
            },
        )
        views.setTextViewText(
            R.id.notification_media_artist,
            info.artist.ifBlank { getString(R.string.app_name) },
        )
        views.setOnClickPendingIntent(R.id.notification_media_open, openIntent)
        if (artwork != null) {
            views.setImageViewBitmap(R.id.notification_media_artwork, artwork)
            views.setViewVisibility(R.id.notification_media_artwork, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.notification_media_artwork, View.GONE)
        }

        views.setImageViewResource(
            R.id.notification_media_play_pause,
            if (playing) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            },
        )
        views.setContentDescription(
            R.id.notification_media_play_pause,
            getString(
                if (playing) {
                    R.string.notification_background_media_pause
                } else {
                    R.string.notification_background_media_play
                }
            ),
        )
        views.setOnClickPendingIntent(
            R.id.notification_media_play_pause,
            actionIntent(
                if (playing) {
                    ACTION_PAUSE
                } else {
                    ACTION_PLAY
                },
                2,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.notification_media_stop,
            actionIntent(ACTION_STOP, 3),
        )

        if (layout == R.layout.notification_media_expanded) {
            fun control(id: Int, visible: Boolean, action: String, code: Int) {
                views.setViewVisibility(
                    id,
                    if (visible) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    },
                )
                if (visible) {
                    views.setOnClickPendingIntent(id, actionIntent(action, code))
                }
            }
            control(
                R.id.notification_media_previous,
                info.canPrevious,
                ACTION_PREVIOUS,
                4,
            )
            control(R.id.notification_media_next, info.canNext, ACTION_NEXT, 5)
            control(R.id.notification_media_rewind, info.canSeek, ACTION_REWIND, 6)
            control(R.id.notification_media_forward, info.canSeek, ACTION_FORWARD, 7)
            control(
                R.id.notification_media_speed,
                info.canChangeSpeed,
                ACTION_SPEED,
                8,
            )
            views.setTextViewText(
                R.id.notification_media_speed,
                getString(
                    R.string.notification_background_media_speed,
                    info.speed.toString(),
                ),
            )
            val progress = flatMediaProgress(info)
            views.setViewVisibility(
                R.id.notification_media_progress,
                if (progress == null) {
                    View.GONE
                } else {
                    View.VISIBLE
                },
            )
            if (progress != null) {
                views.setProgressBar(
                    R.id.notification_media_progress,
                    100,
                    progress,
                    false,
                )
            }
        }
        return views
    }

    private fun refreshNotification() {
        if (foregroundStarted) {
            CustomNotificationManager.updateServiceNotification(
                this,
                CustomNotificationType.BACKGROUND_MEDIA_PLAYBACK,
                buildNotification(),
            )
        }
    }

    override fun onDestroy() {
        foregroundStarted = false
        artworkLoader.close()
        if (activeService?.get() === this) {
            activeService = null
        }
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }
}

/** A bounded, integer-only progress value for the static notification bar. */
internal fun flatMediaProgress(info: MediaPlaybackInfo): Int? {
    if (info.durationMs <= 0L || info.positionMs < 0L || !info.canSeek) {
        return null
    }
    return (info.positionMs.coerceIn(0L, info.durationMs) * 100L / info.durationMs)
        .toInt()
}
