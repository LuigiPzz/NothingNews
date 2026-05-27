package com.nothing.news.ui.news

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.nothing.news.data.local.entity.NewsArticle
import com.nothing.news.util.NewsDateUtils
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = hiltViewModel(),
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val articles by viewModel.newsArticles.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val sharedUrlToProcess by viewModel.sharedUrlToProcess.collectAsState()
    val isSavingSharedUrl by viewModel.isSavingSharedUrl.collectAsState()
    
    if (sharedUrlToProcess != null) {
        AlertDialog(
            onDismissRequest = { if (!isSavingSharedUrl) viewModel.setSharedUrlToProcess(null) },
            title = { Text("Aggiungi ai preferiti") },
            text = {
                Column {
                    Text("Vuoi salvare questo link nei tuoi preferiti?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sharedUrlToProcess!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSavingSharedUrl) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveSharedUrl(sharedUrlToProcess!!) },
                    enabled = !isSavingSharedUrl
                ) {
                    Text("Aggiungi")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.setSharedUrlToProcess(null) },
                    enabled = !isSavingSharedUrl
                ) {
                    Text("Annulla")
                }
            }
        )
    }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    val coroutineScope = rememberCoroutineScope()
    
    var refreshCounter by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            refreshCounter++
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    val pullToRefreshState = androidx.compose.runtime.key(refreshCounter) { 
        rememberPullToRefreshState() 
    }
    var showSortDialog by remember { mutableStateOf(false) }
    var showRemindersSheet by remember { mutableStateOf(false) }
    
    val dissolvingLinks = remember { mutableStateMapOf<String, Boolean>() }
    val resolvingLinks = remember { mutableStateMapOf<String, Boolean>() }
    val sessionReadLinks = remember { mutableStateListOf<String>() }
    val sessionUnreadLinks = remember { mutableStateListOf<String>() }
    
    val currentFilter by viewModel.filterType.collectAsState()
    val currentSort by viewModel.sortOrder.collectAsState()
    val browserPreference by viewModel.browserPreference.collectAsState()
    val selectedBrowserPackage by viewModel.selectedBrowserPackage.collectAsState()
    val articleSummaries by viewModel.articleSummaries.collectAsState()
    val loadingSummaries by viewModel.loadingSummaries.collectAsState()
    val allArticles by viewModel.allNewsArticles.collectAsState()
    val playingArticleLink by viewModel.playingArticleLink.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
    // Safe initial refresh when screen starts
    LaunchedEffect(Unit) {
        viewModel.refreshNews()
    }
    
    val sortSheetState = rememberModalBottomSheetState()



    // Reminders Bottom Sheet
    if (showRemindersSheet) {
        val remindersJson by viewModel.reminders.collectAsState()
        val reminderType = object : com.google.gson.reflect.TypeToken<List<com.nothing.news.util.Reminder>>() {}.type
        val remindersList: List<com.nothing.news.util.Reminder> = try {
            com.google.gson.Gson().fromJson(remindersJson, reminderType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val remindersSheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showRemindersSheet = false },
            sheetState = remindersSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Promemoria Attivi",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                if (remindersList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nessun promemoria programmato",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column {
                            remindersList.sortedBy { it.timeInMillis }.forEachIndexed { index, reminder ->
                                val timeString = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(reminder.timeInMillis))
                                ListItem(
                                    headlineContent = { 
                                        Text(
                                            reminder.title, 
                                            maxLines = 1, 
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    supportingContent = { Text("Programmato per: $timeString") },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.removeReminder(reminder.link) }) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                                if (index < remindersList.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
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
    
    if (showSortDialog) {
        ModalBottomSheet(
            onDismissRequest = { showSortDialog = false },
            sheetState = sortSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Ordinamento",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )
                
                Text(
                    "ORDINE CRONOLOGICO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        listOf("Crescente", "Decrescente").forEachIndexed { index, sort ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.setSortOrder(sort)
                                        showSortDialog = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentSort == sort,
                                    onClick = { 
                                        viewModel.setSortOrder(sort)
                                        showSortDialog = false
                                    }
                                )
                                Text(
                                    text = if (sort == "Crescente") "Più vecchie prima" else "Più recenti prima", 
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            if (index == 0) {
                                HorizontalDivider(
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
    
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            sessionReadLinks.clear()
            sessionUnreadLinks.clear()
            dissolvingLinks.clear()
            resolvingLinks.clear()
            
            viewModel.refreshNews()
            
            // Wait for the ViewModel's state to become 'true' first
            androidx.compose.runtime.snapshotFlow { isRefreshing }.first { it }
            // Wait for it to return to 'false'
            androidx.compose.runtime.snapshotFlow { isRefreshing }.first { !it }
            
            pullToRefreshState.endRefresh()
        }
    }

    val baseArticles = articles
    val displayArticles = remember(baseArticles, dissolvingLinks.size, resolvingLinks.size, sessionReadLinks.size, sessionUnreadLinks.size) {
        val manualLinks = dissolvingLinks.keys + resolvingLinks.keys + sessionReadLinks + sessionUnreadLinks
        val combined = if (manualLinks.isEmpty()) baseArticles
        else {
            val baseArticleLinks = baseArticles.map { it.link }.toSet()
            val extraItems = allArticles.filter { it.link in manualLinks && it.link !in baseArticleLinks }
            (baseArticles + extraItems)
        }
        combined.distinctBy { it.link }.sortedByDescending { it.pubDateTimestamp }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nothing News",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val countText = baseArticles.size.toString()
                    Text(
                        text = " · $countText",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 16.sp),
                        color = Color(0xFFFF2D00)
                    )
                }
                var showMenu by remember { mutableStateOf(false) }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ricerca") },
                            onClick = {
                                showMenu = false
                                onNavigateToSearch()
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Ordinamento") },
                            onClick = {
                                showMenu = false
                                showSortDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Promemoria") },
                            onClick = {
                                showMenu = false
                                showRemindersSheet = true
                            },
                            leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Impostazioni") },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                    }
                }
            }

            // Filter chips row
            val filterOptions = listOf(
                "Tutti" to null,
                "Non Letti" to Icons.Default.MarkEmailUnread,
                "Letti" to Icons.Default.DoneAll,
                "Preferiti" to Icons.Default.Star
            )
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions.size) { idx ->
                    val (label, icon) = filterOptions[idx]
                    val isSelected = currentFilter == label
                    val chipColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            Color.Transparent,
                        animationSpec = tween(200),
                        label = "chipColor"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        animationSpec = tween(200),
                        label = "textColor"
                    )
                    Surface(
                        onClick = { viewModel.setFilterType(label) },
                        shape = CircleShape,
                        color = chipColor,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .clipToBounds() // THIS FIXES THE BUG: hides the resting state of the PullToRefreshContainer
            ) {

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(
                    items = displayArticles,
                    key = { it.link }
                ) { article ->
                    val isDissolving = dissolvingLinks.containsKey(article.link)
                    val isResolving = resolvingLinks.containsKey(article.link)
                    com.nothing.news.ui.components.PixelDissolveContainer(
                        isDissolving = isDissolving,
                        isResolving = isResolving,
                        fadeContent = false,
                        onAnimationEnd = {
                            if (isDissolving) {
                                if (currentFilter == "Preferiti") {
                                    viewModel.toggleFavorite(article)
                                } else {
                                    viewModel.updateReadStatus(article.link, true)
                                    if (!sessionReadLinks.contains(article.link)) {
                                        sessionReadLinks.add(article.link)
                                    }
                                    sessionUnreadLinks.remove(article.link)
                                }
                                dissolvingLinks.remove(article.link)
                            } else if (isResolving) {
                                viewModel.updateReadStatus(article.link, false)
                                resolvingLinks.remove(article.link)
                                sessionReadLinks.remove(article.link)
                                if (!sessionUnreadLinks.contains(article.link)) {
                                    sessionUnreadLinks.add(article.link)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                    ) {
                        NewsItem(
                            article = article,
                            isReadSession = (article.isRead || sessionReadLinks.contains(article.link) || isDissolving) && !isResolving,
                            summary = articleSummaries[article.link],
                            isLoadingSummary = loadingSummaries.contains(article.link),
                            isTtsPlaying = isTtsPlaying && playingArticleLink == article.link,
                            onPlayTts = { text -> viewModel.playSummaryTts(article.link, text) },
                            onStopTts = { viewModel.stopSummaryTts() },
                            onClick = {
                                if (browserPreference == "Interno") {
                                    val intent = CustomTabsIntent.Builder().build()
                                    try {
                                        intent.launchUrl(context, Uri.parse(article.link))
                                    } catch (e: Exception) {
                                        // Fallback if custom tabs fail
                                        val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(article.link))
                                        context.startActivity(fallbackIntent)
                                    }
                                } else {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(article.link))
                                    if (selectedBrowserPackage != null) {
                                        intent.setPackage(selectedBrowserPackage)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback if the specific browser is uninstalled or fails
                                        val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(article.link))
                                        context.startActivity(fallbackIntent)
                                    }
                                }
                                if (!article.isRead && !sessionReadLinks.contains(article.link)) {
                                    dissolvingLinks[article.link] = true
                                }
                            },
                            onToggleFavorite = {
                                if (currentFilter == "Preferiti") {
                                    dissolvingLinks[article.link] = true
                                } else {
                                    viewModel.toggleFavorite(article)
                                }
                            },
                            onToggleRead = {
                                val isCurrentlyRead = article.isRead || sessionReadLinks.contains(article.link)
                                if (isCurrentlyRead) {
                                    // Mark as unread with reverse animation
                                    resolvingLinks[article.link] = true
                                    sessionUnreadLinks.add(article.link)
                                } else {
                                    // Mark as read with animation
                                    dissolvingLinks[article.link] = true
                                    sessionUnreadLinks.remove(article.link)
                                }
                            },
                            onSummarize = {
                                viewModel.summarizeArticle(article)
                            },
                            onClearSummary = {
                                viewModel.clearSummary(article)
                            },
                            onSuggestCalendarEvent = { art ->
                                viewModel.suggestCalendarEvent(art.title, art.description ?: art.content, art.link)
                            }
                        )
                    }
                }
            }
            
            // Bottom Fading Edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
            
            // Scroll to Top Symbol Only
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Torna in cima",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onBackground // Adaptive Black/White
                    )
                }
            }
            
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NewsItem(
    article: NewsArticle,
    isReadSession: Boolean = false,
    summary: String?,
    isLoadingSummary: Boolean,
    isTtsPlaying: Boolean = false,
    onPlayTts: (String) -> Unit = {},
    onStopTts: () -> Unit = {},
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
    onSummarize: () -> Unit,
    onClearSummary: () -> Unit,
    onSuggestCalendarEvent: suspend (NewsArticle) -> com.nothing.news.data.repository.CalendarEventSuggestion?
) {
    // Typewriter effect state - only reset if the summary text actually changes
    var lastProcessedSummary by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var displayedSummary by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var showContextMenu by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var isAnalyzingForCalendar by remember { mutableStateOf(false) }
    var calendarSuggestion by remember { mutableStateOf<com.nothing.news.data.repository.CalendarEventSuggestion?>(null) }
    var calendarShowTitle by remember { mutableStateOf(false) }
    var calendarShowDate by remember { mutableStateOf(false) }
    var calendarShowTime by remember { mutableStateOf(false) }
    var calendarReadyToOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val saturation by animateFloatAsState(
        targetValue = if (isReadSession) 0f else 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "saturation"
    )
    
    val animatedContentColor by animateColorAsState(
        targetValue = if (isReadSession) Color.Gray else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 1000),
        label = "content_color"
    )
    
    val animatedSourceColor by animateColorAsState(
        targetValue = if (isReadSession) Color.Gray else Color(0xFFFF2D00),
        animationSpec = tween(durationMillis = 1000),
        label = "source_color"
    )
    
    val animatedDateColor by animateColorAsState(
        targetValue = if (isReadSession) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 1000),
        label = "date_color"
    )

    LaunchedEffect(summary) {
        if (summary != null && summary != lastProcessedSummary) {
            lastProcessedSummary = summary
            displayedSummary = ""
            summary.forEach { char ->
                displayedSummary += char
                delay(15) // Speed of typing
            }
        } else if (summary == null) {
            lastProcessedSummary = null
            displayedSummary = ""
        }
    }

    val transition = updateTransition(targetState = article.isFavorite, label = "favorite_transition")
    
    val starScale by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)
            } else {
                tween(durationMillis = 200)
            }
        },
        label = "star_scale"
    ) { favorite ->
        if (favorite) 1.4f else 0f
    }

    val starAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "star_alpha"
    ) { favorite ->
        if (favorite) 1f else 0f
    }

    var triggerExplosion by remember { mutableStateOf(false) }
    LaunchedEffect(article.isFavorite) {
        if (article.isFavorite) {
            triggerExplosion = true
        } else {
            triggerExplosion = false
        }
    }

    val currentOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
    val currentOnToggleRead by rememberUpdatedState(onToggleRead)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnToggleRead()
                    false // Don't dismiss, just snap back
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnToggleFavorite()
                    false // Don't dismiss, just snap back
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFFFFC107) // Amber for Favorite
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFF9E9E9E) // Grey for Read
                    else -> Color.Transparent
                }, label = "dismiss_bg"
            )
            
            // Calculate scale and alpha based on swipe progress
            val scale by animateFloatAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) 1.3f else 0.8f,
                label = "icon_scale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) 1f else 0.5f,
                label = "icon_alpha"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) 
                    Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                        if (article.isFavorite) Icons.Outlined.Star else Icons.Filled.Star
                    } else if (isReadSession) {
                        Icons.Default.Clear 
                    } else {
                        Icons.Default.Done
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                )
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { showContextMenu = true }
                    ),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                ) {
                    // Content: Title, and Image
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Source Row with AI Button integrated at the end
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = simplifySourceName(article.sourceName),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    color = animatedSourceColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // AI Summary Button - Discreetly at the end of source row
                                if (summary == null && !isLoadingSummary) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onSummarize() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "GeminiAnim")
                                        val scale by infiniteTransition.animateFloat(
                                            initialValue = 0.85f,
                                            targetValue = 1.15f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1500, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "Scale"
                                        )

                                        val geminiBrush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF4E82EE), // Deep Blue
                                                Color(0xFF67B7D1), // Teal
                                                Color(0xFF9B72CB), // Purple
                                                Color(0xFFA1E3F9)  // Ice Cyan highlight
                                            )
                                        )

                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Gemini",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    alpha = 0.99f
                                                }
                                                .drawWithCache {
                                                    onDrawWithContent {
                                                        drawContent()
                                                        if (saturation > 0.1f) {
                                                            drawRect(geminiBrush, blendMode = BlendMode.SrcAtop, alpha = saturation)
                                                        }
                                                    }
                                                },
                                            tint = if (isReadSession) animatedContentColor else Color.Unspecified
                                        )
                                    }
                                } else if (summary != null) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onClearSummary() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Chiudi riassunto",
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isReadSession) animatedContentColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else if (isLoadingSummary) {
                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 1.dp,
                                            color = animatedContentColor
                                        )
                                    }
                                }
                            }
                            
                            Text(
                                text = if (article.pubDateTimestamp > 0) {
                                    java.text.SimpleDateFormat("HH:mm • dd/MM/yy", java.util.Locale.ITALY).format(java.util.Date(article.pubDateTimestamp))
                                } else {
                                    article.pubDate ?: ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = animatedDateColor,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = animatedContentColor
                            )
                        }

                        if (!article.imageUrl.isNullOrBlank()) {
                            val imageUrl = article.imageUrl
                            val imageRequest = remember(imageUrl) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .build()
                            }
                            Box(
                                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
                                )
                                
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .offset(x = 10.dp, y = (-10).dp) // Center on corner
                                        .size(20.dp) // Matches the star size to prevent layout bounds expansion
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.graphicsLayer(
                                            scaleX = starScale,
                                            scaleY = starScale,
                                            alpha = starAlpha
                                        )
                                    ) {
                                        // White "border" star (slightly larger)
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        // Main Amber star
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    
                                    StarExplosion(
                                        isTriggered = triggerExplosion,
                                        modifier = Modifier.size(60.dp)
                                    )
                                }
                            }
                        } else {
                            // If there is no image, show the star icon on the right side of the row
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(end = 16.dp)
                                    .size(24.dp), // Matches the star size to prevent layout bounds expansion
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.graphicsLayer(
                                        scaleX = starScale,
                                        scaleY = starScale,
                                        alpha = starAlpha
                                    )
                                ) {
                                    // White "border" star (slightly larger)
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    // Main Amber star
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                StarExplosion(
                                    isTriggered = triggerExplosion,
                                    modifier = Modifier.size(70.dp)
                                )
                            }
                        }
                    }

                    // AI Summary Card - Animated appearance
                    AnimatedVisibility(
                        visible = displayedSummary.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Riassunto IA",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        
                                        // TTS Play/Stop Button
                                        androidx.compose.material3.IconButton(
                                            onClick = {
                                                if (isTtsPlaying) {
                                                    onStopTts()
                                                } else {
                                                    onPlayTts(displayedSummary)
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isTtsPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = if (isTtsPlaying) "Ferma lettura" else "Leggi riassunto",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = displayedSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp,
                                        color = if (isReadSession) Color.Gray else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                            DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                showContextMenu = false
                                // Reset state
                                calendarSuggestion = null
                                calendarShowTitle = false
                                calendarShowDate = false
                                calendarShowTime = false
                                calendarReadyToOpen = false
                                isAnalyzingForCalendar = true
                                showCalendarDialog = true
                                coroutineScope.launch {
                                    try {
                                        val suggestion = onSuggestCalendarEvent(article)
                                        calendarSuggestion = suggestion
                                        isAnalyzingForCalendar = false
                                        kotlinx.coroutines.delay(250)
                                        calendarShowTitle = true
                                        kotlinx.coroutines.delay(500)
                                        calendarShowDate = true
                                        kotlinx.coroutines.delay(500)
                                        calendarShowTime = true
                                        kotlinx.coroutines.delay(400)
                                        calendarReadyToOpen = true
                                    } catch (e: Exception) {
                                        isAnalyzingForCalendar = false
                                        showCalendarDialog = false
                                        e.printStackTrace()
                                        android.widget.Toast.makeText(context, "Errore durante l'analisi dell'articolo.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aggiungi al calendario",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    )

    if (showCalendarDialog) {
        val suggestion = calendarSuggestion
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            showCalendarDialog = false
            isAnalyzingForCalendar = false
        }) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        if (isAnalyzingForCalendar) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (isAnalyzingForCalendar) "Analisi in corso..." else "Evento suggerito dall'IA",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Title field
                    androidx.compose.animation.AnimatedVisibility(
                        visible = calendarShowTitle,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()
                    ) {
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = "TITOLO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = suggestion?.title ?: article.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Date field
                    androidx.compose.animation.AnimatedVisibility(
                        visible = calendarShowDate,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()
                    ) {
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = "DATA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = suggestion?.date?.let {
                                    try {
                                        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)
                                        if (d != null) java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.ITALIAN).format(d) else it
                                    } catch (e: Exception) { it }
                                } ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Time field
                    androidx.compose.animation.AnimatedVisibility(
                        visible = calendarShowTime,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()
                    ) {
                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(
                                text = "ORA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = suggestion?.time ?: "09:00",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Action button - appears last
                    androidx.compose.animation.AnimatedVisibility(
                        visible = calendarReadyToOpen,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()
                    ) {
                        androidx.compose.material3.Button(
                            onClick = {
                                showCalendarDialog = false
                                val finalTitle = suggestion?.title ?: article.title
                                val finalDateStr = suggestion?.date
                                val cal = java.util.Calendar.getInstance()
                                if (!finalDateStr.isNullOrBlank()) {
                                    try {
                                        val parsedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(finalDateStr)
                                        if (parsedDate != null) {
                                            cal.time = parsedDate
                                            val timeStr = suggestion?.time
                                            if (!timeStr.isNullOrBlank() && timeStr.contains(":")) {
                                                val parts = timeStr.split(":")
                                                cal.set(java.util.Calendar.HOUR_OF_DAY, parts[0].trim().toIntOrNull() ?: 9)
                                                cal.set(java.util.Calendar.MINUTE, parts[1].trim().toIntOrNull() ?: 0)
                                            } else {
                                                cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
                                                cal.set(java.util.Calendar.MINUTE, 0)
                                            }
                                            cal.set(java.util.Calendar.SECOND, 0)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                } else if (article.pubDateTimestamp > 0) {
                                    cal.timeInMillis = article.pubDateTimestamp
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                    data = android.provider.CalendarContract.Events.CONTENT_URI
                                    putExtra(android.provider.CalendarContract.Events.TITLE, finalTitle)
                                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 60 * 60 * 1000)
                                    putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Promemoria da Nothing News\n${article.title}\n${article.link}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aggiungi al calendario")
                        }
                    }
                }
            }
        }
    }
}


private fun simplifySourceName(name: String): String {
    // Remove Twitter handles or similar parenthetical info
    var cleanedName = name
    if (cleanedName.contains(" (") && cleanedName.contains(")")) {
        cleanedName = cleanedName.substringBefore(" (").trim()
    }

    val separators = listOf(" | ", " - ", " : ", " – ", " — ")
    for (sep in separators) {
        if (cleanedName.contains(sep)) {
            val parts = cleanedName.split(sep)
            if (parts.size >= 2) {
                val p1 = parts[0].trim()
                val p2 = parts[1].trim()
                
                // If p2 looks like a domain (contains .it, .com, etc.) keep p2
                val domainSuffixes = listOf(".it", ".com", ".net", ".org", ".edu")
                if (domainSuffixes.any { p2.lowercase().contains(it) }) {
                    return p2
                }
                
                // If p1 looks like a domain, keep p1
                if (domainSuffixes.any { p1.lowercase().contains(it) }) {
                    return p1
                }
                
                // Otherwise keep the shorter one, assuming it's the brand
                return if (p1.length <= p2.length) p1 else p2
            }
        }
    }
    return cleanedName.trim()
}

private class StarParticle(
    val dirX: Float,
    val dirY: Float,
    val size: androidx.compose.ui.unit.Dp,
    val lifeSpan: Float
)

@Composable
fun StarExplosion(
    isTriggered: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val particles = remember(isTriggered) {
        if (isTriggered) {
            List(25) {
                val angle = kotlin.random.Random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = kotlin.random.Random.nextFloat() * 4f + 2f
                val size = (kotlin.random.Random.nextFloat() * 4 + 2).dp
                val life = kotlin.random.Random.nextFloat() * 0.4f + 0.6f
                StarParticle(
                    dirX = Math.cos(angle.toDouble()).toFloat() * speed,
                    dirY = Math.sin(angle.toDouble()).toFloat() * speed,
                    size = size,
                    lifeSpan = life
                )
            }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(isTriggered) {
        if (isTriggered) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        } else {
            progress.snapTo(0f)
        }
    }

    if (progress.value > 0f && progress.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val p = progress.value
            val maxDistance = 60.dp.toPx()
            particles.forEach { particle ->
                val alpha = (1f - (p / particle.lifeSpan)).coerceIn(0f, 1f)
                if (alpha > 0f) {
                    val currentX = center.x + particle.dirX * p * maxDistance
                    val currentY = center.y + particle.dirY * p * maxDistance
                    drawCircle(
                        color = Color(0xFFFFC107).copy(alpha = alpha),
                        radius = particle.size.toPx() * (1f - p * 0.5f),
                        center = Offset(currentX, currentY)
                    )
                }
            }
        }
    }
}
