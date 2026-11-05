package fr.ecole2recte.app.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.session.LoginUiState
import fr.ecole2recte.app.ui.theme.Background
import fr.ecole2recte.app.ui.theme.CardWhite
import fr.ecole2recte.app.ui.theme.DiagnosticSurface
import fr.ecole2recte.app.ui.theme.DiagnosticText
import fr.ecole2recte.app.ui.theme.DiagnosticTitle
import fr.ecole2recte.app.ui.theme.ErrorRed
import fr.ecole2recte.app.ui.theme.Indigo
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.Muted
import fr.ecole2recte.app.ui.theme.PositiveDark

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLogin: (identifier: String, password: String, securityAnswer: String?) -> Unit,
) {
    var identifier by remember { mutableStateOf(state.initialLogin?.identifier.orEmpty()) }
    var password by remember { mutableStateOf(state.initialLogin?.password.orEmpty()) }
    var birthDay by remember { mutableStateOf("") }
    var birthMonth by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    val automaticSecurityAnswer = remember(
        state.securityQuestion,
        state.securityChoices,
        birthDay,
        birthMonth,
        birthYear,
    ) {
        findBirthAnswer(
            question = state.securityQuestion,
            choices = state.securityChoices,
            day = birthDay,
            month = birthMonth,
            year = birthYear,
        )
    }
    LaunchedEffect(state.securityQuestion, state.securityChoices, automaticSecurityAnswer) {
        securityAnswer = automaticSecurityAnswer.orEmpty()
    }
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Card(
            Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(CardWhite),
        ) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ecole2recte", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Connexion", color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Text("Une connexion simple, puis une reconnexion silencieuse.", color = Muted, fontSize = 12.sp)
                LoginField("Identifiant", identifier, { identifier = it })
                LoginField("Mot de passe", password, { password = it }, password = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoginField("Jour", birthDay, { birthDay = it }, modifier = Modifier.weight(1f), number = true)
                    LoginField("Mois", birthMonth, { birthMonth = it }, modifier = Modifier.weight(1f), number = true)
                    LoginField("Année", birthYear, { birthYear = it }, modifier = Modifier.weight(1.4f), number = true)
                }
                if (state.securityQuestion != null) {
                    Text("Question de sécurité", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(state.securityQuestion, color = Ink, fontSize = 13.sp)
                    if (automaticSecurityAnswer != null) {
                        Text(
                            "Réponse trouvée depuis ta date de naissance",
                            color = PositiveDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (state.securityChoices.isNotEmpty()) {
                        state.securityChoices.forEach { choice ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { securityAnswer = choice }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = securityAnswer == choice, onClick = { securityAnswer = choice })
                                Text(choice, color = Ink, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LoginField("Ta réponse", securityAnswer, { securityAnswer = it })
                    }
                }
                if (state.error != null) Text(state.error, color = ErrorRed, fontSize = 11.sp)
                if (state.diagnostic != null) DiagnosticPanel(state.diagnostic)
                Text("Les réponses candidates sont chiffrées sur cet appareil.", color = Muted, fontSize = 10.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !state.loading) {
                        onLogin(
                            identifier,
                            password,
                            securityAnswer.takeIf { state.securityQuestion != null && it.isNotBlank() },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Indigo,
                ) {
                    Text(
                        if (state.loading) "Connexion…" else "Se connecter  →",
                        color = Color.White,
                        modifier = Modifier.padding(15.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticPanel(diagnostic: String) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = DiagnosticSurface)) {
        Column(Modifier.padding(12.dp)) {
            Text("Détail réseau (pour le debug)", color = DiagnosticTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(diagnostic, color = DiagnosticText, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 7.dp))
            }
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ecole2recte - diagnostic réseau", diagnostic))
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Copier le diagnostic", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    number: Boolean = false,
) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        )
    }
}
