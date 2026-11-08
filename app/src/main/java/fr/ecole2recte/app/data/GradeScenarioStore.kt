package fr.ecole2recte.app.data

import fr.ecole2recte.app.domain.SavedGradeScenario
import fr.ecole2recte.app.domain.ScenarioEdit
import org.json.JSONArray
import org.json.JSONObject

/** Scénarios locaux chiffrés. Profil et période sont deux clés JSON distinctes. */
class GradeScenarioStore(private val storage: TextStorage, private val cipher: ValueCipher) {
    private fun load(): JSONObject = runCatching {
        storage.read()?.let { JSONObject(cipher.decrypt(it)) } ?: JSONObject()
    }.getOrElse { JSONObject() }

    fun list(owner: String, period: String): List<SavedGradeScenario> {
        val array = load().optJSONObject(owner)?.optJSONArray(period) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            val edits = item.getJSONArray("edits")
            SavedGradeScenario(item.getString("id"), item.getString("name"), (0 until edits.length()).map { i ->
                val edit = edits.getJSONObject(i)
                ScenarioEdit(edit.getString("subject"), edit.optString("grade").takeIf { it.isNotBlank() }, edit.optBoolean("removed"),
                    if (edit.isNull("value")) null else edit.getDouble("value"), edit.getDouble("scale"), edit.getDouble("coefficient"))
            })
        }.getOrNull() }
    }

    fun save(owner: String, period: String, scenario: SavedGradeScenario) {
        require(scenario.name.isNotBlank())
        write(owner, period, list(owner, period).filterNot { it.id == scenario.id } + scenario.copy(name = scenario.name.trim()))
    }

    fun delete(owner: String, period: String, id: String) = write(owner, period, list(owner, period).filterNot { it.id == id })

    private fun write(owner: String, period: String, scenarios: List<SavedGradeScenario>) {
        val root = load()
        val periods = root.optJSONObject(owner) ?: JSONObject()
        val items = JSONArray()
        scenarios.forEach { scenario ->
            val edits = JSONArray()
            scenario.edits.forEach { edit -> edits.put(JSONObject().put("subject", edit.subjectId).put("grade", edit.gradeId ?: "")
                .put("removed", edit.removed).put("value", edit.value ?: JSONObject.NULL).put("scale", edit.scale).put("coefficient", edit.coefficient)) }
            items.put(JSONObject().put("id", scenario.id).put("name", scenario.name).put("edits", edits))
        }
        if (scenarios.isEmpty()) periods.remove(period) else periods.put(period, items)
        if (periods.length() == 0) root.remove(owner) else root.put(owner, periods)
        storage.write(cipher.encrypt(root.toString()))
    }

    fun clear(identifier: String) {
        val root = load()
        root.keys().asSequence().toList().filter { it.equals(identifier, true) || it.startsWith("$identifier/", true) }.forEach { root.remove(it) }
        storage.write(cipher.encrypt(root.toString()))
    }
}
