package com.clashgame.client;

/**
 * Non-JavaFX launcher class for fat JAR execution.
 * JavaFX Application subclass cannot be the main class in a shaded JAR
 * due to module system restrictions — this wrapper bypasses that.
 */
public class Launcher {
    public static void main(String[] args) {
        GameClient.main(args);
    }
}
