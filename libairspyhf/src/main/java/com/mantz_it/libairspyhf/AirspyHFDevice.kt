package com.mantz_it.libairspyhf

import android.util.Log
import androidx.annotation.Keep
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * <h1>RF Analyzer - AirspyHF Device</h1>
 *
 * Module:      AirspyHFDevice.kt
 * Description: A Kotlin interface to the native libairspyhf driver
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

class AirspyHFDevice private constructor(private var nativeDevicePtr: Long) {

    companion object {
        private const val TAG = "AirspyHFDevice"

        private const val SAMPLES_TO_TRANSFER = 1024 * 4 // from airspyhf.c:55
        const val BUFFER_SIZE = SAMPLES_TO_TRANSFER * 4 * 2  // one sample is stored as complex float (2x4 byte)

        @JvmStatic
        private external fun nativeOpenFd(fd: Int): Long

        @JvmStatic
        external fun getLibraryVersionString(): String?

        fun open(fd: Int): Pair<AirspyHFDevice?, AirspyHFError?> {
            val result = nativeOpenFd(fd)
            val error = AirspyHFError.fromCode(result.toInt())
            return if (error != null && error != AirspyHFError.AIRSPYHF_SUCCESS) {
                Log.e(TAG, "open: Error opening AirspyHF device: $result ($error)")
                Pair(null, error)
            } else {
                val devicePtr = result // Assuming result is the device pointer on success
                Pair(AirspyHFDevice(devicePtr), null)
            }
        }

        // Load the native library
        init {
            System.loadLibrary("libairspyhf")
            Log.i(TAG, "libairspyhf loaded: ${getLibraryVersionString()}")
        }
    }

    // native functions:
    private external fun nativeVersionStringRead(nativePtr: Long): String?
    private external fun nativeIsStreaming(nativePtr: Long): Boolean
    private external fun nativeClose(nativePtr: Long): Int
    private external fun nativeSetSampleRate(nativePtr: Long, sampleRate: Int): Int
    private external fun nativeSetFrequency(nativePtr: Long, frequency: Int): Int
    private external fun nativeGetSamplerates(nativePtr: Long, sampleRates: List<Int>): Int
    private external fun nativeSetHfAgc(nativePtr: Long, enable: Boolean): Int
    private external fun nativeSetHfAgcThreshold(nativePtr: Long, high: Boolean): Int  // true = high, false = low
    private external fun nativeSetHfAtt(nativePtr: Long, step: Int): Int               // Possible values: 0..8 Range: 0..48 dB Attenuation with 6 dB steps
    private external fun nativeSetHfLna(nativePtr: Long, enable: Boolean): Int
    private external fun nativeStartRX(nativePtr: Long): Int
    private external fun nativeStopRX(nativePtr: Long): Int

    private val bufferPoolSize: Int = 70 // ~ 0.25sec of samples @10MSps; adjust if necessary!
    private val availableBuffers = ArrayBlockingQueue<ByteArray>(bufferPoolSize)
    private val filledBuffers = ArrayBlockingQueue<ByteArray>(bufferPoolSize)

    init {
        if (nativeDevicePtr == 0L) {
            throw IllegalArgumentException("Native device pointer cannot be null (0) for AirspyHFDevice.")
        }
        // Create Buffers:
        repeat(bufferPoolSize) {
            availableBuffers.put(ByteArray(BUFFER_SIZE))
        }
    }

    fun flushBufferQueue() {
        var buffer = filledBuffers.poll()
        while (buffer != null) {
            availableBuffers.offer(buffer)
            buffer = filledBuffers.poll()
        }
    }

