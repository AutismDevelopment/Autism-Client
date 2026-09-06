import java.io.DataInputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URL
import java.net.URLClassLoader
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.util.CheckClassAdapter

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.xmlgraphics:batik-transcoder:1.17")
        classpath("org.apache.xmlgraphics:batik-codec:1.17")
        // litePatchVariant bytecode surgery (Java 25 = class-file v69 needs ASM 9.8).
        classpath("org.ow2.asm:asm:9.8")
        classpath("org.ow2.asm:asm-tree:9.8")
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



// ---------------------------------------------------------------------------
// LITE variant: turns the FINAL release jar (post guardian weaving) into the stripped-down
// "AUTISM Client Lite" build in four stages, with no recompilation so the jars can never drift.
// The pipeline uses NO ProGuard and NO obfuscation tooling of any kind - every stage is our own
// code (ASM for bytecode surgery, plain zip + constant-pool analysis for shrinking):
//   litePatchVariant - copies the full jar, patches AutismLiteVariant.ENABLED to a compile-time
//                      constant true, and folds every literal enabled() gate with its own exact
//                      ASM pass (branch fold + dead-code sweep, CheckClassAdapter-verified).
//   liteShrinkJar    - our own reachability shrink: seeds (entrypoint, addon API, pinned classes,
//                      applied mixins) -> transitive constant-pool closure, then copies surviving
//                      classes WHOLE. Nothing external renames, specializes or rewrites bytecode;
//                      a kept class is byte-identical to the full jar's, a dropped one is simply
//                      absent. Also strips LocalVariableTables (stale after dead-code removal).
//   liteJar          - repackages the shrunk jar: stripped-feature assets out, stripped-mixin
//                      classes + json entries out, unreferenced nested libraries out (data-driven),
//                      marker resource + transformed mixin json + fabric.mod.json edits in.
//   verifyLiteJar    - fails the build on any regression: marker, pinned classes, asset/mixin
//                      leaks, known-dead classes that must NOT survive, size ceiling, a
//                      dangling-reference guard (the NoClassDefFoundError vector), macro/config
//                      parity, and an LVT guard.
// ---------------------------------------------------------------------------

val liteStripMixins = listOf(
    // Multi / POV pilot (the Multi system is stripped from lite)
    "AutismBotPlayerInfoMixin", "AutismBotPilotMixin", "AutismBotEquipmentMixin",
    "AutismPilotLocalPlayerRenderMixin", "AutismPilotChatMixin", "AutismPilotLocalInputMixin",
    "AutismPilotEditRerouteMixin", "AutismPilotHandItemMixin", "AutismMultiPovChatMixin",
    // title/menu-only (custom main menu is stripped from lite; splash mixins stay for panic cosmetics)
    "AutismTitleScreenSupportMixin", "AutismGuiRendererPanoramaMixin",
    "AutismPanoramaOverlayMixin", "AutismLogoRendererMixin",
    // cheat-module-only mixins whose module classes are physically excluded from the lite jar
    // (verified: nothing kept in lite references them; the lithium entries need their package
    // prefix so the json line filter matches)
    "AutismCrystalViewModelMixin", "AutismCrystalViewRendererMixin", "AutismPlayerNoPhysicsMixin",
    "AutismBlockCollisionsMixin", "lithium.AutismLithiumSweeperBlockPosMixin",
    "lithium.AutismLithiumSweeperVoxelShapeMixin",
    // stripped-module render mixins: their only writers (NoRender/Viewmodel/Chams modules) are all
    // physically excluded, so every injection is pure dead weight in lite. AutismNoRenderTotemMixin
    // stays - it is also the hook for AutoTotem's kept No Render setting. The NoRenderState /
    // ViewmodelState / chams state classes stay pinned (referenced by kept mixins).
    "AutismNoRenderHudMixin", "AutismNoRenderNauseaMixin", "AutismNoRenderHurtcamMixin",
    "AutismNoRenderClientLevelMixin", "AutismNoRenderScreenEffectMixin",
    "AutismNoRenderBossHealthOverlayMixin", "AutismNoRenderScreenMixin",
    "AutismNoRenderEatParticleMixin", "AutismNoRenderFogMixin", "AutismNoRenderFogEnvMixin",
    "AutismNoRenderWeatherMixin", "AutismNoRenderWeatherStateMixin", "AutismNoRenderSkyMixin",
    "AutismNoRenderCloudMixin", "AutismNoRenderTimeMixin", "AutismNoRenderWorldBorderMixin",
    "AutismNoRenderBlockBreakMixin", "AutismNoRenderBeaconMixin", "AutismNoRenderEnchantTableMixin",
    "AutismNoRenderSignMixin", "AutismNoRenderMapMixin", "AutismNoRenderBannerMixin",
    "AutismNoRenderParticleMixin", "AutismNoRenderBlockSeedMixin", "AutismNoRenderBlockOffsetMixin",
    "AutismNoRenderBlockEntityMixin", "AutismNoRenderArmorLayerMixin", "AutismNoRenderHeadLayerMixin",
    "AutismNoRenderEntityFlagsMixin", "AutismNoRenderSpawnerMixin", "AutismNoRenderDeadEntityMixin",
    "AutismNoRenderEntityRendererMixin", "AutismNoRenderSpawnPacketMixin", "AutismNoRenderGlintMixin",
    "AutismNoRenderObfuscationMixin",
    "AutismViewmodelMixin", "AutismViewmodelSwingMixin", "AutismViewmodelStrideMixin",
    "AutismChamsLivingEntityMixin", "AutismChamsSubmitMixin", "AutismChamsCapeLayerMixin",
    "AutismEntityRenderStateChamsMixin"
)

// The shrink (liteShrinkJar) removes dead classes on its own once the gates fold; the mixin
// class-file exclusions in liteJar below stay as belt-and-braces so a keep-rule mistake cannot
// leave a json-stripped mixin behind silently (verifyLiteJar checks both sides).

// Assets excluded from the lite jar (~10.7 MB): every one of them is only referenced by
// stripped/mixin-stripped code or by paths the lite gates close (loading overlay, welcome, title).
val liteStripAssets = listOf(
    "assets/autismclient/textures/gui/title/background/",
    "assets/autismclient/captcha/",
    "assets/autismclient/sounds.json", "assets/autismclient/sounds/",
    "assets/autismclient/icons/window/",
    "assets/autismclient/textures/gui/title/loading_logo.png",
    "assets/autismclient/textures/gui/title/autism_client_logo.png",
    "assets/autismclient/textures/gui/title/autism_client_logo.png.mcmeta",
    "assets/autismclient/textures/gui/title/button_text/",
    "assets/autismclient/textures/gui/title/icons/essential.png",
    "assets/autismclient/textures/gui/title/icons/modmenu.png",
    "assets/autismclient/textures/gui/title/icons/discord.png",
    "assets/autismclient/textures/gui/title/icons/accessibility.png",
    "assets/autismclient/textures/gui/title/icons/language.png",
    // donate.png + autismclient_welcome.png STAY: the first-run donate dialog shows in lite too.
    "assets/autismclient/textures/gui/hud/autismclient.png",
    "assets/autismclient/textures/gui/hud/autismclient_hud.png",
    "assets/autismclient/textures/gui/hud/autismclient.svg",
    "assets/autismclient/textures/gui/autism/",
    "assets/autismclient/shaders/core/",
    "assets/autismclient/textures/gui/accounts/share.png",
    "assets/autismclient/textures/gui/vanillaui/icons/matchmaking.png",
    "assets/autismclient/textures/gui/vanillaui/icons/multi.png",
    "assets/autismclient/textures/gui/vanillaui/icons/profiles.png",
    "assets/autismclient/textures/gui/vanillaui/icons/mainmenucategory.png",
    "assets/autismclient/textures/gui/vanillaui/icons/chatcategory.png",
    "assets/autismclient/textures/gui/icons/chevron_left.png",
    "META-INF/services/autismclient.util.mm.guardian.Guardian"
)

// Nested libraries are stripped DATA-DRIVEN by liteJar: any bundled lib with zero references from
// surviving classes (paho once the mm fold lands, etc.) is excluded and its fabric.mod.json
// declaration removed. liteKeepLibs below pins the ones that must always stay.

