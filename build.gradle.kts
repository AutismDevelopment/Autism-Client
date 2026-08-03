import java.io.OutputStream
import java.util.zip.ZipFile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.xmlgraphics:batik-transcoder:1.17")
        classpath("org.apache.xmlgraphics:batik-codec:1.17")
    }
}

plugins {
    alias(libs.plugins.fabric.loom)
    `maven-publish`
}

base {
    archivesName = properties["archives_base_name"] as String
    // Version = "<mod>-<mc>" (e.g. 3.1-26.2). Dev/source builds add a "-dev" suffix so they're distinguishable
    // from a tagged release; a release is built with:  gradlew build -Prelease
    version = libs.versions.mod.version.get() + "-" + libs.versions.minecraft.get() +
        (if (project.hasProperty("release")) "" else "-dev")
    group = properties["maven_group"] as String
}

repositories {
    mavenCentral()
    maven { url = uri("https://api.modrinth.com/maven") }
}

dependencies {

    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    // Compile-only: mixins for Lithium's collision sweeper (runtime-optional, plugin-gated).
    compileOnly(libs.lithium)
    // Compile-only: ReplayMod ReplayStudio types for the team-parser compat mixin (runtime-optional).
    compileOnly("maven.modrinth:replaymod:26.2-2.6.27")

    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("io.netty:netty-handler-proxy:4.1.118.Final")
    implementation("io.netty:netty-codec-socks:4.1.118.Final")
    include("io.netty:netty-handler-proxy:4.1.118.Final")
    include("io.netty:netty-codec-socks:4.1.118.Final")
    implementation("de.florianreuth:waybackauthlib:1.1.0")
    include("de.florianreuth:waybackauthlib:1.1.0")
    implementation("com.github.weisj:jsvg:2.1.0")
    include("com.github.weisj:jsvg:2.1.0")

    // Eclipse Paho MQTT client (standalone, no transitive deps) — powers the redundant MQTT relay mesh
    // (EMQX/HiveMQ public brokers over secure WebSocket) so the encrypted matchmaking transport survives any
    // single public service timing out. Bundled jar-in-jar like the other runtime libs.
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    include("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // Bundle a newer MixinExtras (jar-in-jar) so Fabric Loader loads it over the old 0.5.0 it ships
    // with. Required because ViaFabricPlus (and our own WrapOperation/ModifyExpressionValue mixins)
    // need MixinExtras >= 0.5.3; without this, joining a server crashes during mixin application.
    implementation("io.github.llamalad7:mixinextras-fabric:0.5.4")
    include("io.github.llamalad7:mixinextras-fabric:0.5.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.test {
    useJUnitPlatform()
}

val generatedAutismResourcesDir = layout.buildDirectory.dir("generated/resources/autism/main")

data class SourceFile(val path: String, val text: String)
data class FieldSpec(val name: String, val type: String, val kind: String, val editable: Boolean)
data class PacketSpec(
    val className: String,
    val protocol: String,
    val direction: String,
    val codecStyle: String,
    val packetType: String,
    val source: String,
    val complete: Boolean,
    val fields: List<FieldSpec>
)

sourceSets {
    main {
        resources.srcDir(generatedAutismResourcesDir)
    }
}

val generateVanillaUiAssets by tasks.registering {
    // Semantic feature icons used by the vanilla-friendly UI. Structural
    // actions such as close and reorder are rendered as text symbols.
    val iconSourceDir = file("assets/icons")
    val outputDir = generatedAutismResourcesDir.map { it.dir("assets/autismclient") }

    inputs.dir(iconSourceDir)
    outputs.dir(outputDir)

    doLast {
        val targetRoot = outputDir.get().asFile
        targetRoot.parentFile.resolve("yu" + "ng" + "light").deleteRecursively()
        targetRoot.deleteRecursively()
        val iconTargetDir = targetRoot.resolve("textures/gui/vanillaui/icons")

        iconTargetDir.mkdirs()

        val pngTranscoderClass = Class.forName("org.apache.batik.transcoder.image.PNGTranscoder")
        val transcoderInputClass = Class.forName("org.apache.batik.transcoder.TranscoderInput")
        val transcoderOutputClass = Class.forName("org.apache.batik.transcoder.TranscoderOutput")
        val transcodingHintsKeyClass = Class.forName("org.apache.batik.transcoder.TranscodingHints\$Key")

        val transcoder = pngTranscoderClass.getDeclaredConstructor().newInstance()
        val widthField = pngTranscoderClass.getField("KEY_WIDTH")
        val heightField = pngTranscoderClass.getField("KEY_HEIGHT")
        val addHint = pngTranscoderClass.getMethod("addTranscodingHint", transcodingHintsKeyClass, Any::class.java)
        addHint.invoke(transcoder, widthField.get(null), 256f)
        addHint.invoke(transcoder, heightField.get(null), 256f)

        val inputCtor = transcoderInputClass.getConstructor(String::class.java)
        val outputCtor = transcoderOutputClass.getConstructor(OutputStream::class.java)
        val transcode = pngTranscoderClass.getMethod("transcode", transcoderInputClass, transcoderOutputClass)

        iconSourceDir.listFiles { file -> file.isFile && file.extension.equals("svg", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { svg ->
                val outputFile = iconTargetDir.resolve(svg.nameWithoutExtension.lowercase() + ".png")
                outputFile.outputStream().use { out ->
                    val input = inputCtor.newInstance(svg.toURI().toString())
                    val output = outputCtor.newInstance(out)
                    transcode.invoke(transcoder, input, output)
                }
            }

        // Raster icons committed directly as PNG (e.g. the Matchmaking logo) are copied as-is; the engine
        // recolors/whitens them at draw time just like the SVG-derived icons.
        iconSourceDir.listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { png ->
                png.copyTo(iconTargetDir.resolve(png.nameWithoutExtension.lowercase() + ".png"), overwrite = true)
            }
    }
}

val generateAutismInspectorMappings by tasks.registering {
    val mappingFiles = fileTree(".gradle/loom-cache/source_mappings") {
        include("**/*.tiny")
    }
    val outputFile = generatedAutismResourcesDir.map { it.file("autism-inspector-mappings.tsv") }

    inputs.files(mappingFiles)
    outputs.file(outputFile)

    doLast {
        run {
            val target = outputFile.get().asFile
            target.parentFile.mkdirs()
            target.writeText(
                "# Official Mojang mappings are used for Minecraft ${libs.versions.minecraft.get()}; no Yarn aliases are generated.\n",
                Charsets.UTF_8
            )
            return@doLast
        }

        val tinyFile = mappingFiles.files
            .filter { it.isFile }
            .maxByOrNull { it.lastModified() }
            ?: error("Missing Loom source mappings under ${project.file(".gradle/loom-cache/source_mappings").absolutePath}")

        val lines = tinyFile.readLines(Charsets.UTF_8)
        val header = lines.firstOrNull { it.startsWith("tiny\t") }
            ?: error("Invalid tiny mapping file: ${tinyFile.absolutePath}")
        val namespaces = header.split('\t').drop(3)
        val namedIndex = namespaces.indexOf("named")
        val intermediaryIndex = namespaces.indexOf("intermediary")
        require(namedIndex >= 0 && intermediaryIndex >= 0) {
            "Tiny mapping header must expose named and intermediary namespaces: $header"
        }

        fun readNamespace(parts: List<String>, baseOffset: Int, namespaceIndex: Int): String {
            val index = baseOffset + namespaceIndex
            return if (index in parts.indices) parts[index].trim() else ""
        }

        val namedToIntermediary = linkedMapOf<String, String>()
        val classAliases = linkedMapOf<String, String>()

        for (raw in lines) {
            val trimmed = raw.trimStart('\t')
            if (!trimmed.startsWith("c\t")) continue
            val parts = trimmed.split('\t')
            val namedName = readNamespace(parts, 1, namedIndex)
            val intermediaryName = readNamespace(parts, 1, intermediaryIndex)
            if (namedName.isBlank() || intermediaryName.isBlank()) continue
            namedToIntermediary[namedName] = intermediaryName
            classAliases[intermediaryName.replace('/', '.')] = namedName.substringAfterLast('/').replace('$', '.')
        }

        fun remapDescriptorToIntermediary(descriptor: String): String {
            if (descriptor.isBlank()) return descriptor
            val classRef = Regex("L([^;]+);")
            return classRef.replace(descriptor) { match ->
                val namedInternalName = match.groupValues[1]
                val intermediaryInternalName = namedToIntermediary[namedInternalName] ?: namedInternalName
                "L$intermediaryInternalName;"
            }
        }

        fun storeAlias(
            target: MutableMap<String, String>,
            ambiguous: MutableSet<String>,
            key: String,
            alias: String
        ) {
            if (key.isBlank() || alias.isBlank() || ambiguous.contains(key)) return
            val existing = target[key]
            if (existing == null) {
                target[key] = alias
                return
            }
            if (existing != alias) {
                target.remove(key)
                ambiguous.add(key)
            }
        }

        val fieldAliases = linkedMapOf<String, String>()
        val methodAliases = linkedMapOf<String, String>()
        val ambiguousFieldKeys = linkedSetOf<String>()
        val ambiguousMethodKeys = linkedSetOf<String>()

        var currentOwner = ""
        for (raw in lines) {
            val trimmed = raw.trimStart('\t')
            if (trimmed.isBlank()) continue
            val parts = trimmed.split('\t')
            when (parts.firstOrNull()) {
                "c" -> {
                    currentOwner = readNamespace(parts, 1, intermediaryIndex).replace('/', '.')
                }

                "f" -> {
                    if (currentOwner.isBlank()) continue
                    val descriptor = if (parts.size > 1) remapDescriptorToIntermediary(parts[1]) else ""
                    val namedName = readNamespace(parts, 2, namedIndex)
                    val intermediaryName = readNamespace(parts, 2, intermediaryIndex)
                    if (namedName.isBlank() || intermediaryName.isBlank()) continue
                    storeAlias(fieldAliases, ambiguousFieldKeys, "$currentOwner#$intermediaryName#$descriptor", namedName)
                }

                "m" -> {
                    if (currentOwner.isBlank()) continue
                    val descriptor = if (parts.size > 1) remapDescriptorToIntermediary(parts[1]) else ""
                    val namedName = readNamespace(parts, 2, namedIndex)
                    val intermediaryName = readNamespace(parts, 2, intermediaryIndex)
                    if (namedName.isBlank() || intermediaryName.isBlank()) continue
                    if (namedName == "<init>" || namedName == "<clinit>") continue
                    storeAlias(methodAliases, ambiguousMethodKeys, "$currentOwner#$intermediaryName#$descriptor", namedName)
                }
            }
        }

        val authAliasFragments = listOf(
            "session", "accesstoken", "refreshtoken", "authtoken",
            "clientsession", "playersession", "publicsession"
        )
        fun isAuthAlias(alias: String): Boolean {
            val normalized = alias.lowercase().replace(Regex("[^a-z0-9]"), "")
            return authAliasFragments.any { normalized.contains(it) }
        }

        val filteredClassAliases = classAliases.filter { (_, alias) -> !isAuthAlias(alias) }
        val blockedOwners = classAliases.keys.filter { owner ->
            val alias = classAliases[owner] ?: return@filter false
            isAuthAlias(alias)
        }.toSet()

        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.printWriter(Charsets.UTF_8).use { out ->
            out.println("# Yarn deobfuscation mappings")
            filteredClassAliases.toSortedMap().forEach { (owner, alias) ->
                out.println("C\t$owner\t$alias")
            }
            fieldAliases.toSortedMap().forEach { (key, alias) ->
                val parts = key.split('#', limit = 3)
                if (parts.size == 3 && !blockedOwners.contains(parts[0]) && !isAuthAlias(alias)) {
                    out.println("F\t${parts[0]}\t${parts[1]}\t${parts[2]}\t$alias")
                }
            }
            methodAliases.toSortedMap().forEach { (key, alias) ->
                val parts = key.split('#', limit = 3)
                if (parts.size == 3 && !blockedOwners.contains(parts[0]) && !isAuthAlias(alias)) {
                    out.println("M\t${parts[0]}\t${parts[1]}\t${parts[2]}\t$alias")
                }
            }
        }
    }
}

val generateAutismPacketSchemas by tasks.registering {
    val minecraftVersion = libs.versions.minecraft.get()
    val outputFile = generatedAutismResourcesDir.map { it.file("autism-packet-schemas.tsv") }

    outputs.file(outputFile)

    doLast {
        fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()

        fun splitTopLevelComma(value: String): List<String> {
            val out = mutableListOf<String>()
            var depth = 0
            var start = 0
            for (i in value.indices) {
                when (value[i]) {
                    '<', '(', '[', '{' -> depth++
                    '>', ')', ']', '}' -> if (depth > 0) depth--
                    ',' -> if (depth == 0) {
                        out += value.substring(start, i).trim()
                        start = i + 1
                    }
                }
            }
            val tail = value.substring(start).trim()
            if (tail.isNotBlank()) out += tail
            return out
        }

        fun protocolFromPath(path: String): String {
            val normalized = path.replace('\\', '/')
            val marker = "/network/protocol/"
            val idx = normalized.indexOf(marker)
            val tail = if (idx >= 0) normalized.substring(idx + marker.length) else normalized.substringAfter("net/minecraft/network/protocol/", "")
            return tail.substringBefore('/').ifBlank { "unknown" }
        }

        fun directionFromName(simpleName: String): String = when {
            simpleName.startsWith("Clientbound") -> "S2C"
            simpleName.startsWith("Serverbound") -> "C2S"
            else -> "ANY"
        }

        fun kindForType(type: String): String {
            val lower = type.lowercase()
            return when {
                lower == "byte" || lower == "short" || lower == "int" || lower == "long" || lower == "float" || lower == "double" -> "number"
                lower == "boolean" -> "boolean"
                lower == "string" -> "string"
                lower.contains("itemstack") || lower.contains("hashedstack") -> "item"
                lower.contains("component") -> "component"
                lower.contains("identifier") || lower.contains("resourcekey") -> "identifier"
                lower.contains("holder<") || lower == "holder" -> "holder"
                lower.contains("blockpos") || lower.contains("chunkpos") -> "position"
                lower.contains("vec3") || lower.contains("positionmoverotation") -> "vector"
                lower.contains("uuid") -> "uuid"
                lower.startsWith("optional<") || lower.contains(".optional<") -> "optional"
                lower.startsWith("list<") || lower.startsWith("set<") || lower.contains("list<") || lower.contains("set<") -> "list"
                lower.startsWith("map<") || lower.contains("map<") || lower.contains("int2objectmap") -> "map"
                lower.contains("bitset") -> "bitset"
                lower.contains("enumset") || lower.contains("relative") || lower.contains("containerinput") -> "enum"
                else -> "object"
            }
        }

        fun editableFor(kind: String): Boolean = kind in setOf("number", "boolean", "string", "identifier", "enum", "uuid")

        fun parseComponent(raw: String): FieldSpec? {
            val cleaned = normalizeWhitespace(raw)
                .replace(Regex("^@[\\w.]+(?:\\([^)]*\\))?\\s+"), "")
            val idx = cleaned.lastIndexOf(' ')
            if (idx <= 0 || idx >= cleaned.length - 1) return null
            val type = cleaned.substring(0, idx).trim()
            val name = cleaned.substring(idx + 1).trim().removeSuffix("...")
            if (!name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) return null
            val kind = kindForType(type)
            return FieldSpec(name, type, kind, editableFor(kind))
        }

        fun parseFields(text: String): Pair<String, List<FieldSpec>> {
            val record = Regex("""public\s+record\s+\w+\s*\((.*?)\)\s*implements""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(text)
            if (record != null) {
                val fields = splitTopLevelComma(record.groupValues[1]).mapNotNull(::parseComponent)
                return "record" to fields
            }

            val fields = Regex("""(?m)^\s*(?:private|protected)\s+final\s+([^;=]+?)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*;""")
                .findAll(text)
                .mapNotNull { match ->
                    val type = normalizeWhitespace(match.groupValues[1])
                    val name = match.groupValues[2]
                    val kind = kindForType(type)
                    FieldSpec(name, type, kind, editableFor(kind))
                }
                .toList()
            return if (fields.isNotEmpty()) "fields" to fields else "fallback" to emptyList()
        }

        fun loadSources(): List<SourceFile> {
            val sourcesJar = file("${System.getProperty("user.home")}/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/$minecraftVersion/minecraft-merged-deobf-$minecraftVersion-sources.jar")
            val loomCacheSourceJar = file(".gradle/loom-cache/minecraftMaven/net/minecraft")
                .takeIf { it.isDirectory }
                ?.walkTopDown()
                ?.firstOrNull { file ->
                    file.isFile &&
                        file.name.endsWith("-$minecraftVersion-sources.jar") &&
                        file.invariantSeparatorsPath.contains("/minecraft-merged-")
                }
            val sourceArchive = when {
                sourcesJar.isFile -> sourcesJar
                loomCacheSourceJar != null -> loomCacheSourceJar
                else -> null
            }
            if (sourceArchive != null) {
                ZipFile(sourceArchive).use { zip ->
                    return zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .filter { it.name.startsWith("net/minecraft/network/protocol/") && it.name.endsWith("Packet.java") }
                        .map { entry ->
                            SourceFile(entry.name, zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() })
                        }
                        .toList()
                }
            }

            val localRoot = file("mc-src$minecraftVersion/libs/net.minecraft.minecraft-merged-deobf/net/minecraft/network/protocol")
            if (localRoot.isDirectory) {
                return localRoot.walkTopDown()
                    .filter { it.isFile && it.name.endsWith("Packet.java") }
                    .map { file ->
                        SourceFile(file.relativeTo(localRoot.parentFile.parentFile.parentFile.parentFile).invariantSeparatorsPath, file.readText(Charsets.UTF_8))
                    }
                    .toList()
            }

            logger.warn("Minecraft packet sources are unavailable for $minecraftVersion; generating an empty packet schema resource and using runtime fallback decoding.")
            return emptyList()
        }

        fun packetTypeOf(text: String): String {
            return Regex("""return\s+([A-Za-z0-9_]+PacketTypes\.[A-Z0-9_]+)\s*;""")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?: ""
        }

        fun codecStyleOf(text: String): String = when {
            text.contains("StreamCodec.unit") -> "UNIT"
            text.contains("StreamCodec.composite") -> "COMPOSITE"
            text.contains("Packet.codec") -> "PACKET_CODEC"
            else -> "CUSTOM"
        }

        fun escape(value: String): String = value
            .replace('\t', ' ')
            .replace('\n', ' ')
            .replace('|', '/')
            .replace('~', '-')

        val specs = loadSources().mapNotNull { source ->
            val pkg = Regex("""package\s+([A-Za-z0-9_.]+)\s*;""").find(source.text)?.groupValues?.get(1) ?: return@mapNotNull null
            val simpleName = Regex("""public\s+(?:abstract\s+)?(?:record|class)\s+([A-Za-z0-9_]+Packet)\b""")
                .find(source.text)
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull null
            val codecStyle = codecStyleOf(source.text)
            val parsed = parseFields(source.text)
            val sourceKind = if (codecStyle == "UNIT") "unit" else parsed.first
            val complete = codecStyle == "UNIT" || parsed.second.isNotEmpty()
            PacketSpec(
                "$pkg.$simpleName",
                protocolFromPath(source.path),
                directionFromName(simpleName),
                codecStyle,
                packetTypeOf(source.text),
                sourceKind,
                complete,
                if (codecStyle == "UNIT") emptyList() else parsed.second
            )
        }.sortedBy { it.className }

        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.printWriter(Charsets.UTF_8).use { out ->
            out.println("# Generated from Minecraft $minecraftVersion packet sources. Fields preserve source/record order where available.")
            out.println("# class\tprotocol\tdirection\tcodec\tpacketType\tsource\tcomplete\tfields(name~type~kind~editable|...)")
            for (spec in specs) {
                val fields = spec.fields.joinToString("|") { field ->
                    listOf(field.name, field.type, field.kind, field.editable.toString()).joinToString("~", transform = ::escape)
                }
                val columns = listOf(
                    spec.className,
                    spec.protocol,
                    spec.direction,
                    spec.codecStyle,
                    spec.packetType,
                    spec.source,
                    spec.complete.toString()
                ).joinToString("\t", transform = ::escape)
                out.println("$columns\t$fields")
            }
        }
    }
}

val shippedResourceExtensions = setOf("png", "mcmeta", "ttf", "json", "ogg", "fsh", "vsh", "svg", "bin")
val shippedResourceRootFiles = setOf("fabric.mod.json", "autism.mixins.json")

val verifyShippedResources by tasks.registering {
    group = "verification"
    description = "Fails the build if a non-asset file in src/main/resources would be packaged into the jar."
    val resourceRoot = layout.projectDirectory.dir("src/main/resources").asFile
    inputs.dir(resourceRoot)
    doLast {
        if (!resourceRoot.exists()) return@doLast
        val offenders = resourceRoot.walkTopDown().filter { it.isFile }.mapNotNull { file ->
            val relative = file.relativeTo(resourceRoot).invariantSeparatorsPath
            when {
                !relative.contains('/') ->
                    if (relative in shippedResourceRootFiles) null
                    else "$relative  (unexpected file at the resources root)"
                !relative.startsWith("assets/") ->
                    "$relative  (only assets/ is packaged)"
                file.extension.lowercase() !in shippedResourceExtensions ->
                    "$relative  (.${file.extension} is not an allowed asset type)"
                else -> null
            }
        }.toList()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "These files would be packaged into the jar:\n"
                    + offenders.joinToString("\n") { "  - $it" }
                    + "\n\nMove them out of src/main/resources, or add the extension to shippedResourceExtensions."
            )
        }
    }
}

tasks {
    processResources {
        dependsOn(verifyShippedResources)
        dependsOn(generateAutismInspectorMappings)
        dependsOn(generateAutismPacketSchemas)
        dependsOn(generateVanillaUiAssets)
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
            "fabric_api_version" to libs.versions.fabric.api.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        exclude("addon-template.mixins.json")
        exclude("assets/template/**")

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        // ModMenu API is a compile-only soft dependency: we ship local stubs of its two API
        // interfaces so we can compile the integration without a cross-version ModMenu artifact, but
        // we must NOT bundle them — at runtime the real ModMenu provides them (and if ModMenu is
        // absent, our integration entrypoint is simply never loaded).
        exclude("com/terraformersmc/**")
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
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
        options.compilerArgs.add("-Xlint:-restricted")
    }
}

// Publish the Loom-remapped jar to the local Maven repo so the standalone addon-template (and any
// third-party addon) can depend on it via `modImplementation("com.autismclient:autism:<version>")`.
// Run: ./gradlew publishToMavenLocal
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.autismclient"
            artifactId = "autism"
            version = project.version.toString()
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}

// Pin the templates' versions to this client's. Runs on publishToMavenLocal.
val syncAddonTemplateVersions by tasks.registering {
    group = "addon templates"
    description = "Pin addon-templates/*/gradle/libs.versions.toml to this client's versions."

    val clientCatalog = rootDir.resolve("gradle/libs.versions.toml")
    val templateCatalogs = listOf("minimal", "advanced").map {
        rootDir.resolve("addon-templates/$it/gradle/libs.versions.toml")
    }
    inputs.file(clientCatalog)
    outputs.files(templateCatalogs)

    doLast {
        val client = clientCatalog.readText()
        fun readVersion(key: String): String {
            val m = Regex("(?m)^\\s*" + Regex.escape(key) + "\\s*=\\s*\"([^\"]*)\"").find(client)
                ?: throw GradleException("Key '$key' not found in $clientCatalog")
            return m.groupValues[1]
        }
        // the template's own mod-version is left alone
        val values = linkedMapOf(
            "minecraft" to readVersion("minecraft"),
            "fabric-loader" to readVersion("fabric-loader"),
            "fabric-api" to readVersion("fabric-api"),
            "loom" to readVersion("loom"),
            "autism" to readVersion("mod-version")
        )
        templateCatalogs.forEach { file ->
            if (!file.exists()) throw GradleException("Missing template catalog: $file")
            var text = file.readText()
            values.forEach { (key, value) ->
                val re = Regex("(?m)^(\\s*" + Regex.escape(key) + "\\s*=\\s*\")[^\"]*(\")")
                if (re.containsMatchIn(text)) {
                    text = re.replace(text) { mr -> mr.groupValues[1] + value + mr.groupValues[2] }
                } else {
                    logger.warn("syncAddonTemplateVersions: '$key' not found in ${file.name}; skipped.")
                }
            }
            file.writeText(text)
        }
        logger.lifecycle(
            "Synced addon templates: minecraft=${values["minecraft"]}, fabric-loader=${values["fabric-loader"]}, " +
                "fabric-api=${values["fabric-api"]}, loom=${values["loom"]}, autism=${values["autism"]}"
        )
    }
}

tasks.named("publishToMavenLocal") {
    dependsOn(syncAddonTemplateVersions)
}

// Scaffold/scan tasks call addon-toolkit.py (these need Python 3). Building needs no Python.
fun resolvePythonCommand(): List<String> {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val candidates = if (isWindows) listOf(listOf("py", "-3"), listOf("python"), listOf("python3"))
                     else listOf(listOf("python3"), listOf("python"))
    for (candidate in candidates) {
        try {
            if (ProcessBuilder(candidate + "--version").redirectErrorStream(true).start().waitFor() == 0) return candidate
        } catch (_: Exception) {
        }
    }
    throw GradleException("Python 3 was not found on PATH (needed for the addon toolkit tasks).")
}

fun toolkitArgs(project: Project, action: String): List<String> {
    val args = mutableListOf(
        project.rootDir.resolve("addon-templates/addon-toolkit.py").absolutePath,
        action,
        "--non-interactive",
    )
    // gradleProperty, not findProperty: names like "project"/"name" clash with Project getters.
    fun prop(name: String) = project.providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
    args += listOf("--template", prop("template") ?: "minimal")
    mapOf(
        "name" to "--name", "out" to "--output", "project" to "--project", "addonId" to "--addon-id",
        "package" to "--package", "author" to "--author", "description" to "--description",
        "mavenGroup" to "--maven-group", "archiveName" to "--archive-name", "addonVersion" to "--addon-version",
    ).forEach { (propName, flag) -> prop(propName)?.let { args += listOf(flag, it) } }
    if (project.providers.gradleProperty("advanced").isPresent) args += "--advanced"
    if (project.providers.gradleProperty("yes").isPresent) args += "--yes"
    if (project.providers.gradleProperty("noPublish").isPresent) args += "--no-publish"
    return args
}

listOf(
    Triple("newAddon", "setup", "Scaffold a new addon. Props: -Pname= -Ptemplate=minimal|advanced -Pout= (optional -PaddonId/-Ppackage/-Pauthor/-Padvanced)."),
    Triple("scanAddon", "scan", "Validate one addon project. Prop: -Pproject=<path>."),
    Triple("validateAddons", "validate", "Validate the in-repo addon system and shipped templates."),
    Triple("cleanAddon", "clean", "Delete an addon project's build/.gradle. Props: -Pproject=<path> -Pyes."),
).forEach { (taskName, action, taskDescription) ->
    tasks.register(taskName) {
        group = "addon toolkit"
        description = taskDescription
        doLast {
            val command = resolvePythonCommand() + toolkitArgs(project, action)
            val process = ProcessBuilder(command).directory(project.rootDir).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
            val exit = process.waitFor()
            if (exit != 0) throw GradleException("addon toolkit '$action' failed (exit code $exit).")
        }
    }
}

// Pure Gradle, no Python: publish the API, then build each shipped template via its own wrapper.
tasks.register("buildAllTemplates") {
    group = "addon toolkit"
    description = "Publish the API locally and build both shipped templates (no Python needed)."
    dependsOn("publishToMavenLocal")
    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        for (name in listOf("minimal", "advanced")) {
            val dir = project.rootDir.resolve("addon-templates/$name")
            val wrapper = dir.resolve(if (isWindows) "gradlew.bat" else "gradlew")
            if (!wrapper.isFile) throw GradleException("Missing Gradle wrapper: $wrapper")
            val cmd = if (!isWindows && !wrapper.canExecute()) listOf("sh", wrapper.absolutePath, "build")
                      else listOf(wrapper.absolutePath, "build")
            logger.lifecycle("Building $name template")
            val process = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
            if (process.waitFor() != 0) throw GradleException("$name template build failed.")
        }
    }
}
