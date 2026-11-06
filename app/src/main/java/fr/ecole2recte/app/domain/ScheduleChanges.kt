package fr.ecole2recte.app.domain

import kotlin.math.abs

/** Ce qu'était un cours avant d'être déplacé, remplacé ou modifié : de quoi barrer l'ancienne valeur. */
data class LessonBefore(
    val subject: String,
    val start: String,
    val end: String,
    val room: String,
    val teacher: String,
)

enum class LessonChangeKind {
    /** Même matière le même jour, à une autre heure. */
    Moved,

    /** Une autre matière à la même heure. */
    Replaced,

    /** Même créneau, salle / professeur / fin différents. */
    Updated,

    /** Le cours était annulé, il ne l'est plus. */
    Restored,
}

/**
 * Un changement repéré entre deux lectures de l'emploi du temps. [parts] le dit en courtes phrases
 * (« 10:00 → 14:00 », « salle B12 → C03 ») ; [afterIndex] est la place du cours dans la liste « après ».
 */
data class LessonChange(
    val kind: LessonChangeKind,
    val before: ScheduleEntry,
    val after: ScheduleEntry,
    val afterIndex: Int,
    val parts: List<String>,
)

/** Une ligne de l'ancienne et de la nouvelle valeur, pour la fiche d'un cours. */
data class ChangeLine(val label: String, val old: String, val new: String)

/**
 * Compare deux lectures de l'emploi du temps et repère ce qui a bougé. Fonction pure.
 *
 * Seuls les jours donnés sont regardés, et seulement ceux dont on avait déjà les cours « avant » : un
 * jour qui entre dans la fenêtre n'a rien à comparer. Les cours déplacés **d'un jour à l'autre** ne sont
 * pas rapprochés (rien ne prouve que c'est le même cours).
 */
object ScheduleChanges {
    fun diff(before: List<ScheduleEntry>, after: List<ScheduleEntry>, dates: Set<String>): List<LessonChange> {
        val changes = mutableListOf<LessonChange>()
        for (date in dates.sorted()) {
            val old = before.filter { it.date == date }.sortedBy { minutes(it.start) }
            val fresh = after.indices.filter { after[it].date == date }
            if (old.isEmpty() || fresh.isEmpty()) continue
            val freeBefore = old.toMutableList()
            val freeAfter = fresh.sortedBy { minutes(after[it].start) }.toMutableList()

            // 1. Le même créneau (heure de début et matière).
            for (b in old) {
                val match = freeAfter.firstOrNull { after[it].start == b.start && same(after[it], b) } ?: continue
                freeBefore -= b
                freeAfter -= match
                val a = after[match]
                if (a.canceled && !b.canceled) {
                    // Annulé : déjà annoncé par ailleurs. Mais si la même matière réapparaît ce jour-là, c'est un déplacement.
                    val moved = freeAfter.firstOrNull { same(after[it], b) && !after[it].canceled }
                    if (moved != null) {
                        freeAfter -= moved
                        changes += moveChange(b, after[moved], moved)
                    }
                    continue
                }
                if (a.canceled) continue
                sameSlotChange(b, a, match)?.let { changes += it }
            }
            // 2. La même matière à une autre heure : déplacé.
            for (b in freeBefore.filter { !it.canceled }) {
                val match = freeAfter
                    .filter { same(after[it], b) && !after[it].canceled }
                    .minByOrNull { abs(minutes(after[it].start) - minutes(b.start)) } ?: continue
                freeBefore -= b
                freeAfter -= match
                changes += moveChange(b, after[match], match)
            }
            // 3. Une autre matière à la même heure : remplacé.
            for (b in freeBefore.filter { !it.canceled }) {
                val match = freeAfter.firstOrNull { after[it].start == b.start && !after[it].canceled } ?: continue
                freeBefore -= b
                freeAfter -= match
                val a = after[match]
                changes += LessonChange(
                    LessonChangeKind.Replaced,
                    b,
                    a,
                    match,
                    listOf("${b.subject.name} → ${a.subject.name}") + roomAndTeacher(b, a),
                )
            }
        }
        return changes.sortedWith(compareBy({ it.after.date }, { it.after.start }))
    }

