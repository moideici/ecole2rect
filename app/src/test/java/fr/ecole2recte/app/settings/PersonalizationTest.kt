package fr.ecole2recte.app.settings

import fr.ecole2recte.app.data.SubjectColorStore
import fr.ecole2recte.app.data.TextStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalizationTest {
    private class MemoryStorage(var text: String? = null) : TextStorage {
        override fun read() = text
        override fun write(value: String) {
            text = value
        }
    }

    @Test
    fun `l'accueil montre tous les blocs dans l'ordre d'origine par defaut`() {
        assertEquals(HomeSection.entries, HomeLayout().visible)
    }

    @Test
    fun `monter et descendre un bloc change l'ordre, sans sortir de la liste`() {
        val layout = HomeLayout().move(HomeSection.Grades, -1)
        assertEquals(HomeSection.Grades, layout.order[HomeSection.entries.lastIndex - 1])
        assertEquals(HomeLayout(), HomeLayout().move(HomeSection.NextClass, -1))
        assertEquals(HomeLayout(), HomeLayout().move(HomeSection.Grades, +1))
    }

    @Test
    fun `masquer un bloc le retire de l'affichage mais pas de la liste`() {
        val layout = HomeLayout().toggle(HomeSection.Today)
        assertEquals(false, HomeSection.Today in layout.visible)
        assertEquals(true, HomeSection.Today in layout.order)
        assertEquals(true, HomeSection.Today in layout.toggle(HomeSection.Today).visible)
    }

    @Test
    fun `le reglage survit a l'enregistrement`() {
        val layout = HomeLayout().move(HomeSection.Reminder, -2).toggle(HomeSection.Highlights)
        assertEquals(layout, HomeLayout.parse(layout.serialize()))
    }

    @Test
    fun `un reglage abime ou ancien est repare`() {
        assertEquals(HomeLayout(), HomeLayout.parse(null))
        assertEquals(HomeLayout(), HomeLayout.parse("n'importe quoi"))
        // Un bloc inconnu est ignoré, un bloc ajouté plus tard retrouve sa place à la fin.
        val layout = HomeLayout.parse("Grades,Inconnu,Today;Today")
        assertEquals(HomeSection.Grades, layout.order.first())
        assertEquals(HomeSection.entries.toSet(), layout.order.toSet())
        assertEquals(setOf(HomeSection.Today), layout.hidden)
    }

    @Test
    fun `une couleur choisie est gardee par profil et par matiere`() = runTest {
        val store = SubjectColorStore(MemoryStorage())
        store.set("J.Dupont/10", "maths", 5)
        assertEquals(mapOf("maths" to 5), store.overrides("j.dupont/10").first())
        assertEquals(emptyMap<String, Int>(), store.overrides("j.dupont/11").first())
    }

    @Test
    fun `rendre la couleur d'origine efface le choix et il survit au redemarrage`() = runTest {
        val storage = MemoryStorage()
        SubjectColorStore(storage).apply {
            set("a/1", "maths", 3)
            set("a/1", "anglais", 4)
            set("a/1", "maths", null)
        }
        assertEquals(mapOf("anglais" to 4), SubjectColorStore(storage).overrides("a/1").first())
    }

    @Test
    fun `retirer un compte efface ses couleurs et pas celles des autres`() = runTest {
        val store = SubjectColorStore(MemoryStorage())
        store.set("a/1", "maths", 3)
        store.set("a/2", "maths", 4)
        store.set("b/", "maths", 5)
        store.clear("A")
        assertEquals(emptyMap<String, Int>(), store.overrides("a/1").first())
        assertEquals(emptyMap<String, Int>(), store.overrides("a/2").first())
        assertEquals(mapOf("maths" to 5), store.overrides("b/").first())
    }
}
