#!/usr/bin/env python3
"""Static regression checks for the Android port.

These checks intentionally avoid requiring an Android NDK. They verify that the
Android build stays on SDLActivity/SDL Vulkan paths, keeps desktop-only APIs out
of Android code, and that dev/probe-only paths remain explicitly gated.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text()


def require(condition: bool, message: str) -> None:
    if not condition:
        print(f"FAIL: {message}", file=sys.stderr)
        raise SystemExit(1)


def require_regex(text: str, pattern: str, message: str) -> None:
    require(re.search(pattern, text, flags=re.S) is not None, message)


def main() -> int:
    main_cpp = read("src/main/main.cpp")
    root_cmake = read("CMakeLists.txt")
    rt64_cmake = read("lib/rt64/CMakeLists.txt")
    rt64_window = read("lib/rt64/src/hle/rt64_application_window.cpp")
    null_renderer_header = read("src/android/null_renderer_context.hpp")
    null_renderer_cpp = read("src/android/null_renderer_context.cpp")
    nfd_cmake = read("lib/rt64/src/contrib/nativefiledialog-extended/CMakeLists.txt")
    nfd_src_cmake = read("lib/rt64/src/contrib/nativefiledialog-extended/src/CMakeLists.txt")
    nfd_null = read("lib/rt64/src/contrib/nativefiledialog-extended/src/nfd_null.cpp")
    android_shell = read("src/android/android_shell.cpp")
    sdl_probe = read("src/android/sdl_lifecycle_probe.cpp")

    require('#include "nfd.h"' in main_cpp, "main.cpp should still include NFD for desktop builds")
    require_regex(
        main_cpp,
        r"#if !defined\(__ANDROID__\)\s*\n#include \"nfd\.h\"\s*\n#endif",
        "NFD include must be compiled out on Android",
    )
    require_regex(
        main_cpp,
        r"#if !defined\(__ANDROID__\)\s*\n#include \"SDL2/SDL_syswm\.h\"",
        "SDL_syswm include must be compiled out on Android",
    )
    require_regex(
        main_cpp,
        r"#if !defined\(__ANDROID__\)\s*\n\s*SDL_SysWMinfo wmInfo;",
        "SDL_GetWindowWMInfo block must be compiled out on Android",
    )
    require_regex(
        main_cpp,
        r"#if !defined\(__ANDROID__\)\s*\n\s*NFD_Init\(\);",
        "NFD_Init must be compiled out on Android",
    )
    require_regex(
        main_cpp,
        r"#if !defined\(__ANDROID__\)\s*\n\s*NFD_Quit\(\);",
        "NFD_Quit must be compiled out on Android",
    )

    require("option(BANJO_ANDROID_RENDERER_STUB" in root_cmake, "root CMake must expose BANJO_ANDROID_RENDERER_STUB")
    require("option(BANJO_ANDROID_SHELL_ONLY" in root_cmake, "root CMake must expose BANJO_ANDROID_SHELL_ONLY")
    require("option(BANJO_ANDROID_SDL_LIFECYCLE_PROBE" in root_cmake, "root CMake must expose BANJO_ANDROID_SDL_LIFECYCLE_PROBE")
    require("option(BANJO_ANDROID_DEV_FULL_APK" in root_cmake, "root CMake must expose BANJO_ANDROID_DEV_FULL_APK")
    require("option(BANJO_ANDROID_VULKAN_SMOKE_PROBE" in root_cmake, "root CMake must expose BANJO_ANDROID_VULKAN_SMOKE_PROBE")
    require("add_library(main SHARED" in root_cmake, "Android SDL lifecycle/dev builds must produce libmain.so")
    require("src/android/sdl_lifecycle_probe.cpp" in root_cmake, "Android SDL lifecycle probe target must compile sdl_lifecycle_probe.cpp")
    require("add_library(BanjoAndroidShell SHARED" in root_cmake, "legacy Android shell target must remain explicitly gated")
    require("src/android/android_shell.cpp" in root_cmake, "legacy Android shell target must compile android_shell.cpp")
    require("BANJO_ANDROID_RENDERER_STUB=1" in root_cmake, "root CMake must define BANJO_ANDROID_RENDERER_STUB for targets")
    require("target_compile_definitions(main PRIVATE BANJO_ANDROID_DEV_FULL_APK=1)" in root_cmake, "dev-full APK target must expose BANJO_ANDROID_DEV_FULL_APK to C++")
    require("if (CMAKE_SYSTEM_NAME MATCHES \"Android\")" in root_cmake, "root CMake must have an Android dependency branch")
    require("target_link_libraries(main PRIVATE SDL2::SDL2 android log)" in root_cmake, "SDL lifecycle probe target must link SDL2 plus android/log")
    require("target_link_libraries(BanjoAndroidShell PRIVATE SDL2::SDL2 android log)" in root_cmake, "legacy Android shell target must link SDL2 plus android/log")
    require_regex(
        root_cmake,
        r"target_link_libraries\(\$\{BANJO_RECOMP_TARGET\} PRIVATE SDL2::SDL2 android log vulkan\)",
        "Android full build branch must link SDL2 plus android/log/vulkan through BANJO_RECOMP_TARGET",
    )
    require("JNI_OnLoad" in android_shell and "nativeProbe" in android_shell, "legacy Android shell must expose load/probe entry points")
    require("Java_io_github_banjorecomp_BanjoSDLActivity_nativeSetAndroidSurfaceReady" in sdl_probe, "SDL lifecycle probe must stub nativeSetAndroidSurfaceReady")
    require("Java_io_github_banjorecomp_BanjoSDLActivity_nativeSetAppAudioActive" in sdl_probe, "SDL lifecycle probe must stub nativeSetAppAudioActive")

    require("CMAKE_SYSTEM_NAME MATCHES \"Linux|Android\"" in rt64_cmake, "RT64 must enable SDL Vulkan path for Android as well as Linux")
    require("add_compile_definitions(\"PLUME_SDL_VULKAN_ENABLED\")" in rt64_cmake, "RT64 must define PLUME_SDL_VULKAN_ENABLED")
    require("add_compile_definitions(\"RT64_SDL_WINDOW_VULKAN\")" in rt64_cmake, "RT64 must define RT64_SDL_WINDOW_VULKAN")

    require("class NullRendererContext final" in null_renderer_cpp, "Android target must define a stub/null renderer context")
    require("create_null_renderer_context" in null_renderer_header, "Android null renderer must expose a factory")
    require("banjo::android::create_null_renderer_context" in main_cpp, "main renderer callback must use the Android null renderer in stub mode")

    require("#   elif defined(__ANDROID__)\n        static_assert(false && \"Android unimplemented\");" not in rt64_window, "RT64 Android unconditional static_asserts must be removed")
    require_regex(
        rt64_window,
        r"#\s*elif defined\(__ANDROID__\) && !defined\(RT64_SDL_WINDOW_VULKAN\)\s*\n\s*static_assert",
        "RT64 should only static_assert Android when SDL Vulkan path is not enabled",
    )
    require_regex(
        rt64_window,
        r"#\s*if !defined\(__ANDROID__\)\s*\n\s*SDL_SysWMinfo wmInfo;",
        "RT64 SDL_syswm native handle extraction must be skipped on Android",
    )

    require("elseif(ANDROID)" in nfd_cmake, "NFD must not treat Android as desktop Linux")
    require("PLATFORM_ANDROID" in nfd_cmake, "NFD CMake must define an Android platform")
    require("nfd_null.cpp" in nfd_src_cmake, "NFD Android build must use the null backend")
    require("NFD_OpenDialogU8" in nfd_null and "NFD_OpenDialogN" in nfd_null, "NFD null backend must define both native and UTF-8 APIs")

    print("Android port guard checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
