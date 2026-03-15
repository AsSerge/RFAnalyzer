#include <jni.h>
#include <string>
#include <android/log.h>
#include <libusb.h>
#include <mutex>
#include <unordered_map>
#include "libairspyhf/airspyhf.h"

/**
 * <h1>RF Analyzer - airspyHF device native code</h1>
 *
 * Module:      airspyhf_device_native.cpp
 * Description: The native jni code which is used by AirspyHFDevice.kt
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

#define LOG_TAG "NativeLibAirspyHF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================
// Globals
// ============================================================
static JavaVM *g_vm = NULL;
static jobject g_airspyHFDeviceObj = NULL;
static jmethodID g_getEmptyBufferMethod = NULL;
static jmethodID g_onSamplesReadyMethod = NULL;

// ============================================================
// JNI: Cache JavaVM
// ============================================================
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// ----------------------------
// Per-device wrapper state
// ----------------------------
struct DeviceWrapperState {
    std::mutex m;              // protects the following fields
    bool starting = false;     // set while start in progress
    bool stopping = false;     // set while stop in progress
    bool streaming = false;    // set if we think device is streaming
    jobject globalRef = nullptr; // JNI global ref to Java AirspyHFDevice instance
};

// global structures to hold states
static std::mutex g_states_mutex; // protects g_states map
static std::unordered_map<jlong, DeviceWrapperState*> g_states;

// helper: get-or-create the wrapper state for nativePtr
static DeviceWrapperState* get_or_create_state(jlong nativePtr) {
    std::lock_guard<std::mutex> lock(g_states_mutex);
    auto it = g_states.find(nativePtr);
    if (it != g_states.end()) return it->second;
    DeviceWrapperState* s = new DeviceWrapperState();
    g_states[nativePtr] = s;
    return s;
}

// helper: remove & free state (call when device is truly closed)
static void free_state(jlong nativePtr, JNIEnv* env) {
    std::lock_guard<std::mutex> lock(g_states_mutex);
    auto it = g_states.find(nativePtr);
    if (it == g_states.end()) return;
    DeviceWrapperState* s = it->second;
    // delete global ref if still present
    if (s->globalRef != nullptr && env != nullptr) {
        env->DeleteGlobalRef(s->globalRef);
        s->globalRef = nullptr;
    }
    delete s;
    g_states.erase(it);
}

// Helper to cast long to airspyhf_device*
static inline struct airspyhf_device* get_device_ptr(jlong nativePtr) {
    return reinterpret_cast<struct airspyhf_device*>(nativePtr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_getLibraryVersionString(
        JNIEnv* env,
        jclass) {
    airspyhf_lib_version_t version;
    airspyhf_lib_version(&version);
    const libusb_version* usb_version = libusb_get_version();

    char version_str[128];
    snprintf(version_str, sizeof(version_str), "AirspyHF Version: %d.%d.%d (Libusb Version: %d.%d.%d.%d%s)",
             version.major_version, version.minor_version, version.revision,
             usb_version->major, usb_version->minor, usb_version->micro, usb_version->nano,
             usb_version->rc ? usb_version->rc : "");
    return env->NewStringUTF(version_str);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeOpenFd(
        JNIEnv* env,
        jclass /* clazz */, // For static methods, it's jclass
        jint fd) {
    struct airspyhf_device* device = nullptr;
    LOGI("Attempting to open Airspy device with fd: %d", fd);
    int result = airspyhf_open_fd(&device, fd);

    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to open Airspy device, error: %d", result);
        return result;
    }
    LOGI("Airspy device opened successfully, pointer: %p", device);

    return reinterpret_cast<jlong>(device);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeVersionStringRead(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeVersionStringRead: Invalid native pointer");
        return nullptr;
    }

    char version[128];
    int result = airspyhf_version_string_read(device, reinterpret_cast<char *>(&version), sizeof(version));
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to read version string, error: %d", result);
        return nullptr;
    }
    return env->NewStringUTF(version);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeClose(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeClose: Invalid native pointer or device already closed");
        return AIRSPYHF_ERROR;
    }
    LOGI("nativeClose: Closing AirspyHF device, pointer: %p", device);

    // Ensure device is stopped before closing (defensive — prevents close while streaming)
    airspyhf_stop(device);

    int result = airspyhf_close(device);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("nativeClose: Failed to close AirspyHF device, error: %d", result);
    }

    // Free wrapper state AFTER airspyhf_close()
    free_state(nativePtr, env);
    LOGI("nativeClose: wrapper state freed for device %p", device);

    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeIsStreaming(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeIsStreaming: Invalid native pointer");
        return JNI_FALSE;
    }

    int streaming_status = airspyhf_is_streaming(device);
    return streaming_status ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeSetHfAgc(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jboolean value) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetHfAgc: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }
    uint8_t agc_enabled = (value == JNI_TRUE) ? 1 : 0;
    LOGI("Setting AGC enabled=%d for device %p", agc_enabled, device);
    int result = airspyhf_set_hf_agc(device, agc_enabled);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to set AGC, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeSetHfAgcThreshold(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jboolean value) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetHfAgcThreshold: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }
    uint8_t agc_threshold = (value == JNI_TRUE) ? 1 : 0;
    LOGI("Setting AGC threshold=%d for device %p", agc_threshold, device);
    int result = airspyhf_set_hf_agc_threshold(device, agc_threshold);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to set AGC threshold, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeSetHfAtt(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jint value) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetHfAtt: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }
    uint8_t att = static_cast<uint8_t>(value);
    LOGI("Setting HF Att to %d for device %p", att, device);
    int result = airspyhf_set_hf_att(device, att);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to set Att, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeSetHfLna(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jboolean value) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetHfLna: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }
    uint8_t lna = (value == JNI_TRUE) ? 1 : 0;
    LOGI("Setting HF LNA enabled=%d for device %p", lna, device);
    int result = airspyhf_set_hf_lna(device, lna);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to set HF LNA, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeSetSampleRate(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jint samplerate) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetSampleRate: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }
    uint32_t rate = static_cast<uint32_t>(samplerate);
    LOGI("Setting sample rate to %u for device %p", rate, device);
    int result = airspyhf_set_samplerate(device, rate);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to set sample rate, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeSetFrequency(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jint freq_hz) {
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeSetFrequency: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }
    uint32_t freq = static_cast<uint32_t>(freq_hz);
    LOGI("Setting frequency to %u Hz for device %p", freq, device);
    int result = airspyhf_set_freq(device, freq);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to set frequency, error: %d", result);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeGetSamplerates(
        JNIEnv* env,
        jobject /* this */,
        jlong nativePtr,
        jobject list_samplerates) { // Pass a mutableListOf<Int> object
    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeGetSamplerates: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }

    uint32_t num_samplerates = 0;
    // First call to get the number of available sample rates
    int result = airspyhf_get_samplerates(device, &num_samplerates, 0);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to get number of samplerates, error: %d", result);
        return result;
    }

    if (num_samplerates == 0) {
        LOGI("No samplerates available for device %p", device);
        return AIRSPYHF_SUCCESS; // No error, just no sample rates
    }

    // Allocate a temporary buffer to hold the sample rates
    auto* rates_buffer = new uint32_t[num_samplerates];

    result = airspyhf_get_samplerates(device, rates_buffer, num_samplerates);
    if (result != AIRSPYHF_SUCCESS) {
        LOGE("Failed to get samplerates, error: %d", result);
        delete[] rates_buffer;
        return result;
    }

    // Get the List.add method
    jclass listClass = env->GetObjectClass(list_samplerates);
    jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID integerConstructor = env->GetMethodID(integerClass, "<init>", "(I)V");

    for (uint32_t i = 0; i < num_samplerates; ++i) {
        jobject rate_obj = env->NewObject(integerClass, integerConstructor, static_cast<jint>(rates_buffer[i]));
        env->CallBooleanMethod(list_samplerates, addMethod, rate_obj);
        env->DeleteLocalRef(rate_obj); // Avoid local reference table overflow
    }

    delete[] rates_buffer;
    env->DeleteLocalRef(listClass);
    env->DeleteLocalRef(integerClass);

    return AIRSPYHF_SUCCESS;
}


