package com.example.projet_mescontact_v4.ui

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projet_mescontact_v4.MainActivity
import com.example.projet_mescontact_v4.R
import com.example.projet_mescontact_v4.data.Contact
import com.example.projet_mescontact_v4.data.ContactViewModel
import com.example.projet_mescontact_v4.data.PreferencesManager
import com.example.projet_mescontact_v4.databinding.FragmentListeContactsBinding
import com.example.projet_mescontact_v4.util.IntentUtils

/** Liste chargée depuis SQLite, recherche instantanée, bouton « + », appel par ligne. */
class ListeContactsFragment : Fragment(R.layout.fragment_liste_contacts) {

    private var _binding: FragmentListeContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()
    private lateinit var prefsManager: PreferencesManager
    private lateinit var adapter: ContactAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListeContactsBinding.bind(view)
        prefsManager = PreferencesManager(requireContext())

        adapter = ContactAdapter(
            prefsManager.display(),
            onClick = { contact -> (requireActivity() as MainActivity).openDetail(contact.id) },
            onCall = { contact -> IntentUtils.dial(requireContext(), contact.telephone) }
        )
        binding.recyclerContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerContacts.adapter = adapter

        // Create : ouvre le détail en mode création (id = 0)
        binding.fabAdd.setOnClickListener { (requireActivity() as MainActivity).openDetail(Contact.NEW_ID) }

        if (binding.editSearch.text.toString() != viewModel.query) binding.editSearch.setText(viewModel.query)
        binding.editSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            adapter.submitList(state.contacts)
            val searching = state.query.isNotBlank()
            binding.txtEmpty.visibility = if (state.contacts.isEmpty()) View.VISIBLE else View.GONE
            binding.txtEmpty.text = when {
                state.contacts.isNotEmpty() -> ""
                searching -> getString(R.string.empty_search, state.query.trim())
                else -> getString(R.string.empty_list)
            }
            binding.txtCount.text = if (searching)
                getString(R.string.count_filtered, state.contacts.size, state.total)
            else resources.getQuantityString(R.plurals.count_all, state.total, state.total)
        }
    }

    /**
     * Appelé à chaque affichage, notamment au retour des Paramètres ou du détail :
     * relecture des préférences + rechargement depuis la base → effet immédiat.
     */
    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.title_main)
        adapter.setDisplayPrefs(prefsManager.display())
        viewModel.refresh()          // relit la base avec l'ordre de tri éventuellement modifié
    }

    override fun onDestroyView() {
        binding.recyclerContacts.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "liste"
    }
}