// The pinned must-stay list: classes the audits proved are referenced by lite-executed code
// (always-applied mixins, per-frame/per-tick paths, eager superinterfaces, config save paths).
// verifyLiteJar fails the build if any of these is missing from the lite jar - that is what makes
// an over-aggressive strip impossible to ship.
val liteKeepPinned = listOf(
    "autismclient/modules/Module.class", "autismclient/modules/ModuleCategory.class",
    "autismclient/modules/ModuleRegistry.class", "autismclient/modules/BuiltinModules.class",
    "autismclient/modules/BuiltinModules\$HideModule.class", "autismclient/modules/AutismModule.class",
    "autismclient/modules/PackHideState.class", "autismclient/modules/PackFreecamState.class",
    "autismclient/modules/AutoTotemModule.class", "autismclient/modules/AutoArmorModule.class",
    "autismclient/modules/BedDefenderModule.class", "autismclient/modules/SafeWalkModule.class",
    "autismclient/modules/KillAuraModule.class", "autismclient/modules/FreeLookModule.class",
    "autismclient/modules/GhostBlockModule.class",
    "autismclient/modules/TeamsModule.class", "autismclient/modules/TpClickModule.class",
    "autismclient/modules/HoleEspModule.class", "autismclient/modules/ScaffoldModule.class",
    "autismclient/modules/ModuleOreSim.class", "autismclient/modules/TrajectoriesModule.class",
    // ModuleEspMesh is hit from StorageSnapshot.EMPTY's static initializer - any static access to
    // ModuleWorldRenderer would fail class init in lite without it (bootstrap + per-frame tracers).
    "autismclient/modules/ModuleEspMesh.class",
    "autismclient/modules/AirPlaceModule.class", "autismclient/modules/InventoryTweaksModule.class",
    "autismclient/modules/GoldenLeverModule.class", "autismclient/modules/NameCensorModule.class",
    "autismclient/modules/AntiVanishModule.class", "autismclient/modules/AutoSignModule.class",
    "autismclient/modules/AutoLoginModule.class", "autismclient/modules/AntiHungerModule.class",
    "autismclient/modules/BoatFlyModule.class", "autismclient/modules/EntityControlModule.class",
    "autismclient/modules/AirJumpModule.class", "autismclient/modules/AutismAntiBot.class",
    "autismclient/modules/BuiltinModules\$SneakModule.class", "autismclient/modules/BuiltinModules\$FastBreakModule.class",
    "autismclient/modules/BuiltinModules\$FlightModule.class", "autismclient/modules/BuiltinModules\$SprintModule.class",
    "autismclient/modules/BuiltinModules\$SpeedModule.class",
    "autismclient/gui/screen/AutismPanicTitleScreen.class", "autismclient/gui/screen/AutismVoiceChatPromptScreen.class",
    "autismclient/util/multi/PacketTeleportController.class", "autismclient/util/multi/MultiProxyVerifier.class",
    "autismclient/util/multi/MultiManager.class", "autismclient/util/multi/MultiSession.class",
    "autismclient/util/multi/MultiProfile.class",
    "autismclient/util/multi/MultiPacketPolicy.class", "autismclient/util/multi/MultiAutoAccept.class",
    "autismclient/util/multi/MultiQuickAction.class", "autismclient/util/multi/MultiTakeoverState.class",
    "autismclient/util/multi/MultiPilot.class", "autismclient/util/multi/MultiPilotTruth.class",
    "autismclient/util/multi/MultiPovModuleController.class", "autismclient/util/multi/MultiConnectionMarker.class",
    "autismclient/util/multi/MultiConnectionContext.class",
    "autismclient/util/AutismTheme.class",
    "autismclient/util/AutismThemeTextures.class", "autismclient/util/AutismFabricatorOverlay.class",
    // AutismMarquee is hit from AutismHudManager's own static initializer (spotifyTextCacheKey) -
    // stripping it crashed class init on the first screen click in lite.
    "autismclient/util/AutismMarquee.class",
    "autismclient/util/AutismSvgHudLogo.class"
)

val liteKeepLibs = listOf(
    "META-INF/jars/mixinextras-fabric-0.5.4.jar",
    "META-INF/jars/netty-codec-socks-4.1.118.Final.jar",
    "META-INF/jars/netty-handler-proxy-4.1.118.Final.jar",
    "META-INF/jars/waybackauthlib-1.1.0.jar",
    "META-INF/jars/jsvg-2.1.0.jar"
)

// The inverse of the pin list: classes/libs that the fold + shrink MUST remove. If any of these
// is present in the lite jar, a lite gate lost its literal call-site form (or a new ungated
// referrer appeared) and the shrink silently kept a stripped feature alive - fail the build.
// NOTE: dead modules whose ONLY anchor was the old override rule (CrystalAura, AnchorAura,
// Surround, ...) now die entirely - our reachability walk has no such rule, and after their
// lite-gated bodies fold nothing references them. The multi-macro interpreter core
// (MultiMacroRun/MultiMacroHost) is different: MultiSession.macroRun's field descriptor anchors
// it from lite-live engine code (paced-teleport validation); re-typing that field is a deep
// engine refactor deliberately not done for ~0.3 MB. Not listed here by design.
// Macro parity exemption: the ONLY macro-package classes allowed to exist in the full jar but
// not in lite (the guard below fails the build on any other). Each entry must carry its proof.
// Adding a class here is a deliberate "this does not belong in lite" decision - never use it to
// silence a real leak.
val liteMacroParityExempt = listOf(
    // Constructed only from registry paths that are themselves dead in lite (the WaitGui action
    // type is unconstructable even in the full jar; entity conditions run through a different,
    // lite-live path - nothing alive constructs these). The member pass correctly removes them.
    "autismclient/util/macro/MacroConditionRegistry\$EntityCondition.class",
    "autismclient/util/macro/MacroConditionRegistry\$GuiCloseCondition.class",
    // Referenced only by the stripped AutoFish module/cluster and the dead module menu.
    "autismclient/util/macro/MacroConditionUtil.class",
    // Thrown/caught only from paths that are dead in lite (never from lite-live bindings code).
    "autismclient/util/macro/MacroDynamicBindings\$MissingDynamicValueException.class"
)

val liteKnownDead = listOf(
    "autismclient/modules/BuiltinModules\$ParkourModule.class",
    "autismclient/modules/BuiltinModules\$AdminToolsModule.class",
    "autismclient/commands/impl/IrcCommand.class",
    "autismclient/gui/mm/MatchmakingPanel.class",
    "autismclient/gui/screen/AutismTitleScreen.class",
    "autismclient/gui/screen/AutismModuleScreen.class",
    "autismclient/util/mm/MatchmakingManager.class",
    "autismclient/util/mm/relay/MqttRelay.class",
    "autismclient/util/AutismProfileManager.class",
    "autismclient/util/AutismAdminToolsOverlay.class",
    "META-INF/jars/org.eclipse.paho.client.mqttv3-1.2.5.jar"
)

// ---------------------------------------------------------------------------
// Member-level reachability (phase 2 of the shrink): drops unused methods and fields from kept
// classes, so a dead feature's members stop anchoring its classes (descriptor refs and method
// bodies are what kept DupeRadar/Spotify/geo lookup alive in lite-live classes). Conservative by
// design - over-keep beats under-keep every time:
//   WHOLESALE classes (kept complete): mixin seeds + nested, api.**, the entrypoint, enums,
//   records, interfaces, annotations, and the gson config models (AutismConfig+nesteds,
//   AutismPacketPreset, ServerPluginScanCache$CacheFile) - framework contracts and reflective
//   models are never pruned.
//   SEEDS for the call walk: every method of wholesale classes, every <clinit>, and every no-arg
//   <init> (gson/reflection instantiation safety).
//   WALK: a kept method keeps every method it calls (JVM lookup: owner, then supertypes), every
//   field it touches, every invokedynamic-referenced method, plus every same-signature method in
//   kept supertypes AND kept subtypes (override safety). Synthetic/bridge methods are always
//   kept - dropping those is where subtle dispatch bugs live.
// verifyLiteJar's method-level dangling guard re-proves the closure (every kept call site
// resolves), so a walk bug fails the build instead of the game.
// ---------------------------------------------------------------------------
private data class LiteMember(val owner: String, val name: String, val desc: String)

// Library-callback contracts: methods the JVM or library code calls where NO call site exists in
// our bytecode (string concat invokes toString virtually, HashMap invokes equals/hashCode,
// sort executors invoke run/call/compare/get/apply/accept/test, resource blocks invoke close,
// iteration invokes iterator). Pruning one of these is an AbstractMethodError at runtime with no
// dangling call site to catch it (verified in-game: a codec's toString died exactly so).
private val liteCallbackContracts = setOf(
    "toString()Ljava/lang/String;", "equals(Ljava/lang/Object;)Z", "hashCode()I",
    "clone()Ljava/lang/Object;", "finalize()V",
    "run()V", "call()Ljava/lang/Object;", "get()Ljava/lang/Object;",
    "accept(Ljava/lang/Object;)V", "apply(Ljava/lang/Object;)Ljava/lang/Object;",
    "test(Ljava/lang/Object;)Z", "compareTo(Ljava/lang/Object;)I",
    "compare(Ljava/lang/Object;Ljava/lang/Object;)I",
    "iterator()Ljava/util/Iterator;", "close()V"
)

