#include <jni.h>
#include <android/log.h>
#include <SDL2/SDL.h>

namespace {
constexpr const char* kLogTag = "BanjoAndroidShell";
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "native shell library loaded; SDL compiled version %d.%d.%d",
                        SDL_MAJOR_VERSION, SDL_MINOR_VERSION, SDL_PATCHLEVEL);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_banjorecomp_MainActivity_nativeProbe(JNIEnv*, jclass) {
    SDL_version linked{};
    SDL_GetVersion(&linked);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "native probe OK; SDL linked version %d.%d.%d",
                        linked.major, linked.minor, linked.patch);
    return 0;
}
