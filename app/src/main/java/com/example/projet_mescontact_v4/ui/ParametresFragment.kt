package com.example.projet_mescontact_v4.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.projet_mescontact_v4.R

/**
 * Écran Paramètres. Les valeurs sont enregistrées AUTOMATIQUEMENT dans les SharedPreferences
 * par la bibliothèque AndroidX Preference ; les résumés affichent la valeur choisie
 * (useSimpleSummaryProvider dans preferences.xml).
 */
class ParametresFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.title_settings)
    }

    companion object {
        const val TAG = "parametres"
    }
}
