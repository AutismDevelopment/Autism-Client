plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    // AUTISM Client is consumed from your local Maven repo. In the AUTISM project run:
    //     ./gradlew publishToMavenLocal
    mavenLocal()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
}

dependencies {
    // Mirrors the AUTISM Client build: official Mojang mappings are implicit for the configured version, so there is no
    // explicit `mappings(...)` line and dependencies use plain `implementation`.
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    // The AUTISM Client API (published to mavenLocal from the AUTISM project).
    implementation(libs.autism)
}

// Turns an exact Minecraft version (e.g. "26.2") into a compatible range ("~26.2") so the addon
// keeps loading across patch releases instead of pinning one exact build.
fun toMinecraftCompat(version: String): String {
    val m = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?$""").matchEntire(version)
        ?: return version
    val (year, drop, _) = m.destructured
    return "~$year.$drop"
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
            "mc_compat" to toMinecraftCompat(libs.versions.minecraft.get()),
            "fabric_api_version" to libs.versions.fabric.api.get(),
            "autism_api_version" to libs.versions.autism.get()
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}
