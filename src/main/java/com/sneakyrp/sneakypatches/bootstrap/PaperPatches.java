package com.sneakyrp.sneakypatches.bootstrap;

import java.util.List;

public final class PaperPatches extends PlatformPatches {
    @Override public void onLoad(String mixinPackage) { active = paper(loader()); }
    @Override public List<String> getMixins() { return active ? List.of("ItemEntityMixin") : List.of(); }
}
