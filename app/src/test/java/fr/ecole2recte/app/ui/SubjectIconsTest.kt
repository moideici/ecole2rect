package fr.ecole2recte.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Translate
import fr.ecole2recte.app.ui.common.subjectIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectIconsTest {
    @Test
    fun `les matieres usuelles ont leur icone`() {
        assertEquals(Icons.Outlined.Calculate, subjectIcon("Mathématiques"))
        assertEquals(Icons.Outlined.Translate, subjectIcon("Anglais LV1"))
        assertEquals(Icons.Outlined.Public, subjectIcon("Histoire-Géographie"))
        assertEquals(Icons.Outlined.Science, subjectIcon("Physique-Chimie"))
        assertEquals(Icons.Outlined.Eco, subjectIcon("Sciences de la vie et de la Terre"))
        assertEquals(Icons.Outlined.Paid, subjectIcon("Sciences économiques et sociales"))
    }

    @Test
    fun `une matiere inconnue prend l'icone d'ecole`() {
        assertEquals(Icons.Outlined.School, subjectIcon("Atelier théâtre"))
        assertEquals(Icons.Outlined.School, subjectIcon(""))
    }
}
