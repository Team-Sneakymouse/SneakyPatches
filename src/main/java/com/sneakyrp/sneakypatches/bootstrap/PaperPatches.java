package com.sneakyrp.sneakypatches.bootstrap;

import java.util.List;
import java.util.Map;

public final class PaperPatches extends PlatformPatches {
    /** Each Paper Mixin configuration's package and the mixins it owns. */
    static final Map<String, List<String>> MIXINS = Map.of(
        "com.sneakyrp.sneakypatches.itemmerge.mixin", List.of("ItemEntityMixin"),
        "com.sneakyrp.sneakypatches.trackingrange.mixin",
            List.of("TrackingRangeMixin", "TrackedEntityMixin", "TrackedEntityAccessor", "EntityTagMixin",
                "DisplayViewRangeMixin", "PaperCommandsMixin"));

    private List<String> mixins = List.of();

    @Override public void onLoad(String mixinPackage) {
        active = paper(loader());
        mixins = MIXINS.get(mixinPackage);
        if (mixins == null) throw new IllegalStateException("No Paper patch registered for " + mixinPackage);
    }
    @Override public List<String> getMixins() { return active ? mixins : List.of(); }
}
