package fr.ecole2recte.app.diagnostic

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `le plantage est gardé sans le message de l'exception`() {
        val log = CrashLog(File(folder.root, "crash.txt"))
        log.record(IllegalStateException("Marie Dupont a 18/20"), at = 0)
        val text = log.last().orEmpty()
        assertTrue("java.lang.IllegalStateException" in text)
        assertTrue("CrashLogTest" in text)
        assertFalse("Marie" in text)
        assertFalse("18/20" in text)
    }

    @Test
    fun `les causes sont listées par leur classe`() {
        val log = CrashLog(File(folder.root, "crash.txt"))
        log.record(RuntimeException("a", IllegalArgumentException("secret")), at = 0)
        val text = log.last().orEmpty()
        assertTrue("causé par java.lang.IllegalArgumentException" in text)
        assertFalse("secret" in text)
    }

    @Test
    fun `sans plantage, rien à signaler et l'effacement fonctionne`() {
        val log = CrashLog(File(folder.root, "crash.txt"))
        assertNull(log.last())
        log.record(RuntimeException(), at = 0)
        log.clear()
        assertNull(log.last())
    }
}
