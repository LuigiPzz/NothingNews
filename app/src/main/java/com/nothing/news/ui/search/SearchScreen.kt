package com.nothing.news.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import androidx.compose.ui.text.style.TextOverflow
import com.nothing.news.data.remote.FeedsearchResult
import com.nothing.news.ui.news.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToMyFeeds: () -> Unit = {},
    viewModel: NewsViewModel = hiltViewModel()
) {
    val urlInputState = remember { mutableStateOf("") }
    var urlInput by urlInputState
    val context = LocalContext.current
    val feeds by viewModel.feedSources.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()

    LaunchedEffect(urlInput) {
        if (urlInput.length >= 2 && !urlInput.startsWith("http")) {
            kotlinx.coroutines.delay(300)
            viewModel.searchFeeds(urlInput)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aggiungi Feed") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToMyFeeds) {
                        Text("I MIEI FEED (${feeds.size})")
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "Cerca o aggiungi feed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Parola chiave o URL") },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("es: multiplayerit o https://...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            if (urlInput.startsWith("http")) {
                                viewModel.addFeed(urlInput)
                                urlInput = ""
                            } else {
                                viewModel.searchFeeds(urlInput)
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Cerca")
                    }
                }
            }

            val suggestions = listOf(
                "ANSA.it", "Corriere", "Repubblica", "Il Post", "Sky TG24",
                "Wired.it", "HDblog.it", "Multiplayer.it", "Tom's Hardware",
                "Gazzetta", "Motor1", "HDmotori",
                "Focus.it", "Le Scienze", "GialloZafferano"
            )

            Text(
                text = "Suggerimenti",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { urlInput = suggestion },
                        label = { 
                            Text(
                                suggestion, 
                                style = MaterialTheme.typography.labelSmall
                            ) 
                        }
                    )
                }
            }

            searchError?.let { message ->
                val isSuccess = message.contains("successo", ignoreCase = true)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Risultati ricerca",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    searchResults.forEach { result ->
                        SearchResultItem(
                            result = result,
                            onAdd = { 
                                viewModel.addFeed(result.url)
                            }
                        )
                    }
                }
            }

            // Fallback Search Button
            if (urlInput.contains(".") && !urlInput.startsWith("http")) {
                val domain = urlInput.trim()
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val searchUrl = "https://www.google.com/search?q=site:$domain+rss+feed"
                        val intent = CustomTabsIntent.Builder().build()
                        intent.launchUrl(context, Uri.parse(searchUrl))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Non trovi il feed? Cerca su $domain", style = MaterialTheme.typography.labelMedium)
                }
            }

        }
    }
}

@Composable
fun SearchResultItem(
    result: FeedsearchResult,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    result.version?.let { version ->
                        val isAtom = version.contains("atom", ignoreCase = true)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (isAtom) Color(0xFFE3F2FD) else Color(0xFFF5F5F5),
                            contentColor = if (isAtom) Color(0xFF1976D2) else Color(0xFF757575),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = if (isAtom) "ATOM" else "RSS",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(result.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi", modifier = Modifier.size(20.dp))
            }
        }
    }
}

