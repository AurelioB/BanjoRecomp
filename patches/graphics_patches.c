#include "patches.h"
#include "ultra_extensions.h"
#include "misc_funcs.h"
#include "note_saving.h"
#include "core1/core1.h"
#include "core1/vimgr.h"

extern struct{
    s32 unk0;
    s32 game_mode; //game_mode
    f32 unk8; 
    s32 unkC; //freeze_scene_flag (used for pause menu)
    f32 unk10;
    u8 transition;
    u8 map;
    u8 exit;
    u8 unk17; //reset_on_map_load
    u8 unk18;
    u8 unk19;
    u8 unk1A;
    u8 unk1B;
    u8 unk1C;
} D_8037E8E0;

void func_802E39D0(Gfx **gdl, Mtx **mptr, Vtx **vptr, s32 framebuffer_idx, s32 arg4);

// 16x the original sizes.
#define GFX_BUFFER_COUNT 37000
#define MTX_BUFFER_COUNT 7000
#define VTX_BUFFER_COUNT 4300

struct {
    Gfx gfx[2][GFX_BUFFER_COUNT];
    Mtx mtx[2][MTX_BUFFER_COUNT];
    Vtx vtx[2][VTX_BUFFER_COUNT];
} ExtendedBuffers;

extern Gfx *sGfxStack[2];
extern Mtx *sMtxStack[2];
extern Vtx *sVtxStack[2];
extern s32 sStackSelector;
extern s32 gTextureFilterPoint;
extern u32 heap_occupiedBytes;
extern u32 dynamic_camera_target_index;

extern void recomp_reset_skinning_stack();
extern void recomp_reset_map_model_skinning();
extern void recomp_advance_dynamic_camera_targets();
extern s32 item_getCount(enum item_e item);
extern s32 map_get(void);
extern s32 level_get(void);
extern enum level_e map_getLevel(enum map_e map);
extern s32 getGameMode(void);
extern s32 jiggyscore_total(void);
extern s32 itemscore_noteScores_getTotal(void);
extern s32 honeycombscore_get_total(void);
extern bool fileProgressFlag_get(enum file_progress_e index);
extern s32 gameSelect_getGameNumber(void);
extern s32 gctransition_active(void);
extern s32 gctransition_8030BDC0(void);

static s32 dual_screen_stats_frame_counter = 0;

enum android_dual_screen_display_mode {
    ANDROID_DUAL_SCREEN_LOGO = 0,
    ANDROID_DUAL_SCREEN_STATS = 1,
    ANDROID_DUAL_SCREEN_BLACK = 2,
};

typedef struct AndroidDualScreenStats {
    s32 display_mode;
    s32 active;
    s32 health;
    s32 max_health;
    s32 lives;
    s32 notes;
    s32 eggs;
    s32 red_feathers;
    s32 gold_feathers;
    s32 jiggies;
    s32 mumbo_tokens;
    s32 level;
    s32 jinjos_mask;
    s32 total_jiggies;
    s32 total_notes;
    s32 total_honeycombs;
    s32 reached_gruntys_lair;
    s32 selected_game_number;
    s32 game_transition_phase;
} AndroidDualScreenStats;

static AndroidDualScreenStats dual_screen_stats;
static s32 dual_screen_last_active = -1;
static s32 dual_screen_last_display_mode = -1;
static s32 dual_screen_last_health = -1;
static s32 dual_screen_last_max_health = -1;
static s32 dual_screen_last_lives = -1;
static s32 dual_screen_last_notes = -1;
static s32 dual_screen_last_eggs = -1;
static s32 dual_screen_last_red_feathers = -1;
static s32 dual_screen_last_gold_feathers = -1;
static s32 dual_screen_last_jiggies = -1;
static s32 dual_screen_last_mumbo_tokens = -1;
static s32 dual_screen_last_level = -1;
static s32 dual_screen_last_jinjos = -1;
static s32 dual_screen_last_total_jiggies = -1;
static s32 dual_screen_last_total_notes = -1;
static s32 dual_screen_last_total_honeycombs = -1;
static s32 dual_screen_last_reached_gruntys_lair = -1;
static s32 dual_screen_last_selected_game_number = -1;
static s32 dual_screen_last_game_transition_phase = -1;

