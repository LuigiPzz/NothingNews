package com.nothing.news.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.KeyboardArrowRight
import com.nothing.news.ui.news.NewsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: NewsViewModel,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSync: () -> Unit
) {
    val themePreference by viewModel.themePreference.collectAsState()
    
    var showResetConfirm by remember { mutableStateOf(false) }
    var showBrowserSheet by remember { mutableStateOf(false) }
    val browserSheetState = rememberModalBottomSheetState()

    if (showBrowserSheet) {
        val installedBrowsers = remember { viewModel.getInstalledBrowsers() }
        val browserPreference by viewModel.browserPreference.collectAsState()
        val selectedPackage by viewModel.selectedBrowserPackage.collectAsState()

        ModalBottomSheet(
            onDismissRequest = { showBrowserSheet = false },
            sheetState = browserSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Seleziona Browser",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        // In-app Option
                        ListItem(
                            headlineContent = { Text("In-app (Custom Tabs)") },
                            modifier = Modifier.clickable { 
                                viewModel.setBrowser("Interno")
                                viewModel.setBrowserPackage(null)
                                showBrowserSheet = false
                            },
                            trailingContent = {
                                RadioButton(selected = browserPreference == "Interno", onClick = null)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        
                        // System Default Option
                        ListItem(
                            headlineContent = { Text("Browser di sistema") },
                            modifier = Modifier.clickable { 
                                viewModel.setBrowser("Esterno")
                                viewModel.setBrowserPackage(null)
                                showBrowserSheet = false
                            },
                            trailingContent = {
                                RadioButton(selected = browserPreference == "Esterno" && selectedPackage == null, onClick = null)
                            }
                        )

                        if (installedBrowsers.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            installedBrowsers.forEach { browser ->
                                ListItem(
                                    headlineContent = { Text(browser.name) },
                                    leadingContent = {
                                        androidx.compose.foundation.Image(
                                            painter = coil.compose.rememberAsyncImagePainter(browser.icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable { 
                                        viewModel.setBrowser("Esterno")
                                        viewModel.setBrowserPackage(browser.packageName)
                                        showBrowserSheet = false
                                    },
                                    trailingContent = {
                                        RadioButton(selected = browserPreference == "Esterno" && selectedPackage == browser.packageName, onClick = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Resetta Letti", fontWeight = FontWeight.Bold) },
            text = { Text("Sei sicuro di voler segnare tutte le notizie come non lette?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllReadStatus()
                        showResetConfirm = false
                    }
                ) {
                    Text("RESETTA", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
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
                        "Settings", 
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // SECTION: INTERFACCIA
            SettingsGroup(title = "INTERFACCIA") {
                // Theme Selection
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Light", "Dark", "System").forEach { theme ->
                            val isSelected = themePreference == theme
                            Surface(
                                modifier = Modifier.weight(1f).height(36.dp).clickable { viewModel.setTheme(theme) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(theme, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                
                // Browser Selection
                val browserPreference by viewModel.browserPreference.collectAsState()
                val selectedPackage by viewModel.selectedBrowserPackage.collectAsState()
                val installedBrowsers = remember { viewModel.getInstalledBrowsers() }
                val currentBrowserName = when {
                    browserPreference == "Interno" -> "In-app (Custom Tabs)"
                    selectedPackage == null -> "Browser di sistema"
                    else -> installedBrowsers.find { it.packageName == selectedPackage }?.name ?: "Browser specifico"
                }
                
                ListItem(
                    headlineContent = { Text("Apertura articoli", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(currentBrowserName) },
                    modifier = Modifier.clickable { showBrowserSheet = true },
                    trailingContent = { Icon(Icons.Default.Language, null, modifier = Modifier.scale(0.8f), tint = MaterialTheme.colorScheme.outline) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: AUTOMAZIONE
            val autoMarkReadDays by viewModel.autoMarkReadDays.collectAsState()
            val backgroundUpdateFrequency by viewModel.backgroundUpdateFrequency.collectAsState()

            SettingsGroup(title = "AUTOMAZIONE") {
                // Background Refresh
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Aggiornamento background", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Scarica nuove news ogni:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Mai", 1 to "1h", 3 to "3h", 6 to "6h", 12 to "12h", 24 to "24h").forEach { (h, label) ->
                            val isSelected = backgroundUpdateFrequency == h
                            SelectableChip(label, isSelected) { viewModel.setBackgroundUpdateFrequency(h) }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Auto Mark Read
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Auto-lettura vecchie news", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Marca come lette dopo:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Mai", 1 to "1g", 3 to "3g", 7 to "7g", 14 to "14g", 30 to "30g").forEach { (d, label) ->
                            val isSelected = autoMarkReadDays == d
                            SelectableChip(label, isSelected) { viewModel.setAutoMarkReadDays(d) }
                        }
                    }
                }
            }

            // SECTION: CLOUD E DATI
            val autoBackup by viewModel.autoBackup.collectAsState()
            
            SettingsGroup(title = "CLOUD E DATI") {
                ListItem(
                    headlineContent = { Text("Auto Backup", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Sincronizza dopo ogni aggiornamento") },
                    trailingContent = {
                        Switch(
                            checked = autoBackup,
                            onCheckedChange = { viewModel.setAutoBackup(it) },
                            modifier = Modifier.scale(0.8f)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                ListItem(
                    headlineContent = { Text("Gestione Sincronizzazione", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Backup manuale via Google Drive") },
                    trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline) },
                    modifier = Modifier.clickable { onNavigateToSync() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: INTELLIGENZA ARTIFICIALE
            val geminiApiKey by viewModel.geminiApiKey.collectAsState()
            var tempApiKey by remember(geminiApiKey) { mutableStateOf(geminiApiKey ?: "") }

            SettingsGroup(title = "INTELLIGENZA ARTIFICIALE") {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Google Gemini API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Necessaria per i riassunti IA. Ottienila gratuitamente su Google AI Studio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        placeholder = { Text("Inserisci la chiave API...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            if (tempApiKey != (geminiApiKey ?: "")) {
                                TextButton(onClick = { viewModel.setGeminiApiKey(tempApiKey) }) {
                                    Text("SALVA")
                                }
                            }
                        },
                        visualTransformation = if (tempApiKey.isEmpty()) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            }

            // SECTION: SISTEMA
            SettingsGroup(title = "SISTEMA") {
                ListItem(
                    headlineContent = { Text("Permessi", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Gestisci accessi e notifiche") },
                    trailingContent = { Icon(Icons.Default.Info, null, modifier = Modifier.scale(0.8f), tint = MaterialTheme.colorScheme.outline) },
                    modifier = Modifier.clickable { onNavigateToPermissions() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: AZIONI
            SettingsGroup(title = "AZIONI") {
                ListItem(
                    headlineContent = { Text("Resetta Letti", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Marca tutto come non letto") },
                    trailingContent = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showResetConfirm = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            content = content
        )
    }
}

@Composable
fun SelectableChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 44.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
