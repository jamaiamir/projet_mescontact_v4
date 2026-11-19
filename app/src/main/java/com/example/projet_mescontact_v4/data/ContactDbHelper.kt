package com.example.projet_mescontact_v4.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.projet_mescontact_v4.data.ContactContract.ContactEntry

/**
 * Crée et fait évoluer la base `contacts.db`.
 *
 * - onCreate : appelé UNE SEULE FOIS, quand le fichier n'existe pas encore (premier lancement).
 * - onUpgrade : appelé quand DATABASE_VERSION est supérieure à la version du fichier existant.
 *
 * Singleton construit avec applicationContext : une seule connexion partagée,
 * et aucune fuite d'Activity/Fragment.
 */
class ContactDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, ContactContract.DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // On rejoue les étapes de migration : une base neuve a exactement le même schéma
        // qu'une base v1 migrée (une seule source de vérité pour le schéma).
        db.execSQL(ContactContract.SQL_CREATE_V1)
        db.execSQL(ContactContract.SQL_ADD_FAVORI)
        insertDemoContacts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations incrémentales : les contacts existants sont conservés.
        if (oldVersion < 2) db.execSQL(ContactContract.SQL_ADD_FAVORI)
        // if (oldVersion < 3) { ... } pour une future version
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Cas de développement uniquement (retour à un APK plus ancien) : on repart de zéro.
        db.execSQL(ContactContract.SQL_DROP)
        onCreate(db)
    }

    /** onCreate s'exécute déjà dans une transaction : les insertions sont atomiques. */
    private fun insertDemoContacts(db: SQLiteDatabase) {
        demoContacts().forEach { c ->
            val values = ContentValues().apply {
                put(ContactEntry.COLUMN_NOM, c.nom)
                put(ContactEntry.COLUMN_PRENOM, c.prenom)
                put(ContactEntry.COLUMN_TELEPHONE, c.telephone)
                put(ContactEntry.COLUMN_EMAIL, c.email)
                if (c.latitude != null) put(ContactEntry.COLUMN_LATITUDE, c.latitude)
                else putNull(ContactEntry.COLUMN_LATITUDE)
                if (c.longitude != null) put(ContactEntry.COLUMN_LONGITUDE, c.longitude)
                else putNull(ContactEntry.COLUMN_LONGITUDE)
            }
            db.insert(ContactEntry.TABLE_NAME, null, values)
        }
    }

    private fun demoContacts() = listOf(
        Contact(nom = "Ben Salah", prenom = "Amira", telephone = "+216 20 123 456",
            email = "amira.bensalah@example.com", latitude = 36.8065, longitude = 10.1815),   // Tunis
        Contact(nom = "Trabelsi", prenom = "Youssef", telephone = "+216 22 987 654",
            email = "youssef.trabelsi@example.com", latitude = 34.7406, longitude = 10.7603), // Sfax
        Contact(nom = "Gharbi", prenom = "Mariem", telephone = "+216 55 444 321",
            email = "mariem.gharbi@example.com", latitude = 35.8256, longitude = 10.6360),    // Sousse
        Contact(nom = "Dupont", prenom = "Claire", telephone = "06 12 34 56 78",
            email = "claire.dupont@example.com", latitude = 48.8584, longitude = 2.2945),     // Paris
        // Deux contacts SANS coordonnées GPS (exigence de l'énoncé)
        Contact(nom = "Jlassi", prenom = "Karim", telephone = "+216 98 111 222",
            email = "karim.jlassi@example.com"),
        Contact(nom = "O'Neil", prenom = "Sarah", telephone = "07 55 66 77 88",
            email = "sarah.oneil@example.com")
    )

    companion object {
        /** v1 = schéma de l'énoncé ; v2 = ajout de la colonne favori (bonus). */
        const val DATABASE_VERSION = 2

        @Volatile
        private var instance: ContactDbHelper? = null

        fun getInstance(context: Context): ContactDbHelper =
            instance ?: synchronized(this) {
                instance ?: ContactDbHelper(context.applicationContext).also { instance = it }
            }
    }
}
