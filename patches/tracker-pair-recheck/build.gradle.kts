plugins {
    id("io.papermc.paperweight.userdev")
}

dependencies {
    paperweight.paperDevBundle(providers.gradleProperty("paperVersion").get())
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
}

// Inspect the exact development server without loading or initializing Minecraft classes.
tasks.test {
    classpath += configurations.compileClasspath.get()
}
