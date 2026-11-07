package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.MessagePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMarksTest {
    private class MemoryStorage(var text: String? = null) : TextStorage {
        override fun read() = text
        override fun write(value: String) {
            text = value
        }
    }

    private fun message(id: String, unread: Boolean) = MessagePreview("Vie scolaire", "Réunion", unread, id, "10:00")

    @Test
    fun `une marque lu ou non lu survit au redemarrage et reste propre au profil`() {
        val storage = MemoryStorage()
        MessageReadMarks(ProfileKeyValueStore(storage)).apply {
            set("parent/1", "m1", read = true)
            set("parent/2", "m1", read = false)
        }
        val reloaded = MessageReadMarks(ProfileKeyValueStore(storage))
        assertEquals(mapOf("m1" to true), reloaded.current("parent/1"))
        assertEquals(mapOf("m1" to false), reloaded.current("PARENT/2"))
        assertTrue(reloaded.current("autre/").isEmpty())
    }

    @Test
    fun `les marques locales l'emportent sur l'etat envoye par le serveur`() {
        val messages = listOf(message("a", unread = true), message("b", unread = false), message("c", unread = true))
        val shown = applyReadMarks(messages, mapOf("a" to true, "b" to false))
        assertEquals(listOf(false, true, true), shown.map { it.unread })
    }

    @Test
    fun `sans marque ou sans identifiant la liste ne change pas`() {
        val messages = listOf(message("", unread = true), message("b", unread = true))
        assertEquals(messages, applyReadMarks(messages, emptyMap()))
        assertEquals(messages, applyReadMarks(messages, mapOf("" to true)))
    }

    @Test
    fun `ouvrir un message efface la marque locale`() {
        val marks = MessageReadMarks(ProfileKeyValueStore(MemoryStorage()))
        marks.set("p/", "m1", read = false)
        marks.forget("p/", "m1")
        assertTrue(marks.current("p/").isEmpty())
    }

    @Test
    fun `retirer un compte efface ses marques et celles de ses enfants`() {
        val store = ProfileKeyValueStore(MemoryStorage())
        val marks = MessageReadMarks(store)
        marks.set("Parent/11", "m1", read = true)
        marks.set("parent/12", "m2", read = true)
        marks.set("autre/", "m3", read = true)
        marks.clear("PARENT")
        assertTrue(marks.current("parent/11").isEmpty())
        assertTrue(marks.current("parent/12").isEmpty())
        assertEquals(mapOf("m3" to true), marks.current("autre/"))
    }

    @Test
    fun `un fichier abime repart de zero sans planter`() {
        val marks = MessageReadMarks(ProfileKeyValueStore(MemoryStorage("{pas du json")))
        assertTrue(marks.current("p/").isEmpty())
    }

    @Test
    fun `le filtre des devoirs est retenu par profil`() {
        val storage = MemoryStorage()
        val filters = HomeworkFilterStore(ProfileKeyValueStore(storage))
        assertNull(filters.get("p/1"))
        filters.set("p/1", "Exams")
        filters.set("p/2", "Done")
        val reloaded = HomeworkFilterStore(ProfileKeyValueStore(storage))
        assertEquals("Exams", reloaded.get("p/1"))
        assertEquals("Done", reloaded.get("p/2"))
    }

    // --- Notes « nouvelles » ---

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `le premier passage d'un profil ne rend aucune note nouvelle`() {
        var now = 1_000 * day
        val seen = SeenGrades(ProfileKeyValueStore(MemoryStorage())) { now }
        val first = seen.record("p/", listOf("n1", "n2"))
        assertFalse(isNewGrade(first["n1"], now))
        assertFalse(isNewGrade(first["n2"], now))
    }

    @Test
    fun `une note vue ensuite est nouvelle pendant trois jours exactement`() {
        var now = 1_000 * day
        val seen = SeenGrades(ProfileKeyValueStore(MemoryStorage())) { now }
        seen.record("p/", listOf("n1"))
        now += day
        val second = seen.record("p/", listOf("n1", "n2"))
        assertFalse(isNewGrade(second["n1"], now))
        assertTrue(isNewGrade(second["n2"], now))
        assertTrue(isNewGrade(second["n2"], now + 3 * day - 1))
        assertFalse(isNewGrade(second["n2"], now + 3 * day))
    }

    @Test
    fun `la premiere apparition n'est pas ecrasee par les passages suivants`() {
        var now = 1_000 * day
        val storage = MemoryStorage()
        SeenGrades(ProfileKeyValueStore(storage)) { now }.record("p/", listOf("n1"))
        now += day
        SeenGrades(ProfileKeyValueStore(storage)) { now }.record("p/", listOf("n1", "n2"))
        val later = now + 2 * day
        val table = SeenGrades(ProfileKeyValueStore(storage)) { later }.record("p/", listOf("n1", "n2"))
        assertEquals(now, table["n2"])
    }

    @Test
    fun `un premier passage sans note ne fixe pas la reference`() {
        var now = 1_000 * day
        val seen = SeenGrades(ProfileKeyValueStore(MemoryStorage())) { now }
        seen.record("p/", emptyList())
        val table = seen.record("p/", listOf("n1"))
        assertFalse(isNewGrade(table["n1"], now))
    }

    @Test
    fun `chaque profil a ses notes vues`() {
        var now = 1_000 * day
        val seen = SeenGrades(ProfileKeyValueStore(MemoryStorage())) { now }
        seen.record("a/", listOf("n1"))
        now += day
        val other = seen.record("b/", listOf("n1"))
        assertFalse(isNewGrade(other["n1"], now))
    }
}
