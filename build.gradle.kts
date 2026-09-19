import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.zip.ZipFile

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23" apply false
}

java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
dependencies {
    compileOnly("net.fabricmc:sponge-mixin:${providers.gradleProperty("mixinVersion").get()}")
    testImplementation("net.fabricmc:sponge-mixin:${providers.gradleProperty("mixinVersion").get()}")
    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }
    dependencies {
        "compileOnly"("net.fabricmc:sponge-mixin:${providers.gradleProperty("mixinVersion").get()}")
        "testImplementation"(platform("org.junit:junit-bom:5.14.1"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    // Only the root project produces an installable artifact.
    tasks.named<Jar>("jar") { enabled = false }
    val patchOutput = extensions.getByType<SourceSetContainer>().named("main").map { it.output }
    val patchCheck = tasks.named("check")
    rootProject.tasks.named<Jar>("jar") {
        from(patchOutput)
    }
    rootProject.tasks.named("check") { dependsOn(patchCheck) }
}

val mixinConfigs = subprojects.sortedBy { it.name }.map { "mixins.sneakypatches.${it.name}.json" }
val metadataDirectory = layout.buildDirectory.dir("generated/ignite")
val generateModMetadata by tasks.registering {
    inputs.property("modVersion", project.version.toString())
    inputs.property("mixins", mixinConfigs)
    outputs.dir(metadataDirectory)
    doLast {
        val directory = metadataDirectory.get().asFile.apply { mkdirs() }
        directory.resolve("ignite.mod.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
            "id" to "sneakypatches",
            "version" to inputs.properties.getValue("modVersion"),
            "mixins" to mixinConfigs,
        ))) + "\n")
    }
}

tasks.jar {
    dependsOn(generateModMetadata)
    from(metadataDirectory)
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes("Implementation-Version" to project.version)
}

val verifyBundle by tasks.registering {
    group = "verification"
    description = "Checks Ignite metadata, required Mixin configs, and bundled classes."
    dependsOn(tasks.jar)
    val bundle = tasks.jar.flatMap { it.archiveFile }
    inputs.file(bundle)
    doLast {
        ZipFile(bundle.get().asFile).use { jar ->
            fun json(path: String): Map<*, *> {
                val entry = requireNotNull(jar.getEntry(path)) { "Missing bundle resource: $path" }
                return jar.getInputStream(entry).use { JsonSlurper().parse(it) as Map<*, *> }
            }
            val metadata = json("ignite.mod.json")
            check(metadata["mixins"] == mixinConfigs) { "Incorrect patch registration" }
            check(mixinConfigs.isNotEmpty()) { "The bundle contains no patches" }
            mixinConfigs.forEach { name ->
                val config = json(name)
                check(config["required"] == true) { "$name must fail on application errors" }
                val mixins = config["patchMixins"] as List<*>
                check(mixins.isNotEmpty()) { "$name contains no mixins" }
                check((config["mixins"] as List<*>).isEmpty()) { "$name must select mixins through its platform plugin" }
                val pluginPath = (config["plugin"] as String).replace('.', '/') + ".class"
                check(jar.getEntry(pluginPath) != null) { "Missing platform selector: $pluginPath" }
                mixins.forEach { mixin ->
                    val path = "${config["package"]}.$mixin".replace('.', '/') + ".class"
                    check(jar.getEntry(path) != null) { "Missing mixin class: $path" }
                }
            }
            jar.entries().asSequence().filter { it.name.endsWith(".class") }.forEach {
                check(it.name.startsWith("com/sneakyrp/sneakypatches/")) {
                    "Unexpected bundled dependency: ${it.name}. Server and loader libraries must remain compile-only."
                }
            }
        }
    }
}

tasks.check { dependsOn(verifyBundle) }

tasks.register<Jar>("paperProofJar") {
    dependsOn(tasks.testClasses)
    archiveFileName = "sneakypatches-paper-proof.jar"
    from(sourceSets.test.get().output)
}
