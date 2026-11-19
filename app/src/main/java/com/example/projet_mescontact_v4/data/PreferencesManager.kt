package com.example.projet_mescontact_v4.data

import android.content.Context
import androidx.annotation.ColorInt
import androidx.preference.PreferenceManager

/** Valeurs déjà converties, transmises telles quelles à l'adapter et au détail. */
data class DisplayPrefs(
    val tailleSp: Float,
    @ColorInt val couleur: Int,
    val appelDepuisListe: Boolean
)

/**
 * Lecture CENTRALISÉE des préférences (SharedPreferences par défaut de l'application).
 * Les clés sont les mêmes que dans res/xml/preferences.xml.
 */
class PreferencesManager(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun tailleSp(): Float = PrefConverters.taille(prefs.getString(KEY_TAILLE, DEFAULT_TAILLE))

    @ColorInt
    fun couleur(): Int = PrefConverters.couleur(prefs.getString(KEY_COULEUR, DEFAULT_COULEUR))

    fun appelDepuisListe(): Boolean = prefs.getBoolean(KEY_APPEL, false)

    fun ordreTri(): SortOrder = SortOrder.fromPref(prefs.getString(KEY_TRI, SortOrder.DEFAULT.prefValue))

    fun display() = DisplayPrefs(tailleSp(), couleur(), appelDepuisListe())

    companion object {
        const val KEY_TAILLE = "taille_police"
        const val KEY_COULEUR = "couleur_preferee"
        const val KEY_APPEL = "appel_depuis_liste"
        const val KEY_TRI = "ordre_tri"
        const val DEFAULT_TAILLE = "16"
        const val DEFAULT_COULEUR = "#000000"
    }
}

/** Conversions pures (testées) : une valeur corrompue retombe sur la valeur par défaut. */
object PrefConverters {
    private val TAILLES = setOf(14f, 16f, 20f)

    fun taille(value: String?): Float =
        value?.toFloatOrNull()?.takeIf { it in TAILLES } ?: PreferencesManager.DEFAULT_TAILLE.toFloat()

    @ColorInt
    fun couleur(value: String?): Int = parseHex(value) ?: parseHex(PreferencesManager.DEFAULT_COULEUR)!!

    /** Analyse « #RRGGBB » sans dépendre d'android.graphics (testable sur la JVM). */
    fun parseHex(value: String?): Int? {
        val v = value?.trim() ?: return null
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(v)) return null
        return (0xFF000000 or v.substring(1).toLong(16)).toInt()
    }
}
