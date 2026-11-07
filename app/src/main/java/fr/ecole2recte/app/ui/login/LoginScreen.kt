package fr.ecole2recte.app.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.data.BirthDate
import fr.ecole2recte.app.data.AdviceAction
import fr.ecole2recte.app.data.FailureAdvice
import fr.ecole2recte.app.data.FailureKind
import fr.ecole2recte.app.session.LoginUiState
import fr.ecole2recte.app.ui.common.pressScale
import fr.ecole2recte.app.session.SecurityChallenge
import fr.ecole2recte.app.session.findBirthAnswer
import fr.ecole2recte.app.ui.theme.AppTheme

/**
 * Connexion en deux temps : identifiant + mot de passe, puis — seulement si ÉcoleDirecte le
 * demande — la question de sécurité. Les deux étapes partagent les mêmes champs pour ne jamais
 * faire ressaisir l'identifiant.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onLogin: (identifier: String, password: String) -> Unit,
    onAnswerSecurity: (answer: String, birthDate: BirthDate?) -> Unit,
    onCancelSecurity: () -> Unit,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    var identifier by remember { mutableStateOf(state.identifier) }
    var password by remember { mutableStateOf(state.password) }
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background), contentAlignment = Alignment.Center) {
        Card(
            Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(AppTheme.colors.surface),
        ) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ecole2recte", color = AppTheme.colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val challenge = state.challenge
                if (challenge == null) {
                    CredentialsStep(
                        state = state,
                        identifier = identifier,
                        onIdentifier = { identifier = it },
                        password = password,
                        onPassword = { password = it },
                        onSubmit = { onLogin(identifier, password) },
                    )
                    if (cancelLabel != null && onCancel != null) {
                        Text(
                            cancelLabel,
                            modifier = Modifier.clickable(enabled = !state.loading, onClick = onCancel).padding(vertical = 8.dp),
                            color = AppTheme.colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    SecurityStep(
                        state = state,
                        challenge = challenge,
                        onAnswer = onAnswerSecurity,
                        onCancel = onCancelSecurity,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialsStep(
    state: LoginUiState,
    identifier: String,
    onIdentifier: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val canSubmit = identifier.isNotBlank() && password.isNotBlank() && !state.loading
    Text("Connexion", color = AppTheme.colors.ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    Field(
        label = "Identifiant",
        value = identifier,
        onValueChange = onIdentifier,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
    )
    var visible by remember { mutableStateOf(false) }
    Field(
        label = "Mot de passe",
        value = password,
        onValueChange = onPassword,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
        transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                )
            }
        },
    )
    ErrorBlock(state)
    Text("Identifiants chiffrés sur cet appareil.", color = AppTheme.colors.muted, fontSize = 12.sp)
    PrimaryButton(
        label = "Se connecter  →",
        loading = state.loading,
        enabled = canSubmit,
        onClick = onSubmit,
    )
}

@Composable
private fun SecurityStep(
    state: LoginUiState,
    challenge: SecurityChallenge,
    onAnswer: (String, BirthDate?) -> Unit,
    onCancel: () -> Unit,
) {
    var birthDay by remember { mutableStateOf("") }
    var birthMonth by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var answer by remember(challenge) { mutableStateOf("") }
    val automatic = remember(challenge, birthDay, birthMonth, birthYear) {
        findBirthAnswer(challenge.question, challenge.choices, birthDay, birthMonth, birthYear)
    }
    LaunchedEffect(automatic) {
        if (automatic != null) answer = automatic
    }
    Text("Vérification", color = AppTheme.colors.ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    Text(challenge.question, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    if (challenge.choices.isNotEmpty()) {
        Text("Date de naissance (pour la réponse) :", color = AppTheme.colors.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Jour", birthDay, { birthDay = it }, Modifier.weight(1f), number = true)
            Field("Mois", birthMonth, { birthMonth = it }, Modifier.weight(1f), number = true)
            Field("Année", birthYear, { birthYear = it }, Modifier.weight(1.4f), number = true)
        }
        if (automatic != null) {
            Text(
                "Réponse trouvée",
                color = AppTheme.colors.positive,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        challenge.choices.forEach { choice ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { answer = choice }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = answer == choice, onClick = { answer = choice })
                Text(choice, color = AppTheme.colors.ink, fontSize = 14.sp)
            }
        }
    } else {
        Field("Ta réponse", answer, { answer = it })
    }
    ErrorBlock(state)
    PrimaryButton(
        label = "Valider  →",
        loading = state.loading,
        enabled = answer.isNotBlank() && !state.loading,
        onClick = {
            val birth = BirthDate(birthDay, birthMonth, birthYear)
            onAnswer(answer, birth.takeIf { it.isComplete && automatic == answer })
        },
    )
    Text(
        "Retour",
        modifier = Modifier.clickable(enabled = !state.loading, onClick = onCancel).padding(vertical = 8.dp),
        color = AppTheme.colors.accent,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

@Composable
private fun ErrorBlock(state: LoginUiState) {
    val error = state.error ?: return
    val advice = FailureAdvice.forLogin(error.kind, error.message)
    val color = if (error.kind == FailureKind.Network || error.kind == FailureKind.Service) AppTheme.colors.muted else AppTheme.colors.error
    Text(advice.title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Text(advice.detail, color = color, fontSize = 13.sp)
    state.diagnostic?.let { TechnicalDetails(it) }
}

/** Détails réseau pour le dépannage, repliés par défaut (sans mot de passe ni jeton). */
@Composable
private fun TechnicalDetails(diagnostic: String) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Text(
        if (open) "Masquer les détails" else "Détails",
        modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp),
        color = AppTheme.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
    if (open) {
        Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.warningSurface)) {
            Column(Modifier.padding(12.dp)) {
                Text("Détail réseau", color = AppTheme.colors.warningTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Text(diagnostic, color = AppTheme.colors.warningText, fontSize = 12.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ecole2recte - diagnostic réseau", diagnostic))
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Copier", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.fillMaxWidth().pressScale(source).clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled || loading) AppTheme.colors.accentFill else AppTheme.colors.accentFill.copy(alpha = .4f),
    ) {
        Row(
            Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.padding(end = 10.dp).size(16.dp), color = Color.White, strokeWidth = 2.dp)
            }
            Text(
                if (loading) "Connexion…" else label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    fieldModifier: Modifier = Modifier,
    number: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    transformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, color = AppTheme.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            trailingIcon = trailing,
        )
    }
}
