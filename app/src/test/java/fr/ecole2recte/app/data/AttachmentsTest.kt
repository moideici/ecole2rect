package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.domain.Attachment
import fr.ecole2recte.app.domain.AttachmentSource
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import fr.ecole2recte.app.ui.common.AttachmentFiles
import fr.ecole2recte.app.ui.common.AttachmentSafety
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AttachmentSafetyTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `les programmes, scripts et pages web ne sont jamais ouverts`() {
        listOf("setup.apk", "outil.EXE", "facture.pdf.exe", "run.sh", "page.html", "image.svg", "macro.js", "x.bat").forEach {
            assertNotNull("$it aurait dû être refusé", AttachmentSafety.blockedReason(it))
        }
    }

    @Test
    fun `les documents ordinaires peuvent etre proposes`() {
        listOf("Fiche.pdf", "photo.JPG", "devoir.docx", "tableau.xlsx", "sans-extension", "note.txt").forEach {
            assertNull("$it aurait dû être accepté", AttachmentSafety.blockedReason(it))
        }
    }

    @Test
    fun `un nom de fichier ne sort jamais du dossier prevu`() {
        assertEquals("passwd", AttachmentSafety.safeFileName("../../etc/passwd"))
        assertEquals("b.pdf", AttachmentSafety.safeFileName("a\\b.pdf"))
        assertEquals("document", AttachmentSafety.safeFileName("..."))
        assertEquals("document", AttachmentSafety.safeFileName(""))
        assertEquals("ab.pdf", AttachmentSafety.safeFileName("a\u0000b?.pdf"))
        assertTrue(AttachmentSafety.safeFileName("x".repeat(500) + ".pdf").length <= 80)
    }

    @Test
    fun `le type annonce est garde sauf s'il est vague`() {
        assertEquals("application/pdf", AttachmentSafety.mimeType("application/pdf", "x.bin"))
        assertEquals("application/pdf", AttachmentSafety.mimeType("application/octet-stream", "Fiche.PDF"))
        assertEquals("image/png", AttachmentSafety.mimeType(null, "a.png"))
        assertEquals("application/octet-stream", AttachmentSafety.mimeType("", "inconnu.zzz"))
        // Un « text/html » annoncé ne fait jamais ouvrir une page web : l'extension décide.
        assertEquals("application/pdf", AttachmentSafety.mimeType("text/html; charset=utf-8", "a.pdf"))
    }

    @Test
    fun `deux pieces du meme nom ne se melangent pas et tout s'efface`() {
        val files = AttachmentFiles(File(folder.root, "attachments"))
        val first = files.write(byteArrayOf(1), "Fiche.pdf")
        val second = files.write(byteArrayOf(2), "Fiche.pdf")
        assertNotEquals(first.parentFile, second.parentFile)
        assertTrue(first.canonicalPath.startsWith(File(folder.root, "attachments").canonicalPath))
        assertArrayEquals(byteArrayOf(1), first.readBytes())
        files.clear()
        assertFalse(File(folder.root, "attachments").exists())
    }

    @Test
    fun `un nom malveillant reste dans le dossier des pieces jointes`() {
        val files = AttachmentFiles(File(folder.root, "attachments"))
        val written = files.write(byteArrayOf(1), "../../../evil.pdf")
        assertTrue(written.canonicalPath.startsWith(File(folder.root, "attachments").canonicalPath))
        assertEquals("evil.pdf", written.name)
    }

    @Test
    fun `le nom annonce par le serveur est lu dans l'en-tete`() {
        assertEquals("Fiche.pdf", fileNameFrom("attachment; filename=\"Fiche.pdf\""))
        assertEquals("Fiche été.pdf", fileNameFrom("attachment; filename*=UTF-8''Fiche%20%C3%A9t%C3%A9.pdf"))
        assertEquals("a.pdf", fileNameFrom("inline; filename=a.pdf; size=3"))
        assertNull(fileNameFrom(null))
        assertNull(fileNameFrom("attachment"))
    }
}

