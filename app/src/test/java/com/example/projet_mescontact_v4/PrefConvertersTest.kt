package com.example.projet_mescontact_v4

import com.example.projet_mescontact_v4.data.PrefConverters
import com.example.projet_mescontact_v4.data.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefConvertersTest {

    @Test fun taillesValides() {
        assertEquals(14f, PrefConverters.taille("14"))
        assertEquals(16f, PrefConverters.taille("16"))
        assertEquals(20f, PrefConverters.taille("20"))
    }

    @Test fun tailleInvalide_defaut16() {
        assertEquals(16f, PrefConverters.taille(null))
        assertEquals(16f, PrefConverters.taille("abc"))
        assertEquals(16f, PrefConverters.taille("99"))
    }

    @Test fun couleurs() {
        assertEquals(0xFF000000.toInt(), PrefConverters.couleur("#000000"))
        assertEquals(0xFF1565C0.toInt(), PrefConverters.couleur("#1565C0"))
        assertEquals(0xFF000000.toInt(), PrefConverters.couleur("rouge"))   // invalide → noir
        assertEquals(0xFF000000.toInt(), PrefConverters.couleur(null))
    }

    @Test fun ordreTri() {
        assertEquals(SortOrder.NOM, SortOrder.fromPref("nom"))
        assertEquals(SortOrder.FAVORIS, SortOrder.fromPref("inconnu"))
    }
}
