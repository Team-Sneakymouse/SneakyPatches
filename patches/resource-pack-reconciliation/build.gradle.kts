import java.net.URI
import java.security.MessageDigest

// CE Proxy and Velocity are not published to Maven. Unless -PcraftEngineProxyJar / -PvelocityJar point at local
// copies, the pinned JARs are downloaded once into the Gradle user home and must match their SHA-256.
data class PinnedJar(val property: String, val url: String, val sha256: String)

val pinnedCeProxy = PinnedJar(
    "craftEngineProxyJar",
    "https://github.com/Xiao-MoMi/craft-engine-proxy/releases/download/26.8.1/craft-engine-proxy-velocity-plugin-26.8.1.jar",
    "e68aa410c21d480fb7d602031535735a4bd28016ac120aa8d3b0f4695dd3b9a8",
)
val pinnedVelocity = PinnedJar(
    "velocityJar",
    "https://fill-data.papermc.io/v1/objects/b2879bb60eba6be23e99fdd659084b7adbe1190d42b7ddf296356331f8ea7c9a/velocity-4.2.1-SNAPSHOT-31.jar",
    "b2879bb60eba6be23e99fdd659084b7adbe1190d42b7ddf296356331f8ea7c9a",
)

fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

fun resolveJar(pin: PinnedJar): File {
    providers.gradleProperty(pin.property).orNull?.let { path ->
        val file = rootProject.file(path)
        check(file.isFile) { "-P${pin.property}=$path is not a file" }
        return file
    }
    val cached = gradle.gradleUserHomeDir.resolve("caches/sneakypatches/${pin.sha256}/${pin.url.substringAfterLast('/')}")
    if (cached.isFile && sha256(cached) == pin.sha256) return cached
    cached.parentFile.mkdirs()
    val partial = File(cached.path + ".part")
    URI(pin.url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
    val actual = sha256(partial)
    check(actual == pin.sha256) {
        partial.delete()
        "${pin.url} has SHA-256 $actual, expected ${pin.sha256}"
    }
    check(partial.renameTo(cached) || cached.isFile) { "Could not move $partial to $cached" }
    return cached
}

val ceJar = resolveJar(pinnedCeProxy)
val velocityJar = resolveJar(pinnedVelocity)
dependencies {
    compileOnly(files(ceJar))
    compileOnly(files(velocityJar))
    compileOnly("io.netty:netty-all:4.2.7.Final")
    testImplementation(files(ceJar))
    testImplementation(files(velocityJar))
    testImplementation("io.netty:netty-all:4.2.7.Final")
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
}

// Separate test-only Ignite mod, never included in the installable root artifact.
tasks.register<Jar>("igniteProofJar") {
    dependsOn(tasks.testClasses)
    archiveFileName = "sneakypatches-proof.jar"
    from(sourceSets.test.get().output)
}
