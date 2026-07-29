import net.fabricmc.loom.task.prod.ClientProductionRunTask
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
    id("idea")
    id("fabric-loom") version ("1.14.4")
}

val MINECRAFT_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val FABRIC_LOADER_VERSION: String by rootProject.extra
val FABRIC_API_VERSION: String by rootProject.extra
val SODIUM_DEPENDENCY_FABRIC: Any by rootProject.extra
val MOD_VERSION: String by rootProject.extra

repositories {
    mavenLocal()
    maven("https://maven.luna5ama.dev")
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

base {
    archivesName.set("iris-fabric")
}

val vibrisBridgeTest = sourceSets.create("vibrisBridgeTest") {
    java.srcDir("src/vibrisBridgeTest/java")
    compileClasspath += sourceSets.main.get().output
    compileClasspath += project(":common").sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}

configurations[vibrisBridgeTest.implementationConfigurationName]
    .extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
configurations[vibrisBridgeTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())

val vibrisRuntimeInclude = configurations.create("vibrisRuntimeInclude") {
    isCanBeConsumed = false
    isCanBeResolved = false
    isTransitive = true
}

configurations.named("includeInternal") {
    extendsFrom(vibrisRuntimeInclude)
}

dependencies {
    minecraft("com.mojang:minecraft:${MINECRAFT_VERSION}")
    mappings(loom.layered {
        officialMojangMappings()
        if (PARCHMENT_VERSION != null) {
            parchment("org.parchmentmc.data:parchment-${MINECRAFT_VERSION}:${PARCHMENT_VERSION}@zip")
        }
    })
    modImplementation("net.fabricmc:fabric-loader:$FABRIC_LOADER_VERSION")

    fun addRuntimeFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        modRuntimeOnly(module)
        add("productionRuntimeMods", module)
    }

    fun addEmbeddedFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        modImplementation(module)
        include(module)
    }

    fun implementAndInclude(name: String) {
        modImplementation(name)
        include(name)
    }

    fun implementAndIncludeTransitive(name: String) {
        modImplementation(name) {
            isTransitive = false
        }
        include(name) {
            isTransitive = false
        }
    }

    // Fabric API modules
    addEmbeddedFabricModule("fabric-api-base")
    addEmbeddedFabricModule("fabric-key-binding-api-v1")
    addRuntimeFabricModule("fabric-block-view-api-v2")
    addRuntimeFabricModule("fabric-rendering-fluids-v1")
    addRuntimeFabricModule("fabric-resource-loader-v0")
    addRuntimeFabricModule("fabric-lifecycle-events-v1")
    addRuntimeFabricModule("fabric-renderer-api-v1")
    addRuntimeFabricModule("fabric-command-api-v2")

    modImplementation(SODIUM_DEPENDENCY_FABRIC)
    implementAndInclude("org.antlr:antlr4-runtime:4.13.1")
    implementAndInclude("io.github.douira:glsl-transformer:3.0.0-pre3")
    implementAndInclude("org.anarres:jcpp:1.4.14")

    implementAndIncludeTransitive("dev.luna5ama:kmogus-core:1.1-SNAPSHOT")
    implementAndIncludeTransitive("dev.luna5ama:gl-wrapper-lwjgl-3:1.1.0")
    implementAndIncludeTransitive("dev.luna5ama:gl-wrapper-base:1.1.0")
    implementAndIncludeTransitive("dev.luna5ama:gl-wrapper-core:1.1.0")
    implementAndIncludeTransitive("dev.luna5ama:gl-wrapper-lwjgl-3:1.1.0")
    implementAndIncludeTransitive("dev.luna5ama:vibris-common")
    implementAndIncludeTransitive("dev.luna5ama:vibris-capture")
    modImplementation("dev.luna5ama:vibris-core")
    vibrisRuntimeInclude("dev.luna5ama:vibris-core")

    "vibrisBridgeTestImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "vibrisBridgeTestImplementation"("org.junit.jupiter:junit-jupiter")
    "vibrisBridgeTestImplementation"("dev.luna5ama:vibris-api")
    "vibrisBridgeTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

