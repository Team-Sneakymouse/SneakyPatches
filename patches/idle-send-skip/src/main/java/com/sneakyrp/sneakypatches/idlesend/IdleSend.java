package com.sneakyrp.sneakypatches.idlesend;

/** Implemented on {@code ServerEntity} by the patch's mixin; called from the tracker loop. */
public interface IdleSend {
    /**
     * Whether the tracker loop leaves out this tick's {@code sendChanges()}: the entity is not a player and nobody is
     * tracking it. Remembers the skip, so the next viewer's spawn data starts from the entity's current state.
     */
    boolean sneakyPatches$skipSend();
}
