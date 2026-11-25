package com.example.netprofile.device

import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import com.example.netprofile.domain.PrivateDns
import com.example.netprofile.domain.Rules
import com.example.netprofile.domain.Snapshot

object DeviceReader {

    private const val PRIVATE_DNS_MODE = "private_dns_mode"

    fun read(context: Context): Snapshot {
        val cr = context.contentResolver
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        return Snapshot(
            airplaneMode = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            batteryOptimized = !power.isIgnoringBatteryOptimizations(context.packageName),
            dataSaverOn = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            privateDns = readPrivateDns(cr),
            wifiEnabled = wifi.isWifiEnabled,
        )
    }

    private fun readPrivateDns(cr: ContentResolver): PrivateDns =
        try {
            Rules.parsePrivateDns(Settings.Global.getString(cr, PRIVATE_DNS_MODE))
        } catch (e: SecurityException) {
            PrivateDns.UNKNOWN
        }
}
