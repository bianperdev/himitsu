// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/** What the compass decoy can show on this device. */
enum class CompassMode {
    /** A magnetometer is present: a real compass. */
    Compass,

    /** No magnetometer but an accelerometer: a bubble level. */
    Level,

    /** Neither sensor: only a message. */
    None
}

/** A reading for the compass decoy. */
sealed interface CompassReading {
    /** @property degrees magnetic heading, smoothed, in [0, 360) */
    data class Heading(val degrees: Float, val needsCalibration: Boolean) : CompassReading

    /** Tilt for the bubble level, each axis between -1 and 1. */
    data class Level(val x: Float, val y: Float) : CompassReading
}

/**
 * Reads the motion sensors for the compass decoy.
 *
 * - Needs no permission: the rotation vector, magnetometer and accelerometer are open to every
 *   app, and [SensorManager.SENSOR_DELAY_UI] stays far below the 200 Hz that would need
 *   `HIGH_SAMPLING_RATE_SENSORS`.
 * - Gives the magnetic heading only: true north needs the location for the declination.
 * - Sensors are registered only while the flow is collected, so the battery is spared while the
 *   decoy is not on screen.
 */
class CompassSensor(context: Context) {
    private val mSensorManager = context.getSystemService(SensorManager::class.java)
    private val mMagnetometer: Sensor? = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val mAccelerometer: Sensor? = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mRotationVector: Sensor? = mSensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val mode: CompassMode = when {
        mMagnetometer != null && (mRotationVector != null || mAccelerometer != null) -> CompassMode.Compass
        mAccelerometer != null -> CompassMode.Level
        else -> CompassMode.None
    }

    /**
     * Readings while collected.
     *
     * @param displayRotation one of the `Surface.ROTATION_*` values, so the heading follows the screen
     */
    fun readings(displayRotation: Int): Flow<CompassReading> = when (mode) {
        CompassMode.Compass -> headings(displayRotation)
        CompassMode.Level -> levels()
        CompassMode.None -> flowOf()
    }

    private fun headings(displayRotation: Int): Flow<CompassReading> = callbackFlow {
        val manager = checkNotNull(mSensorManager) { "Sensors without a sensor manager" }
        val filter = HeadingFilter()
        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null
        var needsCalibration = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The rotation vector fuses the sensors already; otherwise combine gravity and field
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity = event.values.clone()
                        if (mRotationVector != null) return
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic = event.values.clone()
                        if (mRotationVector != null) return
                    }
                    else -> return
                }
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
                    val g = gravity ?: return
                    val m = geomagnetic ?: return
                    if (!SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) return
                }
                remap(rotationMatrix, displayRotation, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                trySend(CompassReading.Heading(filter.update(degrees), needsCalibration))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
                needsCalibration = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                    accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        // The magnetometer is always registered: its accuracy tells when to ask for calibration
        listOfNotNull(mRotationVector, mMagnetometer, mAccelerometer.takeIf { mRotationVector == null }).forEach {
            manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun levels(): Flow<CompassReading> = callbackFlow {
        val manager = checkNotNull(mSensorManager) { "Sensors without a sensor manager" }
        var x = 0f
        var y = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Light smoothing; the screen x axis points right and y up
                x += LEVEL_ALPHA * ((-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f) - x)
                y += LEVEL_ALPHA * ((event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f) - y)
                trySend(CompassReading.Level(x, y))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, mAccelerometer, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }

    private fun remap(input: FloatArray, displayRotation: Int, output: FloatArray) {
        val (x, y) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(input, x, y, output)
    }

    private companion object {
        const val LEVEL_ALPHA = 0.2f
    }
}
