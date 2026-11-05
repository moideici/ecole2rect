package fr.ecole2recte.app

import android.appwidget.AppWidgetManager
import android.content.pm.ShortcutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ce qu'Android a retenu du manifeste : icône, widgets, raccourcis. */
@RunWith(AndroidJUnit4::class)
class ManifestOnDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun lApplicationAUneIcone() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue("android:icon doit être déclaré", info.icon != 0)
    }

    @Test
    fun lesSixWidgetsSontDeclaresAvecUnApercuEtUneTailleMinimale() {
        val providers = AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals("six widgets déclarés", 6, providers.size)
        providers.forEach { provider ->
            assertTrue("aperçu manquant pour ${provider.provider.className}", provider.previewImage != 0)
            assertTrue(provider.minWidth > 0 && provider.minHeight > 0)
            assertTrue("redimensionnable : ${provider.provider.className}", provider.resizeMode != android.appwidget.AppWidgetProviderInfo.RESIZE_NONE)
        }
    }

    @Test
    fun lesTroisRaccourcisSontDeclares() {
        val manager = context.getSystemService(ShortcutManager::class.java)
        assertEquals(3, manager.manifestShortcuts.size)
    }
}
