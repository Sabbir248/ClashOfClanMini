package com.clashgame.client.model;

/**
 * Client-side representation of a battle result.
 * Parsed from the server's BATTLE_RESULT message.
 */
public class BattleResult {

    private String battleId;
    private String attackerName;
    private String defenderName;
    private int starsEarned;
    private double destructionPercentage;
    private int goldLooted;
    private int elixirLooted;
    private int trophiesWon;
    private int trophiesLost;
    private int unitsDeployed;
    private int unitsLost;
    private int durationSeconds;
    private boolean isAttacker; // whether current player was the attacker

    // ─── Constructors ────────────────────────────────────────────────

    public BattleResult() {}

    // ─── Getters / Setters ───────────────────────────────────────────

    public String getBattleId() { return battleId; }
    public void setBattleId(String battleId) { this.battleId = battleId; }

    public String getAttackerName() { return attackerName; }
    public void setAttackerName(String attackerName) { this.attackerName = attackerName; }

    public String getDefenderName() { return defenderName; }
    public void setDefenderName(String defenderName) { this.defenderName = defenderName; }

    public int getStarsEarned() { return starsEarned; }
    public void setStarsEarned(int starsEarned) { this.starsEarned = starsEarned; }

    public double getDestructionPercentage() { return destructionPercentage; }
    public void setDestructionPercentage(double pct) { this.destructionPercentage = pct; }

    public int getGoldLooted() { return goldLooted; }
    public void setGoldLooted(int goldLooted) { this.goldLooted = goldLooted; }

    public int getElixirLooted() { return elixirLooted; }
    public void setElixirLooted(int elixirLooted) { this.elixirLooted = elixirLooted; }

    public int getTrophiesWon() { return trophiesWon; }
    public void setTrophiesWon(int trophiesWon) { this.trophiesWon = trophiesWon; }

    public int getTrophiesLost() { return trophiesLost; }
    public void setTrophiesLost(int trophiesLost) { this.trophiesLost = trophiesLost; }

    public int getUnitsDeployed() { return unitsDeployed; }
    public void setUnitsDeployed(int unitsDeployed) { this.unitsDeployed = unitsDeployed; }

    public int getUnitsLost() { return unitsLost; }
    public void setUnitsLost(int unitsLost) { this.unitsLost = unitsLost; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isAttacker() { return isAttacker; }
    public void setAttacker(boolean attacker) { isAttacker = attacker; }

    /**
     * Get a formatted star display string (e.g., "★★☆").
     */
    public String getStarDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(i < starsEarned ? "★" : "☆");
        }
        return sb.toString();
    }
}
