package fr.ecole2recte.app

import android.os.Bundle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.data.EcoleDirecteClient
import fr.ecole2recte.app.data.LoginResult
import fr.ecole2recte.app.data.SavedLogin
import fr.ecole2recte.app.data.SecureCredentialStore
import fr.ecole2recte.app.data.TrustedAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF18202B)
private val Muted = Color(0xFF7F8998)
private val Background = Color(0xFFF5F7FB)
private val Indigo = Color(0xFF5C62E8)
private val SubjectColors = listOf(Color(0xFF7779E8), Color(0xFF63A6E9), Color(0xFFEFA36A), Color(0xFF5CBD92))

data class Subject(val name: String, val teacher: String, val room: String, val colorIndex: Int)
data class Homework(val title: String, val subject: String, val due: String, val colorIndex: Int)

private val subjects = listOf(
    Subject("Philosophie", "Mme Laurent", "B204", 2),
    Subject("Mathématiques", "M. Petit", "C011", 0),
    Subject("Anglais", "Mme Robert", "A112", 1),
    Subject("Histoire-Géo", "M. Dubois", "A112", 3),
)

private val homework = listOf(
    Homework("Dissertation — La liberté", "Philosophie", "Lundi 7 septembre", 2),
    Homework("Exercices 12 à 18", "Mathématiques", "Mardi 8 septembre", 0),
    Homework("Reading comprehension", "Anglais", "Jeudi 10 septembre", 1),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Ecole2recteApp() }
    }
}

@Composable
private fun Ecole2recteApp() {
    val context = LocalContext.current
    val store = remember { SecureCredentialStore(context) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var authChecking by remember { mutableStateOf(store.read() != null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var securityQuestion by remember { mutableStateOf<String?>(null) }
    var pendingClient by remember { mutableStateOf<EcoleDirecteClient?>(null) }
    LaunchedEffect(Unit) {
        val saved = store.read()
        if (saved != null) {
            val result = withContext(Dispatchers.IO) {
                EcoleDirecteClient().login(saved.identifier, saved.password, saved.trustedAnswer)
            }
            if (result is LoginResult.Success) isAuthenticated = true
            else loginError = (result as? LoginResult.Failure)?.message
        }
        authChecking = false
    }
    if (authChecking) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Text("ecole2recte", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        return
    }
    if (!isAuthenticated) {
        LoginScreen(
            initialLogin = store.read(),
            error = loginError,
            securityQuestion = securityQuestion,
            onLogin = { identifier, password, trustedAnswer, securityAnswer ->
                loginError = null
                val client = pendingClient ?: EcoleDirecteClient()
                val result = withContext(Dispatchers.IO) {
                    if (securityAnswer != null && pendingClient != null) {
                        client.completeSecurityQuestion(securityAnswer, identifier, password)
                    } else {
                        client.login(identifier, password, trustedAnswer)
                    }
                }
                when (result) {
                    is LoginResult.Success -> {
                        store.save(SavedLogin(identifier, password, client.approvedAnswer ?: trustedAnswer))
                        isAuthenticated = true
                        securityQuestion = null
                        pendingClient = null
                    }
                    is LoginResult.SecurityQuestionRequired -> {
                        securityQuestion = result.question ?: "Réponds à la question demandée par ÉcoleDirecte."
                        pendingClient = client
                    }
                    is LoginResult.Failure -> loginError = result.message
                }
            },
        )
        return
    }
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var showColorEditor by remember { mutableStateOf(false) }
    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                        )
                    }
                }
            },
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding), color = Background) {
                when (selectedTab) {
                    AppTab.Home -> HomeScreen(onOpenColors = { showColorEditor = true })
                    AppTab.Schedule -> ScheduleScreen()
                    AppTab.Homework -> HomeworkScreen()
                    AppTab.Grades -> GradesScreen()
                    AppTab.Messages -> MessagesScreen()
                }
            }
        }
        if (showColorEditor) {
            ColorEditor(onDismiss = { showColorEditor = false })
        }
    }
}

