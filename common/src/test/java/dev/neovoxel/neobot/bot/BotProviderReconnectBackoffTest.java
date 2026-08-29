package dev.neovoxel.neobot.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The QQ reconnect loop retries every check-interval tick by default. During a QQ risk-control
 * outage that hammers Tencent's servers and risks making the flag worse, so failed attempts back
 * off (doubling, capped) instead of retrying every tick; a successful reconnect resets the streak.
 */
class BotProviderReconnectBackoffTest {

    @Test
    void firstAttemptUsesBaseDelay() {
        assertEquals(5_000L, BotProvider.nextBackoffMillis(0));
    }

    @Test
    void delayDoublesWithEachFailedStreak() {
        assertEquals(10_000L, BotProvider.nextBackoffMillis(1));
        assertEquals(20_000L, BotProvider.nextBackoffMillis(2));
        assertEquals(40_000L, BotProvider.nextBackoffMillis(3));
    }

    @Test
    void delayIsCappedAtMaximum() {
        assertEquals(300_000L, BotProvider.nextBackoffMillis(10));
        assertEquals(300_000L, BotProvider.nextBackoffMillis(1000));
    }
}
