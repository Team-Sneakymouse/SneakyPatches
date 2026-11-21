package com.sneakyrp.sneakypatches.rangelookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListChoiceTest {
    @Test
    void theSmallestCoveringListIsChosen() {
        assertEquals(PlayerList.REALLY_SMALL, PlayerList.listFor(0));
        assertEquals(PlayerList.REALLY_SMALL, PlayerList.listFor(32));
        assertEquals(PlayerList.REALLY_SMALL, PlayerList.listFor(48));
        assertEquals(PlayerList.SMALL, PlayerList.listFor(49));
        assertEquals(PlayerList.SMALL, PlayerList.listFor(160));
        assertEquals(PlayerList.VIEW_DISTANCE, PlayerList.listFor(161));
        assertEquals(PlayerList.VIEW_DISTANCE, PlayerList.listFor(Integer.MAX_VALUE));
    }

    @Test
    void aPlayerInRangeIsAlwaysInTheChosenList() {
        // Paper's check is horizontal distance <= range. A list holds the players whose chunk is within its radius
        // of the entity's chunk on each axis. Entities and players at every offset in a chunk, negative chunks too.
        int[][] lists = {{PlayerList.REALLY_SMALL_BLOCKS, 3}, {PlayerList.SMALL_BLOCKS, 10}};
        for (int[] list : lists) {
            int range = list[0], radius = list[1];
            for (double entity = -16; entity < 16; entity += 0.25) {
                for (double player : new double[] {entity - range, entity + range}) {
                    int chunks = Math.abs(Math.floorDiv((int) Math.floor(player), 16)
                        - Math.floorDiv((int) Math.floor(entity), 16));
                    assertTrue(chunks <= radius, entity + " -> " + player);
                }
            }
        }
    }
}
