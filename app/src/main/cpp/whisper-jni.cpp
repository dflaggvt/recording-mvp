#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_memorystream_transcription_WhisperEngine_nativeInit(
    JNIEnv *env, jobject thiz, jstring model_path) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGE("Failed to load whisper model");
        return 0;
    }

    LOGI("Whisper model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_memorystream_transcription_WhisperEngine_nativeTranscribe(
    JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_data) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
    jsize n_samples = env->GetArrayLength(audio_data);

    LOGI("Transcribing %d samples", n_samples);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.n_threads        = 4;
    params.language         = "en";
    params.translate        = false;

    int result = whisper_full(ctx, params, audio, n_samples);
    env->ReleaseFloatArrayElements(audio_data, audio, 0);

    if (result != 0) {
        LOGE("Whisper transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    std::string text;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        const char *segment_text = whisper_full_get_segment_text(ctx, i);
        text += segment_text;
    }

    LOGI("Transcription: %d segments, %zu chars", n_segments, text.length());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_memorystream_transcription_WhisperEngine_nativeRelease(
    JNIEnv *env, jobject thiz, jlong context_ptr) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Whisper context released");
    }
}

} // extern "C"