fun litePruneMembers(classBytes: Map<String, ByteArray>, reachable: Set<String>, mixinSeeds: Set<String>, classSeeds: Set<String>): Map<String, ByteArray> {
    val nodes = LinkedHashMap<String, ClassNode>()
    for (name in reachable) {
        val cn = ClassNode()
        ClassReader(classBytes.getValue(name)).accept(cn, 0)
        nodes[name] = cn
    }

    val superCache = HashMap<String, List<String>>()
    fun allSupers(name: String): List<String> = superCache.getOrPut(name) {
        val out = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        fun add(c: String?) {
            if (c != null && c in nodes && out.add(c)) queue.addLast(c)
        }
        add(nodes[name]?.superName)
        nodes[name]?.interfaces?.forEach(::add)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            add(nodes[c]?.superName)
            nodes[c]?.interfaces?.forEach(::add)
        }
        out.toList()
    }
    // Reverse map: supertype -> kept subtypes (for the override closure).
    val subtypesOf = HashMap<String, MutableList<String>>()
    for (name in nodes.keys) {
        for (s in allSupers(name)) subtypesOf.getOrPut(s) { mutableListOf() }.add(name)
    }

    val wholesale = HashSet<String>()
    for ((name, cn) in nodes) {
        if (mixinSeeds.any { name == it || name.startsWith("$it\$") }) { wholesale += name; continue }
        if (name.startsWith("autismclient/api/")) { wholesale += name; continue }
        if (name == "autismclient/AutismClientMod" || name == "autismclient/util/AutismLiteVariant") { wholesale += name; continue }
        if (name.startsWith("autismclient/util/AutismConfig")) { wholesale += name; continue }
        if (name == "autismclient/util/AutismPacketPreset" ||
            name == "autismclient/util/ServerPluginScanCache\$CacheFile" ||
            name == "autismclient/util/AutismWaypoints\$Waypoint" ||
            name == "autismclient/util/AutismPresetManager\$PresetEntry" ||
            name == "autismclient/util/AutismPayloadJsonSupport\$EncodedPayload") { wholesale += name; continue }
        if ((cn.access and (Opcodes.ACC_ENUM or Opcodes.ACC_INTERFACE or Opcodes.ACC_ANNOTATION)) != 0) {
            wholesale += name
        }
    }

    val keptMethods = HashSet<LiteMember>()
    val keptFields = HashSet<LiteMember>()
    val methodQueue = ArrayDeque<LiteMember>()

    fun findMethod(owner: String, name: String, desc: String): LiteMember? {
        nodes[owner]?.methods?.forEach { if (it.name == name && it.desc == desc) return LiteMember(owner, name, desc) }
        for (s in allSupers(owner)) {
            nodes[s]?.methods?.forEach { if (it.name == name && it.desc == desc) return LiteMember(s, name, desc) }
        }
        return null
    }
    fun keepMethod(m: LiteMember) {
        if (keptMethods.add(m)) methodQueue.addLast(m)
    }
    fun keepField(owner: String, name: String, desc: String) {
        if (owner in nodes) keptFields += LiteMember(owner, name, desc)
        else for (s in allSupers(owner)) if (s in nodes) { keptFields += LiteMember(s, name, desc); return }
    }

    // Framework-callback classes: a class rooted in a type OUTSIDE the jar (a Minecraft Screen,
    // a netty ChannelDuplexHandler, a brigadier ArgumentType, ClassValue, LinkedHashMap, records,
    // ...) can be called back virtually by that framework with no call site visible in our
    // bytecode, so nothing in the walk would ever reach such a method. Seed ALL of their methods
    // (exactly like wholesale) so the walk traverses INTO them and keeps everything they call.
    // (Verified in-game twice: pruned Screen.init/tick, ChannelDuplexHandler.channelRead/write,
    // ClassValue.computeValue and brigadier ArgumentType.parse were silent breakage; then a kept
    // but unwalked codec decode() lost its callees - NoSuchMethodError at startup.)
    val externalRooted = HashSet<String>()
    for (name in nodes.keys) {
        val visited = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue.addLast(name)
        var external = false
        while (queue.isNotEmpty() && !external) {
            val cn = nodes[queue.removeFirst()] ?: continue
            val supers = listOfNotNull(cn.superName) + cn.interfaces.filterIsInstance<String>()
            for (s in supers) {
                if (s !in nodes) {
                    if (s != "java/lang/Object") { external = true; break }
                } else if (visited.add(s)) {
                    queue.addLast(s)
                }
            }
        }
        if (external) externalRooted += name
    }

    for ((name, cn) in nodes) {
        for (mn in cn.methods) {
            if (name in wholesale || name in externalRooted ||
                mn.name == "<clinit>" || (mn.name == "<init>" && mn.desc == "()V")) {
                keepMethod(LiteMember(name, mn.name, mn.desc))
            }
        }
    }

    while (methodQueue.isNotEmpty()) {
        val cur = methodQueue.removeFirst()
        val cn = nodes[cur.owner] ?: continue
        val mn = cn.methods.firstOrNull { it.name == cur.name && it.desc == cur.desc } ?: continue
        for (insn in mn.instructions) {
            when (insn) {
                is org.objectweb.asm.tree.MethodInsnNode -> findMethod(insn.owner, insn.name, insn.desc)?.let(::keepMethod)
                is org.objectweb.asm.tree.FieldInsnNode -> keepField(insn.owner, insn.name, insn.desc)
                is org.objectweb.asm.tree.InvokeDynamicInsnNode -> {
                    fun handle(h: org.objectweb.asm.Handle?) {
                        if (h != null) findMethod(h.owner, h.name, h.desc)?.let(::keepMethod)
                    }
                    handle(insn.bsm)
                    for (arg in insn.bsmArgs) if (arg is org.objectweb.asm.Handle) handle(arg)
                }
            }
        }
        // Override closure: same signature in every kept supertype and every kept subtype.
        for (s in allSupers(cur.owner)) {
            nodes[s]?.methods?.forEach { if (it.name == cur.name && it.desc == cur.desc) keepMethod(LiteMember(s, it.name, it.desc)) }
        }
        subtypesOf[cur.owner]?.forEach { sub ->
            nodes[sub]?.methods?.forEach { if (it.name == cur.name && it.desc == cur.desc) keepMethod(LiteMember(sub, it.name, it.desc)) }
        }
    }

    // Prune: wholesale classes ship whole; external-rooted classes keep all methods (their fields
    // are still prunable - nothing dispatches to a field virtually); others keep only the walked
    // members. Synthetic methods are NOT exempted - they are kept only when walked-to (a kept
    // invokedynamic keeps its lambda; everything else that is never called is dead, bridge
    // dispatch falls back to the override).
    val prunedBytes = LinkedHashMap<String, ByteArray>()
    for ((name, cn) in nodes) {
        if (name !in wholesale) {
            if (name !in externalRooted) {
                cn.methods.removeIf { mn ->
                    mn.name != "<clinit>" &&
                    (mn.name + mn.desc) !in liteCallbackContracts &&
                    LiteMember(name, mn.name, mn.desc) !in keptMethods
                }
            }
            cn.fields.removeIf { fn -> LiteMember(name, fn.name, fn.desc) !in keptFields }
        }
        val cw = ClassWriter(0)
        cn.accept(cw)
        prunedBytes[name] = cw.toByteArray()
    }

    // Final class-level closure over the pruned bytes: classes only reachable through pruned
    // members (dead feature methods) drop out too. Seeds = the class-pass seeds + wholesale.
    val finalReachable = LinkedHashSet<String>()
    classSeeds.filter { it in prunedBytes }.forEach { finalReachable += it }
    wholesale.filter { it in prunedBytes }.forEach { finalReachable += it }
    val queue = ArrayDeque(finalReachable)
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        val bytes = prunedBytes[c] ?: continue
        for (r in liteReferencedClasses(bytes)) {
            if (r in prunedBytes && finalReachable.add(r)) queue.addLast(r)
        }
    }

    return prunedBytes.filterKeys { it in finalReachable }
}

// ---------------------------------------------------------------------------
// Shared constant-pool scan: every class a .class file references (CONSTANT_Class entries plus
// class names inside field/method descriptor Utf8s - both are JVM verifier-load vectors). Used by
// liteJar's data-driven nested-lib strip and by verifyLiteJar's dangling-reference guard.
// Over-approximation is intentional: a false hit fails the build loudly, never ships a dangle.
// ---------------------------------------------------------------------------
val liteDescRef = Regex("L([A-Za-z0-9_/\$]+);")

// ---------------------------------------------------------------------------
// Usage-precise class reference scan: every class a .class file references through CODE and
// descriptors (supertypes, field/method signatures incl. generics, instructions, exception
// handlers, stack-map frames, annotations) - which is also exactly what the JVM verifier may
// load while linking the class. The InnerClasses and EnclosingMethod attributes are deliberately
// NOT counted: they are reflection metadata that the JVM never eagerly loads, and counting them
// would anchor every nested class (e.g. all BuiltinModules$* modules) through its outer class
// even when nothing uses it. Used by liteShrinkJar's reachability walk, liteJar's data-driven
// nested-lib strip and verifyLiteJar's dangling-reference guard.
// ---------------------------------------------------------------------------
fun liteReferencedClasses(bytes: ByteArray): Set<String> {
    val refs = HashSet<String>()
    val cn = ClassNode()
    ClassReader(bytes).accept(cn, org.objectweb.asm.ClassReader.EXPAND_FRAMES)
    fun addDesc(desc: String?) {
        if (desc == null) return
        liteDescRef.findAll(desc).forEach { refs += it.groupValues[1] }
    }
    fun addType(t: org.objectweb.asm.Type?) {
        if (t != null) addDesc(t.descriptor)
    }
    cn.superName?.let { refs += it }
    refs += cn.interfaces
    addDesc(cn.signature)
    cn.visibleAnnotations?.forEach { addDesc(it.desc) }
    cn.invisibleAnnotations?.forEach { addDesc(it.desc) }
    for (f in cn.fields) {
        addDesc(f.desc)
        addDesc(f.signature)
        f.visibleAnnotations?.forEach { addDesc(it.desc) }
        f.invisibleAnnotations?.forEach { addDesc(it.desc) }
    }
    fun addHandle(h: org.objectweb.asm.Handle?) {
        if (h == null) return
        refs += h.owner
        addDesc(h.desc)
    }
    for (m in cn.methods) {
        addDesc(m.desc)
        addDesc(m.signature)
        m.exceptions?.forEach { refs += it }
        m.visibleAnnotations?.forEach { addDesc(it.desc) }
        m.invisibleAnnotations?.forEach { addDesc(it.desc) }
        m.tryCatchBlocks?.forEach { tcb -> tcb.type?.let { refs += it } }
        for (insn in m.instructions) {
            when (insn) {
                is org.objectweb.asm.tree.TypeInsnNode -> addDesc(insn.desc)
                is org.objectweb.asm.tree.MethodInsnNode -> { refs += insn.owner; addDesc(insn.desc) }
                is org.objectweb.asm.tree.FieldInsnNode -> { refs += insn.owner; addDesc(insn.desc) }
                is org.objectweb.asm.tree.MultiANewArrayInsnNode -> addDesc(insn.desc)
                is org.objectweb.asm.tree.LdcInsnNode -> if (insn.cst is org.objectweb.asm.Type) addType(insn.cst as org.objectweb.asm.Type)
                is org.objectweb.asm.tree.InvokeDynamicInsnNode -> {
                    addDesc(insn.desc)
                    addHandle(insn.bsm)
                    for (arg in insn.bsmArgs) {
                        when (arg) {
                            is org.objectweb.asm.Handle -> addHandle(arg)
                            is org.objectweb.asm.Type -> addType(arg)
                        }
                    }
                }
                is org.objectweb.asm.tree.FrameNode -> {
                    insn.local?.forEach { if (it is String) refs += it }
                    insn.stack?.forEach { if (it is String) refs += it }
                }
            }
        }
    }
    return refs
}

