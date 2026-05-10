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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    val coroutineScope = rememberCoroutineScope()
    
    val pullToRefreshState = rememberPullToRefreshState()
    var showFilterDialog by remember { mutableStateOf(false) }
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
    
    val imageLoader = coil.compose.LocalImageLoader.current

    // Safe initial refresh when screen starts
    LaunchedEffect(Unit) {
        viewModel.refreshNews()
    }
    
    val filterSheetState = rememberModalBottomSheetState()
    val sortSheetState = rememberModalBottomSheetState()

    // Filter Bottom Sheet
    if (showFilterDialog) {
        ModalBottomSheet(
            onDismissRequest = { showFilterDialog = false },
            sheetState = filterSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Filtri",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )
                
                Text(
                    "STATO LETTURA",
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
                        listOf("Tutti", "Letti", "Non Letti", "Preferiti").forEachIndexed { index, filter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.setFilterType(filter)
                                        showFilterDialog = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentFilter == filter,
                                    onClick = { 
                                        viewModel.setFilterType(filter)
                                        showFilterDialog = false
                                    }
                                )
                                Text(
                                    text = filter, 
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            if (index < 3) {
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
    
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshNews()
        }
    }
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
            sessionReadLinks.clear()
            sessionUnreadLinks.clear()
            dissolvingLinks.clear()
            resolvingLinks.clear()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    val baseArticles = articles
    val displayArticles = remember(baseArticles, dissolvingLinks.size, resolvingLinks.size, sessionReadLinks.size, sessionUnreadLinks.size) {
        val manualLinks = dissolvingLinks.keys + resolvingLinks.keys + sessionReadLinks + sessionUnreadLinks
        val combined = if (manualLinks.isEmpty()) baseArticles
        else {
            val extraItems = allArticles.filter { it.link in manualLinks && it !in baseArticles }
            (baseArticles + extraItems)
        }
        combined.distinctBy { it.link }.sortedByDescending { it.pubDateTimestamp }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "NOTHING NEWS",
                            style = MaterialTheme.typography.titleLarge
                        )
                        val countText = baseArticles.size.toString()
                        Text(
                            text = " · $countText",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 16.sp),
                            color = Color(0xFFFF2D00)
                        )
                    }
                },
                actions = {
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
                                text = { Text("Filtri") },
                                onClick = {
                                    showMenu = false
                                    showFilterDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) }
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
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                                viewModel.updateReadStatus(article.link, true)
                                dissolvingLinks.remove(article.link)
                                if (!sessionReadLinks.contains(article.link)) {
                                    sessionReadLinks.add(article.link)
                                }
                                sessionUnreadLinks.remove(article.link)
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
                                viewModel.toggleFavorite(article)
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
                            onSetReminder = { timeInMillis ->
                                viewModel.addReminder(article.title, article.link, timeInMillis)
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
            AnimatedVisibility(
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NewsItem(
    article: NewsArticle,
    isReadSession: Boolean = false,
    summary: String?,
    isLoadingSummary: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
    onSummarize: () -> Unit,
    onClearSummary: () -> Unit,
    onSetReminder: (Long) -> Unit
) {
    // Typewriter effect state - only reset if the summary text actually changes
    var lastProcessedSummary by remember { mutableStateOf<String?>(null) }
    var displayedSummary by remember { mutableStateOf("") }
    var showContextMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
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
        targetValue = if (isReadSession) Color.Gray.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
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
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = " • ${
                                        if (article.pubDateTimestamp > 0) {
                                            java.text.SimpleDateFormat("HH:mm • dd/MM/yy", java.util.Locale.ITALY).format(java.util.Date(article.pubDateTimestamp))
                                        } else {
                                            article.pubDate ?: ""
                                        }
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = animatedDateColor,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Spacer(modifier = Modifier.weight(1f))
                                
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
                                            strokeWidth = 2.dp,
                                            color = animatedContentColor
                                        )
                                    }
                                }
                            }

                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = animatedContentColor
                            )
                        }

                        article.imageUrl?.let { imageUrl ->
                            Box(
                                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
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
                                        .graphicsLayer(
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
                                Text(
                                    text = displayedSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    lineHeight = 18.sp,
                                    color = if (isReadSession) Color.Gray else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Imposta un promemoria") },
                        onClick = {
                            showContextMenu = false
                            val calendar = java.util.Calendar.getInstance()
                            
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            val selectedTime = java.util.Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                            }
                                            com.nothing.news.util.ReminderManager.scheduleReminder(
                                                context,
                                                article.title,
                                                article.link,
                                                selectedTime.timeInMillis
                                            )
                                            onSetReminder(selectedTime.timeInMillis)
                                            android.widget.Toast.makeText(context, "Promemoria in-app impostato!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                        calendar.get(java.util.Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Condividi") },
                        onClick = {
                            showContextMenu = false
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                }
            }
        }
    )
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
