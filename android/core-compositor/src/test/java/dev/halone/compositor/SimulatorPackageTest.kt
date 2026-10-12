package dev.halone.compositor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Same digest as `:core-scene:syncWebPackage` writes beside the compiled package. */
class SimulatorPackageTest {

    @Test
    fun committedSimulatorPackageMatchesTheSceneSources() {
        val sources = File(System.getProperty("sceneSources") ?: "../core-scene/src")
        val committed = File(System.getProperty("scenePackageHash") ?: "../../web/packages/scene/dist/source-hash")
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(File(sources, "commonMain"), File(sources, "jsMain"))
            .flatMap { root ->
                root.walkTopDown().filter { it.isFile }.map { root.name + "/" + it.relativeTo(root).invariantSeparatorsPath to it }
            }
            .sortedBy { it.first }
            .forEach { (path, file) ->
                digest.update((path + "\n").toByteArray())
                digest.update(file.readBytes())
            }
        assertEquals(
            "web/packages/scene is stale; run ./gradlew :core-scene:syncWebPackage",
            digest.digest().joinToString("") { "%02x".format(it) },
            committed.readText().trim(),
        )
    }
}
