package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.FileResponseCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileResponseCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val owner = CacheOwner("J.Dupont", null)

    @Test
    fun `ce qui est ecrit se relit tel quel`() {
        val cache = FileResponseCache(folder.root)
        cache.put(owner, "grades", """{"a":"é\nb"}""", 1234L)
        val stored = cache.get(owner, "grades")
        assertEquals(1234L, stored?.at)
        assertEquals("""{"a":"é\nb"}""", stored?.json)
    }

    @Test
    fun `ecrire deux fois ecrase sans doublon`() {
        val cache = FileResponseCache(folder.root)
        cache.put(owner, "grades", "[1]", 1L)
        cache.put(owner, "grades", "[2]", 2L)
        assertEquals("[2]", cache.get(owner, "grades")?.json)
        assertEquals(1, folder.root.walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `l'identifiant ne tient pas compte de la casse et les eleves sont separes`() {
        val cache = FileResponseCache(folder.root)
        cache.put(CacheOwner("J.Dupont", "10"), "homework", "[1]", 1L)
        assertEquals("[1]", cache.get(CacheOwner("j.dupont", "10"), "homework")?.json)
        assertNull(cache.get(CacheOwner("j.dupont", "11"), "homework"))
    }

    @Test
    fun `retirer un compte efface ses donnees et pas celles des autres`() {
        val cache = FileResponseCache(folder.root)
        cache.put(owner, "grades", "[1]", 1L)
        cache.put(CacheOwner("autre", null), "grades", "[2]", 1L)
        cache.clear("j.dupont")
        assertNull(cache.get(owner, "grades"))
        assertEquals("[2]", cache.get(CacheOwner("autre", null), "grades")?.json)
    }

    @Test
    fun `un fichier abime est ignore`() {
        val cache = FileResponseCache(folder.root)
        cache.put(owner, "grades", "[1]", 1L)
        folder.root.walkTopDown().first { it.isFile }.writeText("n'importe quoi")
        assertNull(cache.get(owner, "grades"))
    }
}
