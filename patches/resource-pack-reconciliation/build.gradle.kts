val ceJar = rootProject.file(providers.gradleProperty("craftEngineProxyJar").getOrElse(".scratch/dependencies/craftengine-proxy.jar"))
val velocityJar = rootProject.file(providers.gradleProperty("velocityJar").get())
dependencies {
    compileOnly(files(ceJar))
    compileOnly(files(velocityJar))
    compileOnly("io.netty:netty-all:4.2.7.Final")
    testImplementation(files(ceJar))
    testImplementation(files(velocityJar))
    testImplementation("io.netty:netty-all:4.2.7.Final")
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
}
tasks.compileJava { doFirst {
    check(ceJar.isFile) { "Supply -PcraftEngineProxyJar=<CE Proxy 26.8.1 JAR> (see README)" }
    check(velocityJar.isFile) { "Supply -PvelocityJar=<current Velocity JAR> (see README)" }
} }

// Separate test-only Ignite mod, never included in the installable root artifact.
tasks.register<Jar>("igniteProofJar") {
    dependsOn(tasks.testClasses)
    archiveFileName = "sneakypatches-proof.jar"
    from(sourceSets.test.get().output)
}
