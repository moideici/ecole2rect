package fr.ecole2recte.app.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les moyennes de l'application sont celles du site, au chiffre près.
 *
 * `src/test/resources/parite/moyennes.json` est écrit par `tools/parite-moyennes/generer.mjs` en faisant tourner
 * le code du site (`ecole2recte/packages/protocol`) sur des cas écrits à la main et sur des cas tirés au hasard.
 * Ce test rejoue les mêmes entrées avec [Averages] et exige les mêmes valeurs, origines, notes « en retard »,
 * courbes, simulations et arrondis d'affichage.
 */
class AveragesParityTest {
    private val fixture: JSONObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResource("parite/moyennes.json")) { "parite/moyennes.json manquant" }.readText()
        JSONObject(text)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)

    private fun JSONObject.optTextOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

    private fun grades(array: JSONArray): List<GradeEntry> = (0 until array.length()).map { index ->
        val raw = array.getJSONObject(index)
        GradeEntry(
            id = raw.getString("id"),
            subject = raw.getString("subject"),
            colorIndex = 0,
            label = raw.getString("label"),
            value = raw.optDoubleOrNull("value"),
            scale = raw.getDouble("scale"),
            coefficient = raw.getDouble("coefficient"),
            date = raw.optTextOrNull("date"),
            classAverage = null,
            minimum = null,
            maximum = null,
            counted = raw.getBoolean("counted"),
            periodCode = raw.getString("periodCode"),
        )
    }

    private fun published(array: JSONArray): List<PublishedAverage> = (0 until array.length()).map { index ->
        val raw = array.getJSONObject(index)
        PublishedAverage(
            periodCode = raw.getString("periodCode"),
            subject = raw.getString("subject"),
            average = raw.optDoubleOrNull("average"),
            coefficient = raw.optDoubleOrNull("coefficient"),
            parentSubject = raw.getString("parentSubject"),
        )
    }

    private fun ids(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }

    private fun near(message: String, expected: Double?, actual: Double?) {
        if (expected == null || actual == null) {
            assertEquals(message, expected, actual)
            return
        }
        assertEquals(message, expected, actual, 1e-9)
    }

    private fun origin(text: String): AverageOrigin = when (text) {
        "official" -> AverageOrigin.Official
        "recomputed" -> AverageOrigin.Recomputed
        "computed" -> AverageOrigin.Computed
        else -> AverageOrigin.None
    }

    private fun series(array: JSONArray): List<Pair<String, Double>> =
        (0 until array.length()).map { array.getJSONObject(it).let { point -> point.getString("date") to point.getDouble("value") } }

    @Test
    fun `chaque cas donne les memes moyennes que le site`() {
        val cases = fixture.getJSONArray("cases")
        assertTrue("trop peu de cas pour parler de parité", cases.length() >= 50)
        var checked = 0
        for (caseIndex in 0 until cases.length()) {
            val case = cases.getJSONObject(caseIndex)
            val name = case.getString("name")
            val grades = grades(case.getJSONArray("grades"))
            val published = published(case.getJSONArray("subjectAverages"))
            val periods = case.getJSONArray("periods")
            val expectedPeriods = case.getJSONObject("expected").getJSONArray("periods")
            for (periodIndex in 0 until periods.length()) {
                val period = periods.getJSONObject(periodIndex)
                val code = period.getString("code")
                val official = period.optDoubleOrNull("average")
                val expected = expectedPeriods.getJSONObject(periodIndex)
                val where = "$name / $code"

                val subjects = Averages.subjectsOfPeriod(published, grades, code)
                assertEquals("$where : matières", ids(expected.getJSONArray("subjects")), subjects.map { it.subject })

                val verdicts = Averages.reconcileSubjectAverages(subjects, grades, code)
                val expectedVerdicts = expected.getJSONArray("verdicts")
                assertEquals("$where : nombre de matières", expectedVerdicts.length(), verdicts.size)
                verdicts.forEachIndexed { index, (average, verdict) ->
                    val wanted = expectedVerdicts.getJSONObject(index)
                    val label = "$where / ${average.subject}"
                    assertEquals("$label : matière", wanted.getString("subject"), average.subject)
                    near("$label : valeur", wanted.optDoubleOrNull("value"), verdict.value)
                    assertEquals("$label : origine", origin(wanted.getString("origin")), verdict.origin)
                    near("$label : publiée", wanted.optDoubleOrNull("official"), verdict.official)
                    assertEquals("$label : notes en retard", ids(wanted.getJSONArray("behind")), verdict.behind.map { it.id })
                    assertEquals("$label : affichage", wanted.optTextOrNull("display"), verdict.value?.let(FrenchFormat::average))
                }

                near("$where : moyenne des matières", expected.optDoubleOrNull("averageOfSubjects"), Averages.averageOfSubjects(verdicts))
                near("$where : moyenne à plat", expected.optDoubleOrNull("flat"), Averages.weightedAverage(Averages.gradesFor(grades, code)))

                val general = Averages.generalAverage(official, grades, code, subjects)
                val wantedGeneral = expected.getJSONObject("general")
                near("$where : générale", wantedGeneral.optDoubleOrNull("value"), general.value)
                assertEquals("$where : origine de la générale", origin(wantedGeneral.getString("origin")), general.origin)
                near("$where : générale publiée", wantedGeneral.optDoubleOrNull("official"), general.official)
                assertEquals("$where : retard de la générale", ids(wantedGeneral.getJSONArray("behind")), general.behind.map { it.id })
                assertEquals("$where : affichage de la générale", wantedGeneral.optTextOrNull("display"), general.value?.let(FrenchFormat::average))

                val notes = grades.filter { it.periodCode == code }
                val generalSeries = Averages.averageSeries(notes, code, subjects)
                val wantedGeneralSeries = series(expected.getJSONArray("generalSeries"))
                assertEquals("$where : dates de la courbe générale", wantedGeneralSeries.map { it.first }, generalSeries.map { it.date })
                wantedGeneralSeries.zip(generalSeries).forEach { (wanted, point) -> near("$where : courbe générale ${point.date}", wanted.second, point.value) }

                val simulation = expected.optJSONObject("simulation")
                if (simulation != null) {
                    val subject = simulation.getString("subject")
                    val subjectSeries = Averages.averageSeries(notes, code, subjects, subject)
                    val wantedSubjectSeries = series(expected.getJSONArray("subjectSeries"))
                    assertEquals("$where : dates de la courbe de $subject", wantedSubjectSeries.map { it.first }, subjectSeries.map { it.date })
                    wantedSubjectSeries.zip(subjectSeries).forEach { (wanted, point) -> near("$where : courbe de $subject ${point.date}", wanted.second, point.value) }

                    val result = Averages.simulate(grades, code, subject, simulation.getDouble("value"))
                    near("$where : simulation avant", simulation.optDoubleOrNull("before"), result.before)
                    near("$where : simulation après", simulation.optDoubleOrNull("after"), result.after)
                    near("$where : simulation écart", simulation.optDoubleOrNull("delta"), result.delta)

                    val required = Averages.requiredGrade(grades, code, subject, simulation.getDouble("target"))
                    near("$where : note à viser", simulation.optDoubleOrNull("required"), required?.value)
                    assertEquals("$where : atteignable", if (simulation.isNull("reachable")) null else simulation.getBoolean("reachable"), required?.reachable)
                }
                checked++
            }
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `le jeu de cas couvre les trois origines d'une moyenne`() {
        val origins = mutableSetOf<String>()
        val cases = fixture.getJSONArray("cases")
        for (caseIndex in 0 until cases.length()) {
            val periods = cases.getJSONObject(caseIndex).getJSONObject("expected").getJSONArray("periods")
            for (index in 0 until periods.length()) {
                origins += periods.getJSONObject(index).getJSONObject("general").getString("origin")
            }
        }
        assertTrue(origins.containsAll(listOf("official", "recomputed", "computed")))
    }
}
