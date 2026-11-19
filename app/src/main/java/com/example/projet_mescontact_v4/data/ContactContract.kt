package com.example.projet_mescontact_v4.data

import android.provider.BaseColumns

/** Noms de la table et des colonnes : aucun nom de colonne n'est écrit en dur ailleurs. */
object ContactContract {

    const val DATABASE_NAME = "contacts.db"

    object ContactEntry : BaseColumns {
        const val TABLE_NAME = "contacts"
        const val COLUMN_ID = BaseColumns._ID          // "_id"
        const val COLUMN_NOM = "nom"
        const val COLUMN_PRENOM = "prenom"
        const val COLUMN_TELEPHONE = "telephone"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PHOTO_URI = "photo_uri"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        /** Bonus — ajoutée en version 2. */
        const val COLUMN_FAVORI = "favori"
    }

    /** Schéma de la version 1 (énoncé, section 3.1). */
    val SQL_CREATE_V1 = """
        CREATE TABLE ${ContactEntry.TABLE_NAME} (
            ${ContactEntry.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${ContactEntry.COLUMN_NOM} TEXT NOT NULL,
            ${ContactEntry.COLUMN_PRENOM} TEXT NOT NULL,
            ${ContactEntry.COLUMN_TELEPHONE} TEXT NOT NULL,
            ${ContactEntry.COLUMN_EMAIL} TEXT,
            ${ContactEntry.COLUMN_PHOTO_URI} TEXT,
            ${ContactEntry.COLUMN_LATITUDE} REAL,
            ${ContactEntry.COLUMN_LONGITUDE} REAL
        )
    """.trimIndent()

    /** Migration 1 → 2 : ajout de la colonne favori sans perte de données. */
    val SQL_ADD_FAVORI = "ALTER TABLE ${ContactEntry.TABLE_NAME} " +
        "ADD COLUMN ${ContactEntry.COLUMN_FAVORI} INTEGER NOT NULL DEFAULT 0"

    const val SQL_DROP = "DROP TABLE IF EXISTS ${ContactEntry.TABLE_NAME}"
}