// Naive byte-substring scan (gate pre-filter for litePatchVariant).
fun liteContainsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
    if (needle.isEmpty() || haystack.size < needle.size) return false
    outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
        return true
    }
    return false
}

fun liteClassUniverse(zip: ZipFile): Set<String> {    val out = HashSet<String>()
    zip.entries().asSequence().forEach { e ->
        when {
            e.name.endsWith(".class") -> out += e.name.removeSuffix(".class")
            e.name.startsWith("META-INF/jars/") && e.name.endsWith(".jar") -> {
                ZipInputStream(zip.getInputStream(e)).use { inner ->
                    generateSequence { inner.nextEntry }.forEach { ie ->
                        if (ie.name.endsWith(".class")) out += ie.name.removeSuffix(".class")
                    }
                }
            }
        }
    }
    return out
}

// Bytecode surgery for litePatchVariant: ENABLED gets a ConstantValue=true attribute (so the gate
// fold sees a compile-time constant at every literal enabled() call site), compute() shrinks to
// `return true`, and the static initializer (its only job was compute()) empties out. Fails
// loudly if the class shape ever drifts - a silent no-patch would ship an unshrunk lite jar.
fun litePatchVariantClass(bytes: ByteArray): ByteArray {
    val cn = ClassNode()
    ClassReader(bytes).accept(cn, 0)
    var enabledPatched = false
    var computePatched = false
    for (f in cn.fields) {
        if (f.name == "ENABLED" && f.desc == "Z") {
            f.value = 1 // ConstantValue: boolean true
            enabledPatched = true
        }
    }
    for (m in cn.methods) {
        when (m.name) {
            "compute" -> {
                val ins = InsnList()
                ins.add(InsnNode(Opcodes.ICONST_1))
                ins.add(InsnNode(Opcodes.IRETURN))
                m.instructions = ins
                m.tryCatchBlocks?.clear()
                m.localVariables?.clear()
                computePatched = true
            }
            "<clinit>" -> {
                val ins = InsnList()
                ins.add(InsnNode(Opcodes.RETURN))
                m.instructions = ins
                m.tryCatchBlocks?.clear()
                m.localVariables?.clear()
            }
        }
    }
    require(enabledPatched && computePatched) {
        "litePatchVariant: AutismLiteVariant shape drifted (ENABLED/compute not found)"
    }
    val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
    cn.accept(cw)
    return cw.toByteArray()
}

// Dead-code elimination can leave a STALE LocalVariableTable behind (entries whose slots no
// longer match the rewritten instruction stream) - and the JVM validates LVTs at class load
// (ClassFormatError, in-game). Local variable tables are optional debug info, so every class in
// the shrunk jar gets them stripped deterministically instead of trusting any rewriter. Line
// numbers stay.
fun liteStripLocalVars(bytes: ByteArray): ByteArray {
    val cn = ClassNode()
    ClassReader(bytes).accept(cn, 0)
    var touched = false
    for (m in cn.methods) {
        if (!m.localVariables.isNullOrEmpty()) {
            m.localVariables = null
            touched = true
        }
    }
    if (!touched) return bytes
    val cw = ClassWriter(0) // frames copied verbatim; we only deleted an attribute
    cn.accept(cw)
    return cw.toByteArray()
}

// ---------------------------------------------------------------------------
// LITE gate fold (our own bytecode transform - the ONLY optimization in the pipeline; there is
// no ProGuard or any other external shrink/obfuscation tool anywhere). It replaces what an
// optimizer would do with the exact, minimal transform the lite shrink needs:
//   [invokestatic AutismLiteVariant.enabled()Z] [ifeq L]  -> deleted (never jumps, fall through)
//   [invokestatic AutismLiteVariant.enabled()Z] [ifne L]  -> [goto L]
//   [invokestatic enabled()Z] [istore n]                  -> [iconst_1][istore n] (hoisted boolean:
//     the slot now provably holds constant 1; same stack shape, so frames stay valid)
//   [iload n][ifeq|ifne L] on such a proven slot          -> folded like the direct shape, but
//     ONLY when no LabelNode (merge point), no MethodInsnNode, and no rewrite of slot n sits
//     between the store and this load - otherwise the load is left alone
// then a mark-and-sweep dead-code pass removes the instructions the fold made unreachable, so
// their constant-pool references disappear and liteShrinkJar's reachability walk can drop the
// dead classes. Any gate shape it does not recognize is simply left alone (feature survives -
// safe, never broken).
// ---------------------------------------------------------------------------

fun liteCommonSuper(a: String, b: String, cl: ClassLoader): String {
    return try {
        val ca = Class.forName(a.replace('/', '.'), false, cl)
        val cb = Class.forName(b.replace('/', '.'), false, cl)
        when {
            ca.isAssignableFrom(cb) -> a
            cb.isAssignableFrom(ca) -> b
            ca.isInterface || cb.isInterface -> "java/lang/Object"
            else -> {
                var c: Class<*> = ca
                while (!c.isAssignableFrom(cb)) c = c.superclass ?: return "java/lang/Object"
                c.name.replace('.', '/')
            }
        }
    } catch (t: Throwable) {
        "java/lang/Object"
    }
}

// Mark-and-sweep DCE over a single method's instruction list. LabelNodes are never removed (so
// line numbers and exception ranges stay structurally sound); exception ranges whose try region
// became empty are dropped. Handler blocks are rooted conservatively (kept even when their try
// region is dead) - at worst a few dead refs survive, never a broken class.
fun liteDeadCodeEliminate(mn: MethodNode) {
    val insns = mn.instructions
    val reachable = IdentityHashMap<AbstractInsnNode, Boolean>()
    val work = ArrayDeque<AbstractInsnNode>()
    fun mark(i: AbstractInsnNode?) {
        if (i != null && reachable.put(i, true) == null) work.add(i)
    }
    mark(insns.first)
    for (tcb in mn.tryCatchBlocks) mark(tcb.handler)
    while (work.isNotEmpty()) {
        val i = work.removeFirst()
        when (i) {
            is JumpInsnNode -> {
                if (i.opcode != Opcodes.GOTO) mark(i.next)
                mark(i.label)
            }
            is TableSwitchInsnNode -> { mark(i.dflt); i.labels.forEach(::mark) }
            is LookupSwitchInsnNode -> { mark(i.dflt); i.labels.forEach(::mark) }
            else -> when (i.opcode) {
                Opcodes.RETURN, Opcodes.IRETURN, Opcodes.LRETURN,
                Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN, Opcodes.ATHROW -> {}
                else -> mark(i.next)
            }
        }
    }
    var p = insns.first
    while (p != null) {
        val next = p.next
        if (p.opcode >= 0 && !reachable.containsKey(p)) insns.remove(p)
        p = next
    }
    mn.tryCatchBlocks.removeIf { tcb ->
        var q: AbstractInsnNode? = tcb.start.next
        var hasCode = false
        while (q != null && q !== tcb.end) {
            if (q.opcode >= 0) { hasCode = true; break }
            q = q.next
        }
        !hasCode
    }
    // Removed instructions leave stale local-variable slots behind; drop the table (the JVM
    // validates it at class load - stale LVTs are a ClassFormatError at runtime).
    mn.localVariables = null
}

