package com.nothing.news.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nothing.news.ui.news.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    viewModel: NewsViewModel,
    onBack: () -> Unit
) {
    val backupStatus by viewModel.backupStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val localBackup by viewModel.localBackupData.collectAsState()
    val remoteBackup by viewModel.remoteBackupData.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val lastAutoBackup by viewModel.lastAutoBackupTimestamp.collectAsState()
    var pendingActionIsBackup by remember { mutableStateOf(true) }

    LaunchedEffect(currentUser) {
        viewModel.refreshBackupInfo()
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            viewModel.handleSignInResult(account, pendingActionIsBackup)
        } catch (e: Exception) {
            viewModel.handleSignInResult(null, pendingActionIsBackup)
        }
    }

    var showConfirmBackupDialog by remember { mutableStateOf(false) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }

    if (backupStatus != null && !isBackingUp) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBackupStatus() },
            title = { Text("Backup Cloud", fontWeight = FontWeight.Bold) },
            text = { Text(backupStatus ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBackupStatus() }) {
                    Text("OK")
                }
            }
        )
    }

    if (showConfirmBackupDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmBackupDialog = false },
            title = { Text("Conferma Backup", fontWeight = FontWeight.Bold) },
            text = { 
                Text("Stai per caricare più dati (feed o preferiti) di quelli attualmente salvati nel cloud. Vuoi procedere con la sovrascrittura?") 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showConfirmBackupDialog = false
                        viewModel.triggerBackupRestore(true)
                    }
                ) {
                    Text("PROCEDI", color = Color(0xFFFF2D00))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBackupDialog = false }) {
                    Text("ANNULLA")
                }
            }
        )
    }

    if (showConfirmRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            title = { Text("Conferma Ripristino", fontWeight = FontWeight.Bold) },
            text = { 
                Text("I dati nel cloud sono meno di quelli attualmente sul dispositivo. Ripristinando ora, perderai i dati locali extra. Vuoi procedere?") 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showConfirmRestoreDialog = false
                        viewModel.triggerBackupRestore(false)
                    }
                ) {
                    Text("RIPRISTINA", color = Color(0xFFFF2D00))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestoreDialog = false }) {
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
                        "Sincronizzazione Cloud", 
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isBackingUp) {
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
                .padding(horizontal = 16.dp)
        ) {
            if (isBackingUp) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "Confronta lo stato attuale del dispositivo con quello salvato nel cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
            )

            // Comparison Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LOCALE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("${localBackup?.feedUrls?.size ?: 0} Feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${localBackup?.favoriteLinks?.size ?: 0} Preferiti", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.CenterVertically),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        }

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("CLOUD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            if (currentUser == null) {
                                Text("Non collegato", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            } else if (remoteBackup == null) {
                                Text("Nessun backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            } else {
                                Text("${remoteBackup?.feedUrls?.size ?: 0} Feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${remoteBackup?.favoriteLinks?.size ?: 0} Preferiti", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    if (remoteBackup != null && currentUser != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        
                        Text(
                            text = "Ultimo sync: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(remoteBackup!!.timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            if (lastAutoBackup > 0L) {
                Text(
                    text = "Ultimo backup AUTOMATICO: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(lastAutoBackup))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    // Account Info / Logout
                    ListItem(
                        headlineContent = { 
                            Text(if (currentUser != null) "Disconnetti account" else "Connetti account") 
                        },
                        supportingContent = { 
                            Text(currentUser?.email ?: "Nessun account collegato") 
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = if (isBackingUp) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.clickable(enabled = !isBackingUp) { 
                            if (currentUser != null) {
                                viewModel.signOut()
                            } else {
                                googleLauncher.launch(viewModel.getSignInIntent())
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = if (isBackingUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Upload
                    ListItem(
                        headlineContent = { Text("Esegui Backup") },
                        supportingContent = { Text("Sovrascrivi il cloud con i dati attuali") },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.CloudUpload, 
                                contentDescription = null, 
                                tint = if (isBackingUp) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.clickable(enabled = !isBackingUp) { 
                            pendingActionIsBackup = true
                            if (currentUser != null) {
                                val localFeeds = localBackup?.feedUrls?.size ?: 0
                                val localFavs = localBackup?.favoriteLinks?.size ?: 0
                                val remoteFeeds = remoteBackup?.feedUrls?.size ?: 0
                                val remoteFavs = remoteBackup?.favoriteLinks?.size ?: 0
                                
                                if (localFeeds > remoteFeeds || localFavs > remoteFavs) {
                                    showConfirmBackupDialog = true
                                } else {
                                    viewModel.triggerBackupRestore(true)
                                }
                            } else {
                                googleLauncher.launch(viewModel.getSignInIntent())
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = if (isBackingUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Download
                    ListItem(
                        headlineContent = { Text("Ripristina") },
                        supportingContent = { Text("Scarica i dati dal cloud su questo dispositivo") },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.CloudDownload, 
                                contentDescription = null, 
                                tint = if (isBackingUp) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.clickable(enabled = !isBackingUp) { 
                            pendingActionIsBackup = false
                            if (currentUser != null) {
                                val localFeeds = localBackup?.feedUrls?.size ?: 0
                                val localFavs = localBackup?.favoriteLinks?.size ?: 0
                                val remoteFeeds = remoteBackup?.feedUrls?.size ?: 0
                                val remoteFavs = remoteBackup?.favoriteLinks?.size ?: 0
                                
                                if (remoteFeeds < localFeeds || remoteFavs < localFavs) {
                                    showConfirmRestoreDialog = true
                                } else {
                                    viewModel.triggerBackupRestore(false)
                                }
                            } else {
                                googleLauncher.launch(viewModel.getSignInIntent())
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = if (isBackingUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}
