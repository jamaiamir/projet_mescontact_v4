package com.example.projet_mescontact_v4.ui

import android.graphics.Bitmap
import android.util.LruCache
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.projet_mescontact_v4.R
import com.example.projet_mescontact_v4.data.Contact
import com.example.projet_mescontact_v4.data.DisplayPrefs
import com.example.projet_mescontact_v4.databinding.ItemContactBinding
import com.example.projet_mescontact_v4.util.ImageUtils

/**
 * Adapter de la liste. Il reçoit des préférences DÉJÀ CONVERTIES ([DisplayPrefs]) et
 * ne lit jamais les SharedPreferences lui-même.
 * Les clics transmettent le Contact (donc son _id), jamais la position.
 */
class ContactAdapter(
    initialPrefs: DisplayPrefs,
    private val onClick: (Contact) -> Unit,
    private val onCall: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(DIFF) {

    private var prefs = initialPrefs
    private val thumbnails = LruCache<String, Bitmap>(50)

    /** Applique de nouvelles préférences ; ne redessine que si elles ont changé. */
    fun setDisplayPrefs(newPrefs: DisplayPrefs) {
        if (newPrefs == prefs) return
        prefs = newPrefs
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                currentContact()?.let(onClick)
            }
            // Le bouton consomme son clic : la fiche détaillée ne s'ouvre pas
            binding.btnCallRow.setOnClickListener {
                currentContact()?.let(onCall)
            }
        }

        private fun currentContact(): Contact? =
            bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { getItem(it) }

        fun bind(contact: Contact) = with(binding) {
            txtNom.text = contact.nom
            txtPrenom.text = contact.prenom
            txtTelephone.text = contact.telephone

            // ---- Préférences d'affichage ----
            txtNom.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.tailleSp)
            txtPrenom.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.tailleSp)
            txtTelephone.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.tailleSp - 2f)
            txtNom.setTextColor(prefs.couleur)
            txtPrenom.setTextColor(prefs.couleur)
            btnCallRow.visibility = if (prefs.appelDepuisListe) View.VISIBLE else View.GONE
            btnCallRow.contentDescription =
                root.context.getString(R.string.cd_call_contact, contact.nomComplet)

            imgFavori.visibility = if (contact.favori) View.VISIBLE else View.GONE
            imgLocation.visibility = if (contact.aUnePosition) View.VISIBLE else View.GONE

            val bitmap = contact.photoUri?.let { path ->
                thumbnails.get(path) ?: ImageUtils.decodeScaled(path, 160)?.also { thumbnails.put(path, it) }
            }
            if (bitmap != null) imgPhoto.setImageBitmap(bitmap)
            else imgPhoto.setImageResource(R.drawable.placeholder_avatar)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ContactViewHolder(
        ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(old: Contact, new: Contact) = old.id == new.id
            override fun areContentsTheSame(old: Contact, new: Contact) = old == new
        }
    }
}