// Folds every literal enabled() gate in a class. Returns the rewritten bytes, or null when the
// class contains no foldable gate (caller copies the original bytes through unchanged).
fun liteFoldGates(bytes: ByteArray, cl: ClassLoader): ByteArray? {
    val cn = ClassNode()
    ClassReader(bytes).accept(cn, 0)
    var changed = false
    for (mn in cn.methods) {
        var methodChanged = false
        val insns = mn.instructions
        // Hoisted gates: `boolean x = enabled();` - after Pattern A the slot provably holds
        // constant 1, and Pattern B below folds branch-on-slot uses of it.
        val provenStores = ArrayList<VarInsnNode>()
        var p = insns.first
        while (p != null) {
            val next = p.next
            if (p is MethodInsnNode && p.opcode == Opcodes.INVOKESTATIC &&
                p.owner == "autismclient/util/AutismLiteVariant" && p.name == "enabled" && p.desc == "()Z") {
                if (next is JumpInsnNode && (next.opcode == Opcodes.IFEQ || next.opcode == Opcodes.IFNE)) {
                    val after = next.next
                    if (next.opcode == Opcodes.IFEQ) {
                        insns.remove(next) // enabled()==true: never jump, fall through
                    } else {
                        insns.set(next, JumpInsnNode(Opcodes.GOTO, next.label)) // always jump
                    }
                    insns.remove(p)
                    methodChanged = true
                    p = after
                    continue
                }
                if (next is VarInsnNode && next.opcode == Opcodes.ISTORE) {
                    // Pattern A: [enabled][istore n] -> [iconst_1][istore n]. iconst_1 has the
                    // same stack shape as the ()Z call, so recomputed frames are unaffected.
                    insns.set(p, InsnNode(Opcodes.ICONST_1))
                    provenStores.add(next)
                    methodChanged = true
                }
            }
            p = next
        }
        // Pattern B: fold [iload n][ifeq|ifne L] on a slot PROVEN constant 1 at this load. The
        // proof holds only for a linear stretch from the store: any LabelNode (a branch target /
        // merge point where n could differ), any MethodInsnNode, or any rewrite of slot n between
        // store and load disqualifies the load. When in doubt, leave it - a surviving feature is
        // safe, a wrong fold is a silent behavior change.
        for (store in provenStores) {
            val slot = store.`var`
            var q = store.next
            while (q != null) {
                if (q is LabelNode || q is MethodInsnNode) break
                if (q is VarInsnNode && q.`var` == slot && q.opcode == Opcodes.ISTORE) break
                if (q is IincInsnNode && q.`var` == slot) break
                val qNext = q.next
                if (q is VarInsnNode && q.opcode == Opcodes.ILOAD && q.`var` == slot &&
                    qNext is JumpInsnNode && (qNext.opcode == Opcodes.IFEQ || qNext.opcode == Opcodes.IFNE)) {
                    val after = qNext.next
                    if (qNext.opcode == Opcodes.IFEQ) {
                        insns.remove(qNext) // slot==1: never jump, fall through
                    } else {
                        insns.set(qNext, JumpInsnNode(Opcodes.GOTO, qNext.label)) // always jump
                    }
                    insns.remove(q)
                    methodChanged = true
                    q = after
                    continue
                }
                q = qNext
            }
        }
        if (methodChanged) {
            liteDeadCodeEliminate(mn)
            changed = true
        }
    }
    if (changed) {
        // Dead-lambda sweep: when a lambda's creation site (its invokedynamic) was folded away,
        // the synthetic lambda$ method stays in the class and its BODY still references the
        // stripped cluster (whole-class shrinking cannot remove unused methods). Remove any
        // synthetic lambda$... method that no remaining invokedynamic in the class references.
        val liveHandles = HashSet<String>()
        for (mn in cn.methods) {
            for (insn in mn.instructions) {
                if (insn is org.objectweb.asm.tree.InvokeDynamicInsnNode) {
                    for (arg in insn.bsmArgs) {
                        if (arg is org.objectweb.asm.Handle) liveHandles += arg.owner + "." + arg.name + arg.desc
                    }
                }
            }
        }
        cn.methods.removeIf { mn ->
            mn.name.startsWith("lambda$") && (mn.access and Opcodes.ACC_SYNTHETIC) != 0 &&
                (cn.name + "." + mn.name + mn.desc) !in liveHandles
        }
    }
    if (!changed) return null
    val cw = object : ClassWriter(ClassWriter.COMPUTE_FRAMES) {
        override fun getCommonSuperClass(t1: String, t2: String): String = liteCommonSuper(t1, t2, cl)
    }
    cn.accept(cw)
    val out = cw.toByteArray()
    // Verify before shipping: a broken fold must fail the build, never the game.
    val report = StringWriter()
    CheckClassAdapter.verify(ClassReader(out), cl, false, PrintWriter(report))
    val text = report.toString()
    require(text.isBlank()) { "liteFoldGates produced invalid bytecode for ${cn.name}:\n$text" }
    return out
}

// ---------------------------------------------------------------------------
// Cross-process execution lock for the LITE pipeline. Multiple agent sessions can build this
// project at the same time; without an interlock the lite stages tear each other's intermediates
// (a patched.jar read mid-write = ZipException, the full jar rebuilt under verifyLiteJar,
// Windows file-lock failures on the libs jar). FileChannel.tryLock is an OS-level lock, so it
// guards across separate Gradle daemons / CLI processes - a JVM ReentrantLock would not.
// ---------------------------------------------------------------------------

// Acquires the exclusive lite build lock (build/liteWork/.lock) or fails fast.
fun liteBuildLockAcquire(): FileLock {
    val lockFile = layout.buildDirectory.file("liteWork/.lock").get().asFile
    lockFile.parentFile.mkdirs()
    val channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock = try {
        channel.tryLock()
    } catch (e: OverlappingFileLockException) {
        null // already held by THIS JVM (e.g. a stale lock from a failed task in this daemon)
    }
    if (lock == null) {
        channel.close()
        throw GradleException(
            "Another LITE build is running (lock file: ${lockFile.absolutePath}). " +
            "Wait for it to finish before running litePatchVariant/liteShrinkJar/liteJar/verifyLiteJar.")
    }
    return lock
}

fun liteBuildLockRelease(lock: FileLock) {
    try {
        lock.release()
    } finally {
        lock.channel().close()
    }
}

// Runs [block] under the exclusive lite build lock; the lock is always released (try/finally).
fun <T> withLiteBuildLock(block: () -> T): T {
    val lock = liteBuildLockAcquire()
    try {
        return block()
    } finally {
        liteBuildLockRelease(lock)
    }
}

tasks.register("litePatchVariant") {
    group = "build"
    description = "Copy the full jar and patch AutismLiteVariant.ENABLED to a compile-time constant true."
    // The full build (guardian secret bake, mixin injection) must be finished first.
    dependsOn("build")

    val fullJar = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}.jar")
    val out = layout.buildDirectory.file("liteWork/patched.jar")
    inputs.file(fullJar)
    outputs.file(out)

    doLast {
        withLiteBuildLock {
            val patched = out.get().asFile
            patched.parentFile.mkdirs()
            // Atomic publish: a concurrent build must never read a torn patched.jar - write to a
            // temp file, then move it into place (same temp+move pattern as the LVT strip below).
            val patchedTmp = File(patched.parentFile, patched.name + ".tmp")
            val target = "autismclient/util/AutismLiteVariant.class"
            val gateMarker = "autismclient/util/AutismLiteVariant".toByteArray(Charsets.US_ASCII)
            // Loader for frame computation + bytecode verification: the full jar's universe (the
            // fold never changes hierarchies) plus the library classpaths.
            val urls = ArrayList<URL>()
            urls.add(fullJar.get().asFile.toURI().toURL())
            project.configurations.getByName("compileClasspath").files.forEach { if (it.isFile) urls.add(it.toURI().toURL()) }
            project.configurations.getByName("runtimeClasspath").files.forEach { if (it.isFile) urls.add(it.toURI().toURL()) }
            val cl = URLClassLoader(urls.toTypedArray(), ClassLoader.getSystemClassLoader())
            var patchedCount = 0
            var foldedClasses = 0
            ZipFile(fullJar.get().asFile).use { zip ->
                ZipOutputStream(patchedTmp.outputStream().buffered()).use { zout ->
                    zip.entries().asSequence().forEach { e ->
                        zout.putNextEntry(ZipEntry(e.name))
                        if (!e.isDirectory) {
                            var bytes = zip.getInputStream(e).readBytes()
                            if (e.name == target) {
                                bytes = litePatchVariantClass(bytes)
                                patchedCount++
                            } else if (e.name.endsWith(".class") && liteContainsSubarray(bytes, gateMarker)) {
                                val folded = liteFoldGates(bytes, cl)
                                if (folded != null) {
                                    bytes = folded
                                    foldedClasses++
                                }
                            }
                            zout.write(bytes)
                        }
                        zout.closeEntry()
                    }
                }
            }
            if (patchedCount != 1) throw GradleException("litePatchVariant: $target not found in the full jar")
            Files.move(patchedTmp.toPath(), patched.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("litePatchVariant: ENABLED patched to constant true, " +
                "gates folded in $foldedClasses classes -> ${patched.name}")
        }
    }
}

