package com.mrturingdev.ssclassify.telemetry

/**
 * Strips anything that could identify a user's screenshots from crash
 * messages before they leave the device: URIs, paths, quoted values, image
 * and data file names (a screenshot name carries the app it was taken in,
 * like a bank), and long numbers (account and reference numbers). Messages
 * like "no such column: screenshot.title" survive, since they are often the
 * fastest way to a cause.
 */
object CrashRedactor {

    const val REDACTED = "<redacted>"

    // Order matters: whole URIs and paths go before the pieces inside them.
    private val rules = listOf(
        Regex("""\b[a-z][a-z0-9+.\-]*://\S+""", RegexOption.IGNORE_CASE),
        Regex("""(?:/[^\s/:'"]+){2,}/?"""),
        Regex("""'[^']*'|"[^"]*""""),
        Regex("""[\w.\-]+\.(?:jpe?g|png|webp|heic|heif|gif|bmp|pdf|db|json|txt)\b""", RegexOption.IGNORE_CASE),
        Regex("""\d{5,}"""),
    )

    fun redact(message: String): String = rules.fold(message) { text, rule -> rule.replace(text, REDACTED) }
}
