$SDL2_VERSION = $env:SDL2_VERSION ?? "2.32.10"
$FREETYPE_VERSION = $env:FREETYPE_VERSION ?? "2.13.3"
$ANDROID_ABI = $env:ANDROID_ABI ?? "arm64-v8a"
$ANDROID_PLATFORM = $env:ANDROID_PLATFORM ?? "24"

# Resolve Android SDK — honour ANDROID_HOME env var, then fall back to the default
# Windows install location that Android Studio uses.
$ANDROID_HOME = $env:ANDROID_HOME
if (-not $ANDROID_HOME) {
    $defaultSdk = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $defaultSdk) {
        $ANDROID_HOME = $defaultSdk
    } else {
        Write-Error "Cannot find Android SDK. Set the ANDROID_HOME environment variable to your SDK root."
        exit 2
    }
}
$ANDROID_HOME = $ANDROID_HOME.Replace('\', '/')

$ANDROID_NDK_HOME = ($env:ANDROID_NDK_HOME ?? "$ANDROID_HOME/ndk/28.2.13676358").Replace('\', '/')
if (-not (Test-Path $ANDROID_NDK_HOME)) {
    Write-Error "Android NDK not found at $ANDROID_NDK_HOME. Set ANDROID_NDK_HOME or install NDK 28.2.13676358 via the SDK Manager."
    exit 2
}

# Resolve cmake — prefer the Android SDK's bundled cmake, fall back to PATH.
$CMAKE_BIN = "$ANDROID_HOME/cmake/3.22.1/bin/cmake.exe"
if (-not (Test-Path $CMAKE_BIN)) {
    $found = Get-Command cmake -ErrorAction SilentlyContinue
    if (-not $found) {
        Write-Error "cmake not found. Install CMake (e.g. via Android Studio SDK Manager, 'winget install Kitware.CMake', or 'choco install cmake') and ensure it is on PATH."
        exit 2
    }
    $CMAKE_BIN = $found.Source
}

# Resolve ninja — use PATH.
$found = Get-Command ninja -ErrorAction SilentlyContinue
if (-not $found) {
    Write-Error "ninja not found. Install Ninja (e.g. 'winget install Ninja-build.Ninja' or 'choco install ninja') and ensure it is on PATH."
    exit 2
}
$NINJA_BIN = $found.Source

switch ($ANDROID_ABI) {
    "arm64-v8a"   { $PREFIX_ABI = "android-arm64" }
    "armeabi-v7a" { $PREFIX_ABI = "android-armv7" }
    "x86_64"      { $PREFIX_ABI = "android-x86_64" }
    "x86"         { $PREFIX_ABI = "android-x86" }
    default       { Write-Error "Unsupported Android ABI: $ANDROID_ABI"; exit 2 }
}

$PREFIX_ROOT = ($env:ANDROID_PREFIX_ROOT ?? "$env:USERPROFILE\Android\prefixes").Replace('\', '/')
$SDL2_PREFIX = ($env:BANJO_ANDROID_SDL2_PREFIX ?? "$PREFIX_ROOT/SDL2-$SDL2_VERSION-$PREFIX_ABI").Replace('\', '/')
$FREETYPE_PREFIX = ($env:BANJO_ANDROID_FREETYPE_PREFIX ?? "$PREFIX_ROOT/freetype-$FREETYPE_VERSION-$PREFIX_ABI").Replace('\', '/')
$WORK_DIR = "$env:TEMP\banjo-android-deps-win"

if (!(Test-Path $PREFIX_ROOT)) { New-Item -ItemType Directory -Path $PREFIX_ROOT | Out-Null }
if (!(Test-Path $WORK_DIR)) { New-Item -ItemType Directory -Path $WORK_DIR | Out-Null }
Push-Location $WORK_DIR

function Fetch-Url($url, $out) {
    if (!(Test-Path $out)) {
        Write-Host "Downloading $url..."
        curl.exe -fsSL --retry 3 --retry-delay 5 -o $out $url
        if ($LASTEXITCODE -ne 0) { Write-Error "Download failed: $url"; exit 1 }
    }
}

# Build SDL2
if (!(Test-Path "$SDL2_PREFIX/lib/libSDL2.so") -or !(Test-Path "$SDL2_PREFIX/lib/cmake/SDL2/SDL2Config.cmake")) {
    Write-Host "Building SDL2 $SDL2_VERSION for $ANDROID_ABI -> $SDL2_PREFIX"
    if (Test-Path "SDL2-$SDL2_VERSION") { Remove-Item -Recurse -Force "SDL2-$SDL2_VERSION" }
    if (Test-Path "build-sdl2-$ANDROID_ABI") { Remove-Item -Recurse -Force "build-sdl2-$ANDROID_ABI" }
    Fetch-Url "https://github.com/libsdl-org/SDL/releases/download/release-$SDL2_VERSION/SDL2-$SDL2_VERSION.zip" "SDL2-$SDL2_VERSION.zip"
    tar.exe -xf "SDL2-$SDL2_VERSION.zip"

    & $CMAKE_BIN `
        -S "SDL2-$SDL2_VERSION" `
        -B "build-sdl2-$ANDROID_ABI" `
        -G Ninja `
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" `
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" `
        -DANDROID_ABI="$ANDROID_ABI" `
        -DANDROID_PLATFORM="android-$ANDROID_PLATFORM" `
        -DCMAKE_BUILD_TYPE=Release `
        -DCMAKE_INSTALL_PREFIX="$SDL2_PREFIX" `
        -DSDL_SHARED=ON `
        -DSDL_STATIC=OFF `
        -DSDL_TEST=OFF
    if ($LASTEXITCODE -ne 0) { Write-Error "SDL2 CMake configure failed"; exit 1 }
    & $CMAKE_BIN --build "build-sdl2-$ANDROID_ABI" --parallel ([Environment]::ProcessorCount)
    if ($LASTEXITCODE -ne 0) { Write-Error "SDL2 build failed"; exit 1 }
    & $CMAKE_BIN --install "build-sdl2-$ANDROID_ABI"
    if ($LASTEXITCODE -ne 0) { Write-Error "SDL2 install failed"; exit 1 }
} else {
    Write-Host "Using cached SDL2 prefix: $SDL2_PREFIX"
}

# Build Freetype
if (!(Test-Path "$FREETYPE_PREFIX/lib/libfreetype.a") -or !(Test-Path "$FREETYPE_PREFIX/include/freetype2")) {
    Write-Host "Building Freetype $FREETYPE_VERSION for $ANDROID_ABI -> $FREETYPE_PREFIX"
    if (Test-Path "freetype-$FREETYPE_VERSION") { Remove-Item -Recurse -Force "freetype-$FREETYPE_VERSION" }
    if (Test-Path "build-freetype-$ANDROID_ABI") { Remove-Item -Recurse -Force "build-freetype-$ANDROID_ABI" }
    Fetch-Url "https://download.savannah.gnu.org/releases/freetype/freetype-$FREETYPE_VERSION.tar.gz" "freetype-$FREETYPE_VERSION.tar.gz"
    tar.exe -xzf "freetype-$FREETYPE_VERSION.tar.gz"

    & $CMAKE_BIN `
        -S "freetype-$FREETYPE_VERSION" `
        -B "build-freetype-$ANDROID_ABI" `
        -G Ninja `
        -DCMAKE_MAKE_PROGRAM="$NINJA_BIN" `
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" `
        -DANDROID_ABI="$ANDROID_ABI" `
        -DANDROID_PLATFORM="android-$ANDROID_PLATFORM" `
        -DCMAKE_BUILD_TYPE=Release `
        -DCMAKE_INSTALL_PREFIX="$FREETYPE_PREFIX" `
        -DBUILD_SHARED_LIBS=OFF `
        -DFT_DISABLE_ZLIB=TRUE `
        -DFT_DISABLE_BZIP2=TRUE `
        -DFT_DISABLE_PNG=TRUE `
        -DFT_DISABLE_HARFBUZZ=TRUE `
        -DFT_DISABLE_BROTLI=TRUE
    if ($LASTEXITCODE -ne 0) { Write-Error "Freetype CMake configure failed"; exit 1 }
    & $CMAKE_BIN --build "build-freetype-$ANDROID_ABI" --parallel ([Environment]::ProcessorCount)
    if ($LASTEXITCODE -ne 0) { Write-Error "Freetype build failed"; exit 1 }
    & $CMAKE_BIN --install "build-freetype-$ANDROID_ABI"
    if ($LASTEXITCODE -ne 0) { Write-Error "Freetype install failed"; exit 1 }
} else {
    Write-Host "Using cached Freetype prefix: $FREETYPE_PREFIX"
}

Pop-Location

if ($env:GITHUB_ENV) {
    "BANJO_ANDROID_SDL2_PREFIX=$SDL2_PREFIX" | Out-File -Append -Encoding utf8 $env:GITHUB_ENV
    "BANJO_ANDROID_FREETYPE_PREFIX=$FREETYPE_PREFIX" | Out-File -Append -Encoding utf8 $env:GITHUB_ENV
}

Write-Host "SDL2 prefix: $SDL2_PREFIX"
Write-Host "Freetype prefix: $FREETYPE_PREFIX"
