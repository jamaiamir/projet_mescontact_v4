package com.example.projet_mescontact_v4.util

/**
 * Validation des coordonnées saisies à la main.
 * Accepte la virgule comme séparateur décimal (« 36,8 » == « 36.8 »).
 */
object CoordinateValidator {

    sealed class Result {
        /** Les deux champs sont vides : le contact n'a pas de position. */
        object Empty : Result()
        data class Valid(val latitude: Double, val longitude: Double) : Result()
        /** [latError] / [lngError] : message d'erreur par champ, null si le champ est correct. */
        data class Invalid(val latError: Error?, val lngError: Error?) : Result()
    }

    enum class Error { MISSING, NOT_A_NUMBER, OUT_OF_RANGE }

    fun parse(latText: String?, lngText: String?): Result {
        val lat = latText.orEmpty().trim()
        val lng = lngText.orEmpty().trim()
        if (lat.isEmpty() && lng.isEmpty()) return Result.Empty

        val latValue = lat.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        val lngValue = lng.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

        val latError = when {
            lat.isEmpty() -> Error.MISSING
            latValue == null -> Error.NOT_A_NUMBER
            latValue !in -90.0..90.0 -> Error.OUT_OF_RANGE
            else -> null
        }
        val lngError = when {
            lng.isEmpty() -> Error.MISSING
            lngValue == null -> Error.NOT_A_NUMBER
            lngValue !in -180.0..180.0 -> Error.OUT_OF_RANGE
            else -> null
        }
        return if (latError == null && lngError == null) Result.Valid(latValue!!, lngValue!!)
        else Result.Invalid(latError, lngError)
    }

    /** Affichage avec point décimal et sans notation scientifique. */
    fun format(value: Double?): String =
        value?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()
}
