import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_17
    }
    js {
        outputModuleName = "halone-scene"
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
        useEsModules()
    }
}

val webPackage = rootProject.file("web/packages/scene/dist")
val sceneSources = listOf(file("src/commonMain"), file("src/jsMain"))

// The simulator imports the compiled scene from the committed web package, so
// `pnpm dev` needs no JDK. core-compositor's tests fail when the hash written
// here no longer matches the sources.
tasks.register<Sync>("syncWebPackage") {
    dependsOn("jsProductionLibraryCompileSync")
    from(layout.buildDirectory.dir("compileSync/js/main/productionLibrary/kotlin")) {
        include("*.mjs", "*.d.mts")
        filter { line -> if (line.startsWith("//# sourceMappingURL=")) "" else line }
    }
    into(webPackage)
    val roots = sceneSources
    val hashFile = File(webPackage, "source-hash")
    inputs.files(roots)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile }.map { root.name + "/" + it.relativeTo(root).invariantSeparatorsPath to it }
        }
            .sortedBy { it.first }
            .forEach { (path, file) ->
                digest.update((path + "\n").toByteArray())
                digest.update(file.readBytes())
            }
        hashFile.writeText(digest.digest().joinToString("") { "%02x".format(it) } + "\n")
    }
}
