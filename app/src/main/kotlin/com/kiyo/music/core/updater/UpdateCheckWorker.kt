package com.kiyo.music.core.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kiyo.music.ui.screens.update.maybeNotifyUpdate
import java.util.concurrent.TimeUnit

/**
 * Looks for a new release a few times a day while Kiyo is closed, so the "Kiyo x.y.z is out" notification shows up
 * without having to open the app first. It uses the same channel, the same checks and the same reminder limits as
 * the check at launch (see maybeNotifyUpdate), and does nothing when update checks or update notifications are off.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!getAutoUpdateCheckSetting(context) || !getUpdateNotificationsSetting(context)) return Result.success()

        // force: the 15 minute shared copy is for the screens, this run is the one that has to see a fresh release
        val found = getLatestRelease(updateChannel(context), force = true)
        // no network or github is busy: try again later; no release published at all: nothing to tell
        if (found.isFailure) return Result.retry()
        val release = found.getOrNull() ?: return Result.success()
        val available = isNewerThanInstalled(release.version)
        saveUpdateAvailableState(context, available)
        if (available) maybeNotifyUpdate(context, release)
        return Result.success()
    }

    companion object {
        private const val WorkName = "update_check"

        /** safe to call on every start: an already scheduled check keeps its place */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
