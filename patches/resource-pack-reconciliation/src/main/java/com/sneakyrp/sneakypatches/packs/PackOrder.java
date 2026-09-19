package com.sneakyrp.sneakypatches.packs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Connection-local state, confined to the client's Netty event loop. */
public final class PackOrder {
    private final List<UUID> installed = new ArrayList<>();
    private int cursor;
    private boolean configuring;
    private final java.util.Set<UUID> requested = new java.util.HashSet<>();

    public void begin() { cursor = 0; configuring = true; requested.clear(); }

    public void request(UUID id) {
        if (!configuring) begin();
        if (!requested.add(id)) throw new IllegalStateException("Duplicate requested pack UUID: " + id);
    }

    public void before(UUID clientId, Consumer<UUID> pop) {
        if (!configuring) begin();
        int position = installed.indexOf(clientId);
        if (position >= 0 && position < cursor) {
            throw new IllegalStateException("Duplicate pack identity in configuration: " + clientId);
        }
        // A new pack appends. Remove the old suffix first so later requests can
        // rebuild it above the new pack, with real client acknowledgements.
        int count = position < 0 ? installed.size() - cursor : position - cursor;
        for (int i = 0; i < count; i++) {
            UUID removed = installed.get(cursor);
            pop.accept(removed); // May fail: do not mutate state before acceptance.
            remove(removed);
        }
    }

    public void pushed(UUID clientId, boolean configuration) {
        int position = installed.indexOf(clientId);
        if (position < 0) installed.add(clientId);
        if (configuration) {
            if (!configuring || cursor >= installed.size() || !installed.get(cursor).equals(clientId)) {
                throw new IllegalStateException("Pack order diverged from forwarded requests");
            }
            cursor++;
        }
    }

    public void finish(Consumer<UUID> pop) {
        if (!configuring) begin(); // Empty destination selection is authoritative.
        while (installed.size() > cursor) {
            UUID removed = installed.get(cursor);
            pop.accept(removed);
            remove(removed);
        }
        configuring = false;
    }

    public void remove(UUID clientId) {
        if (clientId == null) { installed.clear(); cursor = 0; return; }
        int index = installed.indexOf(clientId);
        if (index >= 0) {
            installed.remove(index);
            if (index < cursor) cursor--;
        }
    }
    public List<UUID> snapshot() { return List.copyOf(installed); }
}
