package fr.ecole2recte.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.ecole2recte.app.domain.SchoolLifeEvent
import fr.ecole2recte.app.domain.SchoolLifeKind
import fr.ecole2recte.app.ui.theme.ColorTheme
import fr.ecole2recte.app.ui.theme.ThemeMode
import fr.ecole2recte.app.widget.SchoolLifeWidgetContent
import fr.ecole2recte.app.widget.WidgetColors
import fr.ecole2recte.app.widget.WidgetTheming
import fr.ecole2recte.app.widget.WidgetThemes
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceApi::class)
class SchoolLifeWidgetTest {
    private val events = listOf(SchoolLifeEvent(SchoolLifeKind.Absence, "Absence", "", "", false, countedMinutes = 90))
    private val colors = WidgetColors(WidgetThemes.of(ColorTheme.Indigo, ThemeMode.Light))

    @Test fun counterAtAllSizeBreakpoints() {
        listOf(DpSize(60.dp, 60.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 200.dp)).forEach { size ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(size)
                provideComposable { WidgetTheming(colors) { SchoolLifeWidgetContent(events, false, 0) } }
                onNode(hasText("1")).assertExists()
                onNode(hasText("À justifier")).assertExists()
            }
        }
    }

    @Test fun privacyAtOneByOneSize() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(60.dp, 60.dp))
        provideComposable { WidgetTheming(colors) { SchoolLifeWidgetContent(events, true, 0) } }
        onNode(hasText("•••")).assertExists()
        onNode(hasText("1")).assertDoesNotExist()
    }

    @Test fun largestTextKeepsTheCounterWithoutACutCaption() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(60.dp, 60.dp))
        provideComposable { WidgetTheming(colors) { SchoolLifeWidgetContent(events, false, 0, fontScale = 3f) } }
        onNode(hasText("1")).assertExists()
        onNode(hasText("À justifier")).assertDoesNotExist()
    }
}
