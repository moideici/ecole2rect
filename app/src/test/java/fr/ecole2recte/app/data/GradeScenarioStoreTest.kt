package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class GradeScenarioStoreTest {
    private class Memory : TextStorage {
        var text: String? = null
        override fun read() = text
        override fun write(value: String) { text = value }
    }
    private val cipher = object : ValueCipher {
        override fun encrypt(value: String) = value.reversed()
        override fun decrypt(value: String) = value.reversed()
    }

    @Test fun `les modifications sont sauvegardees par profil et periode sans collision`() {
        val memory = Memory()
        val scenario = SavedGradeScenario("id", "Mon objectif", listOf(ScenarioEdit("m", value = 7.5, scale = 10.0), ScenarioEdit("m", "g", removed = true)))
        GradeScenarioStore(memory, cipher).apply {
            save("parent/1", "T1", scenario)
            save("parent/2", "T1", scenario.copy(name = "Autre enfant"))
            save("parent/1", "T2", scenario.copy(name = "Autre période"))
        }
        val restored = GradeScenarioStore(memory, cipher)
        assertEquals(listOf(scenario), restored.list("parent/1", "T1"))
        assertEquals("Autre enfant", restored.list("parent/2", "T1").single().name)
        assertEquals("Autre période", restored.list("parent/1", "T2").single().name)
        assertFalse(memory.text!!.contains("Mon objectif"))
        restored.delete("parent/1", "T1", "id")
        assertTrue(restored.list("parent/1", "T1").isEmpty())
        assertEquals(1, restored.list("parent/2", "T1").size)
    }

    @Test fun `mettre a jour garde un seul scenario et effacer un compte garde les autres`() {
        val store = GradeScenarioStore(Memory(), cipher)
        val scenario = SavedGradeScenario("id", "Départ", emptyList())
        store.save("a/1", "T1", scenario)
        store.save("a/1", "T1", scenario.copy(name = "Arrivée"))
        store.save("b/1", "T1", scenario)
        assertEquals("Arrivée", store.list("a/1", "T1").single().name)
        store.clear("a")
        assertTrue(store.list("a/1", "T1").isEmpty())
        assertEquals(1, store.list("b/1", "T1").size)
    }

    @Test fun `un stockage corrompu est ignore`() {
        val memory = Memory().apply { text = "corrompu" }
        assertTrue(GradeScenarioStore(memory, cipher).list("a/1", "T1").isEmpty())
    }
}
