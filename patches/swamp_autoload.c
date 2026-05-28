#include "patches.h"
#include "functions.h"

// Diagnostic-only: boot directly into Bubblegloop Swamp after the Android dev
// APK auto-starts the game, so the BGS muddy-water performance path can be
// tested without menu/gameplay setup.
RECOMP_PATCH enum map_e getDefaultBootMap(void) {
    recomp_printf("[bgs-autoload] boot map forced to Bubblegloop Swamp\n");
    return MAP_D_BGS_BUBBLEGLOOP_SWAMP;
}
