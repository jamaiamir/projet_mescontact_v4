package com.example.projet_mescontact_v4

import com.example.projet_mescontact_v4.data.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test fun motifSimple() = assertEquals("%amira%", SearchQuery.likePattern("  amira "))

    /** L'apostrophe n'a pas besoin d'échappement : elle part en argument « ? », pas dans le SQL. */
    @Test fun apostropheConservee() = assertEquals("%O'Neil%", SearchQuery.likePattern("O'Neil"))

    @Test fun jokersEchappes() = assertEquals("%50\\%\\_x%", SearchQuery.likePattern("50%_x"))

    @Test fun espacesReduits() = assertEquals("%amira ben%", SearchQuery.likePattern("amira   ben"))

    @Test fun chiffresTelephone() {
        assertEquals("20123", SearchQuery.phoneDigits("20 123"))
        assertEquals("21620", SearchQuery.phoneDigits("+216 20"))
        assertNull(SearchQuery.phoneDigits("amira"))
        assertNull(SearchQuery.phoneDigits("a1"))
        assertNull(SearchQuery.phoneDigits("  "))
    }
}
