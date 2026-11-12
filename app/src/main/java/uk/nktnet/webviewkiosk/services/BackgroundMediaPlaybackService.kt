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
        private const val ACTION_MUTE = "uk.nktnet.webviewkiosk.action.MEDIA_MUTE"
        private const val ACTION_PREVIOUS_UNAVAILABLE =
            "uk.nktnet.webviewkiosk.action.MEDIA_PREVIOUS_UNAVAILABLE"
        private const val ACTION_NEXT_UNAVAILABLE =
            "uk.nktnet.webviewkiosk.action.MEDIA_NEXT_UNAVAILABLE"
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
                            stopSelf()
                        }

                        override fun onSkipToPrevious() {
                            if (info.canPreviousOrRestart) {
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
                                ACTION_STOP -> onStop()
                                ACTION_MUTE -> {
                                    if (info.canMute) {
                                        BackgroundMediaPlaybackController
                                            .toggleMuteFromNotification()
                                    }
                                }
                                // Android's semantic media controls have no
                                // disabled custom-action state. These dimmed
                                // placeholders are deliberately inert.
                                ACTION_PREVIOUS_UNAVAILABLE,
                                ACTION_NEXT_UNAVAILABLE -> Unit
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
            ACTION_MUTE ->
                BackgroundMediaPlaybackController.toggleMuteFromNotification()
            ACTION_PREVIOUS ->
                BackgroundMediaPlaybackController.previousFromNotification()
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
        if (info.canPreviousOrRestart) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (info.canNext) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        if (info.canSeek) {
            // Keep the position slider, without exposing rewind/forward buttons.
            actions = actions or PlaybackState.ACTION_SEEK_TO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ derives controls from PlaybackState, rather than the
            // notification action list. Fill unavailable skip slots first so
            // both extra slots remain available for Mute and Close, in that order.
            if (!info.canPreviousOrRestart) {
                builder.addCustomAction(
                    ACTION_PREVIOUS_UNAVAILABLE,
                    getString(
                        R.string.notification_background_media_previous_unavailable
                    ),
                    R.drawable.media_previous_disabled,
                )
            }
            if (!info.canNext) {
                builder.addCustomAction(
                    ACTION_NEXT_UNAVAILABLE,
                    getString(
                        R.string.notification_background_media_next_unavailable
                    ),
                    R.drawable.media_next_disabled,
                )
            }
            builder.addCustomAction(
                ACTION_MUTE,
                getString(muteLabel()),
                muteIcon(),
            )
            builder.addCustomAction(
                ACTION_STOP,
                getString(R.string.notification_background_media_stop),
                R.drawable.media_close,
            )
        }
        mediaSession.setPlaybackState(
            builder
                .setActions(actions)
                .setState(
                    if (info.buffering) {
                        PlaybackState.STATE_BUFFERING
                    } else if (playing) {
                        PlaybackState.STATE_PLAYING
                    } else {
                        PlaybackState.STATE_PAUSED
                    },
                    info.positionMs.takeIf { it >= 0 }
                        ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing && !info.buffering) {
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

    private fun muteIcon(): Int =
        if (!info.canMute) {
            R.drawable.media_volume_disabled
        } else if (info.muted) {
            R.drawable.media_volume_off
        } else {
            R.drawable.media_volume_on
        }

    private fun muteLabel(): Int =
        if (!info.canMute) {
            R.string.notification_background_media_mute_unavailable
        } else if (info.muted) {
            R.string.notification_background_media_unmute
        } else {
            R.string.notification_background_media_mute
        }

    @Suppress("DEPRECATION")
    internal fun buildNotification(): Notification {
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
                if (info.buffering) {
                    getString(R.string.notification_background_media_loading)
                } else {
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
                }
            )
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(actionIntent(ACTION_STOP, 5))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setOngoing(true)
        artwork?.let(builder::setLargeIcon)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(
                Notification.FOREGROUND_SERVICE_IMMEDIATE
            )
        }
        var nextIndex = 0
        fun button(icon: Int, label: Int, action: String, enabled: Boolean = true) {
            builder.addAction(
                icon,
                getString(label),
                if (enabled) {
                    actionIntent(action, nextIndex + 1)
                } else {
                    null
                },
            )
            nextIndex++
        }
        button(muteIcon(), muteLabel(), ACTION_MUTE, enabled = info.canMute)
        button(
            if (info.canPreviousOrRestart) {
                R.drawable.media_previous
            } else {
                R.drawable.media_previous_disabled
            },
            if (info.canPreviousOrRestart) {
                R.string.notification_background_media_previous
            } else {
                R.string.notification_background_media_previous_unavailable
            },
            ACTION_PREVIOUS,
            enabled = info.canPreviousOrRestart,
        )
        button(
            if (playing || info.buffering) {
                R.drawable.media_pause
            } else {
                R.drawable.media_play
            },
            if (playing || info.buffering) {
                R.string.notification_background_media_pause
            } else {
                R.string.notification_background_media_play
            },
            if (playing || info.buffering) {
                ACTION_PAUSE
            } else {
                ACTION_PLAY
            },
        )
        button(
            if (info.canNext) {
                R.drawable.media_next
            } else {
                R.drawable.media_next_disabled
            },
            if (info.canNext) {
                R.string.notification_background_media_next
            } else {
                R.string.notification_background_media_next_unavailable
            },
            ACTION_NEXT,
            enabled = info.canNext,
        )
        button(
            R.drawable.media_close,
            R.string.notification_background_media_stop,
            ACTION_STOP,
        )
        builder.setStyle(
            Notification.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(1, 2, 3)
        )
        return builder.build()
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