tasks.register("liteShrinkJar") {
    group = "build"
    description = "Shrink the patched jar with our own reachability analysis (no ProGuard, no obfuscation tooling at all)."
    dependsOn("litePatchVariant")

    val patched = layout.buildDirectory.file("liteWork/patched.jar")
    val out = layout.buildDirectory.file("liteWork/shrunk.jar")
    inputs.file(patched)
    inputs.property("liteStripMixins", liteStripMixins)
    inputs.property("liteKeepPinned", liteKeepPinned)
    outputs.file(out)

    doLast {
        withLiteBuildLock {
            // Reachability shrink, no external tool: seeds -> transitive constant-pool reference
            // closure (liteReferencedClasses, the same over-approximating scan the verifier's
            // dangling-reference guard uses - it can only keep MORE than strictly needed, never
            // less). Surviving classes are copied WHOLE: mixin contracts, gson models, enums and
            // any reflection inside survivors are intact by construction, and there is nothing
            // that can rename, specialize or corrupt bytecode the way an optimizer can.
            val entryBytes = LinkedHashMap<String, ByteArray>()
            val classBytes = HashMap<String, ByteArray>()
            ZipFile(patched.get().asFile).use { zip ->
                zip.entries().asSequence().forEach { e ->
                    if (e.isDirectory) return@forEach
                    val bytes = zip.getInputStream(e).readBytes()
                    entryBytes[e.name] = bytes
                    if (e.name.endsWith(".class")) classBytes[e.name.removeSuffix(".class")] = bytes
                }
            }

            // Seeds: the fabric entrypoint, the addon API (external addons resolve it by name),
            // the pinned lite-live classes, and every mixin the transformed lite config applies
            // (their nested classes are covered by the $-prefix pass below).
            val seeds = HashSet<String>()
            seeds += "autismclient/AutismClientMod"
            seeds += "autismclient/util/AutismLiteVariant"
            for (name in classBytes.keys) {
                if (name.startsWith("autismclient/api/")) seeds += name
            }
            liteKeepPinned.forEach { seeds += it.removeSuffix(".class") }

            val jsonText = entryBytes.getValue("autism.mixins.json").toString(Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parseText(jsonText) as Map<String, Any?>
            val mixinPackage = (json["package"] as? String) ?: "autismclient.mixin"
            @Suppress("UNCHECKED_CAST")
            val client = (json["client"] as? List<Any?>) ?: emptyList<Any?>()
            val keptMixins = client.filterIsInstance<String>()
                .filter { name -> liteStripMixins.none { name.endsWith(it) } && !name.startsWith("guardian.") }
                .map { "${mixinPackage.replace('.', '/')}/${it.replace('.', '/')}" }
                .toMutableList()
            (json["plugin"] as? String)?.let { plugin ->
                val fqn = (if (plugin.contains('.')) plugin else "$mixinPackage.$plugin").replace('.', '/')
                // The mixin config plugin is framework-reflected: the mixin processor calls
                // onLoad/shouldApplyMixin/getMixins reflectively at runtime, so the reachability
                // walk can never see those calls - the class must ship WHOLE (all members and
                // nested classes), or startup dies with AbstractMethodError on any transform.
                keptMixins += fqn
                seeds += fqn
            }
            seeds += keptMixins
            val mixinSeedPrefixes = keptMixins.map { "$it$" }
            for (name in classBytes.keys) {
                if (mixinSeedPrefixes.any { name.startsWith(it) }) seeds += name
            }

            // The closure: seeded classes plus everything they reference, transitively.
            val reachable = LinkedHashSet(seeds)
            val queue = ArrayDeque(seeds)
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                val bytes = classBytes[c] ?: continue
                for (r in liteReferencedClasses(bytes)) {
                    if (r in classBytes && reachable.add(r)) queue.addLast(r)
                }
            }

            // Member-level pass: drop unused methods/fields from kept classes (see litePruneMembers)
            // so dead feature members stop anchoring their classes (DupeRadar, Spotify, geo lookup).
            val pruned = litePruneMembers(classBytes, reachable, keptMixins.toSet(), seeds)

            // Write shrunk.jar atomically: every non-class entry + every surviving class
            // (LVT-stripped - stale after the fold, and optional debug info anyway).
            val tmp = File(out.get().asFile.parentFile, out.get().asFile.name + ".tmp")
            var keptClasses = 0
            var strippedLvt = 0
            ZipOutputStream(tmp.outputStream().buffered()).use { zout ->
                for ((name, bytes) in entryBytes) {
                    if (name.endsWith(".class")) {
                        val prunedClass = pruned[name.removeSuffix(".class")] ?: continue
                        keptClasses++
                        val stripped = liteStripLocalVars(prunedClass)
                        if (stripped !== prunedClass) strippedLvt++
                        zout.putNextEntry(ZipEntry(name))
                        zout.write(stripped)
                    } else {
                        zout.putNextEntry(ZipEntry(name))
                        zout.write(bytes)
                    }
                    zout.closeEntry()
                }
            }
            Files.move(tmp.toPath(), out.get().asFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            val mb = out.get().asFile.length() / 1024.0 / 1024.0
            logger.lifecycle("liteShrinkJar: kept $keptClasses/${classBytes.size} classes " +
                "(${keptMixins.size} mixin seeds, dropped ${classBytes.size - keptClasses}), " +
                "stripped LocalVariableTable from $strippedLvt classes - shrunk jar = ${"%.1f".format(mb)} MB")
        }
    }
}

tasks.register<Jar>("liteJar") {
    group = "build"
    description = "Repackage the shrunk jar as the final LITE variant (assets/mixins/libs out, marker in)."
    dependsOn("liteShrinkJar")

    val libsDir = layout.buildDirectory.dir("libs")
    val shrunkJar = layout.buildDirectory.file("liteWork/shrunk.jar")
    inputs.file(shrunkJar)
    inputs.property("liteStripMixins", liteStripMixins)
    inputs.property("liteStripAssets", liteStripAssets)

    destinationDirectory.set(libsDir)
    archiveBaseName.set("Autism Lite")
    archiveVersion.set(project.version.toString())
    // No zero-byte entries for directories the strip emptied (jar hygiene).
    includeEmptyDirs = false

    // Nested libraries with zero references from surviving classes, computed in doFirst (the
    // shrunk jar only exists at execution time). Drives both the jar exclusions and the
    // fabric.mod.json jars-array edit below.
    val deadLibs = mutableListOf<String>()

    // The lite build lock must span the WHOLE task execution (doFirst -> copy -> doLast), and
    // a Jar task's copy action cannot be wrapped in one try/finally - so it is acquired at the
    // top of doFirst and released in the doLast finally below.
    val liteExecLock = arrayOfNulls<FileLock>(1)

    // Copy the shrunk jar's entries (deferred), minus the stripped-feature assets, the dead mixin
    // classes (belt-and-braces: the shrink should have removed them already) and the dead libs.
    from({ zipTree(shrunkJar.get().asFile) }) {
        exclude("autism.mixins.json") // whole-file-transformed copy is generated in doFirst instead
        liteStripAssets.forEach { prefix ->
            if (prefix.endsWith("/")) exclude(prefix + "**") else exclude(prefix)
        }
        // The stripped mixin classes themselves, nested classes included (json-stripping alone
        // saves zero bytes; a future FooMixin$1.class must not slip through either).
        liteStripMixins.forEach { name ->
            if (name.startsWith("lithium.")) {
                exclude("autismclient/mixin/lithium/${name.removePrefix("lithium.")}.class")
                exclude("autismclient/mixin/lithium/${name.removePrefix("lithium.")}\$*.class")
            } else {
                exclude("autismclient/mixin/$name.class")
                exclude("autismclient/mixin/$name\$*.class")
            }
        }
    }

    // The lite marker: one empty resource, written out at execution time.
    val markerDir = layout.buildDirectory.dir("liteJarMarker")
    from(markerDir)

    // fabric.mod.json: the modmenu entrypoint goes (its config screen links theme/module menu), and
    // every dead nested lib loses its jars-array declaration so metadata matches contents. Name,
    // icon and the rest of the metadata stay byte-identical to the full jar. Single-line JSON =
    // text replaces; deadLibs is populated in doFirst before this filter runs during the copy.
    filesMatching("fabric.mod.json") {
        filter { text ->
            var out = text.replace(",\"modmenu\":[\"autismclient.compat.AutismModMenuIntegration\"]", "")
            for (lib in deadLibs) {
                out = out.replace("{\"file\":\"$lib\"},", "").replace(",{\"file\":\"$lib\"}", "")
            }
            out
        }
    }

    // autism.mixins.json: Gradle's text filter is a LINEFilter (one line per call, re-joined with
    // the platform separator) - useless for a whole-file structural edit. So the original entry is
    // excluded in the main from-spec above and a whole-file-transformed copy is generated in
    // doFirst and re-added via markerDir below.
    doFirst {
        liteExecLock[0] = liteBuildLockAcquire()
        // Data-driven nested-lib strip: a bundled library referenced by NO surviving class is dead
        // weight (the shrink removed its last referrers). The fixpoint starts from outer-class
        // references and follows lib-to-lib edges (e.g. handler-proxy needs codec-socks at runtime
        // even though our bytecode never names codec-socks) - a lib is dead only when NOTHING
        // reachable references it. Excluding here also feeds the fabric.mod.json edit above.
        ZipFile(shrunkJar.get().asFile).use { zip ->
            val outerRefs = HashSet<String>()
            zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { e ->
                outerRefs += liteReferencedClasses(zip.getInputStream(e).readBytes())
            }
            // entryName -> (its classes, classes its classes reference)
            val nested = zip.entries().asSequence()
                .filter { it.name.startsWith("META-INF/jars/") && it.name.endsWith(".jar") }
                .map { libEntry ->
                    val classBytes = ZipInputStream(zip.getInputStream(libEntry)).use { inner ->
                        generateSequence { inner.nextEntry }
                            .filter { it.name.endsWith(".class") }
                            .map { it.name.removeSuffix(".class") to inner.readBytes() }.toList()
                    }
                    val refs = HashSet<String>()
                    classBytes.forEach { refs += liteReferencedClasses(it.second) }
                    Triple(libEntry.name, classBytes.map { it.first }, refs as Set<String>)
                }.toList()
            val aliveLibs = HashSet<String>()
            var frontier: Set<String> = outerRefs
            while (true) {
                val newAlive = nested.filter { it.first !in aliveLibs && it.second.any(frontier::contains) }
                if (newAlive.isEmpty()) break
                newAlive.forEach { aliveLibs += it.first }
                frontier = newAlive.fold(HashSet<String>()) { acc, lib -> acc.apply { addAll(lib.third) } }
            }
            nested.filter { it.first !in aliveLibs }.forEach { lib ->
                deadLibs += lib.first
                exclude(lib.first)
                logger.lifecycle("liteJar: nested lib ${lib.first.substringAfterLast('/')} " +
                    "unreferenced after shrink - stripped (${lib.second.size} classes)")
            }
        }

        val dir = markerDir.get().asFile
        dir.mkdirs()
        dir.resolve("autism-lite.marker").writeText("")

        // Whole-file transform of the built mixins config: drop the lite-strip entries AND the
        // dead guardian entries from the client list (their impl already lives in excluded
        // util/mm), then re-emit - strict-JSON valid by construction.
        val mixinsOut = dir.resolve("autism.mixins.json")
        ZipFile(shrunkJar.get().asFile).use { zip ->
            val text = zip.getInputStream(zip.getEntry("autism.mixins.json")).readBytes().toString(Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parseText(text) as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val client = (json["client"] as? MutableList<Any?>) ?: mutableListOf()
            client.removeAll { name ->
                name is String && (liteStripMixins.any { name.endsWith(it) } || name.startsWith("guardian."))
            }
            json["client"] = client
            mixinsOut.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
        }
    }

    // The pinned K_build sidecar travels with the lite jar under its own name (VPS tooling expects
    // jar + sidecar together; the same secret backs both jars).
    doLast {
        try {
            val sidecar = libsDir.get().file("${base.archivesName.get()}-${project.version}.jar.kbuild").asFile
            val target = libsDir.get().file("Autism Lite-${project.version}.jar.kbuild").asFile
            if (sidecar.isFile) sidecar.copyTo(target, overwrite = true)
            else logger.warn("liteJar: no .kbuild sidecar found at $sidecar (unprotected build?) - skipped.")
        } finally {
            liteExecLock[0]?.let { liteBuildLockRelease(it) }
            liteExecLock[0] = null
        }
    }
}

// ---------------------------------------------------------------------------
// LITE contents verifier: runs automatically after liteJar and FAILS the build if the lite jar
// ever regresses. This is what makes "edit the main mod freely" safe: if a future change drops
// the marker, leaks a stripped asset/mixin back in, drops a pinned class, or breaks the class
// reference closure (the NoClassDefFoundError vector that bit us once), the build says so
// instead of shipping a quietly broken lite jar.
// ---------------------------------------------------------------------------
tasks.register("verifyLiteJar") {
    group = "verification"
    description = "Fail the build if the LITE jar's contents regress (marker, asset/mixin strip, pinned classes, reference closure, metadata)."
    dependsOn("liteJar")

    val liteJarFile = layout.buildDirectory.file("libs/Autism Lite-${project.version}.jar")
    val fullJarFile = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}.jar")
    inputs.file(liteJarFile)
    inputs.file(fullJarFile)
    inputs.property("liteStripMixins", liteStripMixins)
    inputs.property("liteStripAssets", liteStripAssets)
    inputs.property("liteKeepPinned", liteKeepPinned)
    inputs.property("liteKeepLibs", liteKeepLibs)
    inputs.property("liteKnownDead", liteKnownDead)
    inputs.property("liteMacroParityExempt", liteMacroParityExempt)

    doLast {
        withLiteBuildLock {
            val jar = liteJarFile.get().asFile
            fun fail(msg: String): Nothing = throw GradleException("verifyLiteJar: $msg")
            if (!jar.isFile) fail("$jar does not exist")

            val entries = ZipFile(jar).use { zip ->
                zip.entries().asSequence().map { it.name }.toList()
            }

            if (!entries.contains("autism-lite.marker")) {
                fail("autism-lite.marker missing - every runtime gate would stay off in the lite jar")
            }

            // Strip assertions: nothing from the asset/mixin strip lists may be present.
            val leakedAssets = liteStripAssets.filter { prefix -> entries.any { it.startsWith(prefix) } }
            if (leakedAssets.isNotEmpty()) fail("stripped assets leaked into the lite jar: $leakedAssets")
            val leakedMixinClasses = liteStripMixins.filter { name ->
                val base = if (name.startsWith("lithium.")) "autismclient/mixin/lithium/${name.removePrefix("lithium.")}"
                    else "autismclient/mixin/$name"
                entries.any { it == "$base.class" || (it.startsWith("$base\$") && it.endsWith(".class")) }
            }
            if (leakedMixinClasses.isNotEmpty()) fail("stripped mixin classes leaked into the lite jar: $leakedMixinClasses")

            // Pinned must-stay assertions: an over-aggressive copy fails here, not at runtime.
            val missingPinned = liteKeepPinned.filter { !entries.contains(it) }
            if (missingPinned.isNotEmpty()) fail("pinned classes missing from the lite jar: $missingPinned")
            val missingLibs = liteKeepLibs.filter { !entries.contains(it) }
            if (missingLibs.isNotEmpty()) fail("required bundled libraries missing from the lite jar: $missingLibs")

            // Macro parity: the macro system (editor included) ships in lite by design. A new macro
            // action/condition registered the conventional way (AutismMacro.createActionFromTag case
            // + ActionFieldRegistry schema) is statically referenced from lite-live code and lands in
            // lite automatically - so any macro-package class present in the full jar but missing
            // from lite means someone registered ONLY in dead-in-lite code or reflectively, and the
            // action would silently not exist in lite. Fail loudly instead.
            val macroPackages = listOf("autismclient/util/macro/", "autismclient/gui/macro/")
            val fullEntries = ZipFile(fullJarFile.get().asFile).use { zip ->
                zip.entries().asSequence().map { it.name }.toList()
            }
            val macroMissing = fullEntries
                .filter { name -> macroPackages.any { name.startsWith(it) } && name.endsWith(".class") }
                .filter { it !in liteMacroParityExempt && !entries.contains(it) }
            if (macroMissing.isNotEmpty()) {
                fail("macro classes present in full but missing from lite (a new macro action/condition " +
                    "would silently not ship in lite - register it via AutismMacro.createActionFromTag + " +
                    "ActionFieldRegistry, or audit it into liteMacroParityExempt): ${macroMissing.take(10)}")
            }
            // The exemption audit is two-directional: an exempt class that IS present in lite means
            // the audit is stale (the class became lite-live again, or never actually left) and the
            // exemption entry is now hiding a leak instead of documenting one. Fail loudly either way.
            val exemptPresent = liteMacroParityExempt.filter { entries.contains(it) }
            if (exemptPresent.isNotEmpty()) {
                fail("liteMacroParityExempt classes present in the lite jar (stale exemption hiding a " +
                    "leak - re-audit the exemption list): $exemptPresent")
            }

            // Config-model parity: every AutismConfig nested model class in the full jar must exist in
            // lite. A missing one means gson binds that part of the config as generic maps (silently
            // degrading persistence) - this tripwire fires before that ships.
            val configModelMissing = fullEntries
                .filter { it.startsWith("autismclient/util/AutismConfig\$") && it.endsWith(".class") }
                .filter { !entries.contains(it) }
            if (configModelMissing.isNotEmpty()) {
                fail("AutismConfig model classes present in full but missing from lite (gson config " +
                    "persistence would silently degrade): ${configModelMissing.take(10)}")
            }

            // Known-dead assertions: the fold + shrink MUST have removed these. Their presence means a
            // lite gate lost its literal call-site form (or a new ungated referrer appeared) and a
            // stripped feature silently survived the shrink.
            val undead = liteKnownDead.filter { entries.contains(it) }
            if (undead.isNotEmpty()) fail("classes that must have shrunk away are present in the lite jar (a lite gate is not folding): $undead")

            // LocalVariableTable guard: liteShrinkJar strips these because dead-code elimination
            // can leave their slot references stale (the JVM validates them at class load).
            // Attribute-level check (an orphan constant-pool Utf8 without the attribute is harmless).
            val lvtLeaks = mutableListOf<String>()
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { e ->
                    var hasLvt = false
                    ClassReader(zip.getInputStream(e).readBytes()).accept(object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
                        override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?,
                            exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor {
                            return object : org.objectweb.asm.MethodVisitor(Opcodes.ASM9) {
                                override fun visitLocalVariable(n: String?, d: String?, s: String?,
                                    start: org.objectweb.asm.Label?, end: org.objectweb.asm.Label?, index: Int) {
                                    hasLvt = true
                                }
                            }
                        }
                    }, 0)
                    if (hasLvt) lvtLeaks += e.name
                }
            }
            if (lvtLeaks.isNotEmpty()) {
                fail("${lvtLeaks.size} classes still carry LocalVariableTable attributes (invalid after optimization): ${lvtLeaks.take(3)}")
            }

            // Dangling-reference guard. The JVM verifier resolves the FROM type of every invoke,
            // assignment and cast in a class being loaded - even on bootstrap-gated dead branches
            // (register(new ParkourModule()) loads ParkourModule to prove it <: Module) - so any
            // class that is REFERENCED but MISSING from the jar is a runtime NoClassDefFoundError.
            // Prove the closure holds: every class referenced from any shipped class must ship too,
            // or be external (JDK / Minecraft / Fabric), where "shipped in the full jar" decides
            // what is not external. Scanning every shipped class once covers transitivity for free:
            // if A->B and B->C, the B->C edge is checked when B itself is scanned. (Shared scanners:
            // liteReferencedClasses / liteClassUniverse above.)
            val fullUniverse = ZipFile(fullJarFile.get().asFile).use { liteClassUniverse(it) }
            val dangling = sortedMapOf<String, List<String>>()
            ZipFile(jar).use { zip ->
                val liteUniverse = liteClassUniverse(zip)
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { e ->
                    val missing = liteReferencedClasses(zip.getInputStream(e).readBytes())
                        .filter { it in fullUniverse && it !in liteUniverse }
                    if (missing.isNotEmpty()) dangling[e.name.removeSuffix(".class")] = missing
                }
            }
            if (dangling.isNotEmpty()) {
                val detail = dangling.entries.take(10).joinToString("\n  ") { (c, m) -> "$c -> $m" }
                fail("referenced-but-missing classes in the lite jar (the JVM verifier would throw " +
                    "NoClassDefFoundError in-game): ${dangling.size} referrer(s), first:\n  $detail")
            }

            // Method-level dangling guard: the member pass (litePruneMembers) drops unused methods
            // and fields, so prove every kept call site RESOLVES - a method/field the member walk
            // dropped while its caller stayed is a runtime NoSuchMethodError/NoSuchFieldError.
            // Resolution is judged against the FULL jar: a member resolvable through the full
            // jar's hierarchy but missing from lite was pruned -> dangling; a member that does not
            // resolve even in the full jar belongs to Minecraft/JDK and is external. (The previous
            // version treated a hierarchy escaping the jar as satisfied - every hierarchy ends at
            // java/lang/Object, so the guard could never fire. A pruned static helper that a kept
            // codec called proved that in-game: NoSuchMethodError at startup.)
            val liteNodes = HashMap<String, ClassNode>()
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { e ->
                    val cn = ClassNode()
                    ClassReader(zip.getInputStream(e).readBytes()).accept(cn, 0)
                    liteNodes[e.name.removeSuffix(".class")] = cn
                }
            }
            val fullNodes = HashMap<String, ClassNode>()
            ZipFile(fullJarFile.get().asFile).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { e ->
                    val cn = ClassNode()
                    ClassReader(zip.getInputStream(e).readBytes())
                        .accept(cn, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                    fullNodes[e.name.removeSuffix(".class")] = cn
                }
            }
            // Walk owner + supertypes; escaping the scanned universe means "not found here".
            fun resolvesIn(nodes: Map<String, ClassNode>, owner: String, name: String, desc: String, field: Boolean): Boolean {
                val seen = HashSet<String>()
                val q = ArrayDeque<String>()
                q.add(owner)
                while (q.isNotEmpty()) {
                    val c = q.removeFirst()
                    if (!seen.add(c)) continue
                    val node = nodes[c] ?: return false
                    val hit = if (field) node.fields.any { it.name == name && it.desc == desc }
                              else node.methods.any { it.name == name && it.desc == desc }
                    if (hit) return true
                    node.superName?.let(q::addLast)
                    q.addAll(node.interfaces.filterIsInstance<String>())
                }
                return false
            }
            val memberDangling = ArrayList<String>()
            for ((owner, cn) in liteNodes) {
                for (mn in cn.methods) {
                    for (insn in mn.instructions) {
                        when (insn) {
                            is org.objectweb.asm.tree.MethodInsnNode -> {
                                if (insn.owner in liteNodes &&
                                    !resolvesIn(liteNodes, insn.owner, insn.name, insn.desc, false) &&
                                    resolvesIn(fullNodes, insn.owner, insn.name, insn.desc, false)) {
                                    memberDangling += "$owner.${mn.name} -> ${insn.owner}.${insn.name}${insn.desc}"
                                }
                            }
                            is org.objectweb.asm.tree.FieldInsnNode -> {
                                if (insn.owner in liteNodes &&
                                    !resolvesIn(liteNodes, insn.owner, insn.name, insn.desc, true) &&
                                    resolvesIn(fullNodes, insn.owner, insn.name, insn.desc, true)) {
                                    memberDangling += "$owner.${mn.name} -> ${insn.owner}.${insn.name}"
                                }
                            }
                        }
                    }
                }
            }
            if (memberDangling.isNotEmpty()) {
                fail("dangling member references in the lite jar (a caller kept a method/field the " +
                    "member pass dropped - runtime NoSuchMethodError/NoSuchFieldError): ${memberDangling.take(8)}")
            }

            fun entryText(path: String): String {
                ZipFile(jar).use { zip ->
                    val entry = zip.getEntry(path) ?: fail("$path missing from the lite jar")
                    return zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                }
            }

            val fabricJson = entryText("fabric.mod.json")
            if (!fabricJson.contains("\"name\":\"AUTISM Client\"")) {
                fail("fabric.mod.json name drifted - the game title/icon must not change in lite")
            }
            if (fabricJson.contains("AutismModMenuIntegration")) {
                fail("fabric.mod.json still has the modmenu entrypoint (its config screen links stripped UI)")
            }
            // Metadata must match contents both ways: every bundled lib in the jar is declared in
            // fabric.mod.json, and every declared lib is in the jar (the data-driven strip in liteJar
            // removes both sides together).
            val nestedLibs = entries.filter { it.startsWith("META-INF/jars/") && it.endsWith(".jar") }
            val undeclared = nestedLibs.filter { !fabricJson.contains("\"file\":\"$it\"") }
            if (undeclared.isNotEmpty()) fail("lite fabric.mod.json does not declare bundled libraries present in the jar: $undeclared")
            val declared = Regex("\"file\":\"(META-INF/jars/[^\"]+)\"").findAll(fabricJson).map { it.groupValues[1] }.toList()
            val declaredGone = declared.filter { !entries.contains(it) }
            if (declaredGone.isNotEmpty()) fail("lite fabric.mod.json declares bundled libraries missing from the jar: $declaredGone")
            try {
                groovy.json.JsonSlurper().parseText(fabricJson)
            } catch (t: Throwable) {
                fail("fabric.mod.json is not valid JSON: ${t.message}")
            }

            val mixinsJson = entryText("autism.mixins.json")
            val leakedMixins = liteStripMixins.filter { mixinsJson.contains("\"$it\"") }
            if (leakedMixins.isNotEmpty()) fail("strip-listed mixins back in the lite config: $leakedMixins")
            try {
                groovy.json.JsonSlurper().parseText(mixinsJson)
            } catch (t: Throwable) {
                fail("autism.mixins.json is not valid JSON after filtering: ${t.message}")
            }

            // Mixin member-parity guard: every mixin class lite applies (transformed json entries
            // AND the config plugin) must ship with ALL of its full-jar methods. The mixin
            // framework resolves handlers, accessors and the plugin's lifecycle methods
            // (onLoad/shouldApplyMixin/getMixins) reflectively at runtime, so a pruned member is
            // an AbstractMethodError at startup (verified in-game: the plugin's onLoad once died
            // exactly like that).
            val mixinsJsonObj = groovy.json.JsonSlurper().parseText(mixinsJson) as Map<String, Any?>
            val mixinPkg = ((mixinsJsonObj["package"] as? String) ?: "autismclient.mixin").replace('.', '/')
            @Suppress("UNCHECKED_CAST")
            val mixinClassNames = ((mixinsJsonObj["client"] as? List<Any?>)?.filterIsInstance<String>() ?: emptyList())
                .map { "$mixinPkg/${it.replace('.', '/')}" }.toMutableList()
            (mixinsJsonObj["plugin"] as? String)?.let { plugin ->
                mixinClassNames += (if (plugin.contains('.')) plugin else "$mixinPkg.$plugin").replace('.', '/')
            }
            val fullMixinNodes = HashMap<String, ClassNode>()
            ZipFile(fullJarFile.get().asFile).use { zip ->
                for (name in mixinClassNames) {
                    val entry = zip.getEntry("$name.class") ?: continue
                    val cn = ClassNode()
                    ClassReader(zip.getInputStream(entry).readBytes()).accept(cn, 0)
                    fullMixinNodes[name] = cn
                }
            }
            val memberParity = ArrayList<String>()
            for ((name, fullNode) in fullMixinNodes) {
                val liteNode = liteNodes[name] ?: run {
                    memberParity += "$name (class missing from lite)"
                    continue
                }
                for (mn in fullNode.methods) {
                    // Compiler-synthetic methods (lambda$...$) are not framework contract: they are
                    // invoked through invokedynamic handles, and when a lambda's creation site is
                    // lite-gated and folded, the dead-lambda sweep removes it on purpose.
                    if ((mn.access and Opcodes.ACC_SYNTHETIC) != 0) continue
                    if (liteNode.methods.none { it.name == mn.name && it.desc == mn.desc }) {
                        memberParity += "$name.${mn.name}${mn.desc}"
                    }
                }
            }
            if (memberParity.isNotEmpty()) {
                fail("mixin classes missing methods the framework needs (AbstractMethodError at startup): ${memberParity.take(6)}")
            }

            // Callback-contract guards: two families of methods die without a visible call site.
            // 1) liteCallbackContracts - JVM/library invocations (string concat -> toString,
            //    HashMap -> equals/hashCode, executors -> run/call, sort -> compare, ...).
            // 2) external-rooted classes - framework callbacks invoked virtually by code outside
            //    the jar (Screen.init/tick, netty channelRead/write, ClassValue.computeValue,
            //    brigadier ArgumentType.parse, record constructors, ...).
            // Both are exempt from pruning in liteShrinkJar; this proves the exemptions held.
            // (Verified in-game: a codec's pruned toString was an AbstractMethodError at startup;
            // verified by diff: the netty tap's channelRead was silently pruned before the fix.)
            val liteExternalRooted = HashSet<String>()
            for (name in liteNodes.keys) {
                val visited = LinkedHashSet<String>()
                val queue = ArrayDeque<String>()
                queue.addLast(name)
                var external = false
                while (queue.isNotEmpty() && !external) {
                    val cn = liteNodes[queue.removeFirst()] ?: continue
                    val supers = listOfNotNull(cn.superName) + cn.interfaces.filterIsInstance<String>()
                    for (s in supers) {
                        if (s !in liteNodes) {
                            if (s != "java/lang/Object") { external = true; break }
                        } else if (visited.add(s)) {
                            queue.addLast(s)
                        }
                    }
                }
                if (external) liteExternalRooted += name
            }
            val contractGone = ArrayList<String>()
            for ((name, liteNode) in liteNodes) {
                val checkAll = name in liteExternalRooted
                val fullNode = fullNodes[name] ?: continue
                for (mn in fullNode.methods) {
                    if ((mn.access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC)) != 0) continue
                    if ((checkAll || (mn.name + mn.desc) in liteCallbackContracts) &&
                        liteNode.methods.none { it.name == mn.name && it.desc == mn.desc }) {
                        contractGone += "$name.${mn.name}${mn.desc}"
                    }
                }
            }
            if (contractGone.isNotEmpty()) {
                fail("implicitly-called methods pruned (AbstractMethodError at runtime): ${contractGone.take(6)}")
            }

            val mb = jar.length() / 1024.0 / 1024.0
            if (mb > 7.0) {
                fail("lite jar regressed to ${"%.1f".format(mb)} MB (> 7.0 MB ceiling) - the shrink is not doing its job")
            }
            logger.lifecycle("verifyLiteJar: contents OK (marker, ${liteStripMixins.size} mixins stripped, " +
                "${liteStripAssets.size} asset exclusions held, ${liteKeepPinned.size} pinned classes + " +
                "${liteKeepLibs.size} libraries present, ${liteKnownDead.size} known-dead absent, " +
                "reference closure intact, metadata intact) - lite jar = ${"%.1f".format(mb)} MB")
        }
    }
}

tasks.named("liteJar") {
    finalizedBy("verifyLiteJar")
}
