package io.github.banjorecomp;

public class DualScreenStats {
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

    public DualScreenStats(
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
            int jinjosMask) {
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
    }

    public static DualScreenStats probe() {
        return new DualScreenStats(6, 8, 3, 42, 5, 0, 0, 7, 12, 0, 0b10101);
    }
}
