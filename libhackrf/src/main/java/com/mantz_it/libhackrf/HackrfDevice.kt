package com.mantz_it.libhackrf

import android.util.Log
import androidx.annotation.Keep
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * <h1>RF Analyzer - Hackrf Device</h1>
 *
 * Module:      HackrfDevice.kt
 * Description: A Kotlin interface to the native libhackrf driver
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

class HackrfDevice private constructor(private var nativeDevicePtr: Long) {

    companion object {
        private const val TAG = "HackrfDevice"

        // This matches libhackrf TRANSFER_BUFFER_SIZE (262144)
        // one RX transfer contains interleaved signed int8 I/Q samples (2 bytes per sample).
        const val BUFFER_SIZE = 262144 // bytes

        @JvmStatic
        private external fun nativeOpenFd(fd: Int): Long

        @JvmStatic
        external fun getLibraryVersionString(): String?

        fun open(fd: Int): Pair<HackrfDevice?, Int?> {
            val result = nativeOpenFd(fd)
            // On error we return a (negative) error code; on success it's a pointer
            // We detect success by assuming returned > 0 is pointer
            // but pointer may be negative, so only asume error in the range -10000 - 0
            if (result <= 0 && result >= -10000) {
                Log.e(TAG, "open: Error opening HackRF device: $result")
                return Pair(null, result.toInt())
            } else {
                return Pair(HackrfDevice(result), null)
            }
        }

        init {
            System.loadLibrary("libhackrf")
            Log.i(TAG, "libhackrf loaded: ${getLibraryVersionString()}")
        }
    }

    // native functions
    private external fun nativeVersionStringRead(nativePtr: Long): String?
    private external fun nativeIsStreaming(nativePtr: Long): Boolean
    private external fun nativeClose(nativePtr: Long): Int
    private external fun nativeSetLnaGain(nativePtr: Long, gain: Int): Int
    private external fun nativeSetVgaGain(nativePtr: Long, gain: Int): Int
    private external fun nativeSetAmpEnable(nativePtr: Long, enable: Boolean): Int
    private external fun nativeSetAntennaEnable(nativePtr: Long, enable: Boolean): Int
    private external fun nativeSetBasebandFilterBandwidth(nativePtr: Long, bandwidth: Int): Int
    private external fun nativeSetSampleRate(nativePtr: Long, sampleRate: Double): Int
    private external fun nativeSetFrequency(nativePtr: Long, frequency: Long): Int
    private external fun nativeStartRX(nativePtr: Long): Int
    private external fun nativeStopRX(nativePtr: Long): Int

    private val bufferPoolSize: Int = 70 // ~ a few transfers queued; adjust if necessary
    private val availableBuffers = ArrayBlockingQueue<ByteArray>(bufferPoolSize)
    private val filledBuffers = ArrayBlockingQueue<ByteArray>(bufferPoolSize)

    init {
        if (nativeDevicePtr == 0L) {
            throw IllegalArgumentException("Native device pointer cannot be null (0) for HackrfDevice.")
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

    fun close(): Boolean {
        if (nativeDevicePtr != 0L) {
            val result = nativeClose(nativeDevicePtr)
            nativeDevicePtr = 0L
            if (result == 0) {
                Log.i(TAG, "close: HackRF device closed successfully.")
                return true
            } else {
                Log.e(TAG, "close: Error closing HackRF device: $result")
            }
        }
        return false
    }

    protected fun finalize() {
        if (nativeDevicePtr != 0L) {
            Log.w(TAG, "finalize: HackrfDevice was not explicitly closed. Closing in finalize().")
            close()
        }
    }

    fun setBasebandFilterBandwidth(bandwidth: Int): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setBasebandFilterBandwidth: nativeDevicePtr is null.")
            return false
        }
        return nativeSetBasebandFilterBandwidth(nativeDevicePtr, bandwidth) == 0
    }

    fun setSampleRate(sampleRate: Double): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setSampleRate: nativeDevicePtr is null.")
            return false
        }
        return nativeSetSampleRate(nativeDevicePtr, sampleRate) == 0
    }

    fun setFrequency(frequencyHz: Long): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setFrequency: nativeDevicePtr is null.")
            return false
        }
        return nativeSetFrequency(nativeDevicePtr, frequencyHz) == 0
    }

    fun setLnaGain(gain: Int): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setLnaGain: nativeDevicePtr is null.")
            return false
        }
        return nativeSetLnaGain(nativeDevicePtr, gain) == 0
    }

    fun setVgaGain(gain: Int): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setVgaGain: nativeDevicePtr is null.")
            return false
        }
        return nativeSetVgaGain(nativeDevicePtr, gain) == 0
    }

    fun setAmpEnable(enable: Boolean): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setAmpEnable: nativeDevicePtr is null.")
            return false
        }
        return nativeSetAmpEnable(nativeDevicePtr, enable) == 0
    }

    fun setAntennaEnable(enable: Boolean): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "setAntennaEnable: nativeDevicePtr is null.")
            return false
        }
        return nativeSetAntennaEnable(nativeDevicePtr, enable) == 0
    }

    fun startRX(): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "startRX: Device already closed or not opened.")
            return false
        }
        return nativeStartRX(nativeDevicePtr) == 0
    }

    fun stopRX(): Boolean {
        if (nativeDevicePtr == 0L) {
            Log.e(TAG, "stopRX: Device already closed or not opened.")
            return false
        }
        return nativeStopRX(nativeDevicePtr) == 0
    }

    // API to get a filled sample buffer (consumed by the app)
    fun getSampleBuffer(): ByteArray? {
        return filledBuffers.poll(100, TimeUnit.MILLISECONDS)
    }

    // Return a buffer to the pool
    fun returnSampleBuffer(buffer: ByteArray) {
        availableBuffers.put(buffer)
    }

    // Called from JNI when native side has samples ready
    @Keep
    private fun onSamplesReady(buffer: ByteArray) {
        // Blocks if the consumer isn't draining filledBuffers fast enough
        filledBuffers.put(buffer)
    }

    // Called from JNI to fetch an empty buffer for filling
    @Keep
    private fun getEmptyBuffer(): ByteArray {
        // Blocks until a buffer is available
        return availableBuffers.take()
    }
}