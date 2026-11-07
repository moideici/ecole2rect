package fr.ecole2recte.app.ui.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import fr.ecole2recte.app.domain.GlobalSearch
import fr.ecole2recte.app.domain.SchoolRepository
import fr.ecole2recte.app.domain.SearchCorpus
import fr.ecole2recte.app.domain.SearchGroup
import fr.ecole2recte.app.domain.SearchKind
import fr.ecole2recte.app.ui.common.EmptyState
import fr.ecole2recte.app.ui.common.ListCard
import fr.ecole2recte.app.ui.common.LocalActiveProfile
import fr.ecole2recte.app.ui.common.appViewModel
import fr.ecole2recte.app.ui.common.rememberSchoolRepository
import fr.ecole2recte.app.ui.common.LocalPrivacy
import fr.ecole2recte.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Charge une fois ce qui est déjà enregistré sur le téléphone : la recherche n'envoie aucune requête. */
class SearchViewModel(repository: SchoolRepository) : ViewModel() {
    private val mutableCorpus = MutableStateFlow<SearchCorpus?>(null)
    val corpus: StateFlow<SearchCorpus?> = mutableCorpus.asStateFlow()

    init {
        viewModelScope.launch { mutableCorpus.value = runCatching { repository.searchCorpus() }.getOrDefault(SearchCorpus()) }
    }
}

/** La barre factice qui ouvre la recherche, à l'accueil et dans Plus. */
@Composable
fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListCard(modifier, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = AppTheme.colors.muted, modifier = Modifier.size(20.dp))
            Text(
                "Rechercher",
                color = AppTheme.colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun SearchRoute(onClose: () -> Unit, onOpen: (SearchKind) -> Unit) {
    val repository = rememberSchoolRepository()
    val profileKey = LocalActiveProfile.current?.key
    val viewModel = appViewModel(key = "search/$profileKey") { SearchViewModel(repository) }
    val corpus by viewModel.corpus.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val loaded = corpus
    val privacy = LocalPrivacy.current
    val groups = remember(loaded, query, privacy) { loaded?.let { GlobalSearch.search(it, query, LocalDate.now()) }.orEmpty().map(privacy::mask) }

    Column(Modifier.fillMaxSize()) {
        // La marge à droite laisse la place au bouton de compte, posé par-dessus l'écran.
        Row(Modifier.padding(start = 6.dp, end = 74.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Fermer la recherche", tint = AppTheme.colors.ink)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text("Devoirs, messages, notes, cours") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Effacer la recherche") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            when {
                loaded == null -> Unit
                query.isBlank() -> item {
                    EmptyState("Données enregistrées sur ce téléphone")
                }
                groups.isEmpty() -> item { EmptyState("Aucun résultat") }
                else -> groups.forEach { group -> groupItems(group, onOpen) }
            }
        }
    }
}

private fun LazyListScope.groupItems(group: SearchGroup, onOpen: (SearchKind) -> Unit) {
    item(key = "title-${group.kind}") {
        Text(
            "${group.kind.label} · ${group.total}",
            color = AppTheme.colors.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    item(key = "hits-${group.kind}") {
        Column {
            group.hits.forEach { hit ->
                ListCard(onClick = { onOpen(group.kind) }) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(hit.title, color = AppTheme.colors.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (hit.subtitle.isNotBlank()) {
                            Text(hit.subtitle, color = AppTheme.colors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (group.total > group.hits.size) {
                Text(
                    "Et ${group.total - group.hits.size} autre(s) dans ${group.kind.label.lowercase()} : affine la recherche ou ouvre l'onglet.",
                    color = AppTheme.colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
        }
    }
}
