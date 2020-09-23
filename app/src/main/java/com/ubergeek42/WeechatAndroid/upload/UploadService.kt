package com.ubergeek42.WeechatAndroid.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ubergeek42.WeechatAndroid.R
import com.ubergeek42.WeechatAndroid.Weechat
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root

const val ACTION_UPLOAD = "com.ubergeek42.WeechatAndroid.UPLOAD"
const val NOTIFICATION_CHANNEL_UPLOAD = "upload"
const val NOTIFICATION_ID = 64

class UploadService : Service() {
    @Root private val kitty = Kitty.make()

    @MainThread override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    @MainThread override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        val service = this@UploadService
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum class State { UPLOADING_DETERMINATE, UPLOADING_INDETERMINATE, FINISHED, NOT_FINISHED }

    @MainThread fun update() {
        val numberOfUploads = uploads.size
        val stats = uploads.stats

        val state = if (numberOfUploads == 0) {
                        main(delay = 3000) { if (uploads.size == 0) stopSelf() }
                        if (lastRemovedUpload?.state == Upload.State.FAILED) State.NOT_FINISHED else State.FINISHED
                    } else {
                        if (stats.ratio == 1f) State.UPLOADING_INDETERMINATE else State.UPLOADING_DETERMINATE
                    }
        showNotification(state, numberOfUploads, stats.ratio, stats.totalBytes)
    }

    private fun showNotification(state: State, numberOfUploads: Int, ratio: Float, totalBytes: Long) {
        val (title, icon) = when (state) {
            State.UPLOADING_DETERMINATE -> "Uploading $numberOfUploads files" to R.drawable.ic_notification_uploading
            State.UPLOADING_INDETERMINATE -> "Uploading $numberOfUploads files" to R.drawable.ic_notification_uploading
            State.FINISHED -> "Upload finished" to R.drawable.ic_notification_upload_done
            State.NOT_FINISHED -> "Upload not finished" to R.drawable.ic_notification_upload_cancelled
        }

        val percentage = (ratio * 100).toInt()
        val size = humanizeSize(totalBytes.toFloat())
        val text = if (numberOfUploads > 0) "$percentage of $size" else null

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_UPLOAD)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setOngoing(true)

        if (numberOfUploads > 0) {
            builder.setContentText("$percentage% of $size")
            builder.setProgress(100, percentage, state == State.UPLOADING_INDETERMINATE)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    getCancelAllUploadsIntent())
        }

        val notification = builder.build()

        limiter.post {
            kitty.debug("showNotification($state, $numberOfUploads, $ratio, $totalBytes)")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // at the time you can post notifications updates at the rate of roughly 5 per second
    // see DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE here and the relevant rate algorithm here:
    // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/notification/NotificationManagerService.java
    // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/notification/RateEstimator.java
    private val limiter = DelayingLimiter(250)

    companion object {
        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                makeNotificationChannel()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun makeNotificationChannel() {
            val context = Weechat.applicationContext
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            NotificationChannel(NOTIFICATION_CHANNEL_UPLOAD,
                    "Uploads",
                    NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                manager.createNotificationChannel(this)
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////

        private val uploads = mutableSetOf<Upload>()
        private var lastRemovedUpload: Upload? = null

        @MainThread fun onUploadStarted(upload: Upload) {
            Intent(applicationContext, UploadService::class.java).apply {
                action = Intent.ACTION_SEND
                data = upload.suri.uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                suppress<SecurityException> { applicationContext.startService(this) }
            }

            uploads.add(upload)
            updateService()
        }

        @MainThread fun onUploadRemoved(upload: Upload) {
            lastRemovedUpload = upload
            uploads.remove(upload)
            updateService()
        }

        @MainThread fun onUploadProgress() {
            updateService()
        }

        private val bindIntent = Intent(applicationContext, UploadService::class.java)

        private fun updateService() {
            applicationContext.bindService(bindIntent,
                    object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            (service as LocalBinder).service.update()
                            applicationContext.unbindService(this)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) { /* ignored */
                        }
                    }, Context.BIND_AUTO_CREATE)
        }

        ////////////////////////////////////////////////////////////////////////////////////////////

        @MainThread fun cancelAllUploads() {
            uploads.forEach { it.cancel() }
        }
    }
}

private fun getCancelAllUploadsIntent(): PendingIntent {
    val intent = Intent(applicationContext, CancelAllUploadsReceiver::class.java)
    return PendingIntent.getBroadcast(applicationContext, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

class CancelAllUploadsReceiver : BroadcastReceiver() {
    @MainThread override fun onReceive(context: Context, intent: Intent) {
        UploadService.cancelAllUploads()
    }
}