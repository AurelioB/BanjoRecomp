#include <android/log.h>
#include <jni.h>
#include <SDL2/SDL.h>
#include <SDL2/SDL_main.h>

namespace {
constexpr const char* kLogTag = "BanjoSDLProbe";
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

    const Uint32 start = SDL_GetTicks();
    SDL_Event event;
    while (SDL_GetTicks() - start < 3000) {
        while (SDL_PollEvent(&event)) {
            if (event.type == SDL_QUIT) {
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL_QUIT received");
                SDL_DestroyWindow(window);
                SDL_Quit();
                return 0;
            }
        }
        SDL_Delay(16);
    }

    SDL_DestroyWindow(window);
    SDL_Quit();
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "SDL lifecycle probe completed");
    return 0;
}
