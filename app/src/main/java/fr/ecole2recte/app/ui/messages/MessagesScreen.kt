package fr.ecole2recte.app.ui.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ecole2recte.app.ui.common.MessagesViewModel
import fr.ecole2recte.app.ui.common.PageScaffold
import fr.ecole2recte.app.ui.common.StateHost
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.theme.CardWhite
import fr.ecole2recte.app.ui.theme.Ink
import fr.ecole2recte.app.ui.theme.Muted

@Composable
fun MessagesRoute() {
    val repository = rememberSchoolRepository()
    val viewModel = appViewModel { MessagesViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StateHost(state, onRetry = viewModel::refresh) { messages ->
        PageScaffold("Messages", "${messages.count { it.unread }} messages non lus") {
            messages.forEach { message ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(CardWhite),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Text(message.sender, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(message.preview, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
