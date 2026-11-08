package fr.ecole2recte.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lot M : la version de test s'installe à côté de la version publiée. Ces tests gardent les trois
 * endroits qui doivent rester d'accord sur le nom du paquet (gradle, raccourcis, libellé).
 */
class BuildVariantsTest {
    private fun file(path: String) = File(path).takeIf { it.exists() } ?: File("app/$path")

    private fun packages(path: String) = Regex("""targetPackage="([^"]+)"""").findAll(file(path).readText()).map { it.groupValues[1] }.toList()

    @Test
    fun `la version de test a son propre identifiant`() {
        assertEquals("fr.ecole2recte.app.dev", BuildConfig.APPLICATION_ID)
        assertTrue(BuildConfig.VERSION_NAME.endsWith("-dev"))
    }

    @Test
    fun `les raccourcis de la version de test visent la version de test`() {
        val debug = packages("src/debug/res/xml/shortcuts.xml")
        assertEquals(1, debug.size)
        assertEquals(setOf(BuildConfig.APPLICATION_ID), debug.toSet())
    }

    @Test
    fun `les raccourcis de la version publiee visent la version publiee`() {
        val release = packages("src/main/res/xml/shortcuts.xml")
        assertEquals(1, release.size)
        assertEquals(setOf("fr.ecole2recte.app"), release.toSet())
    }

    @Test
    fun `les deux fichiers de raccourcis ne different que par le paquet`() {
        val main = file("src/main/res/xml/shortcuts.xml").readText().replace("\r\n", "\n")
        val debug = file("src/debug/res/xml/shortcuts.xml").readText().replace("\r\n", "\n")
        assertEquals(main.replace("targetPackage=\"fr.ecole2recte.app\"", "targetPackage=\"fr.ecole2recte.app.dev\""), debug)
    }

    @Test
    fun `le libelle de l'application vient d'un parametre de construction`() {
        assertTrue(file("src/main/AndroidManifest.xml").readText().contains("android:label=\"\${appLabel}\""))
    }
}
