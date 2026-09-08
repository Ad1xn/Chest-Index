plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val mcVersion = stonecutter.current.version
val toolchainJava = (property("mod.java") as String).toInt()

version = "${property("mod.version")}+$mcVersion"
group = property("mod.group")!!
base { archivesName = "${property("mod.id")}-$mcVersion" }

loom {
    noIntermediateMappings()

    splitEnvironmentSourceSets()
    mods {
        create(property("mod.id") as String) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    "clientCompileOnly"("com.terraformersmc:modmenu:${property("deps.modmenu")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(toolchainJava)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = toolchainJava
    options.encoding = "UTF-8"
}

val resourceProps = mapOf(
    "version" to project.version.toString(),
    "mod_id" to property("mod.id").toString(),
    "mod_name" to property("mod.name").toString(),
    "mc_dep" to property("mod.mc_dep").toString(),
    "loader_dep" to property("deps.fabric_loader").toString(),
    "java_dep" to toolchainJava.toString(),
    "mixin_compat" to property("mod.mixin_compat").toString(),
)

tasks.processResources {
    inputs.properties(resourceProps)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(resourceProps) }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "4g"
    environment("CT_BENCH", System.getenv("CT_BENCH") ?: "")
    testLogging { showStandardStreams = System.getenv("CT_BENCH") == "1" }
}
