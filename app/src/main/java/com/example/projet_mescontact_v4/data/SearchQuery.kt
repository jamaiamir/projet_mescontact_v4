package com.example.projet_mescontact_v4.data

/**
 * Prépare les ARGUMENTS d'une recherche LIKE (logique pure, testée unitairement).
 * La saisie n'est jamais insérée dans le texte SQL : elle part dans selectionArgs.
 */
object SearchQuery {

    const val ESCAPE_CHAR = '\\'
    private const val PHONE_SEPARATORS = " +-.()"

    /** « 50% » → « %50\%% » : % et _ tapés par l'utilisateur sont pris littéralement. */
    fun likePattern(text: String): String {
        val escaped = buildString {
            for (ch in text.trim().replace(Regex("\\s+"), " ")) {
                if (ch == '%' || ch == '_' || ch == ESCAPE_CHAR) append(ESCAPE_CHAR)
                append(ch)
            }
        }
        return "%$escaped%"
    }

    /** Chiffres à chercher dans le téléphone, ou null si la saisie n'est pas « numérique ». */
    fun phoneDigits(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || !t.all { it.isDigit() || it in PHONE_SEPARATORS }) return null
        return t.filter { it.isDigit() }.ifEmpty { null }
    }
}
