package com.nothing.news.ui.feeds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nothing.news.ui.news.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFeedsScreen(
    viewModel: NewsViewModel,
    onBack: () -> Unit
) {
    val feeds by viewModel.feedSources.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var feedToDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // Title, Url

    if (showDeleteConfirmDialog && feedToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                feedToDelete = null
            },
            title = { Text("Elimina Feed", fontWeight = FontWeight.Bold) },
            text = { Text("Sei sicuro di voler eliminare \"${feedToDelete?.first}\"? Non riceverai più notizie da questa fonte.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedToDelete?.second?.let { viewModel.removeFeed(it) }
                        showDeleteConfirmDialog = false
                        feedToDelete = null
                    }
                ) {
                    Text("ELIMINA", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirmDialog = false
                        feedToDelete = null
                    }
                ) {
                    Text("ANNULLA")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "My Feeds", 
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "SORGENTI ATTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            
            if (feeds.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nessun feed aggiunto",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(feeds.size) { index ->
                            val feed = feeds[index]
                            FeedItem(
                                title = feed.title,
                                url = feed.url,
                                onDelete = { 
                                    feedToDelete = feed.title to feed.url
                                    showDeleteConfirmDialog = true
                                }
                            )
                            if (index < feeds.size - 1) {
                                Divider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedItem(
    title: String,
    url: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Elimina",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}
