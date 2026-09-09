package fr.ecole2recte.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.BuildConfig
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import fr.ecole2recte.app.core.auth.SecurityAnswer
import fr.ecole2recte.app.core.format.Dates
import fr.ecole2recte.app.core.model.Account
import fr.ecole2recte.app.core.model.DataSection
import fr.ecole2recte.app.core.model.SchoolSnapshot
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.core.repo.AutoAnswerState
import fr.ecole2recte.app.core.repo.Insights
import fr.ecole2recte.app.core.repo.SyncState
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.HomeWidget
import fr.ecole2recte.app.core.settings.NotificationSettings
import fr.ecole2recte.app.core.settings.SubjectPalette
import fr.ecole2recte.app.core.settings.ThemeChoice
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.components.SubjectBadge
import fr.ecole2recte.app.ui.components.SubjectDot
import fr.ecole2recte.app.ui.components.subjectColor
import fr.ecole2recte.app.data.IcsExporter
import fr.ecole2recte.app.sync.Notifier
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Les réglages.
 *
 * Trois idées : ce que l'application sait de toi, ce que tu peux changer, et ce qu'elle
 * conserve sur l'appareil. Rien n'est caché derrière un menu obscur.
 */
@Composable
fun SettingsScreen(
    auth: AuthState,
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    sync: SyncState,
    autoAnswer: AutoAnswerState,
    onClose: () -> Unit,
    onOpenDetail: (Detail) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onSelectAccount: (Account) -> Unit,
    onBirthDate: (String, SecurityAnswer.BirthDate) -> Unit,
    onForgetAnswer: (String) -> Unit,
    onDiagnostics: (Boolean) -> Unit,
    onNotifications: ((NotificationSettings) -> NotificationSettings) -> Unit,
    onBiometricLock: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAverageTarget: (Double?) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signedIn = auth as? AuthState.SignedIn
    val account = signedIn?.account ?: snapshot.account
    val nowMillis = System.currentTimeMillis()
    val context = LocalContext.current

    DetailScaffold("Réglages", account?.schoolName?.takeIf { it.isNotBlank() }, onClose) {
        SettingsSection("Compte") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubjectBadge(
                    account?.displayName.orEmpty().ifBlank { "É" },
                    MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account?.displayName?.takeIf { it.isNotBlank() } ?: "Compte ÉcoleDirecte",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        listOfNotNull(
                            account?.className?.takeIf { it.isNotBlank() },
                            account?.schoolName?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { "Profil synchronisé" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Un parent doit voir d'un coup d'œil sous quel compte il est connecté :
                    // c'est ce qui explique pourquoi la messagerie est la sienne et pourquoi
                    // une coche de devoir ne quitte pas l'appareil.
                    if (account?.viewedByFamily == true) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            account.familyName.takeIf { it.isNotBlank() }
                                ?.let { "Vue parent · connecté en tant que $it" }
                                ?: "Vue parent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            val others = signedIn?.accounts.orEmpty().filter { it.id != account?.id }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (account?.viewedByFamily == true) "Autres enfants suivis" else "Autres profils de cette connexion",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                others.forEach { other ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAccount(other) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubjectDot(MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(other.displayName, style = MaterialTheme.typography.bodyMedium)
                            other.className.takeIf { it.isNotBlank() }?.let { className ->
                                Text(
                                    className,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        AutoAnswerSection(
            profiles = signedIn?.accounts.orEmpty().ifEmpty { listOfNotNull(account) },
            state = autoAnswer,
            parentView = account?.viewedByFamily == true,
            onBirthDate = onBirthDate,
            onForget = onForgetAnswer,
        )

        SettingsSection("Scolarité") {
            SettingsRow(
                title = "Vie scolaire",
                description = "Absences, retards, sanctions et encouragements.",
                onClick = { onOpenDetail(Detail.SchoolLife) },
            )
            SettingsRow(
                title = "Actualités",
                description = "Ce que l'établissement publie pour tout le monde.",
                onClick = { onOpenDetail(Detail.News) },
            )
            SettingsRow(
                title = "Manuels numériques",
                description = "Les manuels mis à disposition par l'établissement.",
                onClick = { onOpenDetail(Detail.Textbooks) },
            )
            SettingsRow(
                title = "Espace de stockage",
                description = "Les fichiers partagés par l'établissement.",
                onClick = { onOpenDetail(Detail.Cloud) },
            )
            SettingsRow(
                title = "Documents",
                description = "Bulletins et pièces publiés par l'établissement.",
                onClick = { onOpenDetail(Detail.Documents) },
            )
            SettingsRow(
                title = "Exporter l'emploi du temps",
                description = "Un fichier iCalendar à ouvrir dans l'agenda du téléphone.",
                onClick = {
                    val intent = IcsExporter(context).share(snapshot.lessons)
                    if (intent != null) {
                        runCatching { context.startActivity(Intent.createChooser(intent, "Exporter vers")) }
                    }
                },
            )
        }

        SettingsSection("Objectif") {
            AverageTargetPicker(settings.averageTarget, onAverageTarget)
        }

        SettingsSection("Personnalisation") {
            SettingsRow(
                title = "Composer l'accueil",
                description = "Choisir les widgets affichés et leur ordre.",
                onClick = { onOpenDetail(Detail.HomeEditor) },
            )
            SettingsRow(
                title = "Couleurs des matières",
                description = "Une couleur par matière, identique partout dans l'application.",
                onClick = { onOpenDetail(Detail.SubjectColors) },
            )
            SettingsRow(
                title = "Couleurs des widgets",
                description = when {
                    settings.widgetColors.custom -> "Fond et texte choisis à la main."
                    else -> settings.widgetColors.theme.label + " — un widget vit devant ton fond d'écran."
                },
                onClick = { onOpenDetail(Detail.WidgetColors) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Thème",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterPill(choice.label, selected = settings.theme == choice) { onTheme(choice) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(8.dp))
                SettingsSwitch(
                    title = "Couleurs du fond d'écran",
                    description = "Reprendre la palette du système plutôt que l'indigo de l'application.",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }
        }

        NotificationsSection(settings.notifications, onNotifications)

        SettingsSection("Synchronisation") {
            DataSection.entries.forEach { section ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(section.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            sync.errorFor(section) ?: Dates.freshness(snapshot.syncedAt[section], nowMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sync.errorFor(section) != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    SubjectDot(
                        if (sync.errorFor(section) != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            AppTheme.palette.success
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                title = "Tout synchroniser",
                description = if (sync.offline) {
                    "Hors ligne : les données affichées viennent du cache local."
                } else {
                    "Relancer une synchronisation complète maintenant."
                },
                onClick = onRefresh,
            )
        }

        SettingsSection("Données et confidentialité") {
            Text(
                "Sur cet appareil : tes identifiants chiffrés par le Keystore Android, la dernière " +
                    "synchronisation en cache, et tes réglages. Rien n'est envoyé ailleurs qu'à " +
                    "ÉcoleDirecte, et aucun mot de passe, jeton ou cookie n'apparaît dans les diagnostics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            SettingsSwitch(
                title = "Verrouiller l'application",
                description = if (canLock(context)) {
                    "Demander l'empreinte ou le code de l'appareil à l'ouverture."
                } else {
                    "Aucun verrou n'est configuré sur cet appareil."
                },
                checked = settings.biometricLock,
                onCheckedChange = { wanted -> if (!wanted || canLock(context)) onBiometricLock(wanted) },
            )
            SettingsRow(
                title = "Journal des erreurs",
                description = "Ce qui a échoué depuis le lancement, sans mot de passe ni jeton.",
                onClick = { onOpenDetail(Detail.ErrorLog) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diagnostics réseau", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Affiche le détail technique en cas d'erreur de connexion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.showDiagnostics, onCheckedChange = onDiagnostics)
            }
        }

        SettingsSection("Session") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                    .clickable(onClick = onSignOut)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Se déconnecter et effacer les données locales",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Text(
            "ecole2recte ${BuildConfig.VERSION_NAME} · client non officiel d'ÉcoleDirecte. " +
                "L'API utilisée n'est pas publique et peut changer sans préavis.",
            modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Notifications.
 *
 * Rien n'est envoyé sans permission, et la permission n'est demandée qu'au moment où
 * l'utilisateur allume l'interrupteur — pas au premier lancement, où elle n'aurait aucun
 * sens et serait refusée d'un réflexe.
 */

/**
 * Ce que l'application sait répondre à la place de l'utilisateur.
 *
 * La promesse « on se connecte une fois » repose sur deux informations : la date de
 * naissance de chaque élève suivi, et les réponses déjà validées. Toutes deux appartiennent
 * à l'utilisateur : elles sont donc visibles, corrigeables et effaçables ici — pas enfouies
 * dans un stockage dont on ne saurait rien.
 */
@Composable
private fun AutoAnswerSection(
    profiles: List<Account>,
    state: AutoAnswerState,
    parentView: Boolean,
    onBirthDate: (String, SecurityAnswer.BirthDate) -> Unit,
    onForget: (String) -> Unit,
) {
    SettingsSection("Connexion automatique") {
        Text(
            if (parentView) {
                "ÉcoleDirecte peut reposer sa question de sécurité à tout moment, et elle porte " +
                    "sur la date de naissance de l'enfant. Renseignée ici, l'application " +
                    "reconnaît la bonne proposition et se reconnecte sans vous déranger."
            } else {
                "ÉcoleDirecte peut reposer sa question de sécurité à tout moment. Ta date de " +
                    "naissance renseignée ici, l'application reconnaît la bonne proposition et " +
                    "se reconnecte sans rien te demander."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        profiles.forEach { profile ->
            BirthDateRow(
                name = profile.firstName.ifBlank { profile.displayName }.ifBlank { "Élève" },
                // La date saisie à la connexion n'est reprise que s'il n'y a qu'un élève :
                // l'afficher sous chacun des enfants d'une fratrie laisserait croire qu'ils
                // sont nés le même jour.
                stored = state.birthDates.forName(profile.firstName)
                    .takeIf { it.isFilled }
                    ?: if (profiles.size == 1) state.birthDates.forQuestion("") else SecurityAnswer.BirthDate(),
                onChange = { date -> onBirthDate(profile.firstName, date) },
            )
        }

        val remembered = state.remembered
        if (remembered.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Réponses mémorisées",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            remembered.forEach { answer ->
                val validity = "${Dates.dayAndMonth(answer.validUntil)} ${answer.validUntil.year}"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            answer.readableQuestion,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "« ${answer.answer} » · valable jusqu'au $validity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Oublier",
                        modifier = Modifier
                            .clickable { onForget(answer.question) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Aucune réponse n'est devinée : seules une date que vous avez saisie ou une réponse " +
                "que vous avez validée sont rejouées, et une seule fois par connexion. Refusée, " +
                "elle est oubliée et la question vous revient.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Trois champs pour une date, avec le nom de l'élève qu'elle concerne. */
@Composable
private fun BirthDateRow(
    name: String,
    stored: SecurityAnswer.BirthDate,
    onChange: (SecurityAnswer.BirthDate) -> Unit,
) {
    // La valeur n'est reprise du stockage qu'au premier affichage : sans cela, l'écriture
    // asynchrone qui suit chaque frappe pourrait remettre l'ancien contenu sous les doigts.
    var value by remember(name) { mutableStateOf(stored) }
    Spacer(Modifier.height(12.dp))
    Text(
        "Date de naissance de $name",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField("Jour", value.day, 2, Modifier.weight(1f)) {
            value = value.copy(day = it)
            onChange(value)
        }
        DateField("Mois", value.month, 2, Modifier.weight(1f)) {
            value = value.copy(month = it)
            onChange(value)
        }
        DateField("Année", value.year, 4, Modifier.weight(1.3f)) {
            value = value.copy(year = it)
            onChange(value)
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    maxLength: Int,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        // Un jour ne s'écrit pas avec des lettres : filtrer à la saisie évite une date
        // impossible à rapprocher d'une proposition.
        onValueChange = { raw -> onChange(raw.filter(Char::isDigit).take(maxLength)) },
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun NotificationsSection(
    settings: NotificationSettings,
    onChange: ((NotificationSettings) -> NotificationSettings) -> Unit,
) {
    val context = LocalContext.current
    val notifier = remember(context) { Notifier(context) }
    var granted by remember { mutableStateOf(notifier.canNotify()) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (result) onChange { it.copy(enabled = true) }
    }

    SettingsSection("Notifications") {
        SettingsSwitch(
            title = "Autoriser les notifications",
            description = "Une synchronisation par heure en arrière-plan, et rien qui se répète.",
            checked = settings.enabled && granted,
        ) { wanted ->
            if (!wanted) {
                onChange { it.copy(enabled = false) }
                return@SettingsSwitch
            }
            if (notifier.canNotify()) {
                granted = true
                onChange { it.copy(enabled = true) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Notifications coupées depuis les paramètres du système : nous ne pouvons
                // pas les rallumer nous-mêmes, autant le dire clairement.
                onChange { it.copy(enabled = true) }
            }
        }
        if (settings.enabled && !granted) {
            Text(
                "Les notifications sont bloquées dans les paramètres Android de l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (settings.enabled) {
            SettingsSwitch("Nouvelles notes", "Quand une note est publiée.", settings.grades) { value ->
                onChange { it.copy(grades = value) }
            }
            SettingsSwitch("Nouveaux devoirs", "Quand un devoir est ajouté au cahier de textes.", settings.homework) { value ->
                onChange { it.copy(homework = value) }
            }
            SettingsSwitch("Nouveaux messages", "Quand un message arrive.", settings.messages) { value ->
                onChange { it.copy(messages = value) }
            }
            SettingsSwitch(
                "Emploi du temps",
                "Un cours annulé, déplacé ou ajouté.",
                settings.timetable,
            ) { value ->
                onChange { it.copy(timetable = value) }
            }
            SettingsSwitch("Rappel d'évaluation", "La veille d'un devoir surveillé annoncé.", settings.examReminder) { value ->
                onChange { it.copy(examReminder = value) }
            }
            SettingsSwitch(
                "Rappel du soir",
                "Les cours de demain et ce qu'il faut emporter, entre 18 h et 22 h.",
                settings.tomorrow,
            ) { value ->
                onChange { it.copy(tomorrow = value) }
            }
        }
    }
}

/**
 * La moyenne visée pour la période.
 *
 * Des paliers plutôt qu'un champ de saisie : personne ne vise 14,37, et un clavier numérique
 * pour trois chiffres est plus long à traverser qu'une rangée de pastilles. « Aucun » est le
 * défaut assumé — l'application n'invente pas un objectif à la place de l'élève, et sans
 * objectif le widget correspondant le dit au lieu d'afficher un chiffre inventé.
 */
@Composable
private fun AverageTargetPicker(target: Double?, onChange: (Double?) -> Unit) {
    Text(
        "Ce qu'il faudrait obtenir pour l'atteindre s'affiche dans l'écran Notes et dans le " +
            "widget Objectif, coefficients compris.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("aucun") {
            FilterPill("Aucun", selected = target == null) { onChange(null) }
        }
        items(AVERAGE_TARGETS, key = { it.toString() }) { value ->
            FilterPill(
                text = value.toInt().toString(),
                selected = target != null && kotlin.math.abs(target - value) < 0.01,
            ) { onChange(value) }
        }
    }
}

/** Les paliers proposés : de « je veux la moyenne » aux mentions. */
private val AVERAGE_TARGETS = listOf(10.0, 12.0, 14.0, 15.0, 16.0, 18.0)

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        modifier = Modifier.padding(start = ScreenPadding + 4.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(Modifier.padding(horizontal = ScreenPadding)) {
        AppCard(content = content)
    }
}

@Composable
private fun SettingsRow(title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Composer l'accueil.
 *
 * Des flèches plutôt qu'un glisser-déposer : c'est utilisable au clavier, annoncé correctement
 * par les lecteurs d'écran, et personne ne perd sa carte en la faisant tomber hors de l'écran.
 */
@Composable
fun HomeEditorScreen(
    settings: AppSettings,
    onMove: (HomeWidget, Int) -> Unit,
    onToggle: (HomeWidget) -> Unit,
    onClose: () -> Unit,
) {
    val shown = settings.visibleWidgets.size
    DetailScaffold(
        title = "Composer l'accueil",
        subtitle = "$shown blocs affichés sur ${settings.orderedWidgets.size}",
        onClose = onClose,
    ) {
        val widgets = settings.orderedWidgets
        widgets.forEachIndexed { index, setting ->
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                setting.widget.label,
                                style = MaterialTheme.typography.titleMedium,
                                // Un bloc masqué s'écrit en gris : la liste se lit alors d'un
                                // coup d'œil, sans avoir à suivre l'état de chaque
                                // interrupteur.
                                color = if (setting.visible) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                setting.widget.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = setting.visible, onCheckedChange = { onToggle(setting.widget) })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MoveButton(
                            icon = Icons.Outlined.ArrowUpward,
                            description = "Monter ${setting.widget.label}",
                            enabled = index > 0,
                        ) { onMove(setting.widget, -1) }
                        MoveButton(
                            icon = Icons.Outlined.ArrowDownward,
                            description = "Descendre ${setting.widget.label}",
                            enabled = index < widgets.lastIndex,
                        ) { onMove(setting.widget, 1) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (setting.visible) "Position ${index + 1}" else "Masqué",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Couleurs des matières.
 *
 * Chaque matière connue — cours, devoirs ou notes — peut recevoir sa couleur. Sans choix,
 * la couleur est dérivée du nom de la matière : jamais deux teintes différentes pour la même
 * matière d'un écran à l'autre.
 */
@Composable
fun SubjectColorsScreen(
    snapshot: SchoolSnapshot,
    settings: AppSettings,
    onPick: (String, Long?) -> Unit,
    onClose: () -> Unit,
) {
    val subjects = Insights.knownSubjects(snapshot)
    DetailScaffold("Couleurs des matières", "${subjects.size} matières connues", onClose) {
        if (subjects.isEmpty()) {
            Text(
                "Les matières apparaîtront ici après la première synchronisation.",
                modifier = Modifier.padding(horizontal = ScreenPadding + 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        subjects.forEach { subject ->
            val current = subjectColor(subject, settings)
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(current, 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            subject,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Par défaut",
                            modifier = Modifier
                                .clickable { onPick(subject, null) }
                                .padding(6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(SubjectPalette.COLORS, key = { it }) { value ->
                            val color = Color(value)
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (color == current) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                    .clickable { onPick(subject, value) },
                            )
                        }
                    }
                }
            }
        }
    }
}