//    implementAndIncludeTransitive("org.apache.commons:commons-compress:1.28.0")
//    implementAndIncludeTransitive("commons-codec:commons-codec:1.19.0")
//    implementAndIncludeTransitive("commons-io:commons-io:2.20.0")
//    implementAndIncludeTransitive("org.apache.commons:commons-lang3:3.18.0")

    implementAndIncludeTransitive("org.jetbrains:annotations:13.0")
    implementAndIncludeTransitive("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    implementAndIncludeTransitive("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.21")
    implementAndIncludeTransitive("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")
    implementAndIncludeTransitive("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1")
    implementAndIncludeTransitive("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementAndIncludeTransitive("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementAndIncludeTransitive("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
    implementAndIncludeTransitive("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
    implementAndIncludeTransitive("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation(project.project(":common").sourceSets.getByName("vendored").output)
    implementation(project.project(":common").sourceSets.getByName("api").output)
    compileOnly(project.project(":common").sourceSets.getByName("headers").output)
    implementation(project.project(":common").sourceSets.getByName("main").output)

    compileOnly(files(rootDir.resolve("DHApi.jar")))
}

tasks.named("compileTestJava").configure {
    enabled = false
}

tasks.named("test").configure {
    enabled = false
}

tasks.register<Test>("vibrisBridgeTest") {
    description = "Runs the focused Iris-Vibris runtime bridge tests."
    testClassesDirs = vibrisBridgeTest.output.classesDirs
    classpath = vibrisBridgeTest.runtimeClasspath
    useJUnitPlatform()
}

loom {
    if (project(":common").file("src/main/resources/iris.accesswidener").exists())
        accessWidenerPath.set(project(":common").file("src/main/resources/iris.accesswidener"))

    @Suppress("UnstableApiUsage")
    mixin {
        defaultRefmapName.set("iris-fabric.refmap.json")
        useLegacyMixinAp = false
    }

    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
           // vmArgs("-Dmixin.debug.export=true")
           // vmArg("-XX:+AllowEnhancedClassRedefinition")
        }
        create("clientWithRenderdoc") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
            environmentVariable("LD_PRELOAD", "/home/ims/renderdoc/build/lib/librenderdoc.so")
            vmArgs("-DMC_DEBUG_ENABLED=true", "-DMC_DEBUG_DUMP_TEXTURE_ATLAS=true")
            programArgs("--renderDebugLabels")
        }
    }
}

tasks {
    processResources {
        from(project.project(":common").sourceSets.main.get().resources)
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(zipTree(project.project(":common").tasks.jar.get().archiveFile))

        manifest.attributes["Main-Class"] = "net.irisshaders.iris.LaunchWarn"
    }

    remapJar.get().destinationDirectory = rootDir.resolve("build").resolve("libs")
}

tasks.register<ClientProductionRunTask>("runVibrisPhase4Client") {
    description = "Runs the exact patched Iris JAR in an isolated Phase 4 game directory."
    group = "verification"

    val patchedJar = providers.gradleProperty("phase4PatchedJar")
    val gameDirectory = providers.gradleProperty("phase4GameDir")
    val runId = providers.gradleProperty("phase4RunId")
    val scenario = providers.gradleProperty("phase4Scenario")
    val eventFile = providers.gradleProperty("phase4EventFile")
    val receiptFile = providers.gradleProperty("phase4ReceiptFile")
    val commandFile = providers.gradleProperty("phase4CommandFile")

    mods.from(patchedJar.map { file(it) })
    mods.from(SODIUM_DEPENDENCY_FABRIC)
    runDir.set(layout.dir(gameDirectory.map { file(it) }))
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs.set(runId.zip(gameDirectory) { id, game ->
        listOf(
            "-Dvibris.phase4.runId=$id",
            "-Dvibris.phase4.gameDir=${file(game).absolutePath}",
            "-Dvibris.phase4.scenario=${scenario.get()}",
            "-Dvibris.phase4.eventFile=${file(eventFile.get()).absolutePath}",
            "-Dvibris.phase4.receiptFile=${file(receiptFile.get()).absolutePath}",
            "-Dvibris.phase4.commandFile=${file(commandFile.get()).absolutePath}",
            "-Dvibris.pendingShadersRoot=${file(game).resolve("vibris/pending").absolutePath}",
            "-Dvibris.artifactRoot=${file(game).resolve("vibris/artifacts").absolutePath}"
        )
    })
    programArgs.set(gameDirectory.map { game ->
        listOf("--gameDir", file(game).absolutePath, "--quickPlaySingleplayer", "vibris-phase4-world")
    })
}
