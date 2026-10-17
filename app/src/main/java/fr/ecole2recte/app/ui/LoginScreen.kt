package fr.ecole2recte.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.LoginForm
import fr.ecole2recte.app.core.format.Voice
import fr.ecole2recte.app.core.repo.AuthState
import fr.ecole2recte.app.ui.components.AppCard
import fr.ecole2recte.app.ui.components.FilterPill
import fr.ecole2recte.app.ui.components.PrimaryButton
import fr.ecole2recte.app.ui.components.ScreenPadding
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Écran de connexion.
 *
 * Il porte une promesse simple : on se connecte une fois. Deux choses la rendent tenable.
 *
 * D'abord le choix du profil, tout en haut : élève ou parent. Il ne change pas la façon de
 * se connecter — ÉcoleDirecte ne demande rien de plus à un parent — mais il change de qui on
 * parle. La question de sécurité d'un compte parent porte sur la date de naissance de
 * l'enfant ; demander « ta date de naissance » à une mère produirait une réponse fausse, et
 * une connexion manuelle de plus à chaque expiration de jeton.
 *
 * Ensuite la date elle-même, demandée en même temps que les identifiants : elle sert à
 * reconnaître la bonne proposition le jour où la question tombe, y compris en pleine
 * synchronisation de fond. Elle n'est jamais envoyée telle quelle, et rien n'est tenté deux
 * fois de suite.
 */
@Composable
fun LoginScreen(
    state: AuthState,
    form: LoginForm,
    suggestedAnswer: (String, List<String>) -> String?,
    showDiagnostics: Boolean,
    onFormChange: ((LoginForm) -> LoginForm) -> Unit,
    onSubmit: () -> Unit,
    onAnswer: (String) -> Unit,
    onToggleDiagnostics: (Boolean) -> Unit,
) {
    val question = state as? AuthState.SecurityQuestion
    val failure = state as? AuthState.Failed
    val connecting = state is AuthState.Connecting

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(Modifier.padding(vertical = 32.dp)) {
            Text(
                "ecole2recte",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text("Connexion", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Une seule connexion, puis l'application se reconnecte toute seule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "Je me connecte en tant que",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(
                    text = "Élève",
                    selected = !form.parentMode,
                    onClick = { onFormChange { it.copy(parentMode = false) } },
                )
                FilterPill(
                    text = "Parent",
                    selected = form.parentMode,
                    onClick = { onFormChange { it.copy(parentMode = true) } },
                )
            }
            Spacer(Modifier.height(18.dp))

            LabelledField(
                label = "Identifiant",
                value = form.identifier,
                onValueChange = { value -> onFormChange { it.copy(identifier = value) } },
            )
            Spacer(Modifier.height(12.dp))
            LabelledField(
                label = "Mot de passe",
                value = form.password,
                onValueChange = { value -> onFormChange { it.copy(password = value) } },
                password = true,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                Voice.birthDateLabel(form.parentMode) + " (facultatif)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                Voice.birthDateHelp(form.parentMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelledField(
                    label = "Jour",
                    value = form.birth.day,
                    onValueChange = { value -> onFormChange { it.copy(birth = it.birth.copy(day = value)) } },
                    modifier = Modifier.weight(1f),
                    number = true,
                )
                LabelledField(
                    label = "Mois",
                    value = form.birth.month,
                    onValueChange = { value -> onFormChange { it.copy(birth = it.birth.copy(month = value)) } },
                    modifier = Modifier.weight(1f),
                    number = true,
                )
                LabelledField(
                    label = "Année",
                    value = form.birth.year,
                    onValueChange = { value -> onFormChange { it.copy(birth = it.birth.copy(year = value)) } },
                    modifier = Modifier.weight(1.3f),
                    number = true,
                )
            }

            if (question != null) {
                SecurityQuestionBlock(
                    question = question,
                    chosen = form.chosenAnswer,
                    suggestion = suggestedAnswer(question.question, question.choices),
                    parentMode = form.parentMode,
                    onChoose = { value -> onFormChange { it.copy(chosenAnswer = value) } },
                )
            }

            if (failure != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(failure.message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (failure.diagnostic != null) {
                    DiagnosticPanel(
                        diagnostic = failure.diagnostic!!,
                        expanded = showDiagnostics,
                        onExpandedChange = onToggleDiagnostics,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = when {
                    connecting -> "Connexion…"
                    question != null -> "Valider la réponse"
                    else -> "Se connecter"
                },
                onClick = { if (question != null) onAnswer(form.chosenAnswer) else onSubmit() },
                enabled = if (question != null) form.chosenAnswer.isNotBlank() else form.canSubmit,
                loading = connecting,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Identifiants chiffrés par le Keystore Android. Ni mot de passe ni jeton n'apparaissent dans les diagnostics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecurityQuestionBlock(
    question: AuthState.SecurityQuestion,
    chosen: String,
    suggestion: String?,
    parentMode: Boolean,
    onChoose: (String) -> Unit,
) {
    Spacer(Modifier.height(20.dp))
    Text(
        "QUESTION DE SÉCURITÉ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Text(question.question, style = MaterialTheme.typography.titleMedium)

    // La question n'arrive ici que si l'application n'a pas su y répondre seule. Quand c'est
    // une réponse enregistrée qui vient d'être refusée, le dire évite de laisser croire à un
    // bug : la classe a changé, ou la date saisie était fausse.
    question.notice?.let { notice ->
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppTheme.palette.warningContainer,
            contentColor = AppTheme.palette.warning,
        ) {
            Text(notice, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (suggestion != null) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(AppTheme.palette.success, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (parentMode) {
                    "Une proposition correspond à la date de naissance saisie : $suggestion"
                } else {
                    "Une proposition correspond à ta date de naissance : $suggestion"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.palette.success,
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    if (question.choices.isEmpty()) {
        LabelledField(label = "Ta réponse", value = chosen, onValueChange = onChoose)
    } else {
        question.choices.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = chosen == choice, onClick = { onChoose(choice) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen == choice, onClick = { onChoose(choice) })
                Spacer(Modifier.width(4.dp))
                Text(choice, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Cette réponse sera retenue jusqu'à la rentrée de septembre, pour ne plus avoir à la " +
            "redonner. Rien n'est tenté en boucle : une réponse refusée rend la main.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Diagnostic réseau, replié par défaut.
 *
 * Utile pour rapporter une panne quand l'API change ; expurgé des mots de passe, jetons et
 * cookies avant d'arriver ici.
 */
@Composable
private fun DiagnosticPanel(
    diagnostic: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Spacer(Modifier.height(10.dp))
    Text(
        if (expanded) "Masquer le détail technique" else "Voir le détail technique",
        modifier = Modifier
            .clickable { onExpandedChange(!expanded) }
            .padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (expanded) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppTheme.palette.warningContainer,
            contentColor = AppTheme.palette.warning,
        ) {
            Column(Modifier.padding(14.dp)) {
                SelectionContainer {
                    Text(diagnostic, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Copier",
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("ecole2recte — diagnostic", diagnostic),
                            )
                        }
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    number: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = when {
                !password || revealed -> VisualTransformation.None
                else -> PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    number -> KeyboardType.Number
                    password -> KeyboardType.Password
                    else -> KeyboardType.Text
                },
            ),
            trailingIcon = if (!password) {
                null
            } else {
                {
                    Text(
                        if (revealed) "Masquer" else "Voir",
                        modifier = Modifier
                            .clickable { revealed = !revealed }
                            .padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}