@Composable
private fun LoginScreen(
    initialLogin: SavedLogin?,
    error: String?,
    securityQuestion: String?,
    onLogin: suspend (String, String, TrustedAnswer?, String?) -> Unit,
) {
    var identifier by remember { mutableStateOf(initialLogin?.identifier.orEmpty()) }
    var password by remember { mutableStateOf(initialLogin?.password.orEmpty()) }
    var birthDay by remember { mutableStateOf("") }
    var birthMonth by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (securityQuestion != null) {
                    Text("Question de sécurité", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(securityQuestion, color = Ink, fontSize = 13.sp)
                    LoginField("Ta réponse", securityAnswer, { securityAnswer = it })
                }
                if (error != null) Text(error, color = Color(0xFFB74D58), fontSize = 11.sp)
                Text("Les réponses candidates sont chiffrées sur cet appareil.", color = Muted, fontSize = 10.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !loading) {
                        loading = true
                        val trusted = if (birthDay.isNotBlank() && birthMonth.isNotBlank() && birthYear.isNotBlank()) {
                            TrustedAnswer(birthDay, "$birthMonth-$birthYear")
                        } else null
                        scope.launch {
                            onLogin(identifier, password, trusted, securityAnswer.takeIf { securityQuestion != null && it.isNotBlank() })
                            loading = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Indigo,
                ) {
                    Text(if (loading) "Connexion…" else "Se connecter  →", color = Color.White, modifier = Modifier.padding(15.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
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
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        )
    }
}

private enum class AppTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Home("Accueil", Icons.Outlined.Home),
    Schedule("Planning", Icons.Outlined.CalendarMonth),
    Homework("Devoirs", Icons.Outlined.Assignment),
    Grades("Notes", Icons.Outlined.MenuBook),
    Messages("Messages", Icons.Outlined.ChatBubbleOutline),
}

@Composable
private fun PageHeader(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("ecole2recte", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(7.dp))
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        action?.invoke()
    }
}

@Composable
private fun HomeScreen(onOpenColors: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            PageHeader("Bonjour Martin", "Samedi 5 septembre 2026")
            NextClassCard()
        }
        item { SectionTitle("Aujourd'hui", "Voir le planning", onClick = {}) }
        item { ScheduleSummary() }
        item { SectionTitle("À ne pas oublier", "Tout voir", onClick = {}) }
        item { ReminderCard() }
        item { SectionTitle("Tes dernières notes", "Voir les notes", onClick = {}) }
        item { GradesSummary() }
        item {
            Text(
                "Couleurs par matière",
                modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp).clickable(onClick = onOpenColors),
                color = Indigo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NextClassCard() {
    val subject = subjects.first()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Indigo),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("PROCHAIN COURS", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("10:15  ·  dans 24 min", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Text(subject.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Salle ${subject.room}  ·  ${subject.teacher}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                }
                SubjectChip(subject)
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .25f), RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(.64f).height(4.dp).background(Color.White, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(action, modifier = Modifier.clickable(onClick = onClick), color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScheduleSummary() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp)) {
            subjects.take(3).forEachIndexed { index, subject ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf("08:00", "10:15", "13:30")[index], color = Muted, fontSize = 11.sp, modifier = Modifier.size(45.dp, 20.dp))
                    Box(Modifier.size(8.dp).background(SubjectColors[subject.colorIndex], RoundedCornerShape(4.dp)))
                    Text(subject.name, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                    Text(subject.room, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderCard() {
    val task = homework.first()
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(Color(0xFF202935)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(SubjectColors[task.colorIndex].copy(alpha = .2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("!", color = SubjectColors[task.colorIndex], fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 13.dp)) {
                Text("À NE PAS OUBLIER", color = Color.White.copy(alpha = .6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(task.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("À rendre ${task.due}", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GradesSummary() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("15,8", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(" / 20\nMoyenne générale", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("↗ +1,2", color = Color(0xFF2AA270), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SubjectChip(subject: Subject) {
    Box(Modifier.size(56.dp).background(Color.White.copy(alpha = .85f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
        Text(subject.name.take(2), color = SubjectColors[subject.colorIndex], fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun ScheduleScreen() {
    PageScaffold("Emploi du temps", "Semaine du 5 septembre") {
        subjects.forEach { subject ->
            ScheduleRow(subject)
        }
    }
}

@Composable
private fun ScheduleRow(subject: Subject) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp, 42.dp).background(SubjectColors[subject.colorIndex], RoundedCornerShape(4.dp)))
            Column(Modifier.padding(start = 13.dp)) {
                Text(subject.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Lundi · 10:15 — 12:00  ·  Salle ${subject.room}", color = Muted, fontSize = 11.sp)
                Text(subject.teacher, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HomeworkScreen() {
    PageScaffold("Devoirs", "3 devoirs à venir") {
        homework.forEach { task ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(task.subject, color = SubjectColors[task.colorIndex], fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(task.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Pour ${task.due}", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GradesScreen() {
    PageScaffold("Notes", "Moyenne générale · 15,8 / 20") {
        subjects.forEachIndexed { index, subject ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(SubjectColors[subject.colorIndex], RoundedCornerShape(5.dp)))
                    Text(subject.name, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Text(listOf("14,0", "17,5", "16,0", "15,5")[index], color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MessagesScreen() {
    PageScaffold("Messages", "2 messages non lus") {
        listOf("Vie scolaire" to "Réunion parents-professeurs...", "M. Petit · Maths" to "Correction du devoir maison").forEach { (sender, preview) ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(17.dp)) {
                    Text(sender, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(preview, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PageScaffold(title: String, subtitle: String, content: @Composable () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageHeader(title, subtitle) }
        item { content() }
    }
}

@Composable
private fun ColorEditor(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(Color.White)) {
            Column(Modifier.padding(24.dp)) {
                Text("Couleurs par matière", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Une couleur reste identique dans tout l'espace.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                subjects.forEach { subject ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(SubjectColors[subject.colorIndex], RoundedCornerShape(6.dp)))
                        Text(subject.name, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                        Spacer(Modifier.weight(1f))
                        Text("Modifier", color = Indigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Fermer", color = Indigo, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End).padding(top = 15.dp).clickable(onClick = onDismiss))
            }
        }
    }
}
