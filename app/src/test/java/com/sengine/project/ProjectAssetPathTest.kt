package com.sengine.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectAssetPathTest {
    @Test
    fun allowsAssetPathsInsideProjectAssets() {
        val root = Files.createTempDirectory("sengine-assets").toFile()
        val project = Project(File(root, "Game"))

        val asset = project.assetFile("scripts/Player.js")

        assertTrue(asset.path.startsWith(project.assetsDir.canonicalPath + File.separator))
        assertEquals("Player.js", asset.name)
    }

    @Test
    fun rejectsParentTraversalAndAbsolutePaths() {
        val root = Files.createTempDirectory("sengine-assets").toFile()
        val project = Project(File(root, "Game"))

        assertThrows(IllegalArgumentException::class.java) { project.assetFile("../project.json") }
        assertThrows(IllegalArgumentException::class.java) { project.assetFile("../../../agent/config.json") }
        assertThrows(IllegalArgumentException::class.java) {
            project.assetFile(File(root, "outside.txt").absolutePath)
        }
        assertThrows(IllegalArgumentException::class.java) { project.assetFile(" ") }
    }

    @Test
    fun rejectsSymlinkThatEscapesAssetsDirectory() {
        val root = Files.createTempDirectory("sengine-assets").toFile()
        val project = Project(File(root, "Game"))
        project.assetsDir.mkdirs()
        val outside = File(root, "outside.txt").apply { writeText("private") }
        val link = File(project.assetsDir, "outside-link.txt")

        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertThrows(IllegalArgumentException::class.java) { project.assetFile("outside-link.txt") }
    }

    @Test
    fun rejectsReadAndWriteOutsideAssets() {
        val root = Files.createTempDirectory("sengine-assets").toFile()
        val project = Project(File(root, "Game"))
        val outside = File(project.dir, "private.txt").apply { writeText("secret") }

        assertThrows(IllegalArgumentException::class.java) { project.readAsset("../private.txt") }
        assertThrows(IllegalArgumentException::class.java) { project.writeAsset("../private.txt", "changed") }
        assertEquals("secret", outside.readText())
    }
}