// ============================================================
// Airspy RX callback (runs on AirspyHF’s thread)
// ============================================================
static int airspyhf_callback(airspyhf_transfer_t *transfer) {
    JNIEnv *env = nullptr;

    if (g_airspyHFDeviceObj == nullptr) {
        LOGE("airspyhf_callback: g_airspyHFDeviceObj is null");
        return 0;
    }

    if (g_vm->AttachCurrentThread(&env, nullptr) != 0) {
        LOGE("airspyhf_callback: Failed to attach Airspy thread to JVM");
        return 0;
    }

    // Request an empty buffer from Kotlin (blocks if none free)
    jbyteArray buffer = (jbyteArray)env->CallObjectMethod(g_airspyHFDeviceObj, g_getEmptyBufferMethod);

    // Copy samples into buffer
    jbyte *buf_ptr = env->GetByteArrayElements(buffer, nullptr);
    memcpy(buf_ptr, transfer->samples, transfer->sample_count * sizeof(airspyhf_complex_float_t));
    env->ReleaseByteArrayElements(buffer, buf_ptr, 0);

    // Notify Kotlin that samples are ready
    env->CallVoidMethod(g_airspyHFDeviceObj, g_onSamplesReadyMethod, buffer);

    // Release local ref to avoid leaks
    env->DeleteLocalRef(buffer);

    return 0;
}