    /** Pose sur chaque cours changé ce qu'il était avant : l'agenda barre alors l'ancienne valeur. */
    fun annotate(before: List<ScheduleEntry>, after: List<ScheduleEntry>, dates: Set<String>): List<ScheduleEntry> {
        val changes = diff(before, after, dates).filter { it.kind != LessonChangeKind.Restored }
        if (changes.isEmpty()) return after
        val byIndex = changes.associateBy { it.afterIndex }
        return after.mapIndexed { index, entry ->
            byIndex[index]?.let { entry.copy(before = it.before.toBefore()) } ?: entry
        }
    }

    /** Les lignes « ancienne valeur → nouvelle valeur » d'un cours annoté ; vide si rien n'a changé. */
    fun lines(entry: ScheduleEntry): List<ChangeLine> {
        val b = entry.before ?: return emptyList()
        return buildList {
            if (!b.subject.equals(entry.subject.name, ignoreCase = true) && b.subject.isNotBlank()) {
                add(ChangeLine("Matière", b.subject, entry.subject.name))
            }
            if (b.start != entry.start || b.end != entry.end) {
                add(ChangeLine("Horaire", "${b.start} – ${b.end}", "${entry.start} – ${entry.end}"))
            }
            if (b.room != entry.subject.room && entry.subject.room.isNotBlank()) {
                add(ChangeLine("Salle", b.room.ifBlank { "non précisée" }, entry.subject.room))
            }
            if (b.teacher != entry.subject.teacher && entry.subject.teacher.isNotBlank()) {
                add(ChangeLine("Professeur", b.teacher.ifBlank { "non précisé" }, entry.subject.teacher))
            }
        }
    }

    /** Le mot à afficher sur un cours changé : « Déplacé », « Remplacé » ou « Modifié » ; `null` sans changement connu. */
    fun tag(entry: ScheduleEntry): String? {
        val b = entry.before ?: return null
        return when {
            !b.subject.equals(entry.subject.name, ignoreCase = true) -> "Remplacé"
            b.start != entry.start -> "Déplacé"
            else -> "Modifié"
        }
    }

    private fun moveChange(b: ScheduleEntry, a: ScheduleEntry, index: Int) = LessonChange(
        LessonChangeKind.Moved,
        b,
        a,
        index,
        listOf("${b.start} → ${a.start}") + roomAndTeacher(b, a),
    )

    private fun sameSlotChange(b: ScheduleEntry, a: ScheduleEntry, index: Int): LessonChange? {
        val parts = mutableListOf<String>()
        val restored = b.canceled && !a.canceled
        if (restored) parts += "cours rétabli"
        parts += roomAndTeacher(b, a)
        if (b.end != a.end) parts += "fin à ${a.end}"
        if (parts.isEmpty() && a.modified && !b.modified) parts += "modifié"
        if (parts.isEmpty()) return null
        return LessonChange(if (restored) LessonChangeKind.Restored else LessonChangeKind.Updated, b, a, index, parts)
    }

    private fun roomAndTeacher(b: ScheduleEntry, a: ScheduleEntry): List<String> = buildList {
        val oldRoom = b.subject.room
        val newRoom = a.subject.room
        if (oldRoom != newRoom && newRoom.isNotBlank()) add(if (oldRoom.isBlank()) "salle $newRoom" else "salle $oldRoom → $newRoom")
        val oldTeacher = b.subject.teacher
        val newTeacher = a.subject.teacher
        if (oldTeacher != newTeacher && newTeacher.isNotBlank()) add(if (oldTeacher.isBlank()) "avec $newTeacher" else "professeur $oldTeacher → $newTeacher")
    }

    private fun same(x: ScheduleEntry, y: ScheduleEntry) = x.subject.name.equals(y.subject.name, ignoreCase = true)

    private fun ScheduleEntry.toBefore() = LessonBefore(subject.name, start, end, subject.room, subject.teacher)

    private fun minutes(time: String): Int =
        runCatching { time.take(2).toInt() * 60 + time.takeLast(2).toInt() }.getOrDefault(0)
}
