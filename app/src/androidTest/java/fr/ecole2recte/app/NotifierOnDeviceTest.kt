package fr.ecole2recte.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import fr.ecole2recte.app.sync.AndroidNotifier
import fr.ecole2recte.app.sync.EventKind
import fr.ecole2recte.app.sync.NotificationEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Les notifications sur un vrai téléphone : version publique sans détail, enfant en sous-titre,
 * importance basse pour cours et devoirs. Ignoré si la permission de notifier n'a pas été accordée.
 */
class NotifierOnDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After
    fun cleanUp() {
        manager.cancelAll()
    }

    private fun canNotify() = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @Test
    fun lesCanauxOntLaBonneImportance() {
        AndroidNotifier(context).ensureChannels()
        EventKind.entries.forEach { kind ->
            val channel = manager.getNotificationChannel(kind.channelId)
            assertNotNull("canal ${kind.channelId}", channel)
            val expected = if (kind.quiet) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
            assertEquals(kind.channelId, expected, channel.importance)
        }
        EventKind.legacyChannelIds.forEach { assertEquals(null, manager.getNotificationChannel(it)) }
    }

    @Test
    fun ecranVerrouilleSansDetailEtEnfantEnSousTitre() {
        assumeTrue("permission de notifier non accordée", canNotify())
        AndroidNotifier(context).post(
            listOf(NotificationEvent(EventKind.Grade, "essai", "Nouvelle note en Maths", "17 / 20 · DS 3", owner = "Léa")),
        )
        val posted = manager.activeNotifications.map { it.notification }.firstOrNull { it.extras.getString(Notification.EXTRA_TITLE) == "Nouvelle note en Maths" }
        assertNotNull("la notification devrait être affichée", posted)
        posted!!
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals("Léa", posted.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        val public = posted.publicVersion
        assertNotNull(public)
        val publicTexts = listOfNotNull(
            public.extras.getCharSequence(Notification.EXTRA_TITLE),
            public.extras.getCharSequence(Notification.EXTRA_TEXT),
        ).joinToString(" ")
        assertEquals("Nouvelle note", public.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertFalse("la version publique ne doit rien révéler", "17" in publicTexts || "Maths" in publicTexts || "DS" in publicTexts)
        assertTrue(publicTexts.isNotBlank())
    }

    @Test
    fun deuxEnfantsAvecLaMemeNouveauteGardentDeuxNotifications() {
        assumeTrue("permission de notifier non accordée", canNotify())
        val event = NotificationEvent(EventKind.Homework, "devoir", "Pour demain : Maths", "Exercice 4")
        AndroidNotifier(context).post(listOf(event.copy(owner = "Léa"), event.copy(owner = "Tom")))
        val subs = manager.activeNotifications.mapNotNull { it.notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() }
        assertEquals(setOf("Léa", "Tom"), subs.toSet())
    }
}
