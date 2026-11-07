package fr.ecole2recte.app.sync

import fr.ecole2recte.app.data.FailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SyncJournalTest {
    private class Memory : JournalStorage {
        var text = ""
        override fun read() = text
        override fun write(text: String) {
            this.text = text
        }
    }

    private fun entry(n: Int, result: SyncResultCode = SyncResultCode.Done, items: Int = 0) =
        SyncJournalEntry(startedAt = 1_000L * n, durationMs = 2_000, result = result, newItems = items)

    @Test
    fun `le journal est borne aux trente derniers passages`() {
        val journal = SyncJournal(Memory())
        (1..45).forEach { journal.record(entry(it)) }
        val entries = journal.entries()
        assertEquals(30, entries.size)
        assertEquals(45_000L, entries.first().startedAt) // le plus récent d'abord
        assertEquals(16_000L, entries.last().startedAt)
    }

    @Test
    fun `le journal survit a une relecture du stockage`() {
        val storage = Memory()
        SyncJournal(storage).record(entry(1, SyncResultCode.PasswordRefused, 0))
        SyncJournal(storage).record(entry(2, SyncResultCode.Done, 3))
        val read = SyncJournal(storage).entries()
        assertEquals(listOf(SyncResultCode.Done, SyncResultCode.PasswordRefused), read.map { it.result })
        assertEquals(3, read.first().newItems)
    }

    @Test
    fun `les lignes abimees sont ignorees et un code inconnu devient une erreur`() {
        val storage = Memory().also { it.text = "n'importe quoi\n5;10;INCONNU;2\n7;x;Done;1\n9;100;Done;1" }
        val read = SyncJournal(storage).entries()
        assertEquals(listOf(9L, 5L), read.map { it.startedAt })
        assertEquals(SyncResultCode.Error, read.last().result)
    }

    @Test
    fun `le fichier ne contient que des nombres et un code`() {
        val storage = Memory()
        SyncJournal(storage).record(entry(1, SyncResultCode.Blocked, 2))
        assertTrue(storage.text.matches(Regex("""\d+;\d+;[A-Za-z]+;\d+""")))
    }

    @Test
    fun `chaque echec typé a son code et sa famille`() {
        assertEquals(SyncResultCode.NoNetwork, SyncResultCode.fromFailure(FailureKind.Network))
        assertEquals(SyncResultCode.ServiceDown, SyncResultCode.fromFailure(FailureKind.Service))
        assertEquals(SyncResultCode.PasswordRefused, SyncResultCode.fromFailure(FailureKind.InvalidCredentials))
        assertEquals(SyncResultCode.Blocked, SyncResultCode.fromFailure(FailureKind.Blocked))
        assertEquals(SyncResultCode.SecurityRefused, SyncResultCode.fromFailure(FailureKind.SecurityRefused))
        assertEquals(SyncResultCode.ApiVersionRefused, SyncResultCode.fromFailure(FailureKind.StaleVersion))
        assertEquals(SyncResultCode.Error, SyncResultCode.fromFailure(FailureKind.Other))
        FailureKind.entries.forEach { SyncResultCode.fromFailure(it) } // aucun oubli
        assertEquals(SyncResultCode.Group.Stopped, SyncResultCode.Blocked.group)
        assertEquals(SyncResultCode.Group.NoNetwork, SyncResultCode.NoNetwork.group)
    }

    @Test
    fun `une ligne se lit en francais`() {
        val paris = ZoneId.of("Europe/Paris")
        val at = ZonedDateTime.of(2026, 11, 5, 18, 2, 0, 0, paris).toInstant().toEpochMilli()
        assertEquals(
            "5 nov. à 18 h 02 · 4 s · réussie · 2 nouveautés",
            SyncJournalEntry(at, 4_200, SyncResultCode.Done, 2).describe(paris),
        )
        assertEquals(
            "5 nov. à 18 h 02 · moins d'1 s · arrêtée : mot de passe refusé · aucune nouveauté",
            SyncJournalEntry(at, 300, SyncResultCode.PasswordRefused, 0).describe(paris),
        )
        assertEquals(
            "5 nov. à 18 h 02 · 1 min 05 s · sans réseau · 1 nouveauté",
            SyncJournalEntry(at, 65_000, SyncResultCode.NoNetwork, 1).describe(paris),
        )
        assertFalse(SyncJournalEntry(at, 1, SyncResultCode.Error, 0).describe(paris).isBlank())
    }

    @Test
    fun `effacer vide le journal`() {
        val journal = SyncJournal(Memory())
        journal.record(entry(1))
        journal.clear()
        assertTrue(journal.entries().isEmpty())
    }
}
