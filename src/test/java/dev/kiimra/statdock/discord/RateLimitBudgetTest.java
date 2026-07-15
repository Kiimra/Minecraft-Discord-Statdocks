package dev.kiimra.statdock.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimitBudgetTest {

    @Test
    void allowsUpToMaxEditsThenBlocksUntilWindowSlides() {
        RateLimitBudget budget = new RateLimitBudget(2, 600_000L);
        long t = 1_000_000L;

        assertTrue(budget.canEditNow(t));
        budget.recordEdit(t);
        assertTrue(budget.canEditNow(t));
        budget.recordEdit(t);

        // Budget of 2 is now spent within the window.
        assertFalse(budget.canEditNow(t));
        assertEquals(600_000L, budget.millisUntilNextAllowed(t));

        // Just before the window slides, still blocked; just after, allowed.
        assertFalse(budget.canEditNow(t + 599_999L));
        assertTrue(budget.canEditNow(t + 600_001L));
    }

    @Test
    void honoursHardBlockFrom429() {
        RateLimitBudget budget = new RateLimitBudget(2, 600_000L);
        long t = 5_000L;

        assertTrue(budget.canEditNow(t));
        budget.blockFor(t, 30_000L);
        assertFalse(budget.canEditNow(t));
        assertEquals(30_000L, budget.millisUntilNextAllowed(t));
        assertTrue(budget.canEditNow(t + 30_001L));
    }

    @Test
    void longestConstraintWins() {
        RateLimitBudget budget = new RateLimitBudget(1, 600_000L);
        long t = 0L;
        budget.recordEdit(t);           // window blocks for 600s
        budget.blockFor(t, 120_000L);   // 429 blocks for 120s
        // The window (600s) is the longer of the two constraints.
        assertEquals(600_000L, budget.millisUntilNextAllowed(t));
    }
}
