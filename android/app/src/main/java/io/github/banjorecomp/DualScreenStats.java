package io.github.banjorecomp;

public class DualScreenStats {
    public static final int DISPLAY_LOGO = 0;
    public static final int DISPLAY_STATS = 1;
    public static final int DISPLAY_BLACK = 2;
    public static final int GAME_TRANSITION_NONE = 0;
    public static final int GAME_TRANSITION_IN = 1;
    public static final int GAME_TRANSITION_OUT = 2;

    public final int displayMode;
    public final int health;
    public final int maxHealth;
    public final int lives;
    public final int notes;
    public final int eggs;
    public final int redFeathers;
    public final int goldFeathers;
    public final int jiggies;
    public final int mumboTokens;
    public final int levelId;
    public final int jinjosMask;
    public final int totalJiggies;
    public final int totalNotes;
    public final int totalHoneycombs;
    public final boolean reachedGruntysLair;
    public final int selectedGameNumber;
    public final int gameTransitionPhase;

    public DualScreenStats(
            int displayMode,
            int health,
            int maxHealth,
            int lives,
            int notes,
            int eggs,
            int redFeathers,
            int goldFeathers,
            int jiggies,
            int mumboTokens,
            int levelId,
            int jinjosMask,
            int totalJiggies,
            int totalNotes,
            int totalHoneycombs,
            boolean reachedGruntysLair,
            int selectedGameNumber,
            int gameTransitionPhase) {
        this.displayMode = displayMode;
        this.health = health;
        this.maxHealth = maxHealth;
        this.lives = lives;
        this.notes = notes;
        this.eggs = eggs;
        this.redFeathers = redFeathers;
        this.goldFeathers = goldFeathers;
        this.jiggies = jiggies;
        this.mumboTokens = mumboTokens;
        this.levelId = levelId;
        this.jinjosMask = jinjosMask;
        this.totalJiggies = totalJiggies;
        this.totalNotes = totalNotes;
        this.totalHoneycombs = totalHoneycombs;
        this.reachedGruntysLair = reachedGruntysLair;
        this.selectedGameNumber = selectedGameNumber;
        this.gameTransitionPhase = gameTransitionPhase;
    }

    public static DualScreenStats probe() {
        return new DualScreenStats(DISPLAY_LOGO, 6, 8, 3, 42, 5, 0, 0, 7, 12, 0, 0b10101, 7, 42, 4, true, 0, GAME_TRANSITION_NONE);
    }

    public boolean sameValues(DualScreenStats other) {
        return other != null
                && displayMode == other.displayMode
                && health == other.health
                && maxHealth == other.maxHealth
                && lives == other.lives
                && notes == other.notes
                && eggs == other.eggs
                && redFeathers == other.redFeathers
                && goldFeathers == other.goldFeathers
                && jiggies == other.jiggies
                && mumboTokens == other.mumboTokens
                && levelId == other.levelId
                && jinjosMask == other.jinjosMask
                && totalJiggies == other.totalJiggies
                && totalNotes == other.totalNotes
                && totalHoneycombs == other.totalHoneycombs
                && reachedGruntysLair == other.reachedGruntysLair
                && selectedGameNumber == other.selectedGameNumber
                && gameTransitionPhase == other.gameTransitionPhase;
    }
}
