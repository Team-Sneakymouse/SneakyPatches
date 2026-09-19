package com.sneakyrp.sneakypatches.packs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackOrderTest {
    static UUID id(int n) { return new UUID(0, n); }
    static List<List<UUID>> choices() {
        var result = new ArrayList<List<UUID>>();
        extend(new ArrayList<>(), result);
        return result;
    }
    static void extend(List<UUID> prefix, List<List<UUID>> result) {
        result.add(List.copyOf(prefix));
        for (int n = 1; n <= 5; n++) {
            UUID id = id(n);
            if (!prefix.contains(id)) {
                prefix.add(id); extend(prefix, result); prefix.removeLast();
            }
        }
    }
    @Test void everyOrderedSubsetTransition() {
        var choices = choices();
        for (var previous : choices) for (var desired : choices) {
            PackOrder state = new PackOrder();
            previous.forEach(p -> state.pushed(p, false));
            var client = new ArrayList<>(previous);
            int[] counts = {0, 0};
            java.util.function.Consumer<UUID> pop = p -> { assertTrue(client.remove(p)); counts[0]++; };
            state.begin();
            for (UUID id : desired) {
                state.before(id, pop);
                if (!client.contains(id)) { client.add(id); counts[1]++; }
                state.pushed(id, true);
            }
            state.finish(pop);
            assertEquals(desired, client);
            assertEquals(desired, state.snapshot());
            if (previous.equals(desired)) assertArrayEquals(new int[]{0, 0}, counts);
            if (previous.stream().filter(desired::contains).toList().equals(desired)) assertEquals(0, counts[1]);
        }
    }
    @Test void failedRemovalDoesNotCommit() {
        var state = new PackOrder(); state.pushed(id(1), false); state.begin();
        assertThrows(IllegalStateException.class, () -> state.finish(p -> { throw new IllegalStateException(); }));
        assertEquals(List.of(id(1)), state.snapshot());
    }
    @Test void duplicateIdentityFails() {
        var state = new PackOrder(); state.begin();
        state.before(id(1), p -> fail()); state.pushed(id(1), true);
        assertThrows(IllegalStateException.class, () -> state.before(id(1), p -> fail()));
    }
    @Test void duplicateRequestedUuidFailsEvenIfItsHashChanged() {
        var state = new PackOrder(); state.request(id(1));
        assertThrows(IllegalStateException.class, () -> state.request(id(1)));
        state.begin(); state.request(id(1));
    }
    @Test void explicitRefreshCanClearAndRebuild() {
        var state = new PackOrder(); state.pushed(id(1), false); state.pushed(id(2), false);
        state.remove(null); state.pushed(id(2), false); state.pushed(id(1), false);
        state.begin(); state.before(id(2), p -> fail()); state.pushed(id(2), true);
        var removed = new ArrayList<UUID>(); state.finish(removed::add);
        assertEquals(List.of(id(1)), removed); assertEquals(List.of(id(2)), state.snapshot());
    }
}
