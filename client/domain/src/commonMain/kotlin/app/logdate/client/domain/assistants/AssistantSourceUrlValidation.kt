package app.logdate.client.domain.assistants

import app.logdate.client.repository.assistants.AssistantProtocolLimits
import io.ktor.http.URLDecodeException
import io.ktor.http.URLParserException
import io.ktor.http.Url

/** Validates stored web references without fetching them or normalizing the reviewed payload. */
internal object AssistantSourceUrlValidation {
    fun allows(value: String): Boolean {
        if (value.isBlank() || !AssistantProtocolLimits.textFits(value, AssistantProtocolLimits.URL_CODE_POINTS)) return false
        if (value.any { it.isWhitespace() || it.code < 32 || it.code == 127 || it == '\\' }) return false
        if (!value.startsWith("https://", ignoreCase = true) && !value.startsWith("http://", ignoreCase = true)) return false
        if (!validPercentEncoding(value)) return false
        val authority =
            value
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
        if (!validAuthority(authority)) return false
        return try {
            val url = Url(value)
            url.host.isNotBlank() && url.user == null && url.password == null
        } catch (_: URLParserException) {
            false
        } catch (_: URLDecodeException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun validAuthority(authority: String): Boolean {
        if (authority.isBlank() || '@' in authority) return false
        if (authority.startsWith('[')) {
            val end = authority.indexOf(']')
            if (end < 0 || !validIpv6(authority.substring(1, end))) return false
            val suffix = authority.substring(end + 1)
            return suffix.isEmpty() || (suffix.startsWith(':') && validPort(suffix.substring(1)))
        }
        if ('[' in authority || ']' in authority) return false
        val parts = authority.split(':')
        val host = parts.first()
        val validHost = host.isNotBlank() && host.all { it.isLetterOrDigit() || it.code >= 128 || it in "-._~!\$&'()*+,;=%" }
        return parts.size <= 2 && validHost && (parts.size == 1 || validPort(parts[1]))
    }

    private fun validPort(value: String): Boolean =
        value.isEmpty() || (value.all { it in '0'..'9' } && value.toIntOrNull()?.let { it in 0..65535 } == true)

    private fun validIpv6(literal: String): Boolean {
        val zoneStart = literal.indexOf("%25")
        val address = if (zoneStart >= 0) literal.substring(0, zoneStart) else literal
        if (zoneStart >= 0) {
            val zone = literal.substring(zoneStart + 3)
            if (zone.isEmpty() || zone.any { !it.isLetterOrDigit() && it !in "-._~%" }) return false
        }
        if (address.isEmpty() || ":::" in address || address.indexOf("::") != address.lastIndexOf("::")) return false
        if ((address.startsWith(':') && !address.startsWith("::")) ||
            (address.endsWith(':') && !address.endsWith("::"))
        ) {
            return false
        }
        val groups = address.split(':').filter { it.isNotEmpty() }
        var units = 0
        groups.forEachIndexed { index, group ->
            if ('.' in group) {
                if (index != groups.lastIndex || !address.endsWith(group) || !validIpv4(group)) return false
                units += 2
            } else {
                if (group.length !in 1..4 || group.any { !hex(it) }) return false
                units++
            }
        }
        return if ("::" in address) units < 8 else units == 8
    }

    private fun validIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 &&
            octets.all { octet ->
                octet.length in 1..3 &&
                    octet.all { it in '0'..'9' } &&
                    (octet.length == 1 || !octet.startsWith('0')) &&
                    octet.toIntOrNull()?.let { it in 0..255 } == true
            }
    }

    private fun validPercentEncoding(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (value[index++] != '%') continue
            if (index + 1 >= value.length || !hex(value[index]) || !hex(value[index + 1])) return false
            index += 2
        }
        return true
    }

    private fun hex(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}
