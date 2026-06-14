package io.github.banjorecomp;

final class DualScreenDebugAreas {
    static final class Area {
        final int mapId;
        final int assetId;
        final int textureIndex;
        final String name;
        Area(int mapId, int assetId, int textureIndex, String name) {
            this.mapId = mapId;
            this.assetId = assetId;
            this.textureIndex = textureIndex;
            this.name = name;
        }
    }

    static final Area[] AREAS = {
            new Area(0x01, 0x14CF, 71, "1_SM_SPIRAL_MOUNTAIN"),
            new Area(0x02, 0x14AA, 0, "2_MM_MUMBOS_MOUNTAIN"),
            new Area(0x05, 0x146F, 0, "5_TTC_BLUBBERS_SHIP"),
            new Area(0x06, 0x146D, 0, "6_TTC_NIPPERS_SHELL"),
            new Area(0x07, 0x146B, 0, "7_TTC_TREASURE_TROVE_COVE"),
            new Area(0x8F, 0x1473, 0, "8F_TTC_SHARKFOOD_ISLAND"),
            new Area(0x0A, 0x1471, 0, "A_TTC_SANDCASTLE"),
            new Area(0x0B, 0x14ED, 0, "B_CC_CLANKERS_CAVERN"),
            new Area(0x0C, 0x14AC, 0, "C_MM_TICKERS_TOWER"),
            new Area(0x0D, 0x14D1, 0, "D_BGS_BUBBLEGLOOP_SWAMP"),
            new Area(0x0E, 0x14AE, 0, "E_MM_MUMBOS_SKULL"),
            new Area(0x10, 0x14D3, 0, "10_BGS_MR_VILE"),
            new Area(0x11, 0x14D4, 0, "11_BGS_TIPTUP"),
            new Area(0x12, 0x1474, 0, "12_GV_GOBIS_VALLEY"),
            new Area(0x92, 0x147E, 0, "92_GV_SNS_CHAMBER"),
            new Area(0x13, 0x1476, 0, "13_GV_MEMORY_GAME"),
            new Area(0x14, 0x1478, 0, "14_GV_SANDYBUTTS_MAZE"),
            new Area(0x15, 0x147A, 0, "15_GV_WATER_PYRAMID"),
            new Area(0x16, 0x147C, 0, "16_GV_RUBEES_CHAMBER"),
            new Area(0x1A, 0x147D, 0, "1A_GV_INSIDE_JINXY"),
            new Area(0x1B, 0x147F, 0, "1B_MMM_MAD_MONSTER_MANSION"),
            new Area(0x1C, 0x1486, 0, "1C_MMM_CHURCH"),
            new Area(0x1D, 0x1482, 0, "1D_MMM_CELLAR"),
            new Area(0x1E, 0x149D, 0, "1E_CS_START_NINTENDO"),
            new Area(0x1F, 0x14A0, 0, "1F_CS_START_RAREWARE"),
            new Area(0x20, 0x14A9, 0, "20_CS_END_NOT_100"),
            new Area(0x21, 0x14EF, 0, "21_CC_WITCH_SWITCH_ROOM"),
            new Area(0x22, 0x14F0, 0, "22_CC_INSIDE_CLANKER"),
            new Area(0x23, 0x14F2, 0, "23_CC_GOLDFEATHER_ROOM"),
            new Area(0x24, 0x1488, 0, "24_MMM_TUMBLARS_SHED"),
            new Area(0x25, 0x1495, 0, "25_MMM_WELL"),
            new Area(0x26, 0x1485, 0, "26_MMM_NAPPERS_ROOM"),
            new Area(0x27, 0x14C8, 0, "27_FP_FREEZEEZY_PEAK"),
            new Area(0x28, 0x1489, 0, "28_MMM_EGG_ROOM"),
            new Area(0x29, 0x148B, 0, "29_MMM_NOTE_ROOM"),
            new Area(0x2A, 0x148D, 0, "2A_MMM_FEATHER_ROOM"),
            new Area(0x2B, 0x1483, 0, "2B_MMM_SECRET_CHURCH_ROOM"),
            new Area(0x2C, 0x148F, 0, "2C_MMM_BATHROOM"),
            new Area(0x2D, 0x1491, 0, "2D_MMM_BEDROOM"),
            new Area(0x2E, 0x1493, 0, "2E_MMM_HONEYCOMB_ROOM"),
            new Area(0x2F, 0x1481, 0, "2F_MMM_WATERDRAIN_BARREL"),
            new Area(0x30, 0x14AE, 0, "30_MMM_MUMBOS_SKULL"),
            new Area(0x8D, 0x1498, 0, "8D_MMM_INSIDE_LOGGO"),
            new Area(0x31, 0x14B0, 0, "31_RBB_RUSTY_BUCKET_BAY"),
            new Area(0x8B, 0x14C5, 0, "8B_RBB_ANCHOR_ROOM"),
            new Area(0x34, 0x14B2, 0, "34_RBB_ENGINE_ROOM"),
            new Area(0x35, 0x14B4, 0, "35_RBB_WAREHOUSE"),
            new Area(0x36, 0x14B6, 0, "36_RBB_BOATHOUSE"),
            new Area(0x37, 0x14B8, 0, "37_RBB_CONTAINER_1"),
            new Area(0x38, 0x14BA, 0, "38_RBB_CONTAINER_3"),
            new Area(0x39, 0x14BD, 0, "39_RBB_CREW_CABIN"),
            new Area(0x3A, 0x14BE, 0, "3A_RBB_BOSS_BOOM_BOX"),
            new Area(0x3B, 0x14C1, 0, "3B_RBB_STORAGE_ROOM"),
            new Area(0x3C, 0x14C3, 0, "3C_RBB_KITCHEN"),
            new Area(0x3D, 0x14C0, 0, "3D_RBB_NAVIGATION_ROOM"),
            new Area(0x3E, 0x14B9, 0, "3E_RBB_CONTAINER_2"),
            new Area(0x3F, 0x14BB, 0, "3F_RBB_CAPTAINS_CABIN"),
            new Area(0x40, 0x14D8, 0, "40_CCW_HUB"),
            new Area(0x41, 0x14CA, 0, "41_FP_BOGGYS_IGLOO"),
            new Area(0x7F, 0x14CC, 0, "7F_FP_WOZZAS_CAVE"),
            new Area(0x43, 0x14D9, 0, "43_CCW_SPRING"),
            new Area(0x44, 0x14DA, 0, "44_CCW_SUMMER"),
            new Area(0x45, 0x14DB, 0, "45_CCW_AUTUMN"),
            new Area(0x46, 0x14DC, 0, "46_CCW_WINTER"),
            new Area(0x47, 0x14AE, 0, "47_BGS_MUMBOS_SKULL"),
            new Area(0x48, 0x14AE, 0, "48_FP_MUMBOS_SKULL"),
            new Area(0x4A, 0x14AE, 0, "4A_CCW_SPRING_MUMBOS_SKULL"),
            new Area(0x4B, 0x14AE, 0, "4B_CCW_SUMMER_MUMBOS_SKULL"),
            new Area(0x4C, 0x14AE, 0, "4C_CCW_AUTUMN_MUMBOS_SKULL"),
            new Area(0x4D, 0x14AE, 0, "4D_CCW_WINTER_MUMBOS_SKULL"),
            new Area(0x53, 0x14CB, 0, "53_FP_CHRISTMAS_TREE"),
            new Area(0x5A, 0x14DD, 0, "5A_CCW_SUMMER_ZUBBA_HIVE"),
            new Area(0x5B, 0x14DD, 0, "5B_CCW_SPRING_ZUBBA_HIVE"),
            new Area(0x5C, 0x14DD, 0, "5C_CCW_AUTUMN_ZUBBA_HIVE"),
            new Area(0x5E, 0x14DE, 0, "5E_CCW_SPRING_NABNUTS_HOUSE"),
            new Area(0x5F, 0x14DE, 0, "5F_CCW_SUMMER_NABNUTS_HOUSE"),
            new Area(0x60, 0x14DE, 0, "60_CCW_AUTUMN_NABNUTS_HOUSE"),
            new Area(0x61, 0x14DE, 0, "61_CCW_WINTER_NABNUTS_HOUSE"),
            new Area(0x62, 0x14E0, 0, "62_CCW_WINTER_HONEYCOMB_ROOM"),
            new Area(0x63, 0x14E1, 0, "63_CCW_AUTUMN_NABNUTS_WATER_SUPPLY"),
            new Area(0x64, 0x14E1, 0, "64_CCW_WINTER_NABNUTS_WATER_SUPPLY"),
            new Area(0x65, 0x14DF, 0, "65_CCW_SPRING_WHIPCRACK_ROOM"),
            new Area(0x66, 0x14DF, 0, "66_CCW_SUMMER_WHIPCRACK_ROOM"),
            new Area(0x67, 0x14DF, 0, "67_CCW_AUTUMN_WHIPCRACK_ROOM"),
            new Area(0x68, 0x14DF, 0, "68_CCW_WINTER_WHIPCRACK_ROOM"),
            new Area(0x69, 0x14F3, 0, "69_GL_MM_LOBBY"),
            new Area(0x6A, 0x14F4, 0, "6A_GL_TTC_AND_CC_PUZZLE"),
            new Area(0x6B, 0x14F5, 0, "6B_GL_180_NOTE_DOOR"),
            new Area(0x6C, 0x14F6, 0, "6C_GL_RED_CAULDRON_ROOM"),
            new Area(0x6D, 0x14F7, 0, "6D_GL_TTC_LOBBY"),
            new Area(0x6E, 0x14F8, 0, "6E_GL_GV_LOBBY"),
            new Area(0x6F, 0x14F9, 0, "6F_GL_FP_LOBBY"),
            new Area(0x74, 0x14FD, 0, "74_GL_GV_PUZZLE"),
            new Area(0x70, 0x14FB, 0, "70_GL_CC_LOBBY"),
            new Area(0x75, 0x14FE, 0, "75_GL_MMM_LOBBY"),
            new Area(0x7A, 0x14FF, 0, "7A_GL_CRYPT"),
            new Area(0x71, 0x1500, 0, "71_GL_STATUE_ROOM"),
            new Area(0x72, 0x1501, 0, "72_GL_BGS_LOBBY"),
            new Area(0x76, 0x1502, 0, "76_GL_640_NOTE_DOOR"),
            new Area(0x77, 0x1503, 0, "77_GL_RBB_LOBBY"),
            new Area(0x78, 0x1504, 0, "78_GL_RBB_AND_MMM_PUZZLE"),
            new Area(0x79, 0x1505, 0, "79_GL_CCW_LOBBY"),
            new Area(0x80, 0x1506, 0, "80_GL_FF_ENTRANCE"),
            new Area(0x93, 0x150F, 0, "93_GL_DINGPOT"),
            new Area(0x90, 0x14FC, 0, "90_GL_BATTLEMENTS"),
            new Area(0x7B, 0x150F, 0, "7B_CS_INTRO_GL_DINGPOT_1"),
            new Area(0x7C, 0x14A2, 0, "7C_CS_INTRO_BANJOS_HOUSE_1"),
            new Area(0x7D, 0x14CF, 0, "7D_CS_SPIRAL_MOUNTAIN_1"),
            new Area(0x7E, 0x14CF, 0, "7E_CS_SPIRAL_MOUNTAIN_2"),
            new Area(0x81, 0x150F, 0, "81_CS_INTRO_GL_DINGPOT_2"),
            new Area(0x82, 0x150F, 0, "82_CS_ENTERING_GL_MACHINE_ROOM"),
            new Area(0x83, 0x150F, 0, "83_CS_GAME_OVER_MACHINE_ROOM"),
            new Area(0x84, 0x150F, 0, "84_CS_UNUSED_MACHINE_ROOM"),
            new Area(0x85, 0x14CF, 0, "85_CS_SPIRAL_MOUNTAIN_3"),
            new Area(0x86, 0x14CF, 0, "86_CS_SPIRAL_MOUNTAIN_4"),
            new Area(0x87, 0x14A6, 0, "87_CS_SPIRAL_MOUNTAIN_5"),
            new Area(0x88, 0x14CF, 0, "88_CS_SPIRAL_MOUNTAIN_6"),
            new Area(0x94, 0x14CF, 0, "94_CS_INTRO_SPIRAL_7"),
            new Area(0x98, 0x149F, 0, "98_CS_END_SPIRAL_MOUNTAIN_1"),
            new Area(0x99, 0x149F, 0, "99_CS_END_SPIRAL_MOUNTAIN_2"),
            new Area(0x95, 0x14A9, 0, "95_CS_END_ALL_100"),
            new Area(0x89, 0x14A2, 0, "89_CS_INTRO_BANJOS_HOUSE_2"),
            new Area(0x8A, 0x14A2, 0, "8A_CS_INTRO_BANJOS_HOUSE_3"),
            new Area(0x96, 0x14A9, 0, "96_CS_END_BEACH_1"),
            new Area(0x97, 0x14A9, 0, "97_CS_END_BEACH_2"),
            new Area(0x91, 0x14A2, 0, "91_FILE_SELECT"),
            new Area(0x8C, 0x14A2, 0, "8C_SM_BANJOS_HOUSE"),
            new Area(0x8E, 0x14E8, 0, "8E_GL_FURNACE_FUN"),
    };

    private DualScreenDebugAreas() {}

    static Area areaForMapId(int mapId) {
        for (Area area : AREAS) {
            if (area.mapId == mapId) return area;
        }
        return null;
    }

    static int indexForMapId(int mapId) {
        for (int i = 0; i < AREAS.length; i++) {
            if (AREAS[i].mapId == mapId) return i;
        }
        return -1;
    }

    static String backdropKey(int mapId) {
        return "level_portrait_map_" + Integer.toHexString(mapId & 0xFF);
    }
}