    fun getVersionString(): String? {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "getVersionString: Device already closed or not opened.")
            return null
        }
        return nativeVersionStringRead(nativeDevicePtr)
    }

    fun isStreaming(): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "isStreaming: Device already closed or not opened.")
            return false
        }
        return nativeIsStreaming(nativeDevicePtr)
    }

    // Call this to release the native resources
    fun close(): Boolean {
        if (nativeDevicePtr != 0L) {
            val result = nativeClose(nativeDevicePtr)
            nativeDevicePtr = 0L // Mark as closed, prevent further use
            if (result == AirspyHFError.AIRSPYHF_SUCCESS.code) { // Assuming you map errors
                Log.i(TAG, "close: AirspyHF device closed successfully.")
                return true
            } else {
                Log.e(TAG, "close: Error closing AirspyHF device: ${AirspyHFError.fromCode(result)}")
            }
        }
        return false
    }

    // Ensure resources are released if the object is GC'd without explicit close
    // This is a fallback, explicit close() is always better.
    protected fun finalize() {
        if (nativeDevicePtr != 0L) {
            Log.w(TAG, "finalize: AirspyHFDevice was not explicitly closed. Closing in finalize().")
            close()
        }
    }

    fun setSampleRate(sampleRate: Int): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setSampleRate: nativeDevicePtr is null. Device might be closed or not initialized.")
            return false
        }
        return nativeSetSampleRate(nativeDevicePtr, sampleRate) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun setFrequency(frequency: Int): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setFrequency: nativeDevicePtr is null. Device might be closed or not initialized.")
            return false
        }
        return nativeSetFrequency(nativeDevicePtr, frequency) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun getSupportedSampleRates(): List<Int>? {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "getSupportedSampleRates: Device already closed or not opened.")
            return null
        }
        val sampleRates = mutableListOf<Int>()
        nativeGetSamplerates(nativeDevicePtr, sampleRates)
        return sampleRates.filter { it != 0 } // Filter out any trailing zeros if the native side pads
    }

    fun setHfAgcEnabled(enabled: Boolean): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setHfAgcEnabled: nativeDevicePtr is null. Device might be closed or not initialized.")
            return false
        }
        return nativeSetHfAgc(nativeDevicePtr, enabled) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun setHfAgcThreshold(high: Boolean): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setHfAgcThreshold: nativeDevicePtr is null. Device might be closed or not initialized.")
            return false
        }
        return nativeSetHfAgcThreshold(nativeDevicePtr, high) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun setHfAttenuation(step: Int): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setHfAttenuation: nativeDevicePtr is null. Device might be closed or not initialized.")
            return false
        }
        return nativeSetHfAtt(nativeDevicePtr, step) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun setHfLnaEnabled(enabled: Boolean): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setHfLnaEnabled: nativeDevicePtr is null. Device might be closed or not initialized.")
            return false
        }
        return nativeSetHfLna(nativeDevicePtr, enabled) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun startRX(): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "startRX: Device already closed or not opened.")
            return false
        }
        return nativeStartRX(nativeDevicePtr) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    fun stopRX(): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "stopRX: Device already closed or not opened.")
            return false
        }
        return nativeStopRX(nativeDevicePtr) == AirspyHFError.AIRSPYHF_SUCCESS.code
    }

    // Get a buffer with samples from the Airspy
    fun getSampleBuffer(timeout: Int = 1000): ByteArray? {
        return filledBuffers.poll(timeout.toLong(), TimeUnit.MILLISECONDS) // returns null if no buffer is available after timeout milliseconds
    }

    // Return a buffer to the pool
    fun returnSampleBuffer(buffer: ByteArray) {
        availableBuffers.put(buffer) // return buffer to pool
    }

    // Called from JNI when native side has samples ready
    @Keep
    private fun onSamplesReady(buffer: ByteArray) {
        filledBuffers.put(buffer) // blocks if user isn't consuming fast enough
    }

    // Called from JNI to fetch an empty buffer for filling
    @Keep
    private fun getEmptyBuffer(): ByteArray {
        return availableBuffers.take() // blocks until a buffer is free
    }
}

enum class AirspyHFError(val code: Int) {
    AIRSPYHF_SUCCESS(0),
    AIRSPYHF_ERROR(-1),
    AIRSPYHF_UNSUPPORTED(-2);

    companion object {
        private val map = entries.associateBy(AirspyHFError::code)
        fun fromCode(code: Int): AirspyHFError? = map[code]
    }

    override fun toString(): String {
        return when (this) {
            AIRSPYHF_SUCCESS -> "SUCCESS"
            AIRSPYHF_ERROR -> "ERROR"
            AIRSPYHF_UNSUPPORTED -> "UNSUPPORTED"
        }
    }
}
