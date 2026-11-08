package fr.ecole2recte.app.domain

import fr.ecole2recte.app.data.ProfileKeyValueStore
import fr.ecole2recte.app.data.TextStorage
import org.junit.Assert.*
import org.junit.Test

class ShortcutLogicTest {
    @Test fun validDeepLinks() {
        assertEquals(NavigationTarget("notes", "Français"), DeepLinks.parse("ecole2recte://notes?matiere=Fran%C3%A7ais"))
        assertEquals(NavigationTarget("agenda"), DeepLinks.parse("ecole2recte://agenda"))
        assertEquals(NavigationTarget("agenda_homework"), DeepLinks.parse("ecole2recte://devoirs"))
        assertEquals(NavigationTarget("messages"), DeepLinks.parse("ecole2recte://messages"))
    }
    @Test fun invalidUnknownAndMalformedLinksAreIgnored() {
        listOf("", "https://agenda", "ecole2recte://inconnu", "ecole2recte://notes?matiere=%", "ecole2recte://notes?matiere=", "ecole2recte://notes?matiere=A&matiere=B", "ecole2recte://notes?matiere=%00", "ecole2recte://agenda/path", "ecole2recte://user@agenda", "ecole2recte://agenda:80", "ecole2recte://messages#x").forEach {
            assertNull(it, DeepLinks.parse(it))
        }
    }
    @Test fun fourMostVisitedThenRecentSubjectsOnly() {
        val visits = (1..6).map { SubjectVisit("$it", "Matière $it", if (it == 2) 10 else 1, it.toLong()) }
        assertEquals(listOf("2", "6", "5", "4"), ShortcutLogic.select(visits, (1..6).map { "$it" }.toSet(), false).map { it.id })
        assertTrue(ShortcutLogic.select(visits, setOf("2"), true).isEmpty())
        assertEquals(listOf("2"), ShortcutLogic.select(visits, setOf("2"), false).map { it.id })
    }
    @Test fun subjectLinksResolveByIdentityCodeOrUnambiguousName() {
        val subjects = listOf(SubjectAverage(Subject("Français", "", "", 0), "12", id = "fr-id", subjectCode = "FR"))
        assertEquals("fr-id", ShortcutLogic.resolveSubject("fr-id", subjects))
        assertEquals("fr-id", ShortcutLogic.resolveSubject("FR", subjects))
        assertEquals("fr-id", ShortcutLogic.resolveSubject("FRANCAIS", subjects))
        assertNull(ShortcutLogic.resolveSubject("Inconnu", subjects))
        assertNull(ShortcutLogic.resolveSubject("Français", subjects + subjects.single().copy(id = "other-id")))
        assertEquals(NavigationTarget("agenda_homework"), DeepLinks.feature("Devoirs"))
        assertNull(DeepLinks.feature("Inconnu"))
    }
    @Test fun storageIsPerProfileAndCleansAllChildrenOfRemovedAccount() {
        val memory = object : TextStorage {
            var text: String? = null
            override fun read() = text
            override fun write(value: String) { text = value }
        }
        val visits = SubjectVisits(ProfileKeyValueStore(memory))
        visits.record("alice/1", "math", "Mathématiques", 10)
        visits.record("alice/1", "math", "Mathématiques", 20)
        visits.record("alice/2", "fr", "Français", 30)
        visits.record("bob/1", "fr", "Français", 40)
        assertEquals(2, visits.read("alice/1").single().count)
        assertEquals(20L, visits.read("alice/1").single().lastOpened)
        visits.clear("alice")
        assertTrue(visits.read("alice/1").isEmpty())
        assertTrue(visits.read("alice/2").isEmpty())
        assertEquals(1, visits.read("bob/1").size)
    }
}
