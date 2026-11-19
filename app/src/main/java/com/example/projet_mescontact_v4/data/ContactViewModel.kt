package com.example.projet_mescontact_v4.data

import android.app.Application
import android.database.SQLException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.projet_mescontact_v4.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ce qu'affiche la liste : résultats, nombre total en base et filtre utilisé. */
data class ListState(val contacts: List<Contact>, val total: Int, val query: String)

sealed class DbResult {
    data class Inserted(val id: Long) : DbResult()
    object Updated : DbResult()
    object Deleted : DbResult()
    object NotFound : DbResult()             // update/delete ont renvoyé 0
    data class Failed(val error: Throwable?) : DbResult()   // insert = -1 ou exception SQL
}

/**
 * ViewModel PARTAGÉ (portée MainActivity). Il lance chaque appel au DAO dans viewModelScope
 * avec withContext(Dispatchers.IO) : aucun accès disque sur le thread principal.
 * Après chaque écriture, la liste est rechargée depuis la base → la liste est notifiée.
 */
class ContactViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = ContactDao(ContactDbHelper.getInstance(app))
    private val prefs = PreferencesManager(app)

    private val _state = MutableLiveData<ListState>()
    val state: LiveData<ListState> = _state

    var query: String = ""
        private set

    private var loadJob: Job? = null

    fun setQuery(text: String) {
        if (text == query) return
        query = text
        refresh()
    }

    /** Relit la base (avec le filtre et l'ordre de tri courants). Une requête par frappe :
     *  la précédente est annulée pour que seul le dernier résultat s'affiche. */
    fun refresh() {
        loadJob?.cancel()
        val q = query
        val order = prefs.ordreTri()
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ListState(dao.search(q, order), dao.count(), q) }
            }
            result.onSuccess { _state.value = it }
                .onFailure { _state.value = ListState(emptyList(), 0, q) }
        }
    }

    /** Lecture de la fiche depuis la base, à chaque affichage du détail. */
    suspend fun loadContact(id: Long): Contact? = withContext(Dispatchers.IO) {
        runCatching { dao.getById(id) }.getOrNull()
    }

    /**
     * Insère (id == 0) ou met à jour le contact.
     * [oldPhoto] : photo enregistrée avant modification, supprimée du disque si elle est remplacée.
     */
    fun save(contact: Contact, oldPhoto: String?, onDone: (DbResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (contact.estNouveau) {
                        val id = dao.insert(contact)
                        if (id == -1L) DbResult.Failed(null) else DbResult.Inserted(id)
                    } else {
                        if (dao.update(contact) == 1) {
                            if (oldPhoto != null && oldPhoto != contact.photoUri) ImageUtils.deleteQuietly(oldPhoto)
                            DbResult.Updated
                        } else DbResult.NotFound
                    }
                } catch (e: SQLException) {
                    DbResult.Failed(e)          // ex. contrainte NOT NULL non respectée
                }
            }
            refresh()
            onDone(result)
        }
    }

    fun delete(contact: Contact, onDone: (DbResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (dao.delete(contact.id) == 1) {
                        ImageUtils.deleteQuietly(contact.photoUri)
                        DbResult.Deleted
                    } else DbResult.NotFound
                } catch (e: SQLException) {
                    DbResult.Failed(e)
                }
            }
            refresh()
            onDone(result)
        }
    }
}
