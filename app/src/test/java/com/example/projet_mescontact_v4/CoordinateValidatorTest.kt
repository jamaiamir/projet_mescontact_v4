package com.example.projet_mescontact_v4

import com.example.projet_mescontact_v4.util.CoordinateValidator
import com.example.projet_mescontact_v4.util.CoordinateValidator.Error
import com.example.projet_mescontact_v4.util.CoordinateValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateValidatorTest {

    @Test fun deuxChampsVides_aucunePosition() =
        assertTrue(CoordinateValidator.parse("", " ") is Result.Empty)

    @Test fun virguleAcceptee() =
        assertEquals(Result.Valid(36.8065, 10.18), CoordinateValidator.parse("36,8065", "10.18"))

    @Test fun bornesIncluses() =
        assertTrue(CoordinateValidator.parse("-90", "180") is Result.Valid)

    @Test fun latitudeHorsBornes() =
        assertEquals(Result.Invalid(Error.OUT_OF_RANGE, null), CoordinateValidator.parse("91", "0"))

    @Test fun longitudeHorsBornes() =
        assertEquals(Result.Invalid(null, Error.OUT_OF_RANGE), CoordinateValidator.parse("0", "-180.1"))

    @Test fun nonNumerique_etManquant() =
        assertEquals(Result.Invalid(Error.NOT_A_NUMBER, Error.MISSING), CoordinateValidator.parse("abc", ""))

    @Test fun nanEtInfiniRefuses() =
        assertTrue(CoordinateValidator.parse("NaN", "Infinity") is Result.Invalid)

    @Test fun formatage() {
        assertEquals("36.8065", CoordinateValidator.format(36.8065))
        assertEquals("10", CoordinateValidator.format(10.0))
        assertEquals("", CoordinateValidator.format(null))
    }
}
