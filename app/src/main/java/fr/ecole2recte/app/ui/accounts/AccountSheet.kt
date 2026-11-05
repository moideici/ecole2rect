package fr.ecole2recte.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ecole2recte.app.session.AccountSummary
import fr.ecole2recte.app.session.ActiveProfile
import fr.ecole2recte.app.ui.theme.AppTheme

/** Actions possibles sur les comptes, fournies par la session. */
class AccountActions(
    val onSwitch: (identifier: String) -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (identifier: String) -> Unit,
    val onSelectStudent: (studentId: String) -> Unit,
)

@Composable
fun Avatar(initials: String, active: Boolean = true, size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(if (active) AppTheme.colors.accentFill else AppTheme.colors.accent.copy(alpha = .14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = if (active) Color.White else AppTheme.colors.accent,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.36f).sp,
        )
    }
}

/** Pastille d'initiales qui ouvre la liste des comptes. */
@Composable
fun AccountButton(profile: ActiveProfile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .semantics { contentDescription = "Comptes : ${profile?.displayName ?: "aucun"}" }
            .clickable(onClick = onClick),
    ) {
        Avatar(profile?.initials ?: "?")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    accounts: List<AccountSummary>,
    actions: AccountActions,
    onDismiss: () -> Unit,
) {
    var confirmRemoval by remember { mutableStateOf<AccountSummary?>(null) }
    val active = accounts.firstOrNull { it.isActive }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.surface,
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Comptes", color = AppTheme.colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onDismiss()
                        actions.onSwitch(account.identifier)
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(account.initials, active = account.isActive)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(account.displayName, color = AppTheme.colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(account.typeLabel, color = AppTheme.colors.muted, fontSize = 12.sp)
                    }
                    if (account.isActive) Icon(Icons.Outlined.Check, contentDescription = "Compte actif", tint = AppTheme.colors.accent)
                }
            }
            if (active != null && active.students.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Élève affiché", color = AppTheme.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                active.students.forEach { student ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onDismiss()
                            actions.onSelectStudent(student.id)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = student.id == active.activeStudentId, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 10.dp, bottom = 10.dp))
                        Column {
                            Text("${student.firstName} ${student.lastName}".trim(), color = AppTheme.colors.ink, fontSize = 14.sp)
                            if (student.className.isNotBlank()) Text(student.className, color = AppTheme.colors.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Outlined.PersonAdd, "Ajouter un compte", AppTheme.colors.accent) {
                onDismiss()
                actions.onAdd()
            }
            if (active != null) {
                ActionRow(Icons.AutoMirrored.Outlined.Logout, "Retirer ce compte de l'appareil", AppTheme.colors.error) {
                    confirmRemoval = active
                }
            }
        }
    }
    confirmRemoval?.let { account ->
        AlertDialog(
            onDismissRequest = { confirmRemoval = null },
            title = { Text("Retirer ce compte ?") },
            text = { Text("L'identifiant et le mot de passe de ${account.displayName} seront effacés de cet appareil.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoval = null
                    onDismiss()
                    actions.onRemove(account.identifier)
                }) { Text("Retirer", color = AppTheme.colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoval = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
