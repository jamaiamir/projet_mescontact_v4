package com.example.projet_mescontact_v4.data

import android.content.ContentValues
import android.database.Cursor
import androidx.annotation.WorkerThread
import com.example.projet_mescontact_v4.data.ContactContract.ContactEntry as E

/**
 * SEULE classe contenant du SQL. Toutes les valeurs variables passent par des arguments « ? ».
 * Méthodes bloquantes (@WorkerThread) : appelées uniquement depuis Dispatchers.IO.
 */
class ContactDao(private val helper: ContactDbHelper) {

    private val allColumns = arrayOf(
        E.COLUMN_ID, E.COLUMN_NOM, E.COLUMN_PRENOM, E.COLUMN_TELEPHONE, E.COLUMN_EMAIL,
        E.COLUMN_PHOTO_URI, E.COLUMN_LATITUDE, E.COLUMN_LONGITUDE, E.COLUMN_FAVORI
    )

    /** @return le nouvel _id, ou -1 en cas d'échec. */
    @WorkerThread
    fun insert(contact: Contact): Long =
        helper.writableDatabase.insert(E.TABLE_NAME, null, toValues(contact))

    /** @return nombre de lignes modifiées (1 = OK, 0 = contact introuvable). */
    @WorkerThread
    fun update(contact: Contact): Int = helper.writableDatabase.update(
        E.TABLE_NAME, toValues(contact), "${E.COLUMN_ID} = ?", arrayOf(contact.id.toString())
    )

    /** @return nombre de lignes supprimées (1 = OK, 0 = contact introuvable). */
    @WorkerThread
    fun delete(id: Long): Int = helper.writableDatabase.delete(
        E.TABLE_NAME, "${E.COLUMN_ID} = ?", arrayOf(id.toString())
    )

    @WorkerThread
    fun getAll(order: SortOrder = SortOrder.DEFAULT): List<Contact> =
        query(selection = null, args = null, order = order)

    @WorkerThread
    fun getById(id: Long): Contact? =
        query("${E.COLUMN_ID} = ?", arrayOf(id.toString()), SortOrder.DEFAULT).firstOrNull()

    /**
     * Recherche sur nom, prénom, « prénom nom », « nom prénom » et téléphone.
     * LIKE est insensible à la casse (lettres ASCII) ; « 20123 » trouve « +216 20 123 456 ».
     */
    @WorkerThread
    fun search(texte: String, order: SortOrder = SortOrder.DEFAULT): List<Contact> {
        if (texte.isBlank()) return getAll(order)
        val pattern = SearchQuery.likePattern(texte)
        val esc = "ESCAPE '${SearchQuery.ESCAPE_CHAR}'"   // constante, pas une saisie
        val clauses = mutableListOf(
            "${E.COLUMN_NOM} LIKE ? $esc",
            "${E.COLUMN_PRENOM} LIKE ? $esc",
            "${E.COLUMN_TELEPHONE} LIKE ? $esc",
            "(${E.COLUMN_PRENOM} || ' ' || ${E.COLUMN_NOM}) LIKE ? $esc",
            "(${E.COLUMN_NOM} || ' ' || ${E.COLUMN_PRENOM}) LIKE ? $esc"
        )
        val args = MutableList(clauses.size) { pattern }
        SearchQuery.phoneDigits(texte)?.let { digits ->
            clauses += "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(${E.COLUMN_TELEPHONE}," +
                "' ',''),'+',''),'-',''),'.',''),'(',''),')','') LIKE ?"
            args += "%$digits%"
        }
        return query(clauses.joinToString(" OR "), args.toTypedArray(), order)
    }

    @WorkerThread
    fun count(): Int = helper.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM ${E.TABLE_NAME}", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ------------------------------------------------------------------------------------------

    private fun query(selection: String?, args: Array<String>?, order: SortOrder): List<Contact> =
        helper.readableDatabase.query(
            E.TABLE_NAME, allColumns, selection, args, null, null, order.orderBy
        ).use { cursor ->                       // le Cursor est toujours fermé
            val iId = cursor.getColumnIndexOrThrow(E.COLUMN_ID)
            val iNom = cursor.getColumnIndexOrThrow(E.COLUMN_NOM)
            val iPrenom = cursor.getColumnIndexOrThrow(E.COLUMN_PRENOM)
            val iTel = cursor.getColumnIndexOrThrow(E.COLUMN_TELEPHONE)
            val iEmail = cursor.getColumnIndexOrThrow(E.COLUMN_EMAIL)
            val iPhoto = cursor.getColumnIndexOrThrow(E.COLUMN_PHOTO_URI)
            val iLat = cursor.getColumnIndexOrThrow(E.COLUMN_LATITUDE)
            val iLng = cursor.getColumnIndexOrThrow(E.COLUMN_LONGITUDE)
            val iFav = cursor.getColumnIndexOrThrow(E.COLUMN_FAVORI)
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Contact(
                            id = cursor.getLong(iId),
                            nom = cursor.getString(iNom),
                            prenom = cursor.getString(iPrenom),
                            telephone = cursor.getString(iTel),
                            email = cursor.stringOrNull(iEmail),
                            photoUri = cursor.stringOrNull(iPhoto),
                            latitude = cursor.doubleOrNull(iLat),
                            longitude = cursor.doubleOrNull(iLng),
                            favori = cursor.getInt(iFav) != 0
                        )
                    )
                }
            }
        }

    private fun toValues(c: Contact) = ContentValues().apply {
        put(E.COLUMN_NOM, c.nom)
        put(E.COLUMN_PRENOM, c.prenom)
        put(E.COLUMN_TELEPHONE, c.telephone)
        putOrNull(E.COLUMN_EMAIL, c.email?.takeIf { it.isNotBlank() })
        putOrNull(E.COLUMN_PHOTO_URI, c.photoUri)
        if (c.latitude != null && c.longitude != null) {
            put(E.COLUMN_LATITUDE, c.latitude)
            put(E.COLUMN_LONGITUDE, c.longitude)
        } else {                                // position absente = NULL dans les DEUX colonnes
            putNull(E.COLUMN_LATITUDE)
            putNull(E.COLUMN_LONGITUDE)
        }
        put(E.COLUMN_FAVORI, if (c.favori) 1 else 0)
    }

    private fun ContentValues.putOrNull(key: String, value: String?) =
        if (value == null) putNull(key) else put(key, value)

    private fun Cursor.stringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.doubleOrNull(i: Int): Double? = if (isNull(i)) null else getDouble(i)
}
