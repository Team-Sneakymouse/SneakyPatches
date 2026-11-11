package com.sneakyrp.sneakypatches.pairrecheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DueTest {
    @Test
    void everyEntityIsDueOncePerInterval() {
        for (int id : new int[] {0, 1, 7, 12345, Integer.MAX_VALUE, -3}) {
            int due = 0;
            for (int tick = 1000; tick < 1000 + PairRecheck.INTERVAL; tick++) {
                if (PairRecheck.due(tick, id)) due++;
            }
            assertEquals(1, due, "entity " + id);
        }
    }

    @Test
    void checksAreSpreadAcrossTicks() {
        int[] perTick = new int[PairRecheck.INTERVAL];
        for (int id = 0; id < 10_000; id++) {
            for (int t = 0; t < PairRecheck.INTERVAL; t++) {
                if (PairRecheck.due(t, id)) perTick[t]++;
            }
        }
        for (int count : perTick) assertTrue(Math.abs(count - 10_000 / PairRecheck.INTERVAL) <= 1);
    }

    @Test
    void tickCounterWrapDoesNotThrow() {
        PairRecheck.due(Integer.MAX_VALUE, Integer.MAX_VALUE);
        PairRecheck.due(Integer.MIN_VALUE, 5);
    }
}
