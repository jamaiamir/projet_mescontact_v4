package com.example.projet_mescontact_v4

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import com.example.projet_mescontact_v4.databinding.ActivityMainBinding
import com.example.projet_mescontact_v4.ui.CarteContactFragment
import com.example.projet_mescontact_v4.ui.DetailContactFragment
import com.example.projet_mescontact_v4.ui.ListeContactsFragment
import com.example.projet_mescontact_v4.ui.ParametresFragment

/**
 * Unique activité : barre d'application (avec l'accès aux Paramètres) + FragmentContainerView.
 * Toute la navigation passe par ici (replace + addToBackStack).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Écrit les valeurs par défaut de preferences.xml au tout premier lancement
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        supportFragmentManager.addOnBackStackChangedListener { updateToolbar() }

        // Après rotation, le FragmentManager restaure lui-même la pile : pas de doublon.
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, ListeContactsFragment(), ListeContactsFragment.TAG)
            }
        }
        updateToolbar()
    }

    private fun updateToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
        invalidateOptionsMenu()
    }

    private fun currentFragment(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.fragment_container)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Pas d'icône Paramètres quand on y est déjà
        menu.findItem(R.id.action_settings)?.isVisible = currentFragment() !is ParametresFragment
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.action_settings) { openSettings(); true }
        else super.onOptionsItemSelected(item)

    /** Flèche « haut » = bouton Retour (le détail l'intercepte pour « Annuler »). */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ------------------------------- Navigation -------------------------------

    private fun navigate(fragment: Fragment, tag: String) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out
            )
            replace(R.id.fragment_container, fragment, tag)
            addToBackStack(tag)
        }
    }

    /** [contactId] = _id en base, ou Contact.NEW_ID (0) pour une création. */
    fun openDetail(contactId: Long) = navigate(DetailContactFragment.newInstance(contactId), DetailContactFragment.TAG)

    fun openMap(contactId: Long, name: String, latitude: String, longitude: String) =
        navigate(CarteContactFragment.newInstance(contactId, name, latitude, longitude), CarteContactFragment.TAG)

    fun openSettings() {
        if (currentFragment() is ParametresFragment) return
        navigate(ParametresFragment(), ParametresFragment.TAG)
    }

    fun goBack() {
        if (!supportFragmentManager.isStateSaved) supportFragmentManager.popBackStack()
    }
}
