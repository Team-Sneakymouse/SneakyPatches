package com.sneakyrp.sneakypatches.bootstrap;

import java.util.Arrays;

/** Class-transformation proof only; starts no world or listener and accepts no EULA. */
public final class PaperProof {
    public static void main(String[] args) throws Exception {
        PaperPatches paper = new PaperPatches(); paper.onLoad("");
        VelocityPatches velocity = new VelocityPatches(); velocity.onLoad("");
        if (paper.getMixins().isEmpty() || !velocity.getMixins().isEmpty())
            throw new AssertionError("Wrong platform patches selected");
        Class<?> entity = Class.forName("net.minecraft.world.entity.item.ItemEntity", false, Thread.currentThread().getContextClassLoader());
        if (Arrays.stream(entity.getDeclaredMethods()).noneMatch(m -> m.getName().contains("sneakyPatches$preventItemMerging")))
            throw new AssertionError("Item merge injection missing");
        System.out.println("PASS Paper transformation: item merge active, Velocity patches inactive");
    }
}