static s32 dual_screen_is_intro_logo_map(s32 map) {
    return map == MAP_1E_CS_START_NINTENDO
        || map == MAP_1F_CS_START_RAREWARE
        || map == MAP_7B_CS_INTRO_GL_DINGPOT_1
        || map == MAP_7C_CS_INTRO_BANJOS_HOUSE_1
        || map == MAP_7D_CS_SPIRAL_MOUNTAIN_1
        || map == MAP_7E_CS_SPIRAL_MOUNTAIN_2
        || map == MAP_81_CS_INTRO_GL_DINGPOT_2
        || map == MAP_85_CS_SPIRAL_MOUNTAIN_3
        || map == MAP_86_CS_SPIRAL_MOUNTAIN_4
        || map == MAP_87_CS_SPIRAL_MOUNTAIN_5
        || map == MAP_88_CS_SPIRAL_MOUNTAIN_6
        || map == MAP_89_CS_INTRO_BANJOS_HOUSE_2
        || map == MAP_8A_CS_INTRO_BANJOS_HOUSE_3
        || map == MAP_94_CS_INTRO_SPIRAL_7;
}

static s32 dual_screen_display_mode_for_game_state(s32 game_mode, s32 map, s32 level) {
    if (map == MAP_91_FILE_SELECT) {
        return ANDROID_DUAL_SCREEN_STATS;
    }

    if (dual_screen_is_intro_logo_map(map)
        || game_mode == GAME_MODE_7_ATTRACT_DEMO
        || game_mode == GAME_MODE_9_BANJO_AND_KAZOOIE
        || game_mode == GAME_MODE_1_UNKNOWN
        || game_mode == GAME_MODE_2_UNKNOWN
        || game_mode == GAME_MODE_5_UNKNOWN) {
        return ANDROID_DUAL_SCREEN_LOGO;
    }

    if (level == LEVEL_D_CUTSCENE) {
        return ANDROID_DUAL_SCREEN_BLACK;
    }

    if (game_mode == GAME_MODE_3_NORMAL || game_mode == GAME_MODE_4_PAUSED || game_mode == GAME_MODE_6_FILE_PLAYBACK) {
        return ANDROID_DUAL_SCREEN_STATS;
    }

    return ANDROID_DUAL_SCREEN_LOGO;
}

