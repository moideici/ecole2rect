package fr.ecole2recte.app.domain

import org.junit.Assert.*
import org.junit.Test

class AverageReconciliationTest {
    private fun grade(id: String, value: Double, date: String? = "2026-10-$id", counted: Boolean = true) =
        GradeEntry(id, "Maths", 0, "DS $id", value, 20.0, 1.0, date, null, null, null, counted)
    private val notes = listOf(grade("01", 12.0), grade("02", 16.0), grade("03", 20.0))

    @Test fun `un bulletin a jour conserve son origine officielle`() {
        assertEquals(AverageOrigin.Official, AverageReconciliation.reconcile(16.0, notes, "2026-10-03").origin)
    }
    @Test fun `dateCalcul identifie une note plus recente`() {
        val result = AverageReconciliation.reconcile(14.0, notes, "2026-10-02")
        assertEquals(16.0, result.value!!, .0001)
        assertEquals(listOf("03"), result.behind.map { it.id })
        assertEquals(14.0, result.official!!, .0001)
    }
    @Test fun `sans dateCalcul retrouve une puis deux notes ignorees`() {
        assertEquals(1, AverageReconciliation.reconcile(14.0, notes, null).behind.size)
        assertEquals(2, AverageReconciliation.reconcile(12.0, notes, null).behind.size)
    }
    @Test fun `meme jour exige que la moyenne officielle reapparaisse`() {
        val sameDay = notes.map { it.copy(date = "2026-10-03") }
        assertEquals(AverageOrigin.Recomputed, AverageReconciliation.reconcile(14.0, sameDay, "2026-10-03").origin)
        assertEquals(AverageOrigin.Official, AverageReconciliation.reconcile(13.0, sameDay, "2026-10-03").origin)
    }
    @Test fun `une date inconnue empeche de deviner la chronologie`() {
        assertTrue(AverageReconciliation.reconcile(12.0, notes.map { if (it.id == "03") it.copy(date = null) else it }, null).behind.isEmpty())
    }
    @Test fun `un ecart inexplique laisse raison au bulletin`() {
        assertEquals(13.0, AverageReconciliation.reconcile(13.0, notes, null).value!!, .0001)
    }
    @Test fun `la tolerance respecte les arrondis du bulletin`() {
        assertEquals(AverageOrigin.Official, AverageReconciliation.reconcile(16.05, notes, null).origin)
        assertEquals(AverageOrigin.Recomputed, AverageReconciliation.reconcile(14.05, notes, null).origin)
        assertEquals(AverageOrigin.Official, AverageReconciliation.reconcile(14.06, notes, null).origin)
    }
    @Test fun `les absences coefficients nuls et non significatives ne prouvent aucun retard`() {
        val ignored = listOf(grade("04", 0.0, counted = false), grade("05", 0.0).copy(coefficient = 0.0), grade("06", 0.0).copy(value = null))
        assertTrue(AverageReconciliation.reconcile(16.0, notes + ignored, "2026-10-03").behind.isEmpty())
    }
    @Test fun `sans officiel calcule et sans notes ne fabrique rien`() {
        assertEquals(AverageOrigin.Computed, AverageReconciliation.reconcile(null, notes, null).origin)
        assertEquals(AverageOrigin.None, AverageReconciliation.reconcile(null, emptyList(), null).origin)
    }
}
