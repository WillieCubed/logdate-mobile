@file:Suppress("ktlint:standard:function-naming")

package app.logdate

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScreenshotComparisonSheetTest {
    private val root: File = Files.createTempDirectory("screenshot-sheet").toFile()

    @AfterTest fun cleanUp() {
        root.deleteRecursively()
    }

    @Test fun scalesEveryTileToTheRowHeightAndKeepsItsAspectRatio() {
        val placements =
            ScreenshotComparisonSheet.layout(
                sizes = listOf(Size(400, 800), Size(1200, 800)),
                tileHeight = 200,
                maxRowWidth = 2000,
                gap = 10,
            )

        assertEquals(listOf(Placement(0, 0, 100, 200), Placement(110, 0, 300, 200)), placements)
    }

    @Test fun wrapsTilesThatDoNotFitTheRow() {
        val placements =
            ScreenshotComparisonSheet.layout(
                sizes = listOf(Size(100, 100), Size(100, 100), Size(100, 100)),
                tileHeight = 100,
                maxRowWidth = 220,
                gap = 10,
                labelHeight = 20,
            )

        assertEquals(
            listOf(Placement(0, 0, 100, 100), Placement(110, 0, 100, 100), Placement(0, 130, 100, 100)),
            placements,
        )
    }

    @Test fun keepsATileWiderThanTheRowOnItsOwnRow() {
        val placements =
            ScreenshotComparisonSheet.layout(
                sizes = listOf(Size(100, 100), Size(1000, 100)),
                tileHeight = 100,
                maxRowWidth = 300,
                gap = 10,
                labelHeight = 0,
            )

        assertEquals(Placement(0, 110, 1000, 100), placements[1])
    }

    @Test fun rendersALabeledSheetForEveryImage() {
        val first = image("a.png", 200, 400)
        val second = image("b.png", 400, 200)
        val output = root.resolve("out/sheet.png")

        ScreenshotComparisonSheet.render(
            title = "privacy-system-search",
            tiles = listOf(SheetTile("Phone", first), SheetTile("Tablet", second)),
            output = output,
            tileHeight = 100,
            maxRowWidth = 1000,
        )

        val sheet = ImageIO.read(output)
        assertTrue(sheet.width >= 250, "sheet is ${sheet.width} wide")
        assertTrue(sheet.height > 100, "sheet is ${sheet.height} tall")
    }

    @Test fun refusesToRenderAMissingImage() {
        assertFailsWith<IllegalArgumentException> {
            ScreenshotComparisonSheet.render(
                title = "missing",
                tiles = listOf(SheetTile("Phone", root.resolve("nope.png"))),
                output = root.resolve("out/sheet.png"),
            )
        }
    }

    @Test fun collectsAndroidDesktopAndSystemTilesForOneFeature() {
        val roots =
            ScreenshotCoverage.Roots(
                repoRoot = root,
                androidSources = root.resolve("src"),
                androidReferences = root.resolve("ref"),
                desktopReferences = root.resolve("desktop"),
            )
        image("ref/app/flows/Flow20_01_PrivacyKt/S01_Privacy_Phone_abc_0.png", 10, 20)
        image("ref/app/flows/Flow20_01_PrivacyKt/S01_Privacy_RTL_def_0.png", 10, 20)
        image("desktop/privacy-settings--phone.png", 10, 20)
        image("docs/shade.png", 10, 20)

        val tiles =
            ScreenshotComparisonSheet.tilesFor(
                listOf(
                    ScreenshotCoverage.Requirement.AndroidPreview("privacy", "app/flows/Flow20_01_Privacy.kt", "S01_Privacy"),
                    ScreenshotCoverage.Requirement.DesktopScene("privacy", "privacy-settings"),
                    ScreenshotCoverage.Requirement.SystemCapture("privacy", "docs/shade.png"),
                    ScreenshotCoverage.Requirement.SystemCapture("privacy", "docs/evidence.txt"),
                ),
                roots,
            )

        assertEquals(
            listOf("Android S01_Privacy: Phone", "Android S01_Privacy: RTL", "Desktop privacy-settings: phone", "System docs/shade.png"),
            tiles.map { it.label },
        )
    }

    private fun image(
        path: String,
        width: Int,
        height: Int,
    ): File =
        root.resolve(path).apply {
            parentFile.mkdirs()
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", this)
        }
}
