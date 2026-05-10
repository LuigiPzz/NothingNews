package com.nothing.news.ui.news

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.news.data.local.entity.FeedSource
import com.nothing.news.data.local.entity.NewsArticle
import com.nothing.news.data.repository.NewsRepository
import com.nothing.news.data.remote.FeedsearchResult
import com.nothing.news.data.local.PreferenceManager
import com.nothing.news.util.GoogleDriveBackupManager
import com.nothing.news.util.NewsDateUtils
import com.nothing.news.data.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.nothing.news.worker.NewsWorker

data class BrowserInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable? = null
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: NewsRepository,
    private val aiRepository: AiRepository,
    private val preferenceManager: PreferenceManager,
    private val googleDriveBackupManager: GoogleDriveBackupManager
) : ViewModel() {

    // 1. ALL MutableStateFlow declarations first
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus = _backupStatus.asStateFlow()

    private val _remoteBackupData = MutableStateFlow<com.nothing.news.util.BackupData?>(null)
    val remoteBackupData = _remoteBackupData.asStateFlow()

    private val _localBackupData = MutableStateFlow<com.nothing.news.util.BackupData?>(null)
    val localBackupData = _localBackupData.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    private val _currentUser = MutableStateFlow<com.google.android.gms.auth.api.signin.GoogleSignInAccount?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _articleSummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val articleSummaries: StateFlow<Map<String, String>> = _articleSummaries

    private val _loadingSummaries = MutableStateFlow<Set<String>>(emptySet())
    val loadingSummaries: StateFlow<Set<String>> = _loadingSummaries

    private val _searchResults = MutableStateFlow<List<FeedsearchResult>>(emptyList())
    val searchResults: StateFlow<List<FeedsearchResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    private val _sortOrder = MutableStateFlow("Decrescente")
    val sortOrder: StateFlow<String> = _sortOrder

    private val _filterType = MutableStateFlow("Tutti")
    val filterType: StateFlow<String> = _filterType

    // 2. Computed StateFlows from PreferenceManager
    val themePreference: StateFlow<String> = preferenceManager.themePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")

    val browserPreference: StateFlow<String> = preferenceManager.browserPreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Interno")

    val selectedBrowserPackage: StateFlow<String?> = preferenceManager.browserPackagePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autoMarkReadDays: StateFlow<Int> = preferenceManager.autoMarkReadDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val backgroundUpdateFrequency: StateFlow<Int> = preferenceManager.backgroundUpdateFrequency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val autoBackup: StateFlow<Boolean> = preferenceManager.autoBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastAutoBackupTimestamp: StateFlow<Long> = preferenceManager.lastAutoBackupTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val reminders: StateFlow<String> = preferenceManager.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "[]")

    val geminiApiKey: StateFlow<String?> = preferenceManager.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val newsArticles: StateFlow<List<NewsArticle>> = combine(
        repository.allNews,
        _sortOrder,
        _filterType
    ) { articles, sort, filter ->
        val filtered = when (filter) {
            "Letti" -> articles.filter { it.isRead }
            "Non Letti" -> articles.filter { !it.isRead }
            "Preferiti" -> articles.filter { it.isFavorite }
            else -> articles
        }
        
        if (sort == "Crescente") {
            filtered.sortedBy { it.pubDateTimestamp }
        } else {
            filtered.sortedByDescending { it.pubDateTimestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allNewsArticles: StateFlow<List<NewsArticle>> = repository.allNews
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val feedSources: StateFlow<List<FeedSource>> = repository.allFeeds
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 3. Init block
    init {
        viewModelScope.launch {
            preferenceManager.sortPreference.collect { _sortOrder.value = it }
        }
        viewModelScope.launch {
            preferenceManager.filterPreference.collect { _filterType.value = it }
        }
        
        viewModelScope.launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    _currentUser.value = account
                }
            } catch (e: Exception) { }
        }
    }

    // 4. Preference Setters
    fun setTheme(theme: String) {
        viewModelScope.launch {
            preferenceManager.setTheme(theme)
        }
    }

    fun setBrowser(browser: String) {
        viewModelScope.launch {
            preferenceManager.setBrowser(browser)
        }
    }

    fun setBrowserPackage(packageName: String?) {
        viewModelScope.launch {
            preferenceManager.setBrowserPackage(packageName)
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setAutoBackup(enabled)
        }
    }

    fun setAutoMarkReadDays(days: Int) {
        viewModelScope.launch {
            preferenceManager.setAutoMarkReadDays(days)
            repository.markOldAsRead(days)
        }
    }

    fun setBackgroundUpdateFrequency(hours: Int) {
        viewModelScope.launch {
            preferenceManager.setBackgroundUpdateFrequency(hours)
            scheduleBackgroundUpdate(hours)
        }
    }

    fun setSortOrder(sort: String) {
        viewModelScope.launch {
            preferenceManager.setSort(sort)
            _sortOrder.value = sort
        }
    }

    fun setFilterType(filter: String) {
        viewModelScope.launch {
            preferenceManager.setFilter(filter)
            _filterType.value = filter
        }
    }

    fun setGeminiApiKey(key: String?) {
        viewModelScope.launch {
            preferenceManager.setGeminiApiKey(key)
        }
    }

    // 5. App Functions
    fun clearBackupStatus() { _backupStatus.value = null }

    fun getSignInIntent() = googleDriveBackupManager.getGoogleSignInClient().signInIntent

    fun refreshBackupInfo() {
        viewModelScope.launch {
            try {
                _localBackupData.value = repository.getBackupData()
                val account = _currentUser.value
                if (account != null) {
                    kotlinx.coroutines.withTimeout(10000) {
                        _remoteBackupData.value = googleDriveBackupManager.downloadBackup(account)
                    }
                }
            } catch (e: Exception) {
                _remoteBackupData.value = null
            }
        }
    }

    fun signOut() {
        googleDriveBackupManager.getGoogleSignInClient().signOut()
        _currentUser.value = null
        _remoteBackupData.value = null
    }

    fun triggerBackupRestore(isBackup: Boolean) {
        val account = _currentUser.value
        if (account == null) {
            _backupStatus.value = "Devi prima collegare un account."
            return
        }
        
        viewModelScope.launch {
            _isBackingUp.value = true
            _backupStatus.value = if (isBackup) "Salvataggio su Google Drive..." else "Ripristino da Google Drive..."
            try {
                kotlinx.coroutines.withTimeout(20000) {
                    if (isBackup) {
                        val data = repository.getBackupData()
                        val success = googleDriveBackupManager.uploadBackup(account, data)
                        if (success) {
                            _remoteBackupData.value = data
                            _backupStatus.value = "Backup salvato su Google Drive!"
                        } else {
                            _backupStatus.value = "Errore durante il salvataggio su Drive."
                        }
                    } else {
                        val data = googleDriveBackupManager.downloadBackup(account)
                        if (data != null) {
                            repository.restoreBackupData(data)
                            _localBackupData.value = repository.getBackupData()
                            _backupStatus.value = "Dati ripristinati correttamente!"
                            refreshNews()
                        } else {
                            _backupStatus.value = "Nessun file di backup trovato su Drive."
                        }
                    }
                }
            } catch (e: Exception) {
                _backupStatus.value = "Errore: ${e.localizedMessage}"
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    fun handleSignInResult(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?, isBackup: Boolean) {
        if (account == null) {
            _backupStatus.value = "Accesso Google fallito."
            return
        }
        _currentUser.value = account
        triggerBackupRestore(isBackup)
    }

    private fun performAutoBackup() {
        val account = _currentUser.value
        if (account == null || !autoBackup.value) return
        
        viewModelScope.launch {
            try {
                val data = repository.getBackupData()
                val success = googleDriveBackupManager.uploadBackup(account, data)
                if (success) {
                    _remoteBackupData.value = data
                    preferenceManager.setLastAutoBackupTimestamp(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                // Silently fail for auto backup to not disturb user
                e.printStackTrace()
            }
        }
    }

    fun searchFeeds(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            _searchResults.value = emptyList()
            try {
                repository.searchFeeds(query).collect { results ->
                    _searchResults.value = results
                }
                if (_searchResults.value.isEmpty()) {
                    _searchError.value = "Nessun feed trovato per '$query'."
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _searchResults.value = emptyList()
                    _searchError.value = "Errore ricerca: ${e.message ?: "Connessione fallita"}"
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun summarizeArticle(article: NewsArticle) {
        if (_articleSummaries.value.containsKey(article.link)) return
        if (_loadingSummaries.value.contains(article.link)) return
        
        viewModelScope.launch {
            _loadingSummaries.value += article.link
            try {
                val summary = aiRepository.summarizeArticle(article.title, article.description ?: article.content, article.link)
                if (summary != null) {
                    _articleSummaries.value += (article.link to summary)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loadingSummaries.value -= article.link
            }
        }
    }

    fun clearSummary(article: NewsArticle) {
        _articleSummaries.value -= article.link
    }

    fun refreshNews() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val days = autoMarkReadDays.value
            if (days > 0) {
                repository.markOldAsRead(days)
            }
            // Always delete news older than 30 days (except favorites)
            repository.deleteOldNews(30)

            try {
                val feeds = repository.allFeeds.first()
                for (feed in feeds) {
                    repository.fetchNewsForSource(feed)
                }
                
                // Trigger auto backup after refresh if enabled
                performAutoBackup()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun resetAllReadStatus() {
        viewModelScope.launch {
            repository.resetAllReadStatus()
        }
    }

    fun addFeed(url: String) {
        viewModelScope.launch {
            try {
                repository.addFeed(url)
                _searchResults.value = _searchResults.value.filter { it.url != url }
                _searchError.value = "Feed aggiunto con successo!"
            } catch (e: Exception) {
                _searchError.value = "Errore nell'aggiunta del feed: ${e.message}"
            }
        }
    }

    fun removeFeed(url: String) {
        viewModelScope.launch {
            repository.removeFeed(url)
        }
    }

    fun toggleReadStatus(article: NewsArticle) {
        viewModelScope.launch {
            repository.updateReadStatus(article.link, !article.isRead)
        }
    }

    fun updateReadStatus(link: String, isRead: Boolean) {
        viewModelScope.launch {
            repository.updateReadStatus(link, isRead)
        }
    }

    fun toggleFavorite(article: NewsArticle) {
        viewModelScope.launch {
            repository.toggleFavorite(article.link, !article.isFavorite)
        }
    }

    fun addReminder(title: String, link: String, timeInMillis: Long) {
        viewModelScope.launch {
            val currentJson = preferenceManager.reminders.first()
            val type = object : com.google.gson.reflect.TypeToken<List<com.nothing.news.util.Reminder>>() {}.type
            val currentList = com.google.gson.Gson().fromJson<MutableList<com.nothing.news.util.Reminder>>(currentJson, type) ?: mutableListOf()
            currentList.removeAll { it.link == link }
            currentList.add(com.nothing.news.util.Reminder(title, link, timeInMillis))
            preferenceManager.setReminders(com.google.gson.Gson().toJson(currentList))
        }
    }

    fun removeReminder(link: String) {
        viewModelScope.launch {
            val currentJson = preferenceManager.reminders.first()
            val type = object : com.google.gson.reflect.TypeToken<List<com.nothing.news.util.Reminder>>() {}.type
            val currentList = com.google.gson.Gson().fromJson<MutableList<com.nothing.news.util.Reminder>>(currentJson, type) ?: mutableListOf()
            currentList.removeAll { it.link == link }
            preferenceManager.setReminders(com.google.gson.Gson().toJson(currentList))
            com.nothing.news.util.ReminderManager.cancelReminder(context, link)
        }
    }

    private fun scheduleBackgroundUpdate(hours: Int) {
        val workManager = WorkManager.getInstance(context)
        if (hours <= 0) {
            workManager.cancelAllWorkByTag("news_update")
        } else {
            val workRequest = PeriodicWorkRequestBuilder<NewsWorker>(
                hours.toLong(), TimeUnit.HOURS
            ).addTag("news_update").build()
            
            workManager.enqueueUniquePeriodicWork(
                "news_update",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    fun getInstalledBrowsers(): List<BrowserInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        val pm = context.packageManager
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        
        return resolveInfos.map { resolveInfo ->
            BrowserInfo(
                name = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(pm)
            )
        }
    }
}
