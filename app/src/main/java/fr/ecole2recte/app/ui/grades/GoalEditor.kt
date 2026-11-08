package fr.ecole2recte.app.ui.grades

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.ecole2recte.app.domain.FrenchFormat
import fr.ecole2recte.app.domain.GradeSimulation
import fr.ecole2recte.app.widget.GoalStore
import fr.ecole2recte.app.widget.WidgetRefresh

/** Champ partagé avec la configuration Objectifs ; une saisie vide supprime l'objectif local. */
@Composable
fun GoalEditor(profile: String, subjectId: String, subjectName: String) {
    val context = LocalContext.current
    val store = remember { GoalStore.at(context) }
    var text by rememberSaveable(profile, subjectId) { mutableStateOf(store.values(profile)[subjectId]?.let { FrenchFormat.decimal(it, 2) }.orEmpty()) }
    var feedback by remember(profile, subjectId) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(text, { text = it; feedback = null }, label = { Text("Objectif · $subjectName") },
            supportingText = { Text("Sur 20 · vide pour supprimer") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        TextButton(onClick = {
            val value = if (text.isBlank()) null else GradeSimulation.parse(text)
            if (text.isNotBlank() && value == null) feedback = "Saisis un nombre entre 0 exclu et 20."
            else if (store.set(profile, subjectId, value)) {
                feedback = if (value == null) "Objectif supprimé." else "Objectif enregistré sur le téléphone."
                WidgetRefresh.requestAll(context)
            } else feedback = if (profile.isBlank()) "Ouvre un profil pour définir un objectif." else "L'objectif doit être supérieur à 0 et inférieur ou égal à 20."
        }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer l'objectif") }
        feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
