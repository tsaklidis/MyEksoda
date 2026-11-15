package io.eksoda.core.data.imports

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import io.eksoda.core.data.R
import io.eksoda.core.data.util.Fixed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Receipt from AB Vasilopoulos imported · €32.10" (§7.6), only when the user turned import
 * notifications on (Settings, L7), the permission is granted, and the app is not visible. The
 * receipt URL is never part of a notification (§8.1).
 */
@Singleton
class ImportNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
) {
    suspend fun imported(persisted: Persisted) {
        if (preferences.data.first()[NOTIFY_IMPORTS_KEY] != true) return
        val visible = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (visible || !canNotify()) return
        ensureChannel()
        val amount = NumberFormat.getCurrencyInstance().apply {
            currency =
                Currency.getInstance("EUR")
        }
            .format(Fixed.toDecimal(persisted.grossTotalMinor, Fixed.MINOR))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_RECEIPT_ID, persisted.receiptId)
        val content = launch?.let {
            PendingIntent.getActivity(
                context,
                persisted.receiptId.hashCode(),
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(
                context.getString(R.string.import_notification_title, persisted.merchantName),
            )
            .setContentText(amount)
            .setContentIntent(content)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(persisted.receiptId.hashCode(), notification)
    }

    private fun canNotify(): Boolean {
        val granted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.import_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_RECEIPT_ID = "io.eksoda.extra.RECEIPT_ID"
        val NOTIFY_IMPORTS_KEY = booleanPreferencesKey("notify_imports")
        private const val CHANNEL_ID = "imports"
        private const val GROUP = "imports"
    }
}
