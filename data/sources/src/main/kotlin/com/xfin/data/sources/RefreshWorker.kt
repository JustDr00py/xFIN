package com.xfin.data.sources

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Keeps playlists (daily) and guides (twice a day) fresh in the background. */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sources: SourceRepository,
    private val epg: EpgRepository,
    private val vod: VodRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        sources.refreshStale(maxAgeMs = TimeUnit.HOURS.toMillis(24))
        epg.refreshStale(maxAgeMs = TimeUnit.HOURS.toMillis(12))
        vod.refreshStale(maxAgeMs = TimeUnit.HOURS.toMillis(24))
        return Result.success()
    }

    companion object {
        private const val NAME = "xfin-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            // Also catch up once per app start (e.g. after an upgrade or a long time offline).
            workManager.enqueueUniqueWork(
                "$NAME-startup",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
