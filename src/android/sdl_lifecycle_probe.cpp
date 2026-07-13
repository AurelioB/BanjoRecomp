#include <android/log.h>
#include <jni.h>
#include <SDL2/SDL.h>
#include <SDL2/SDL_main.h>
#include <SDL2/SDL_system.h>

namespace {
constexpr const char* kLogTag = "BanjoSDLProbe";

void set_dual_screen_probe_active(bool active) {
    JNIEnv* env = static_cast<JNIEnv*>(SDL_AndroidGetJNIEnv());
    if (env == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "JNI env unavailable for dual-screen probe");
        return;
    }

    jclass activity_class = env->FindClass("io/github/banjorecomp/BanjoSDLActivity");
    if (activity_class == nullptr) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "BanjoSDLActivity class unavailable for dual-screen probe");
        return;
    }

    jmethodID update_stats = env->GetStaticMethodID(activity_class,
                                                    "updateDualScreenStatsFromNative",
                                                    "(IIIIIIIIIIIIIIIIII)V");
    jmethodID set_active = env->GetStaticMethodID(activity_class,
                                                  "setDualScreenGameplayActiveFromNative",
                                                  "(Z)V");
    if (update_stats != nullptr) {
        env->CallStaticVoidMethod(activity_class, update_stats,
                                  active ? 1 : 0, 6, 8, 3, 42, 5, 0, 0, 7, 12, 0x10, 0b10101,
                                  7, 42, 4, 1, 0, 0);
    }
    if (set_active != nullptr) {
        env->CallStaticVoidMethod(activity_class, set_active, active ? JNI_TRUE : JNI_FALSE);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(activity_class);
}
}

extern "C" __attribute__((visibility("default"))) void Java_io_github_banjorecomp_BanjoSDLActivity_nativeSetAndroidSurfaceReady(
    JNIEnv*,
    jclass,
    jboolean ready) {
    __android_log_print(ANDROID_LOG_VERBOSE, kLogTag, "surface ready=%d", ready ? 1 : 0);
}

extern "C" __attribute__((visibility("default"))) void Java_io_github_banjorecomp_BanjoSDLActivity_nativeSetAppAudioActive(
    JNIEnv*,
    jclass,
    jboolean active) {
    __android_log_print(ANDROID_LOG_VERBOSE, kLogTag, "audio active=%d", active ? 1 : 0);
}

#if !defined(BANJO_ANDROID_VULKAN_SMOKE_PROBE_STANDALONE)
extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char** argv) {
    (void)argc;
    (void)argv;

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL_main entered");

    SDL_version linked{};
    SDL_GetVersion(&linked);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL linked version %d.%d.%d", linked.major, linked.minor, linked.patch);

    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_AUDIO | SDL_INIT_GAMECONTROLLER | SDL_INIT_JOYSTICK) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "SDL_Init failed: %s", SDL_GetError());
        return 1;
    }

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL_Init succeeded; video=%s audio=%s",
                        SDL_GetCurrentVideoDriver() ? SDL_GetCurrentVideoDriver() : "(none)",
                        SDL_GetCurrentAudioDriver() ? SDL_GetCurrentAudioDriver() : "(none)");

    SDL_Window* window = SDL_CreateWindow("Banjo SDL Probe",
                                          SDL_WINDOWPOS_UNDEFINED,
                                          SDL_WINDOWPOS_UNDEFINED,
                                          1280,
                                          720,
                                          SDL_WINDOW_SHOWN | SDL_WINDOW_RESIZABLE);
    if (!window) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "SDL_CreateWindow failed: %s", SDL_GetError());
        SDL_Quit();
        return 2;
    }

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL window created");
    set_dual_screen_probe_active(true);

    const Uint32 start = SDL_GetTicks();
    SDL_Event event;
    while (SDL_GetTicks() - start < 3000) {
        while (SDL_PollEvent(&event)) {
            if (event.type == SDL_QUIT) {
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL_QUIT received");
                set_dual_screen_probe_active(false);
                SDL_DestroyWindow(window);
                SDL_Quit();
                return 0;
            }
        }
        SDL_Delay(16);
    }

    set_dual_screen_probe_active(false);
    SDL_DestroyWindow(window);
    SDL_Quit();
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL lifecycle probe completed");
    return 0;
}
#endif