/** Le téléchargement d'une pièce jointe : lecture seule, binaire, bornée. */
class AttachmentDownloadTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var answer: () -> MockResponse = { MockResponse() }

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return answer()
            }
        }
        server.start()
    }

    @After
    fun stop() {
        runCatching { server.shutdown() }
    }

    private val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x00, 0x7F, -1, -128, 10, 13)

    private fun client() = EcoleDirecteClient(apiBase = server.url("/v3").toString().trimEnd('/')).also {
        it.restore(SavedSession("jeton", mapOf("GTK" to "g"), "gtk", USER_AGENT, 1, System.currentTimeMillis(), listOf(Account("10", "E", "J", "D"))))
    }

    private fun pdfResponse() = MockResponse()
        .addHeader("Content-Type", "application/pdf")
        .addHeader("Content-Disposition", "attachment; filename=\"Fiche.pdf\"")
        .setBody(Buffer().write(pdf))

    @Test
    fun `les octets arrivent intacts avec leur type et leur nom`() {
        answer = ::pdfResponse
        val download = client().download("/telechargement.awp", mapOf("fichierId" to "42", "leTypeDeFichier" to "PIECE_JOINTE"))
        assertArrayEquals(pdf, download.bytes)
        assertEquals("application/pdf", download.contentType)
        assertEquals("Fiche.pdf", download.fileName)

        val request = requests.single()
        assertEquals("/v3/telechargement.awp", request.requestUrl!!.encodedPath)
        assertEquals("get", request.requestUrl!!.queryParameter("verbe"))
        assertEquals("42", request.requestUrl!!.queryParameter("fichierId"))
        assertEquals("PIECE_JOINTE", request.requestUrl!!.queryParameter("leTypeDeFichier"))
        assertEquals("jeton", request.getHeader("X-Token"))
        assertEquals("data=%7B%7D", request.body.readUtf8())
    }

    @Test
    fun `un jeton expire a la place du fichier est signale comme une expiration`() {
        answer = { MockResponse().addHeader("Content-Type", "application/json").setBody("""{"code":520}""") }
        try {
            client().download("/telechargement.awp", mapOf("fichierId" to "1"))
            fail("l'expiration aurait dû être signalée")
        } catch (expected: SessionExpiredException) {
            // attendu
        }
    }

    @Test
    fun `une page web ou une erreur n'est jamais prise pour un fichier`() {
        answer = { MockResponse().addHeader("Content-Type", "text/html").setBody("<html>erreur</html>") }
        try {
            client().download("/telechargement.awp", mapOf("fichierId" to "1"))
            fail("une page web n'est pas un fichier")
        } catch (expected: EcoleDirecteException) {
            assertTrue(expected.message!!.contains("pas disponible"))
        }
    }

    @Test
    fun `un fichier plus gros que la limite est refuse`() {
        answer = { MockResponse().addHeader("Content-Type", "application/pdf").setBody(Buffer().write(ByteArray(5000))) }
        try {
            client().download("/telechargement.awp", mapOf("fichierId" to "1"), maxBytes = 1000)
            fail("trop gros")
        } catch (expected: EcoleDirecteException) {
            assertTrue(expected.message!!.contains("volumineux"))
        }
    }

    @Test
    fun `une erreur serveur est une panne, pas un fichier vide`() {
        answer = { MockResponse().setResponseCode(503).addHeader("Content-Type", "application/pdf") }
        try {
            client().download("/telechargement.awp", mapOf("fichierId" to "1"))
            fail("503")
        } catch (expected: EcoleDirecteException) {
            assertEquals(FailureKind.Service, expected.kind)
        }
    }

    private fun repository(): EcoleDirecteRepository {
        val session = ApiSession()
        session.open("jdupont", client(), listOf(Account("10", "E", "Jean", "DUPONT")))
        return EcoleDirecteRepository(session, ActiveProfile("jdupont", null, "Jean", "Jean Dupont", "JD"), InMemoryResponseCache())
    }

    @Test
    fun `le type de fichier suit l'endroit d'ou vient la piece, sauf type precis annonce`() = runTest {
        answer = ::pdfResponse
        val repository = repository()
        repository.downloadAttachment(Attachment("1", "a.pdf", "FICHIER", AttachmentSource.Message))
        repository.downloadAttachment(Attachment("2", "b.pdf", "", AttachmentSource.Homework))
        repository.downloadAttachment(Attachment("3", "c.pdf", "FICHIER_CDT", AttachmentSource.Message))
        assertEquals(
            listOf("PIECE_JOINTE", "FICHIER_CDT", "FICHIER_CDT"),
            requests.map { it.requestUrl!!.queryParameter("leTypeDeFichier") },
        )
        // Une pièce de message annonce l'année des messages, pas celle d'un devoir.
        assertEquals("", requests[0].requestUrl!!.queryParameter("anneeMessages"))
        assertNull(requests[1].requestUrl!!.queryParameter("anneeMessages"))
    }

    @Test
    fun `le depot rend le nom du serveur, ou celui de la liste a defaut`() = runTest {
        answer = ::pdfResponse
        assertEquals("Fiche.pdf", repository().downloadAttachment(Attachment("1", "autre.pdf", "", AttachmentSource.Message)).fileName)
        answer = { MockResponse().addHeader("Content-Type", "application/pdf").setBody(Buffer().write(pdf)) }
        assertEquals("autre.pdf", repository().downloadAttachment(Attachment("1", "autre.pdf", "", AttachmentSource.Message)).fileName)
    }
}

class AttachmentWiringTest {
    private fun file(path: String) = File(path).takeIf { it.exists() } ?: File("app/$path")

    @Test
    fun `seul le dossier des pieces jointes est partage par le FileProvider`() {
        val paths = file("src/main/res/xml/file_paths.xml").readText()
        assertTrue(paths.contains("<cache-path name=\"attachments\" path=\"attachments/\""))
        assertFalse(paths.contains("files-path") || paths.contains("external"))
    }

    @Test
    fun `le FileProvider n'est pas exporte et son autorite suit l'identifiant de l'application`() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:authorities=\"\${applicationId}.files\""))
        val provider = manifest.substringAfter("androidx.core.content.FileProvider").substringBefore("</provider>")
        assertTrue(provider.contains("android:exported=\"false\""))
        assertTrue(provider.contains("android:grantUriPermissions=\"true\""))
    }
}
