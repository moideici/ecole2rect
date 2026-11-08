package fr.ecole2recte.app.domain

import org.junit.Assert.*
import org.junit.Test

class ClassPositionTest {
    @Test fun `rang et effectif ne sont affiches que fournis et coherents`() {
        assertEquals("Rang : 3 sur 28", ClassPosition(rank = 3, size = 28).rankLabel())
        for (position in listOf(ClassPosition(), ClassPosition(0, 28), ClassPosition(29, 28), ClassPosition(null, 28)))
            assertNull(position.rankLabel())
        assertEquals("Rang : 3", ClassPosition(3, null).rankLabel())
        assertEquals("Effectif : 28", ClassPosition(null, 28).sizeLabel())
        assertNull(ClassPosition(size = 0).sizeLabel())
    }

    @Test fun `histogramme garde le zero et omet chaque donnee absente`() {
        val bars = ClassPosition(minimum = 0.0, average = 12.0, median = 13.0).bars(15.0)
        assertEquals(listOf("Minimum", "Classe", "Moi", "Médiane"), bars.map { it.label })
        assertEquals(0.0, bars.first().value, 0.0)
        assertEquals(.75f, bars[2].fraction, .00001f)
        assertEquals(listOf("Moi"), ClassPosition(minimum = -2.0, median = Double.NaN).bars(14.0).map { it.label })
    }

    @Test fun `description vocale francaise couvre chaque barre et le rang`() {
        val description = ClassPosition(3, 28, 6.0, 12.0, 18.0, 13.0).describe("Maths", 15.0, 2)
        assertEquals("Maths : Rang : 3 sur 28. Minimum 6,00 sur 20 ; Classe 12,00 sur 20 ; Moi 15,00 sur 20 ; Médiane 13,00 sur 20 ; Maximum 18,00 sur 20.", description)
        assertEquals("Maths : aucune comparaison de classe disponible.", ClassPosition().describe("Maths", null, 2))
    }

    @Test fun `le mode montrer masque aussi rang mediane et extremes generaux`() {
        val position = ClassPosition(3, 28, 6.0, 12.0, 18.0, 13.0)
        val line = SubjectAverage(Subject("Maths", "", "", 0), "15,00", classPosition = position)
        val overview = GradeOverview("15,00", "", listOf(line), classPosition = position)
        val masked = PrivacyMode(enabled = true).mask(overview)
        assertNull(masked.classPosition)
        assertNull(masked.bySubject.single().classPosition)
    }
}
