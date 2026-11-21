package com.sneakyrp.sneakypatches.rangelookup.mixin;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import com.sneakyrp.sneakypatches.rangelookup.PlayerList;
import com.sneakyrp.sneakypatches.rangelookup.RangeSizedLookup;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The tracker tick reads its candidate players from the smallest Moonrise list that covers the entity's range, instead
 * of always the view-distance list. Everything after the lookup is Paper's: every candidate goes through
 * {@code updatePlayer} (distance, view distance, chunk sent, {@code canSee}, events), and viewers who are no longer in
 * the list are removed when the chunk's lists changed.
 *
 * <p>Paper removes such viewers only when the chunk or its update count changed. A switch to a smaller list (the range
 * was lowered, a wide passenger got off) changes neither, so the switch itself must force that removal.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", remap = false)
abstract class TrackedEntityLookupMixin {
    @Shadow(remap = false)
    private NearbyPlayers.TrackedChunk lastTrackedChunk;

    /** The value {@code updatePlayer} compares against: the widest passenger and the broadcast percentage included. */
    @Shadow(remap = false)
    private int getEffectiveRange() {
        throw new AssertionError();
    }

    @Unique
    private int sneakyPatches$lastList = -1;

    @Redirect(
        method = "moonrise$tick(Lca/spottedleaf/moonrise/common/misc/NearbyPlayers$TrackedChunk;)V",
        at = @At(
            value = "INVOKE",
            target = "Lca/spottedleaf/moonrise/common/misc/NearbyPlayers$TrackedChunk;getPlayers(Lca/spottedleaf/moonrise/common/misc/NearbyPlayers$NearbyMapType;)Lca/spottedleaf/moonrise/common/list/ReferenceList;",
            remap = false
        ),
        remap = false,
        require = 1,
        allow = 1
    )
    private ReferenceList<ServerPlayer> sneakyPatches$rangeSized(NearbyPlayers.TrackedChunk chunk,
                                                                 NearbyPlayers.NearbyMapType paperList) {
        if (!RangeSizedLookup.ENABLED) return chunk.getPlayers(paperList);
        int list = PlayerList.listFor(getEffectiveRange());
        if (list != sneakyPatches$lastList) {
            sneakyPatches$lastList = list;
            // Paper reads this after the lookup: a different chunk makes it remove viewers missing from the list.
            lastTrackedChunk = null;
        }
        return chunk.getPlayers(switch (list) {
            case PlayerList.REALLY_SMALL -> NearbyPlayers.NearbyMapType.GENERAL_REALLY_SMALL;
            case PlayerList.SMALL -> NearbyPlayers.NearbyMapType.GENERAL_SMALL;
            default -> paperList;
        });
    }
}
