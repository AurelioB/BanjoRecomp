$N64RECOMP_SOURCE_DIR = "lib/N64ModernRuntime/N64Recomp"
$N64RECOMP_BUILD_DIR = "$env:TEMP/banjo-n64recomp-build"
$CMAKE_BIN = "cmake"
$NINJA_BIN = "ninja"

function Have-RuntimeSources {
    $hasFuncs = (Get-ChildItem "RecompiledFuncs" -Filter "*.c" -ErrorAction SilentlyContinue).Count -gt 0 -or
                (Get-ChildItem "RecompiledFuncs" -Filter "*.cpp" -ErrorAction SilentlyContinue).Count -gt 0
    return $hasFuncs -and (Test-Path "rsp/n_aspMain.cpp") -and (Test-Path "RecompiledPatches/patches.c")
}

function Build-RecompTools {
    if ((Test-Path "N64Recomp.exe") -and (Test-Path "RSPRecomp.exe")) {
        Write-Host "N64Recomp.exe and RSPRecomp.exe are already present."
        return
    }

    if (-not (Test-Path $N64RECOMP_SOURCE_DIR)) {
        Write-Error "N64Recomp source directory is missing: $N64RECOMP_SOURCE_DIR"
        Write-Error "Run: git submodule update --init --recursive"
        exit 2
    }

    Write-Host "Building N64Recomp/RSPRecomp from $N64RECOMP_SOURCE_DIR..."
    & $CMAKE_BIN -S $N64RECOMP_SOURCE_DIR -B $N64RECOMP_BUILD_DIR -G Ninja `
        -DCMAKE_BUILD_TYPE=Release `
        -DCMAKE_MAKE_PROGRAM=$NINJA_BIN
    if ($LASTEXITCODE -ne 0) { Write-Error "CMake configure failed"; exit 1 }

    & $CMAKE_BIN --build $N64RECOMP_BUILD_DIR --config Release --target N64RecompCLI RSPRecomp -j ([Environment]::ProcessorCount)
    if ($LASTEXITCODE -ne 0) { Write-Error "CMake build failed"; exit 1 }

    Copy-Item "$N64RECOMP_BUILD_DIR/N64Recomp.exe" "./N64Recomp.exe"
    Copy-Item "$N64RECOMP_BUILD_DIR/RSPRecomp.exe" "./RSPRecomp.exe"
    Write-Host "N64Recomp.exe and RSPRecomp.exe copied to repository root."
}

# if (Have-RuntimeSources) {
#     Write-Host "Generated runtime sources are already present."
#     exit 0
# }

if (-not (Test-Path "banjo.us.v10.decompressed.z64")) {
    Write-Error "Decompressed ROM not found: banjo.us.v10.decompressed.z64"
    Write-Error "See BUILDING.md step 3 for how to obtain it."
    exit 2
}

Build-RecompTools

Write-Host "Running N64Recomp on banjo.us.rev0.toml..."
& "./N64Recomp.exe" banjo.us.rev0.toml
if ($LASTEXITCODE -ne 0) { Write-Error "N64Recomp banjo.us.rev0.toml failed"; exit 1 }

Write-Host "Running RSPRecomp on n_aspMain.us.rev0.toml..."
& "./RSPRecomp.exe" n_aspMain.us.rev0.toml
if ($LASTEXITCODE -ne 0) { Write-Error "RSPRecomp n_aspMain.us.rev0.toml failed"; exit 1 }

Write-Host "Building patches (requires clang and ld.lld in PATH)..."
$env:CC = "clang"
$env:LD = "ld.lld"
& make -C patches
if ($LASTEXITCODE -ne 0) { Write-Error "make -C patches failed"; exit 1 }

Write-Host "Running N64Recomp on patches.toml..."
& "./N64Recomp.exe" patches.toml
if ($LASTEXITCODE -ne 0) { Write-Error "N64Recomp patches.toml failed"; exit 1 }

Write-Host "Generated runtime sources are ready."
