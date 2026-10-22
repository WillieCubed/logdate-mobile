package app.logdate.client.feature.widgets

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

/** Reads restored bytes without opening a cached or cross-process SharedPreferences instance. */
internal fun parseWidgetSourcePreferences(bytes: ByteArray): Map<Int, WidgetSettingValues> {
    require(bytes.size <= 1_048_576)
    val handler = WidgetPreferencesXmlHandler()
    val reader = SAXParserFactory.newInstance().newSAXParser().xmlReader
    reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
    reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
    reader.contentHandler = handler
    reader.entityResolver = handler
    reader.errorHandler = handler
    reader.parse(InputSource(ByteArrayInputStream(bytes)))
    return handler.values()
}

private class WidgetPreferencesXmlHandler : DefaultHandler2() {
    private var depth = 0
    private var current: Pair<Int, String>? = null
    private var currentFlag: Boolean? = null
    private val text = StringBuilder()
    private val strings = mutableMapOf<Int, MutableMap<String, String>>()
    private val flags = mutableMapOf<Int, MutableMap<String, Boolean>>()
    private val seen = mutableSetOf<String>()

    override fun startDTD(
        name: String?,
        publicId: String?,
        systemId: String?,
    ): Nothing = throw SAXException("Document types are not widget choices")

    override fun resolveEntity(
        publicId: String?,
        systemId: String?,
    ): InputSource = throw SAXException("External widget metadata is forbidden")

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String,
        attributes: Attributes,
    ) {
        if (depth == 0) require(qName == "map")
        if (depth > 1 && current != null) throw SAXException("Nested widget choices are invalid")
        if (depth == 1) {
            val name = attributes.getValue("name").orEmpty()
            val id = name.substringBefore('.').toIntOrNull()?.takeIf { it > 0 }
            val field = name.substringAfter('.', "")
            val knownString = field in setOf("chosenNoteId", "photoShape", "recallMode", "contentTypes")
            if (id != null && (knownString || field == "usePhotoPrompt")) {
                require(seen.add(name))
                require(qName == if (knownString) "string" else "boolean")
                current = id to field
                currentFlag = if (knownString) null else requireNotNull(attributes.getValue("value").toBooleanStrictOrNull())
                text.setLength(0)
            }
        }
        depth++
    }

    override fun characters(
        ch: CharArray,
        start: Int,
        length: Int,
    ) {
        if (current != null) text.append(ch, start, length)
    }

    override fun endElement(
        uri: String?,
        localName: String?,
        qName: String?,
    ) {
        depth--
        if (depth == 1) {
            current?.let { (id, field) ->
                if (currentFlag == null) {
                    strings.getOrPut(id) { mutableMapOf() }[field] = text.toString()
                } else {
                    flags.getOrPut(id) { mutableMapOf() }[field] = requireNotNull(currentFlag)
                }
            }
            current = null
            currentFlag = null
        }
    }

    fun values(): Map<Int, WidgetSettingValues> =
        (strings.keys + flags.keys).associateWith { WidgetSettingValues(strings[it].orEmpty(), flags[it].orEmpty()) }
}
