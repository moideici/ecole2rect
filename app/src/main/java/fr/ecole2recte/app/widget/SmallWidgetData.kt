package fr.ecole2recte.app.widget

import fr.ecole2recte.app.data.isNewGrade
import fr.ecole2recte.app.domain.*
import fr.ecole2recte.app.sync.WidgetSnapshot
import java.time.LocalDateTime

enum class SmallWidgetKind(val label: String, val icon: String, val tab: String) {
    NextClass("Prochain cours", "◷", "agenda"),
    Homework("Devoirs restants", "✓", "agenda_homework"),
    LastGrade("Dernière note", "∑", "grades"),
    Messages("Messages non lus", "✉", "messages"),
    Exam("Prochain contrôle", "◷", "agenda_homework"),
}

data class SmallWidgetReading(val value: String = "–", val caption: String = "Ouvre l'app.", val detail: String = "", val badge: String = "", val tone: MeanTone = MeanTone.Neutral)

object SmallWidgetData {
    fun read(kind: SmallWidgetKind, snapshot: WidgetSnapshot?, now: LocalDateTime, nowMillis: Long, privacy: PrivacyMode): SmallWidgetReading {
        if (snapshot == null) return SmallWidgetReading()
        return when (kind) {
            SmallWidgetKind.NextClass -> {
                val next = WidgetLogic.upcoming(snapshot.lessons, now).firstOrNull()
                if (next == null) SmallWidgetReading(caption = "Pas de cours à venir")
                else SmallWidgetReading(next.subject, "${next.start} · ${WidgetLogic.status(next, now)}", next.room.takeIf { it.isNotBlank() }?.let { "Salle $it" }.orEmpty())
            }
            SmallWidgetKind.Homework -> {
                fun due(task: fr.ecole2recte.app.sync.WidgetTask) = runCatching { java.time.LocalDate.parse(task.dueDate) }.getOrNull()
                val tasks = snapshot.tasks.filter { due(it)?.let { date -> date >= now.toLocalDate() } ?: true }
                    .sortedBy { due(it) ?: java.time.LocalDate.MAX }
                SmallWidgetReading(tasks.size.toString(), "devoir${if (tasks.size == 1) "" else "s"} à faire", tasks.firstOrNull()?.subject.orEmpty(),
                    tone = if (tasks.any { due(it)?.let { date -> date <= now.toLocalDate().plusDays(1) } == true }) MeanTone.Negative else MeanTone.Neutral)
            }
            SmallWidgetKind.LastGrade -> {
                val grade = snapshot.grades.sortedByDescending { it.date }.firstOrNull()
                when {
                    grade == null -> SmallWidgetReading(caption = "Pas encore de note")
                    privacy.hidesGrades -> SmallWidgetReading(MASKED, grade.subject)
                    else -> SmallWidgetReading(grade.value, grade.subject, grade.classGap?.let(GradeMath::classGapLabel).orEmpty(),
                        if (isNewGrade(grade.firstSeenAt, nowMillis)) "Nouveau" else "")
                }
            }
            SmallWidgetKind.Messages -> SmallWidgetReading(if (privacy.hidesMessages) MASKED else snapshot.unread.toString(), "messages non lus")
            SmallWidgetKind.Exam -> {
                val exams = ExamCountdown.upcoming(snapshot.exams.map { Homework(it.title, it.subject, it.due, it.colorIndex, dueDate = it.dueDate, exam = true) }, now.toLocalDate())
                val exam = exams.firstOrNull()
                if (exam == null) SmallWidgetReading(caption = "Pas de contrôle à venir")
                else SmallWidgetReading("J−${ExamCountdown.daysUntil(exam, now.toLocalDate())}", exam.subject,
                    FrenchFormat.dayMonthShort(java.time.LocalDate.parse(exam.date)))
            }
        }
    }
}
