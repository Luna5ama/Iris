import net.fabricmc.loom.task.prod.ClientProductionRunTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.nio.file.Files

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
    implementAndInclude("io.github.douira:glsl-transformer:3.0.0-pre3-iris9")
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
    "vibrisBridgeTestImplementation"("dev.luna5ama:vibris-capture")
    "vibrisBridgeTestImplementation"("dev.luna5ama:vibris-core")
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
    }
}

tasks {
    processResources {
        dependsOn(project(":common").tasks.named("generateBuildConfig"))
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

tasks.register<ClientProductionRunTask>("runVibrisAutomationClient") {
    description = "Runs the exact patched Iris JAR in an isolated Vibris automation game directory."
    group = "verification"

    val patchedJar = providers.gradleProperty("automationPatchedJar")
    val gameDirectory = providers.gradleProperty("automationGameDir")
    val runId = providers.gradleProperty("automationRunId")
    val scenario = providers.gradleProperty("automationScenario")
    doFirst {
        val game = file(gameDirectory.get())
        val pending = game.resolve("vibris/pending")
        val artifacts = game.resolve("vibris/artifacts")
        val shaderpack = game.resolve("shaderpacks/vibris")
        listOf(pending, artifacts, shaderpack).forEach { Files.createDirectories(it.toPath()) }
        val serverConfig = game.resolve("config/vibris/server.json")
        Files.createDirectories(serverConfig.parentFile.toPath())
        fun jsonPath(value: File): String = value.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")
        serverConfig.writeText(
            """
            {
              "schema_version": 1,
              "listen_address": "127.0.0.1:50051",
              "pending_shaders_root": "${jsonPath(pending)}",
              "artifact_root": "${jsonPath(artifacts)}",
              "artifact_quota_bytes": 3221225472,
              "shaderpack_root": "${jsonPath(shaderpack)}",
              "max_source_bytes": 536870912,
              "max_source_files": 100000,
              "max_global_queue": 32,
              "max_actions_per_job": 64
            }
            """.trimIndent()
        )
    }

    mods.from(patchedJar.map { file(it) })
    mods.from(SODIUM_DEPENDENCY_FABRIC)
    runDir.set(layout.dir(gameDirectory.map { file(it) }))
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs.set(runId.zip(scenario) { id, selectedScenario ->
        listOf("-Dvibris.automation.runId=$id") + if (selectedScenario == "g008-c003") {
            listOf("-Dio.grpc.netty.shaded.io.netty.allocator.type=unpooled")
        } else {
            emptyList()
        }
    })
    programArgs.set(gameDirectory.map { game ->
        listOf("--gameDir", file(game).absolutePath, "--quickPlaySingleplayer", "vibris-automation-world")
    })
}
