package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.format.Voice
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.HomeworkItem
import fr.ecole2recte.app.core.model.Lesson
import fr.ecole2recte.app.core.model.SchoolLifeKind
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.ProgressBar
import fr.ecole2recte.app.ui.components.RoundIconButton
import fr.ecole2recte.app.ui.components.ScreenHeader
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.Tag
import fr.ecole2recte.app.ui.components.subjectColor
import fr.ecole2recte.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * L'accueil.
 *
 * Ce n'est pas une page de plus : c'est la réponse à « qu'est-ce que je dois savoir, là,
 * maintenant ». Chaque bloc est un widget que l'utilisateur peut déplacer ou masquer ;
 * l'ordre est le sien, et il est conservé.
 */
@Composable
fun HomeScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    now: LocalDateTime,
    accounts: List<Account>,
    onNavigate: (Destination) -> Unit,
    onOpenDetail: (Detail) -> Unit,
    onRefresh: () -> Unit,
    onToggleHomework: (HomeworkItem) -> Unit,
    onSelectAccount: (Account) -> Unit,
) {
    val today = now.toLocalDate()
    val account = snapshot.account
    val date = Dates.fullDate(today).replaceFirstChar { it.uppercase() }

    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item("header") {
            ScreenHeader(
                // Un parent n'est pas salué par son prénom : ce qu'il vient voir, c'est la
                // journée de son enfant, et c'est ce nom-là qui doit être en haut de l'écran.
                title = Voice.homeTitle(account),
                subtitle = if (account?.viewedByFamily == true) {
                    listOfNotNull(account.className.takeIf { it.isNotBlank() }, date).joinToString(" · ")
                } else {
                    date
                },
                actions = {
                    RoundIconButton(Icons.Outlined.Search, "Rechercher") { onOpenDetail(Detail.Search) }
                    RoundIconButton(Icons.Outlined.Refresh, "Synchroniser", onClick = onRefresh)
                    RoundIconButton(Icons.Outlined.Settings, "Réglages") { onOpenDetail(Detail.Settings) }
                },
            )
        }
        // Une fratrie se suit d'un geste : passer d'un enfant à l'autre depuis les réglages
        // demanderait quatre touchers pour une question posée dix fois par jour.
        if (accounts.size > 1) {
            item("enfants") {
                ChildSwitcher(
                    accounts = accounts,
                    selectedId = account?.id,
                    onSelect = onSelectAccount,
                )
            }
        }
        item("sync") { SyncStatusRow(sync, snapshot.lastSyncedAt, now) }

        settings.visibleWidgets.forEach { widget ->
            item(widget.name) {
                Box(Modifier.padding(horizontal = ScreenPadding, vertical = 7.dp)) {
                    when (widget) {
                        HomeWidget.NEXT_CLASS -> NextClassWidget(snapshot, settings, now, onNavigate)
                        HomeWidget.URGENT -> UrgentWidget(snapshot, settings, today, onOpenDetail)
                        HomeWidget.TODAY -> TodayWidget(snapshot, settings, now, onNavigate)
                        HomeWidget.HOMEWORK -> HomeworkWidget(snapshot, settings, today, onNavigate, onToggleHomework)
                        HomeWidget.EXAMS -> ExamsWidget(snapshot, settings, today, onOpenDetail)
                        HomeWidget.GRADES -> GradesWidget(snapshot, settings, onNavigate)
                        HomeWidget.SUBJECT_AVERAGES -> SubjectAveragesWidget(snapshot, settings, today, onNavigate)
                        HomeWidget.MESSAGES -> MessagesWidget(snapshot, settings, onNavigate)
                        HomeWidget.WEEK -> WeekOverviewWidget(snapshot, settings, today, onNavigate)
                        HomeWidget.TOMORROW -> TomorrowWidget(snapshot, settings, today, onNavigate)
                        HomeWidget.SCHOOL_LIFE -> SchoolLifeWidget(snapshot, today, onOpenDetail)
                        HomeWidget.NEWS -> NewsWidget(snapshot, onOpenDetail)
                    }
                }
            }
        }

        item("customize") {
            Text(
                "Personnaliser l'accueil",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(Detail.HomeEditor) }
                    .padding(horizontal = ScreenPadding + 4.dp, vertical = 18.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Bascule entre les élèves suivis.
 *
 * Une pastille par enfant, dans l'ordre renvoyé par ÉcoleDirecte. Le cache de chacun est
 * conservé : revenir en arrière est immédiat, sans nouvelle synchronisation.
 */
@Composable
private fun ChildSwitcher(accounts: List<Account>, selectedId: String?, onSelect: (Account) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(accounts, key = { it.id }) { entry ->
            FilterPill(
                text = entry.firstName.ifBlank { entry.displayName },
                selected = entry.id == selectedId,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/**
 * Les dernières actualités de l'établissement.
 *
 * Le bloc ne s'affiche que si le module a répondu : un établissement qui ne l'a pas ouvert
 * ne doit pas laisser un cadre vide sur l'accueil de tous ses élèves.
 */
@Composable
private fun NewsWidget(snapshot: SchoolSnapshot, onOpenDetail: (Detail) -> Unit) {
    val news = snapshot.news.take(2)
    if (news.isEmpty()) return
    AppCard(onClick = { onOpenDetail(Detail.News) }) {
        WidgetLabel("ACTUALITÉS")
        Spacer(Modifier.height(10.dp))
        news.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    item.date?.let { Dates.dayAndMonth(it) },
                    item.category.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Publication" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ligne d'état : ce qui est affiché vient-il du réseau ou du cache, et de quand ? */
@Composable
fun SyncStatusRow(sync: SyncState, lastSyncedAt: Long?, now: LocalDateTime) {
    // L'horloge du ViewModel plutôt que l'heure système : le libellé « il y a 4 min »
    // se recompose alors tout seul, sans dépendre d'un autre changement d'état.
    val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val color = when {
        sync.offline -> AppTheme.palette.warning
        sync.errors.isNotEmpty() -> AppTheme.palette.warning
        else -> AppTheme.palette.success
    }
    val label = when {
        sync.running -> "Synchronisation en cours…"
        sync.offline -> "Hors ligne · données de ${Dates.freshness(lastSyncedAt, nowMillis)}"
        sync.errors.isNotEmpty() -> "Partiellement à jour · ${Dates.freshness(lastSyncedAt, nowMillis)}"
        else -> "À jour · ${Dates.freshness(lastSyncedAt, nowMillis)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding + 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NextClassWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    now: LocalDateTime,
    onNavigate: (Destination) -> Unit,
) {
    val lesson = Insights.currentOrNextLesson(snapshot.lessons, now)
    if (lesson == null) {
        AppCard(onClick = { onNavigate(Destination.TIMETABLE) }) {
            WidgetLabel("PROCHAIN COURS")
            Spacer(Modifier.height(12.dp))
            Text("Rien de prévu", style = MaterialTheme.typography.titleLarge)
            Text(
                "Aucun cours à venir dans l'emploi du temps synchronisé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val accent = subjectColor(lesson.subject, settings)
    AppCard(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        onClick = { onNavigate(Destination.TIMETABLE) },
    ) {
        Text(
            "PROCHAIN COURS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Dates.time(lesson.start)} · ${Dates.relativeToNow(lesson.start, lesson.end, now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        lesson.room.takeIf { it.isNotBlank() }?.let { "Salle $it" },
                        lesson.teacher.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SubjectDot(accent, 14.dp)
            }
        }
        if (lesson.canceled) {
            Spacer(Modifier.height(12.dp))
            Tag("Cours annulé", MaterialTheme.colorScheme.onPrimary, Color.White.copy(alpha = 0.2f))
        } else {
            Spacer(Modifier.height(18.dp))
            ProgressBar(
                fraction = Dates.progress(lesson.start, lesson.end, now),
                trackColor = Color.White.copy(alpha = 0.25f),
                indicatorColor = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    Dates.range(lesson.start, lesson.end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
                Text(
                    Dates.duration(java.time.Duration.between(lesson.start, lesson.end).toMinutes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun UrgentWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onOpenDetail: (Detail) -> Unit,
) {
    val urgent = Insights.urgentHomework(snapshot.homework, settings, today) ?: return
    val accent = subjectColor(urgent.subject, settings)
    AppCard(
        containerColor = AppTheme.palette.spotlight,
        contentColor = AppTheme.palette.onSpotlight,
        onClick = { onOpenDetail(Detail.Homework(urgent.id)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "À NE PAS OUBLIER",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.palette.onSpotlightMuted,
                modifier = Modifier.weight(1f),
            )
            SubjectDot(accent, 10.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            urgent.subject,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (urgent.content.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                urgent.content,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.palette.onSpotlightMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Dates.dueLabel(urgent.dueDate, today).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.palette.onSpotlightMuted,
            )
            if (urgent.exam) {
                Spacer(Modifier.width(8.dp))
                Tag("Évaluation", AppTheme.palette.onSpotlight, Color.White.copy(alpha = 0.14f))
            }
        }
    }
}

@Composable
private fun TodayWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    now: LocalDateTime,
    onNavigate: (Destination) -> Unit,
) {
    val lessons = Insights.lessonsOn(snapshot.lessons, now.toLocalDate())
    AppCard(onClick = { onNavigate(Destination.TIMETABLE) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetLabel("AUJOURD'HUI", Modifier.weight(1f))
            Text(
                "Voir la semaine",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (lessons.isEmpty()) {
            Text(
                "Aucun cours aujourd'hui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        lessons.take(4).forEach { lesson ->
            LessonRow(lesson, settings, now)
        }
        if (lessons.size > 4) {
            Spacer(Modifier.height(6.dp))
            Text(
                "+ ${lessons.size - 4} autres cours",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, settings: AppSettings, now: LocalDateTime) {
    val past = lesson.end.isBefore(now)
    val current = !past && !lesson.start.isAfter(now)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Dates.time(lesson.start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        SubjectDot(
            subjectColor(lesson.subject, settings).copy(alpha = if (past) 0.35f else 1f),
            if (current) 11.dp else 9.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                lesson.subject,
                style = MaterialTheme.typography.bodyMedium,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (lesson.room.isNotBlank()) {
                Text(
                    "Salle ${lesson.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (lesson.canceled) Tag("Annulé", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun HomeworkWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onNavigate: (Destination) -> Unit,
    onToggle: (HomeworkItem) -> Unit,
) {
    val pending = Insights.pendingHomework(snapshot.homework, settings, today)
    val late = pending.count { it.dueDate.isBefore(today) }
    AppCard(onClick = { onNavigate(Destination.HOMEWORK) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetLabel("À FAIRE", Modifier.weight(1f))
            if (late > 0) Tag("$late en retard", MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        if (pending.isEmpty()) {
            Text("Rien à faire. Profite.", style = MaterialTheme.typography.titleMedium)
            return@AppCard
        }
        Text(
            if (pending.size == 1) "1 devoir" else "${pending.size} devoirs",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        pending.take(3).forEach { item ->
            HomeworkRow(item, settings, today, onToggle)
        }
    }
}

@Composable
fun HomeworkRow(
    item: HomeworkItem,
    settings: AppSettings,
    today: LocalDate,
    onToggle: (HomeworkItem) -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    val done = settings.isHomeworkDone(item.id, item.done)
    val accent = subjectColor(item.subject, settings)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onToggle(item) },
            colors = CheckboxDefaults.colors(checkedColor = accent),
            // Sans cela, le lecteur d'écran annonce « case à cocher » sans dire de quoi.
            modifier = Modifier.semantics {
                contentDescription = if (done) {
                    "Marquer le devoir de ${item.subject} comme à faire"
                } else {
                    "Marquer le devoir de ${item.subject} comme fait"
                }
            },
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.exam) {
                    Spacer(Modifier.width(8.dp))
                    Tag("Évaluation", accent)
                }
                // Un devoir à déposer en ligne ne se termine pas en cochant une case : le
                // signaler dans la liste évite la mauvaise surprise de la veille au soir.
                if (item.onlineSubmission) {
                    Spacer(Modifier.width(8.dp))
                    Tag("En ligne", AppTheme.palette.warning)
                }
            }
            Text(
                buildString {
                    append(Dates.dueLabel(item.dueDate, today).replaceFirstChar { it.uppercase() })
                    if (item.content.isNotBlank()) {
                        append(" · ")
                        append(fr.ecole2recte.app.core.format.Text.preview(item.content, 60))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.dueDate.isBefore(today) && !done) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GradesWidget(snapshot: SchoolSnapshot, settings: AppSettings, onNavigate: (Destination) -> Unit) {
    val period = snapshot.periods.firstOrNull { it.current } ?: snapshot.periods.firstOrNull()
    val grades = Insights.gradesForPeriod(snapshot.grades, period?.code)
    val average = period?.average ?: Insights.weightedAverage(grades)
    val trend = Insights.trend(grades)
    AppCard(onClick = { onNavigate(Destination.GRADES) }) {
        WidgetLabel("NOTES")
        Spacer(Modifier.height(12.dp))
        if (average == null) {
            Text("Pas encore de moyenne", style = MaterialTheme.typography.titleMedium)
            Text(
                "Elle apparaîtra dès que des notes seront publiées.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(formatGrade(average), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(6.dp))
            Text(
                "/ 20",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            if (trend != null && kotlin.math.abs(trend) >= 0.1) {
                val positive = trend > 0
                Tag(
                    text = (if (positive) "↗ +" else "↘ ") + formatGrade(trend),
                    color = if (positive) AppTheme.palette.success else MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            period?.name?.let { "Moyenne générale · $it" } ?: "Moyenne générale",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        grades.take(3).forEach { grade ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectDot(subjectColor(grade.subject, settings))
                Spacer(Modifier.width(10.dp))
                Text(
                    grade.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    grade.value?.let { "${formatGrade(it)} / ${formatGrade(grade.scale)}" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MessagesWidget(snapshot: SchoolSnapshot, settings: AppSettings, onNavigate: (Destination) -> Unit) {
    val unread = Insights.unreadMessages(snapshot.inbox, settings)
    AppCard(onClick = { onNavigate(Destination.MESSAGES) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetLabel("MESSAGES", Modifier.weight(1f))
            if (unread.isNotEmpty()) Tag("${unread.size} non lus")
        }
        Spacer(Modifier.height(10.dp))
        val preview = snapshot.inbox.take(3)
        if (preview.isEmpty()) {
            Text(
                "Aucun message.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        preview.forEach { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectBadge(message.sender, subjectColor(message.sender, settings), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        message.sender,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        message.subject,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!settings.isMessageRead(message.id, message.read)) {
                    SubjectDot(MaterialTheme.colorScheme.primary, 8.dp)
                }
            }
        }
    }
}

/**
 * Évaluations annoncées.
 *
 * Un devoir surveillé ne se prépare pas la veille : ce bloc les sort de la liste des devoirs
 * et les classe par compte à rebours, ce qui est la seule chose qui compte pour réviser.
 */
@Composable
private fun ExamsWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onOpenDetail: (Detail) -> Unit,
) {
    val exams = Insights.upcomingExams(snapshot.homework, settings, today)
    AppCard {
        WidgetLabel("ÉVALUATIONS À VENIR")
        Spacer(Modifier.height(10.dp))
        if (exams.isEmpty()) {
            Text(
                "Aucune évaluation annoncée pour les trois prochaines semaines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        exams.take(3).forEach { exam ->
            val accent = subjectColor(exam.subject, settings)
            val days = ChronoUnit.DAYS.between(today, exam.dueDate)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(Detail.Homework(exam.id)) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectBadge(exam.subject, accent, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        exam.subject,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Dates.dayAndMonth(exam.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Tag(
                    text = when {
                        days < 0L -> "passée"
                        days == 0L -> "aujourd'hui"
                        days == 1L -> "demain"
                        else -> "dans $days j"
                    },
                    color = if (days <= 2L) MaterialTheme.colorScheme.error else accent,
                )
            }
        }
    }
}

/** Moyennes par matière : la barre rend l'écart lisible sans lire les chiffres. */
@Composable
private fun SubjectAveragesWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onNavigate: (Destination) -> Unit,
) {
    val averages = Insights.subjectAverages(snapshot, today)
    AppCard(onClick = { onNavigate(Destination.GRADES) }) {
        WidgetLabel("MOYENNES PAR MATIÈRE")
        Spacer(Modifier.height(10.dp))
        if (averages.isEmpty()) {
            Text(
                "Les moyennes apparaîtront dès la publication des premières notes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        averages.take(5).forEach { (subject, value) ->
            val accent = subjectColor(subject, settings)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectDot(accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    subject,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.width(64.dp)) {
                    ProgressBar(
                        fraction = (value / 20.0).toFloat(),
                        indicatorColor = accent,
                        height = 5.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    formatGrade(value),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
        }
    }
}

/**
 * La semaine en un coup d'œil.
 *
 * Chaque barre est proportionnelle au temps de cours du jour : on voit immédiatement la
 * journée chargée du mercredi sans lire un seul chiffre.
 */
@Composable
private fun WeekOverviewWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onNavigate: (Destination) -> Unit,
) {
    val weekStart = Dates.startOfWeek(today)
    val week = Insights.week(snapshot.lessons, weekStart)
    val busiest = week.maxOfOrNull { it.minutes }?.coerceAtLeast(1L) ?: 1L
    AppCard(onClick = { onNavigate(Destination.TIMETABLE) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetLabel("SEMAINE", Modifier.weight(1f))
            Text(
                "${week.sumOf { it.lessons }} cours",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { day ->
                val isToday = day.date == today
                val height = (18 + (54 * day.minutes / busiest)).toInt().dp
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (day.isEmpty) "—" else "${day.minutes / 60}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(height)
                            .background(
                                when {
                                    day.isEmpty -> MaterialTheme.colorScheme.surfaceVariant
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                },
                                RoundedCornerShape(8.dp),
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Dates.dayName(day.date).take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** Demain : ce qu'il faut mettre dans le sac ce soir. */
@Composable
private fun TomorrowWidget(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    today: LocalDate,
    onNavigate: (Destination) -> Unit,
) {
    val tomorrow = today.plusDays(1)
    val summary = Insights.daySummary(snapshot.lessons, tomorrow)
    val lessons = Insights.lessonsOn(snapshot.lessons, tomorrow).filterNot { it.canceled }
    val homework = Insights.homeworkDueOn(snapshot.homework, tomorrow)
        .filterNot { settings.isHomeworkDone(it.id, it.done) }
    AppCard(onClick = { onNavigate(Destination.TIMETABLE) }) {
        WidgetLabel("DEMAIN")
        Spacer(Modifier.height(8.dp))
        Text(
            Dates.dayAndMonth(tomorrow).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (summary.isEmpty) {
                "Aucun cours prévu."
            } else {
                "${summary.lessons} cours · ${Dates.time(summary.start!!)} — ${Dates.time(summary.end!!)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lessons.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lessons.take(6).forEach { lesson ->
                    SubjectDot(subjectColor(lesson.subject, settings), 10.dp)
                }
            }
        }
        if (homework.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (homework.size == 1) {
                    "1 devoir à rendre demain"
                } else {
                    "${homework.size} devoirs à rendre demain"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            homework.take(2).forEach { item ->
                Text(
                    "· ${item.subject}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Vie scolaire.
 *
 * Le mois écoulé seulement : une absence de septembre n'a plus à occuper l'accueil en juin,
 * et c'est le non-justifié qui doit sauter aux yeux.
 */
@Composable
private fun SchoolLifeWidget(
    snapshot: SchoolSnapshot,
    today: LocalDate,
    onOpenDetail: (Detail) -> Unit,
) {
    val recent = snapshot.schoolLife.filter { it.date.isAfter(today.minusDays(30)) }
    AppCard(onClick = { onOpenDetail(Detail.SchoolLife) }) {
        WidgetLabel("VIE SCOLAIRE")
        Spacer(Modifier.height(10.dp))
        if (recent.isEmpty()) {
            Text("Rien à signaler ce mois-ci.", style = MaterialTheme.typography.bodyMedium)
            return@AppCard
        }
        val unjustified = recent.count { it.kind == SchoolLifeKind.ABSENCE && !it.justified }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${recent.size} sur 30 jours",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (unjustified > 0) Tag("$unjustified non justifiée(s)", MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        recent.take(3).forEach { event ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectDot(
                    when (event.kind) {
                        SchoolLifeKind.ENCOURAGEMENT -> AppTheme.palette.success
                        SchoolLifeKind.LATE -> AppTheme.palette.warning
                        else -> MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    event.kind.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Dates.dayAndMonth(event.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WidgetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Les notes s'écrivent avec une virgule, et sans décimale inutile. */
fun formatGrade(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    val text = if (rounded == Math.floor(rounded)) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.FRANCE, "%.2f", rounded).trimEnd('0').trimEnd(',')
    }
    return text
}