// ============================================================
// JNI: Start Airspy
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeStartRX(JNIEnv *env, jobject thiz, jlong nativePtr) {

    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeStartRX: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }

    DeviceWrapperState* st = get_or_create_state(nativePtr);

    // Fast pre-check & set "starting" under the state lock to avoid races
    {
        std::lock_guard<std::mutex> lock(st->m);
        if (st->starting) {
            LOGW("nativeStartRX: start already in progress for device %p", (void*)device);
            return AIRSPYHF_ERROR;
        }
        if (st->streaming) {
            LOGI("nativeStartRX: device already streaming, returning success");
            return AIRSPYHF_SUCCESS;
        }
        st->starting = true; // mark we are starting (cleared below)
    }

    // Create global ref and resolve Java method IDs BEFORE calling into library.
    // We do this here so callbacks have a stable jobject during streaming.
    jobject newGlobalRef = env->NewGlobalRef(thiz);
    if (newGlobalRef == nullptr) {
        LOGE("nativeStartRX: NewGlobalRef failed");
        std::lock_guard<std::mutex> lock(st->m);
        st->starting = false;
        return AIRSPYHF_ERROR;
    }

    // Keep global reference to AirspyHFDevice instance
    g_airspyHFDeviceObj = newGlobalRef;

    // Resolve Java methods for airspyhf_callback()
    jclass cls = env->GetObjectClass(thiz);
    g_getEmptyBufferMethod = env->GetMethodID(cls, "getEmptyBuffer", "()[B");
    g_onSamplesReadyMethod = env->GetMethodID(cls, "onSamplesReady", "([B)V");

    // Call the library start function (this may create threads inside the lib)
    int result = airspyhf_start(device, airspyhf_callback, nullptr);

    // Update wrapper state after the library call
    {
        std::lock_guard<std::mutex> lock(st->m);
        st->starting = false;
        if (result == AIRSPYHF_SUCCESS) {
            st->streaming = true;
            st->globalRef = newGlobalRef; // keep the ref for later delete
            LOGI("nativeStartRX: AirspyHF streaming started (device=%p)", (void*)device);
        } else {
            // start failed — clean up the global ref we created
            LOGE("nativeStartRX: airspyhf_start() failed: %d", result);
            airspyhf_close(device);
            g_airspyHFDeviceObj = nullptr;
            g_getEmptyBufferMethod = nullptr;
            g_onSamplesReadyMethod = nullptr;
            env->DeleteGlobalRef(newGlobalRef);
        }
    }

    return result;
}

// ============================================================
// JNI: Stop Airspy
// ============================================================
extern "C" JNIEXPORT jint JNICALL
Java_com_mantz_1it_libairspyhf_AirspyHFDevice_nativeStopRX(JNIEnv *env, jobject thiz, jlong nativePtr) {

    struct airspyhf_device* device = get_device_ptr(nativePtr);
    if (device == nullptr) {
        LOGE("nativeStopRX: Invalid native pointer");
        return AIRSPYHF_ERROR;
    }

    DeviceWrapperState* st = get_or_create_state(nativePtr);

    // Quick check & mark "stopping" to serialize concurrent calls
    {
        std::lock_guard<std::mutex> lock(st->m);
        if (st->stopping) {
            LOGW("nativeStopRX: stop already in progress for device %p", (void*)device);
            return AIRSPYHF_ERROR;
        }
        if (!st->streaming) {
            // Not streaming — nothing to do. Still attempt to tidy up any leftover ref.
            LOGI("nativeStopRX: device not streaming (no-op)");
            if (st->globalRef) {
                env->DeleteGlobalRef(st->globalRef);
                st->globalRef = nullptr;
            }
            // Optionally free per-device state if you know the device will be closed.
            // free_state(nativePtr, env);
            return AIRSPYHF_ERROR;
        }
        st->stopping = true;
    }

    // Now call library stop (this will attempt to stop threads and cancel transfers)
    int result = airspyhf_stop(device);

    // Clear state and delete JNI global ref
    {
        std::lock_guard<std::mutex> lock(st->m);
        st->stopping = false;
        st->streaming = false;

        if (st->globalRef) {
            // Delete the per-device ref we created in start
            env->DeleteGlobalRef(st->globalRef);
            st->globalRef = nullptr;
            g_airspyHFDeviceObj = nullptr;
        }

    }

    LOGI("nativeStopRX: AirspyHF streaming stopped (device=%p), result=%d", (void*)device, result);

    // optionally free state now if you know device is about to be closed; otherwise keep it.
    // free_state(nativePtr, env);

    return result;
}
