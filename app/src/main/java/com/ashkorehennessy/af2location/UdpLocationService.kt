package com.ashkorehennessy.af2location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class UdpLocationService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var socket: DatagramSocket? = null
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "AF2_GPS_CHANNEL"
        const val ACTION_STOP = "STOP_SERVICE"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra("PORT", 49002) ?: 49002

        if (!isRunning) {
            isRunning = true
            createNotificationChannel()

            startForeground(1, createNotification(port), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

            Toast.makeText(this, getString(R.string.toast_service_started, port), Toast.LENGTH_SHORT).show()
            serviceScope.launch {
                runUdpServer(port)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        socket?.close()
        serviceJob.cancel()

        try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {}

        Toast.makeText(this, getString(R.string.toast_service_stopped), Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runUdpServer(port: Int) {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providerName = LocationManager.GPS_PROVIDER

        try {
            if (locationManager.allProviders.contains(providerName)) {
                try { locationManager.removeTestProvider(providerName) } catch (_: Exception) {}
            }

            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (_: SecurityException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, getString(R.string.toast_mock_error), Toast.LENGTH_LONG).show()
            }
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e("UDP", "Init error: ${e.message}")
        }

        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }

            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            val kalmanFilter = KalmanLatLong()
            Log.d("UDP", "Listening on $port")

            while (currentCoroutineContext().isActive) {
                socket?.receive(packet)
                val rawData = String(packet.data, 0, packet.length, Charsets.US_ASCII).trim()

                if (rawData.startsWith("XGPS")) {
                    val parts = rawData.substringAfter(",").split(",")
                    if (parts.size >= 5) {
                        val rawLon = parts[0].toDoubleOrNull() ?: 0.0
                        val rawLat = parts[1].toDoubleOrNull() ?: 0.0
                        val alt = parts[2].toDoubleOrNull() ?: 0.0
                        val rawBearing = parts[3].toDoubleOrNull()?.toFloat() ?: 0.0f
                        val speed = parts[4].toDoubleOrNull()?.toFloat() ?: 0.0f

                        val currentTime = System.currentTimeMillis()

                        val (smoothLat, smoothLon) = kalmanFilter.process(rawLat, rawLon, currentTime)

                        val mockLocation = Location(providerName).apply {
                            latitude = smoothLat
                            longitude = smoothLon
                            altitude = alt
                            this.bearing = rawBearing
                            this.speed = speed
                            accuracy = 3.0f
                            verticalAccuracyMeters = 1.0f
                            speedAccuracyMetersPerSecond = 0.1f
                            bearingAccuracyDegrees = 1.0f
                            time = System.currentTimeMillis()
                            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                            extras = Bundle().apply { putInt("satellites", 15) }
                        }
                        try {
                            locationManager.setTestProviderLocation(providerName, mockLocation)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UDP", "Loop error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aerofly GPS Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(port: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, UdpLocationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_content, port))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop_service), stopPendingIntent)
            .build()
    }
}

class KalmanLatLong {
    private val minAccuracy = 1f
    private var qMetresPerSecond = minAccuracy * 5
    private var timeStampMilliseconds: Long = 0
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var variance: Float = -1f

    private val sensorAccuracy = 15f

    fun setState(latitude: Double, longitude: Double, accuracy: Float, timeStamp: Long) {
        lat = latitude
        lng = longitude
        variance = accuracy * accuracy
        timeStampMilliseconds = timeStamp
    }

    fun process(latMeasurement: Double, lngMeasurement: Double, timeStamp: Long): Pair<Double, Double> {
        if (variance < 0) {
            setState(latMeasurement, lngMeasurement, sensorAccuracy, timeStamp)
            return Pair(latMeasurement, lngMeasurement)
        }

        val duration = timeStamp - timeStampMilliseconds
        if (duration > 0) {
            variance += duration * qMetresPerSecond * qMetresPerSecond / 1000
            timeStampMilliseconds = timeStamp
        }

        val k = variance / (variance + sensorAccuracy * sensorAccuracy)

        lat += k * (latMeasurement - lat)
        lng += k * (lngMeasurement - lng)

        variance *= (1 - k)

        return Pair(lat, lng)
    }
}