static void update_dual_screen_stats(void) {
    // Only show the secondary-screen stats during the normal gameplay mode. The prior
    // D_8037E8E0 field read is not reliable here on Android; use the game's exported
    // game-mode helper instead.
    s32 game_mode = getGameMode();
    s32 map = map_get();
    if (map == 0) {
        map = D_8037E8E0.map;
    }
    s32 level = level_get();
    if (level == 0 && map != 0) {
        level = map_getLevel(map);
    }
    s32 display_mode = dual_screen_display_mode_for_game_state(game_mode, map, level);
    s32 active = (display_mode == ANDROID_DUAL_SCREEN_STATS);
    s32 total_jiggies = jiggyscore_total();
    s32 total_notes = itemscore_noteScores_getTotal();
    s32 total_honeycombs = honeycombscore_get_total();
    s32 reached_gruntys_lair = fileProgressFlag_get(FILEPROG_BD_ENTER_LAIR_CUTSCENE) ? 1 : 0;
    s32 selected_game_number = gameSelect_getGameNumber();
    s32 game_transition_phase = 0;
    if (gctransition_active()) {
        // gctransition_8030BDC0 is true for fade-out/loading phases. Otherwise an active
        // transition is the fade-in phase revealing the new screen.
        game_transition_phase = gctransition_8030BDC0() ? 2 : 1;
    }
    s32 health = item_getCount(ITEM_14_HEALTH);
    s32 max_health = item_getCount(ITEM_15_HEALTH_TOTAL);
    s32 lives = item_getCount(ITEM_16_LIFE);
    s32 notes = item_getCount(ITEM_C_NOTE);
    if (map == MAP_91_FILE_SELECT || level == LEVEL_6_LAIR) {
        notes = total_notes;
    }
    s32 eggs = item_getCount(ITEM_D_EGGS);
    s32 red_feathers = item_getCount(ITEM_F_RED_FEATHER);
    s32 gold_feathers = item_getCount(ITEM_10_GOLD_FEATHER);
    s32 jiggies = item_getCount(ITEM_26_JIGGY_TOTAL);
    if (map == MAP_91_FILE_SELECT || level == LEVEL_6_LAIR) {
        jiggies = total_jiggies;
    }
    s32 mumbo_tokens = item_getCount(ITEM_25_MUMBO_TOKEN_TOTAL);
    // For the secondary UI, pass the current map id. Java groups maps/interiors into
    // display names; level_get() can be 0 during normal gameplay in some areas.
    level = map;
    s32 jinjos = item_getCount(ITEM_12_JINJOS);

    if (++dual_screen_stats_frame_counter < 15 &&
            active == dual_screen_last_active &&
            display_mode == dual_screen_last_display_mode &&
            health == dual_screen_last_health &&
            max_health == dual_screen_last_max_health &&
            lives == dual_screen_last_lives &&
            notes == dual_screen_last_notes &&
            eggs == dual_screen_last_eggs &&
            red_feathers == dual_screen_last_red_feathers &&
            gold_feathers == dual_screen_last_gold_feathers &&
            jiggies == dual_screen_last_jiggies &&
            mumbo_tokens == dual_screen_last_mumbo_tokens &&
            level == dual_screen_last_level &&
            jinjos == dual_screen_last_jinjos &&
            total_jiggies == dual_screen_last_total_jiggies &&
            total_notes == dual_screen_last_total_notes &&
            total_honeycombs == dual_screen_last_total_honeycombs &&
            reached_gruntys_lair == dual_screen_last_reached_gruntys_lair &&
            selected_game_number == dual_screen_last_selected_game_number &&
            game_transition_phase == dual_screen_last_game_transition_phase) {
        return;
    }

    dual_screen_stats_frame_counter = 0;
    dual_screen_last_active = active;
    dual_screen_last_display_mode = display_mode;
    dual_screen_last_health = health;
    dual_screen_last_max_health = max_health;
    dual_screen_last_lives = lives;
    dual_screen_last_notes = notes;
    dual_screen_last_eggs = eggs;
    dual_screen_last_red_feathers = red_feathers;
    dual_screen_last_gold_feathers = gold_feathers;
    dual_screen_last_jiggies = jiggies;
    dual_screen_last_mumbo_tokens = mumbo_tokens;
    dual_screen_last_level = level;
    dual_screen_last_jinjos = jinjos;
    dual_screen_last_total_jiggies = total_jiggies;
    dual_screen_last_total_notes = total_notes;
    dual_screen_last_total_honeycombs = total_honeycombs;
    dual_screen_last_reached_gruntys_lair = reached_gruntys_lair;
    dual_screen_last_selected_game_number = selected_game_number;
    dual_screen_last_game_transition_phase = game_transition_phase;

    // ITEM_12_JINJOS is a bitfield for collected Jinjos, not a simple count. Mask it down
    // to the five displayed Jinjo bits; treating the raw bitfield as a count made some
    // two-Jinjo combinations display as all five collected.
    s32 jinjos_mask = jinjos & 0x1F;
    dual_screen_stats.display_mode = display_mode;
    dual_screen_stats.active = active;
    dual_screen_stats.health = health;
    dual_screen_stats.max_health = max_health;
    dual_screen_stats.lives = lives;
    dual_screen_stats.notes = notes;
    dual_screen_stats.eggs = eggs;
    dual_screen_stats.red_feathers = red_feathers;
    dual_screen_stats.gold_feathers = gold_feathers;
    dual_screen_stats.jiggies = jiggies;
    dual_screen_stats.mumbo_tokens = mumbo_tokens;
    dual_screen_stats.level = level;
    dual_screen_stats.jinjos_mask = jinjos_mask;
    dual_screen_stats.total_jiggies = total_jiggies;
    dual_screen_stats.total_notes = total_notes;
    dual_screen_stats.total_honeycombs = total_honeycombs;
    dual_screen_stats.reached_gruntys_lair = reached_gruntys_lair;
    dual_screen_stats.selected_game_number = selected_game_number;
    dual_screen_stats.game_transition_phase = game_transition_phase;
    recomp_android_update_dual_screen_stats(&dual_screen_stats);
}

// @recomp Patched to not free anything.
RECOMP_PATCH void graphicsCache_release(void) {
    if (sGfxStack[0]) {
        // @recomp Nothing to free, as the extended buffers are statically allocated.
        sGfxStack[0] = NULL;
    }
}

