package app.logdate.client.domain.export.archive

import app.logdate.client.domain.export.archive.support.ArchiveSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadmeTemplateTest {
    private val html = ReadableCopy(ReadableFormat.HTML, ArchivePath.of("journal/html/index.html"))
    private val markdown = ReadableCopy(ReadableFormat.MARKDOWN, ArchivePath.of("journal/markdown/index.md"))

    private val complete = ArchiveSamples.manifest.copy(scope = ArchiveScope(complete = true))

    private fun render(
        manifest: ArchiveManifest = ArchiveSamples.manifest,
        copies: List<ReadableCopy> = listOf(html, markdown),
    ) = ReadmeTemplate.render(manifest, copies)

    /** The text with every run of whitespace collapsed, so a sentence can be found across a line break. */
    private fun flat(text: String) = text.split(Regex("\\s+")).joinToString(" ")

    @Test
    fun `it is plain ascii with short lines and unix line endings`() {
        val text = render()

        assertTrue(text.all { it.code < 128 }, "README must open the same in any editor")
        assertFalse('\r' in text)
        assertTrue(text.lines().all { it.length <= 100 }, "long lines: ${text.lines().filter { it.length > 100 }}")
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `it tells the reader where to start when a web page copy exists`() {
        val text = render()

        assertTrue("journal/html/index.html START HERE." in flat(text), text)
        assertTrue("journal/markdown/index.md" in text)
    }

    @Test
    fun `without a readable copy it points at the entries file`() {
        val text = render(copies = emptyList())

        assertTrue("data/notes.json" in text)
        assertTrue("There is no page-by-page reading copy" in flat(text))
        assertFalse("journal/" in text)
    }

    @Test
    fun `a complete export says nothing is missing`() {
        val text = render(manifest = complete)

        assertTrue("Nothing. This export holds everything LogDate keeps for you." in text)
        assertTrue("complete, self-contained copy" in text)
    }

    @Test
    fun `a partial export lists what is missing and why in plain words`() {
        val text = render()

        assertTrue("- people: this version of LogDate does not export it yet" in flat(text), text)
        assertTrue("see WHAT IS MISSING below" in text)
    }

    @Test
    fun `a date range is stated in the missing section`() {
        val text = render()

        assertTrue("limited to those from 2026-09-17 to 2026-09-17" in flat(text), text)
    }

    @Test
    fun `it gives a command to check the files on each system`() {
        val text = render()

        assertTrue("shasum -a 256 -c SHA256SUMS" in text)
        assertTrue("Get-FileHash" in text)
    }

    @Test
    fun `it counts what is in the copy with the right singular and plural`() {
        val one = ArchiveSamples.manifest.copy(counts = ArchiveCounts(1, 1, 0, 1, 0, 0, hasProfile = false))
        val many = ArchiveSamples.manifest.copy(counts = ArchiveCounts(2, 3, 0, 4, 0, 0, hasProfile = false))

        val single = render(manifest = one)
        val plural = render(manifest = many)

        assertTrue("  1 journal\n" in single)
        assertTrue("  1 entry\n" in single)
        assertTrue("  1 photo, video or voice recording\n" in single)
        assertTrue("  2 journals\n" in plural)
        assertTrue("  3 entries\n" in plural)
        assertTrue("  4 photos, videos and voice recordings\n" in plural)
    }

    @Test
    fun `it shows when it was made in the zone the export was made in`() {
        assertTrue("Made on 2026-09-17 at 21:30 (America/Denver)." in render())
    }

    @Test
    fun `an export with nothing in it says so instead of listing zero counts`() {
        val empty = ArchiveSamples.manifest.copy(counts = ArchiveCounts(0, 0, 0, 0, 0, 0, hasProfile = false))

        assertTrue("Nothing yet: there were no entries to export." in render(manifest = empty))
    }

    @Test
    fun `the whole readme matches the reviewed text`() {
        val text = render(manifest = ArchiveSamples.manifest.copy(scope = ArchiveScope(complete = true)))

        assertEquals(GOLDEN.replace('§', '$'), text)
    }

    private companion object {
        // § stands for a dollar sign, which a Kotlin raw string would read as a template.
        val GOLDEN =
            """
            YOUR LOGDATE EXPORT
            ===================

            Made on 2026-09-17 at 21:30 (America/Denver).

            This folder is a complete, self-contained copy of what LogDate kept for you.
            You do not need the LogDate app, an account or an internet connection to open
            anything in it. Nothing in it is encrypted, so keep it somewhere private.

            WHAT IS IN THIS COPY
            --------------------
              1 journal
              2 entries
              1 photo, video or voice recording
              1 unfinished draft
              1 saved place
              1 location point
              your profile

            WHERE TO LOOK
            -------------
              journal/html/index.html     START HERE. Open this page in a web browser to
                                          read your entries day by day. It also prints
                                          well.
              journal/markdown/index.md   Your entries as plain text files. They open in
                                          any text editor and in apps such as Obsidian.
              media/                      Your photos, videos and voice recordings. Each
                                          is named for when it was captured, so they
                                          sort in order.
              data/                       The same information in a form other programs
                                          can read (JSON). You can ignore this unless
                                          you are moving to another app or writing a
                                          script.
              schema/                     Descriptions of the files in data/, for
                                          programmers.
              manifest.json               A summary of this export: what it holds, what
                                          it leaves out and why.
              SHA256SUMS                  A checklist for confirming that no file was
                                          damaged.

            WHAT IS MISSING
            ---------------
            Nothing. This export holds everything LogDate keeps for you.

            TIMES
            -----
            Times in the readable copy are shown in the time zone you were in when you
            wrote each entry, where LogDate recorded it, and otherwise in America/Denver.
            The data files store every time in UTC, marked by the letter Z at the end.

            CHECKING YOUR FILES (OPTIONAL)
            ------------------------------
            SHA256SUMS lets you confirm that every file is exactly as it was exported.
            Open a terminal in this folder and run:

              macOS or Linux:  shasum -a 256 -c SHA256SUMS
              Windows (PowerShell):
                Get-Content SHA256SUMS | ForEach-Object { §h, §f = §_ -split '  ', 2;
                  if ((Get-FileHash §f -Algorithm SHA256).Hash.ToLower() -ne §h) { "CHANGED: §f" } }

            Every file reporting OK, or no CHANGED lines, means nothing was damaged. It
            does not show who made the files.

            MOVING TO ANOTHER APP
            ---------------------
            The files in data/ are JSON, which most apps and programming languages can
            read, and manifest.json lists each one with the schema that describes it. The
            Markdown files can be copied straight into a note-taking app.

            This is LogDate export format 2.0.
            """.trimIndent() + "\n"
    }
}
