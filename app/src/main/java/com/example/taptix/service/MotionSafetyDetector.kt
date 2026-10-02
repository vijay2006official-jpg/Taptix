package com.example.taptix.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects vehicle movement/vibration using native SensorManager.
 * Triggers Safety Lock to collapse UI into a single extra-large Safety Toggle when driving.
 */
class MotionSafetyDetector(
    context: Context,
    private val onMotionStateChanged: (isDriving: Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isDrivingState = false
    private var lastUpdate = System.currentTimeMillis()
    private var lastMotionTime = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (now - lastUpdate < 300) return // Sample every 300ms
        lastUpdate = now

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total acceleration magnitude minus gravity (~9.8 m/s^2)
        val acceleration = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH

        // Threshold for driving motion/acceleration: > 2.5 m/s^2
        if (acceleration > MOTION_THRESHOLD) {
            lastMotionTime = now
            if (!isDrivingState) {
                isDrivingState = true
                onMotionStateChanged(true)
            }
        } else {
            // Revert back to stationary if no strong motion detected for 5 seconds
            if (isDrivingState && now - lastMotionTime > STATIONARY_TIMEOUT_MS) {
                isDrivingState = false
                onMotionStateChanged(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val MOTION_THRESHOLD = 2.5
        private const val STATIONARY_TIMEOUT_MS = 5000L
    }
}