// @recomp Patched to use the extended buffers for the graphics stacks.
RECOMP_PATCH void graphicsCache_init(void){
    if(sGfxStack[0] == NULL){
        // @recomp Point the stacks to the extended buffers. 
        sGfxStack[0] = ExtendedBuffers.gfx[0];
        sGfxStack[1] = ExtendedBuffers.gfx[1];
        sMtxStack[0] = ExtendedBuffers.mtx[0];
        sMtxStack[1] = ExtendedBuffers.mtx[1];
        sVtxStack[0] = ExtendedBuffers.vtx[0];
        sVtxStack[1] = ExtendedBuffers.vtx[1];
        dummy_func_80254464();
    }
    sStackSelector = 0;
    gTextureFilterPoint = 0;
}

void handle_cutscene_timings(void);
// @recomp Patched to check for graphics stack overflow after processing a frame.
// Also patched to wait for a message when the displaylist is completed immediately after queueing it to solve vertex modification race conditions.
RECOMP_PATCH void game_draw(s32 arg0){
    Gfx *gfx;
    Gfx *gfx_start;
    Gfx *sp2C;
    Mtx *mtx;
    Vtx *vtx;

    if(arg0) {
        scissorBox_setDefault();
    }

    getGraphicsStacks(&gfx, &mtx, &vtx);

    if(D_8037E8E0.unkC == 1){
        getGraphicsStacks(&gfx, &mtx, &vtx);
    }

    // @recomp Reset the high precision position skinning stacks.
    recomp_reset_skinning_stack();
    recomp_reset_map_model_skinning();

    // @recomp Advance the frame used as reference by the dynamic camera target changes for analog camera.
    recomp_advance_dynamic_camera_targets();

    // @recomp Update note saving state.
    note_saving_update();

    // @recomp Publish Android secondary-screen stats when gameplay is active.
    update_dual_screen_stats();

    // @recomp Track the original values.
    Mtx* mtx_start = mtx;
    Vtx* vtx_start = vtx;

    gfx_start = gfx;

    // @recomp Enable the extended gbi.
    gEXEnable(gfx++);
    gEXSetRDRAMExtended(gfx++, TRUE);
    gEXSetRefreshRate(gfx++, 60 / viMgr_func_8024BFA0()); // Input framerate is equal to 60 Hz divided by the frame divisor

    // @recomp Turn off nearclipping (i.e. turn on depth clamp) to prevent the camera from clipping through lots of geometry in ultrawide aspect ratios.
    gEXSetNearClipping(gfx++, FALSE);

    // @recomp Set the global wrapping point for texture coordinate interpolation to 256.
    gEXSetTexcoordWrapPoint(gfx++, 256 * 4, 256 * 4);

    func_802E39D0(&gfx, &mtx, &vtx, getActiveFramebuffer(), arg0);

    // @recomp Check for graphics stack overflow
    u32 gfx_count = gfx - gfx_start;
    u32 mtx_count = mtx - mtx_start;
    u32 vtx_count = vtx - vtx_start;

    // recomp_printf("gfx: %-5u mtx: %-5u vtx: %-5u (%06X / %06X)\n", gfx_count, mtx_count, vtx_count, heap_occupiedBytes, 0x210520);

    if (gfx_count >= GFX_BUFFER_COUNT) {
        recomp_error("Gfx buffer overflow\n");
    }

    if (mtx_count >= MTX_BUFFER_COUNT) {
        recomp_error("Mtx buffer overflow\n");
    }

    if (vtx_count >= VTX_BUFFER_COUNT) {
        recomp_error("Vtx buffer overflow\n");
    }

    if(D_8037E8E0.unkC == 0){
        sp2C = gfx;
        viMgr_func_8024C1DC();

        // @recomp Set up the message queue and event for waiting on DL completion.
        OSMesgQueue dl_complete_queue;
        OSMesg dl_complete_queue_buf;
        osCreateMesgQueue(&dl_complete_queue, &dl_complete_queue_buf, 1);
        osExQueueDisplaylistEvent(&dl_complete_queue, NULL, gfx_start, OS_EX_DISPLAYLIST_EVENT_PARSED);

        func_80253EC4(gfx_start, sp2C);
        
        // @recomp Wait for the displaylist to be completed after submitting it. This removes the race condition
        // that exists with vertex modifications for texture scrolling.
        osRecvMesg(&dl_complete_queue, NULL, OS_MESG_BLOCK);

        if(arg0) {
            scissorBox_setDefault();
        }
    }

    // @recomp Call the relevant function to fix cutscene timings, if there is one.
    handle_cutscene_timings();

    // Allow interpolation for the next frame.
    set_all_interpolation_skipped(FALSE);
}
