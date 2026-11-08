package fr.ecole2recte.app.data

import fr.ecole2recte.app.data.cache.CacheOwner
import fr.ecole2recte.app.data.cache.InMemoryResponseCache
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.session.ApiSession
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WidgetCachedPeriodsTest {
    @Test fun `lecture des periodes sans session ouverte ni client reseau`() = runTest {
        val cache = InMemoryResponseCache()
        cache.put(CacheOwner("fictif", "1"), "grades", javaClass.getResource("/captures/grades_subjects.json")!!.readText(), 1)
        val repository = EcoleDirecteRepository(ApiSession(), ActiveProfile("fictif", "1", "Test", "Test", "T"), cache,
            ioDispatcher = StandardTestDispatcher(testScheduler))
        val periods = repository.cachedGradePeriods()
        assertEquals(1, periods.size)
        assertEquals("13,00", periods.single().average)
        assertEquals(4, periods.single().bySubject.size)
        assertTrue(periods.single().bySubject[1].isSubSubject)
        assertEquals(0L, repository.freshness.value.lastSyncMillis ?: 0L)
    }

    @Test fun `profil sans cache ne reprend jamais le bulletin dun autre enfant`() = runTest {
        val cache = InMemoryResponseCache()
        cache.put(CacheOwner("fictif", "1"), "grades", javaClass.getResource("/captures/grades_subjects.json")!!.readText(), 1)
        val repository = EcoleDirecteRepository(ApiSession(), ActiveProfile("fictif", "2", "Test", "Test", "T"), cache,
            ioDispatcher = StandardTestDispatcher(testScheduler))
        assertTrue(repository.cachedGradePeriods().isEmpty())
    }
}
