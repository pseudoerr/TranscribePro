#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

#define UNUSED(x) (void)(x)
#define TAG "JNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Mock structure for whisper_context
typedef struct whisper_context {
    int dummy;
} whisper_context;

// Mock structure for whisper full params
typedef struct whisper_full_params {
    int dummy;
} whisper_full_params;

// Mock functions for whisper API
whisper_context* whisper_init(void* loader) {
    return (whisper_context*)malloc(sizeof(whisper_context));
}

whisper_context* whisper_init_from_file_with_params(const char* path, void* params) {
    return (whisper_context*)malloc(sizeof(whisper_context));
}

whisper_context* whisper_init_with_params(void* loader, void* params) {
    return (whisper_context*)malloc(sizeof(whisper_context));
}

void whisper_free(whisper_context* ctx) {
    if (ctx) {
        free(ctx);
    }
}

struct whisper_full_params whisper_full_default_params(int strategy) {
    struct whisper_full_params params;
    params.dummy = strategy;
    return params;
}

void whisper_reset_timings(whisper_context* ctx) {
    // Do nothing
}

int whisper_full(whisper_context* ctx, struct whisper_full_params params, const float* samples, int n_samples) {
    return 0;
}

void whisper_print_timings(whisper_context* ctx) {
    // Do nothing
}

int whisper_full_n_segments(whisper_context* ctx) {
    return 1;
}

const char* whisper_full_get_segment_text(whisper_context* ctx, int i) {
    return "Mocked transcription";
}

long whisper_full_get_segment_t0(whisper_context* ctx, int i) {
    return 0;
}

long whisper_full_get_segment_t1(whisper_context* ctx, int i) {
    return 100;
}

const char* whisper_print_system_info() {
    return "Mocked system info";
}

const char* whisper_bench_memcpy_str(int n_threads) {
    return "Mocked memcpy benchmark";
}

const char* whisper_bench_ggml_mul_mat_str(int n_threads) {
    return "Mocked GGML matrix multiplication benchmark";
}

void* whisper_context_default_params() {
    return NULL;
}

// Implementation of JNI functions

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContextFromInputStream(
        JNIEnv *env, jobject thiz, jobject input_stream) {
    UNUSED(thiz);
    UNUSED(input_stream);
    
    LOGI("Initializing context from input stream");
    struct whisper_context *context = whisper_init(NULL);
    return (jlong) context;
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring asset_path_str) {
    UNUSED(thiz);
    UNUSED(assetManager);
    
    LOGI("Initializing context from asset");
    struct whisper_context *context = whisper_init(NULL);
    return (jlong) context;
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    UNUSED(model_path_str);
    
    LOGI("Initializing context from file");
    struct whisper_context *context = whisper_init(NULL);
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    
    LOGI("Freeing context");
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads, jfloatArray audio_data) {
    UNUSED(thiz);
    UNUSED(num_threads);
    
    LOGI("Running full transcribe");
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);

    struct whisper_full_params params = whisper_full_default_params(0);
    whisper_reset_timings(context);

    LOGI("About to run whisper_full");
    if (whisper_full(context, params, audio_data_arr, audio_data_length) != 0) {
        LOGI("Failed to run the model");
    } else {
        whisper_print_timings(context);
    }
    
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    
    LOGI("Getting text segment count");
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    
    LOGI("Getting text segment %d", index);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_segment_text(context, index);
    jstring string = (*env)->NewStringUTF(env, text);
    return string;
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    
    LOGI("Getting text segment T0 for index %d", index);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_segment_t0(context, index);
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    
    LOGI("Getting text segment T1 for index %d", index);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_segment_t1(context, index);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz
) {
    UNUSED(thiz);
    
    LOGI("Getting system info");
    const char *sysinfo = whisper_print_system_info();
    jstring string = (*env)->NewStringUTF(env, sysinfo);
    return string;
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchMemcpy(JNIEnv *env, jobject thiz,
                                                                 jint n_threads) {
    UNUSED(thiz);
    
    LOGI("Running memcpy benchmark with %d threads", n_threads);
    const char *bench_ggml_memcpy = whisper_bench_memcpy_str(n_threads);
    jstring string = (*env)->NewStringUTF(env, bench_ggml_memcpy);
    return string;
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchGgmlMulMat(JNIEnv *env, jobject thiz,
                                                                     jint n_threads) {
    UNUSED(thiz);
    
    LOGI("Running GGML matrix multiplication benchmark with %d threads", n_threads);
    const char *bench_ggml_mul_mat = whisper_bench_ggml_mul_mat_str(n_threads);
    jstring string = (*env)->NewStringUTF(env, bench_ggml_mul_mat);
    return string;
}
