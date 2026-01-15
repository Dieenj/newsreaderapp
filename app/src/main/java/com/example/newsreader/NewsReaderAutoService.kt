package com.example.newsreader

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.newsreader.database.ArticleEntity
import com.example.newsreader.database.NewsDatabase
import com.example.newsreader.database.ArticleRepository
import com.example.newsreader.data.ArticleContentFetcher
import com.example.newsreader.data.RssFeedManager
import kotlinx.coroutines.*
import java.util.*

class NewsReaderAutoService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private var textToSpeech: TextToSpeech? = null
    private var currentArticle: ArticleEntity? = null
    private var isPlaying = false
    private var isPaused = false

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentFetchJob: Job? = null
    private lateinit var repository: ArticleRepository
    private val contentFetcher = ArticleContentFetcher()
    private val rssFeedManager = RssFeedManager()

    companion object {
        const val MEDIA_ROOT_ID = "news_root"
        const val RECENT_NEWS_ID = "recent_news"
        const val BY_SOURCE_ID = "by_source"
        const val BY_CATEGORY_ID = "by_category"

        const val SOURCE_PREFIX = "source_"
        const val SOURCE_VNEXPRESS = "${SOURCE_PREFIX}vnexpress"
        const val SOURCE_TUOITRE = "${SOURCE_PREFIX}tuoitre"
        const val SOURCE_THANHNIEN = "${SOURCE_PREFIX}thanhnien"
        const val SOURCE_DANTRI = "${SOURCE_PREFIX}dantri"
        const val SOURCE_ZINGNEWS = "${SOURCE_PREFIX}zingnews"
        const val SOURCE_VIETNAMNET = "${SOURCE_PREFIX}vietnamnet"
        const val SOURCE_BAOMOI = "${SOURCE_PREFIX}baomoi"

        const val CAT_PREFIX = "cat_"
        const val CAT_POLITICS = "${CAT_PREFIX}politics"
        const val CAT_WORLD = "${CAT_PREFIX}world"
        const val CAT_BUSINESS = "${CAT_PREFIX}business"
        const val CAT_ENTERTAINMENT = "${CAT_PREFIX}entertainment"
        const val CAT_SPORTS = "${CAT_PREFIX}sports"
        const val CAT_TECH = "${CAT_PREFIX}tech"
        const val CAT_HEALTH = "${CAT_PREFIX}health"
        const val CAT_EDUCATION = "${CAT_PREFIX}education"
        const val CAT_LAW = "${CAT_PREFIX}law"
        const val CAT_CULTURE = "${CAT_PREFIX}culture"

        const val SOURCE_DETAIL_PREFIX = "sourcedetail_"
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val database = NewsDatabase.getDatabase(this)
        repository = ArticleRepository(database.articleDao())

        // BƯỚC 1: TẠO MEDIASESSION
        setupMediaSession()

        // BƯỚC 2: KHỞI TẠO TTS VỚI AUDIO ATTRIBUTES
        setupTextToSpeech()
    }

    private fun setupMediaSession() {
        // Tạo MediaSession với token
        mediaSession = MediaSessionCompat(this, "NewsReaderService").apply {

            // Set flags để handle transport controls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            // Set callback để xử lý play/pause/stop
            setCallback(mediaSessionCallback)

            // Set playback state ban đầu
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackStateCompat.ACTION_STOP or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .build()
            )

            // Set session active
            isActive = true
        }

        // Set session token cho Service
        sessionToken = mediaSession.sessionToken

        android.util.Log.d("MediaSession", "MediaSession created and activated")
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set ngôn ngữ Tiếng Việt
                val langResult = textToSpeech?.setLanguage(Locale("vi", "VN"))

                when (langResult) {
                    TextToSpeech.LANG_MISSING_DATA -> {
                        android.util.Log.e("TTS", "Vietnamese language data missing")
                    }
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        android.util.Log.e("TTS", "Vietnamese not supported")
                    }
                    else -> {
                        android.util.Log.d("TTS", "Vietnamese TTS ready")

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            val audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                                .build()

                            textToSpeech?.setAudioAttributes(audioAttributes)
                            android.util.Log.d("TTS", "Audio attributes set to MEDIA stream")
                        }

                        // Set listener để track progress
                        setupTTSListener()
                    }
                }
            } else {
                android.util.Log.e("TTS", "TTS initialization failed: $status")
            }
        }
    }

    private fun setupTTSListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                android.util.Log.d("TTS", "Started speaking: $utteranceId")
                isPlaying = true
                isPaused = false

                // Update MediaSession state
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onDone(utteranceId: String?) {
                android.util.Log.d("TTS", "Finished speaking: $utteranceId")
                isPlaying = false

                // Update MediaSession state
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }

            override fun onError(utteranceId: String?) {
                android.util.Log.e("TTS", "Error speaking: $utteranceId")
                isPlaying = false

                // Update MediaSession state
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                android.util.Log.e("TTS", "Error speaking: $utteranceId, code: $errorCode")
                onError(utteranceId)
            }
        })
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            android.util.Log.d("MediaSession", "onPlay() called")

            if (isPaused && textToSpeech?.isSpeaking == false) {
                // Resume không hoạt động với TTS, cần đọc lại
                currentArticle?.let { readArticle(it) }
            } else {
                currentArticle?.let { readArticle(it) }
            }
        }

        override fun onPause() {
            android.util.Log.d("MediaSession", "onPause() called")

            if (textToSpeech?.isSpeaking == true) {
                textToSpeech?.stop()
                isPaused = true
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }
        }

        override fun onStop() {
            android.util.Log.d("MediaSession", "onStop() called")

            textToSpeech?.stop()
            abandonAudioFocus()
            currentArticle = null
            isPlaying = false
            isPaused = false
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }

        override fun onSkipToNext() {
            android.util.Log.d("MediaSession", "onSkipToNext() called")
            textToSpeech?.stop()
            loadNextArticle()
        }

        override fun onSkipToPrevious() {
            android.util.Log.d("MediaSession", "onSkipToPrevious() called")
            textToSpeech?.stop()
            loadPreviousArticle()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            android.util.Log.d("MediaSession", "onPlayFromMediaId: $mediaId")

            textToSpeech?.stop()

            mediaId?.let {
                if (it.startsWith(SOURCE_PREFIX) || it.startsWith(CAT_PREFIX)) {
                    // Browsable items, ignore
                } else {
                    loadArticleById(it)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        android.util.Log.d("AudioFocus", "Request result: $hasAudioFocus")

        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }

        hasAudioFocus = false
        android.util.Log.d("AudioFocus", "Audio focus abandoned")
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        android.util.Log.d("AudioFocus", "Focus change: $focusChange")

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Có audio focus, có thể phát
                android.util.Log.d("AudioFocus", "Gained audio focus")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Mất audio focus vĩnh viễn, dừng hẳn
                android.util.Log.d("AudioFocus", "Lost audio focus")
                textToSpeech?.stop()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Mất tạm thời (VD: notification), pause
                android.util.Log.d("AudioFocus", "Lost audio focus temporarily")
                textToSpeech?.stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Có thể giảm âm lượng
                android.util.Log.d("AudioFocus", "Can duck")
                // TTS không hỗ trợ duck, nên pause
                textToSpeech?.stop()
            }
        }
    }

    private fun readArticle(article: ArticleEntity) {
        currentArticle = article

        android.util.Log.d("NewsReader", "Reading article: ${article.title}")

        currentFetchJob?.cancel()

        if (article.fullContent != null && article.fullContent.isNotEmpty()) {
            speakArticle(article, article.fullContent)
        } else {
            currentFetchJob = serviceScope.launch(Dispatchers.IO) {
                try {
                    val fullContent = contentFetcher.fetchFullContent(article.url)

                    if (!coroutineContext.isActive) return@launch

                    if (fullContent != null && fullContent.isNotEmpty()) {
                        repository.updateFullContent(article.id, fullContent)

                        withContext(Dispatchers.Main) {
                            speakArticle(article, fullContent)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            speakArticle(article, article.content)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("NewsReader", "Error fetching content", e)
                    withContext(Dispatchers.Main) {
                        speakArticle(article, article.content)
                    }
                }
            }
        }
    }

    private fun speakArticle(article: ArticleEntity, content: String) {
        android.util.Log.d("TTS", "speakArticle() called")
        android.util.Log.d("TTS", "Article: ${article.title}")
        android.util.Log.d("TTS", "Content length: ${content.length} chars")

        if (textToSpeech == null) {
            android.util.Log.e("TTS", "TTS is null!")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        // BƯỚC 1: Request Audio Focus
        if (!requestAudioFocus()) {
            android.util.Log.e("TTS", "Failed to gain audio focus!")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        // BƯỚC 2: Stop nếu đang đọc
        if (textToSpeech?.isSpeaking == true) {
            android.util.Log.d("TTS", "Stopping current speech")
            textToSpeech?.stop()
        }

        // BƯỚC 3: Chuẩn bị text
        val fullText = "${article.title}. $content"
        android.util.Log.d("TTS", "Full text length: ${fullText.length} chars")

        // BƯỚC 4: Tạo params
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, article.id)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        // BƯỚC 5: Gọi TTS speak
        val result = textToSpeech?.speak(
            fullText,
            TextToSpeech.QUEUE_FLUSH,
            params,
            article.id
        )

        android.util.Log.d("TTS", "speak() returned: $result (SUCCESS=0, ERROR=-1)")

        if (result == TextToSpeech.SUCCESS) {
            // BƯỚC 6: Update MediaSession
            isPlaying = true
            isPaused = false
            updateMediaMetadata(article, content)
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)

            // BƯỚC 7: Đánh dấu đã đọc
            serviceScope.launch {
                repository.markAsRead(article.id)
            }
        } else {
            android.util.Log.e("TTS", "speak() failed!")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun updateMediaMetadata(article: ArticleEntity, content: String) {
        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, article.title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, article.source)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, article.summary)
            .putLong(
                android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION,
                estimateReadingTime(content)
            )
            .build()

        mediaSession.setMetadata(metadata)
        android.util.Log.d("MediaSession", "Metadata updated")
    }

    private fun estimateReadingTime(text: String): Long {
        val wordCount = text.split("\\s+".toRegex()).size
        return (wordCount * 60000L / 150) // 150 words per minute
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, 0, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)

        val stateStr = when(state) {
            PlaybackStateCompat.STATE_PLAYING -> "PLAYING"
            PlaybackStateCompat.STATE_PAUSED -> "PAUSED"
            PlaybackStateCompat.STATE_STOPPED -> "STOPPED"
            PlaybackStateCompat.STATE_ERROR -> "ERROR"
            PlaybackStateCompat.STATE_BUFFERING -> "BUFFERING"
            else -> "UNKNOWN($state)"
        }
        android.util.Log.d("MediaSession", "State updated to: $stateStr")
    }

    private fun loadArticleById(articleId: String) {
        serviceScope.launch {
            val article = repository.getArticleById(articleId)
            currentArticle = article
            article?.let { readArticle(it) }
        }
    }

    private fun loadNextArticle() {
        serviceScope.launch {
            val nextArticle = repository.getNextArticle(currentArticle?.id ?: "")
            currentArticle = nextArticle
            nextArticle?.let { readArticle(it) }
        }
    }

    private fun loadPreviousArticle() {
        serviceScope.launch {
            val prevArticle = repository.getPreviousArticle(currentArticle?.id ?: "")
            currentArticle = prevArticle
            prevArticle?.let { readArticle(it) }
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        android.util.Log.d("MediaBrowser", "onGetRoot called from: $clientPackageName")
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        android.util.Log.d("MediaBrowser", "onLoadChildren: $parentId")
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        when (parentId) {
            MEDIA_ROOT_ID -> {
                mediaItems.add(createBrowsableMediaItem(BY_SOURCE_ID, "Trang báo", "Chọn theo tờ báo"))
                result.sendResult(mediaItems)
            }

            RECENT_NEWS_ID -> {
                result.detach()
                serviceScope.launch {
                    try {
                        val articles = repository.getAllArticlesSync()
                        articles.forEach { article ->
                            mediaItems.add(createPlayableMediaItem(article))
                        }
                        result.sendResult(mediaItems)
                    } catch (e: Exception) {
                        result.sendResult(mediaItems)
                    }
                }
            }

            BY_SOURCE_ID -> {
                mediaItems.add(createBrowsableMediaItem(SOURCE_VNEXPRESS, "VnExpress", "Báo VnExpress"))
                mediaItems.add(createBrowsableMediaItem(SOURCE_TUOITRE, "Tuổi Trẻ", "Báo Tuổi Trẻ"))
                mediaItems.add(createBrowsableMediaItem(SOURCE_THANHNIEN, "Thanh Niên", "Báo Thanh Niên"))
                mediaItems.add(createBrowsableMediaItem(SOURCE_DANTRI, "Dân Trí", "Báo Dân Trí"))
                mediaItems.add(createBrowsableMediaItem(SOURCE_ZINGNEWS, "Zing News", "Zing News"))
                mediaItems.add(createBrowsableMediaItem(SOURCE_VIETNAMNET, "VietnamNet", "Báo VietnamNet"))
                mediaItems.add(createBrowsableMediaItem(SOURCE_BAOMOI, "Báo Mới", "Báo Mới"))
                result.sendResult(mediaItems)
            }

            BY_CATEGORY_ID -> {
                mediaItems.add(createBrowsableMediaItem(CAT_POLITICS, "Thời sự", "Tin thời sự, chính trị"))
                mediaItems.add(createBrowsableMediaItem(CAT_WORLD, "Thế giới", "Tin quốc tế"))
                mediaItems.add(createBrowsableMediaItem(CAT_BUSINESS, "Kinh doanh", "Tin kinh tế, doanh nghiệp"))
                mediaItems.add(createBrowsableMediaItem(CAT_ENTERTAINMENT, "Giải trí", "Tin giải trí, showbiz"))
                mediaItems.add(createBrowsableMediaItem(CAT_SPORTS, "Thể thao", "Tin thể thao"))
                mediaItems.add(createBrowsableMediaItem(CAT_TECH, "Công nghệ", "Tin công nghệ, số hóa"))
                mediaItems.add(createBrowsableMediaItem(CAT_HEALTH, "Sức khỏe", "Tin sức khỏe, y tế"))
                mediaItems.add(createBrowsableMediaItem(CAT_EDUCATION, "Giáo dục", "Tin giáo dục"))
                mediaItems.add(createBrowsableMediaItem(CAT_LAW, "Pháp luật", "Tin pháp luật"))
                mediaItems.add(createBrowsableMediaItem(CAT_CULTURE, "Văn hóa", "Tin văn hóa, du lịch"))
                result.sendResult(mediaItems)
            }

            SOURCE_VNEXPRESS -> showSourceCategories("VnExpress", result, mediaItems)
            SOURCE_TUOITRE -> showSourceCategories("Tuổi Trẻ", result, mediaItems)
            SOURCE_THANHNIEN -> showSourceCategories("Thanh Niên", result, mediaItems)
            SOURCE_DANTRI -> showSourceCategories("Dân Trí", result, mediaItems)
            SOURCE_ZINGNEWS -> showSourceCategories("Zing News", result, mediaItems)
            SOURCE_VIETNAMNET -> showSourceCategories("VietnamNet", result, mediaItems)
            SOURCE_BAOMOI -> showSourceCategories("Báo Mới", result, mediaItems)

            else -> {
                when {
                    parentId.startsWith(SOURCE_DETAIL_PREFIX) -> {
                        handleSourceDetailRequest(parentId, result, mediaItems)
                    }
                    parentId.startsWith(CAT_PREFIX) -> {
                        handleCategoryRequest(parentId, result, mediaItems)
                    }
                    else -> {
                        result.sendResult(mediaItems)
                    }
                }
            }
        }
    }

    private fun showSourceCategories(
        sourceName: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        mediaItems: MutableList<MediaBrowserCompat.MediaItem>
    ) {
        val categories = RssFeedManager.NEWS_SOURCES[sourceName] ?: emptyMap()

        categories.forEach { (categoryName, _) ->
            val id = "${SOURCE_DETAIL_PREFIX}${sourceName}__$categoryName"
            mediaItems.add(createBrowsableMediaItem(id, categoryName, "Tin $categoryName từ $sourceName"))
        }

        result.sendResult(mediaItems)
    }

    private fun handleSourceDetailRequest(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        mediaItems: MutableList<MediaBrowserCompat.MediaItem>
    ) {
        result.detach()

        serviceScope.launch {
            try {
                val parts = parentId.removePrefix(SOURCE_DETAIL_PREFIX).split("__")
                if (parts.size == 2) {
                    val sourceName = parts[0]
                    val categoryName = parts[1]

                    val articles = rssFeedManager.fetchFeedsByCategory(sourceName, categoryName)

                    if (articles.isNotEmpty()) {
                        repository.insertArticles(articles)
                    }

                    articles.forEach { article ->
                        mediaItems.add(createPlayableMediaItem(article))
                    }
                }

                result.sendResult(mediaItems)
            } catch (e: Exception) {
                android.util.Log.e("NewsReader", "Error loading source detail", e)
                result.sendResult(mediaItems)
            }
        }
    }

    private fun handleCategoryRequest(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        mediaItems: MutableList<MediaBrowserCompat.MediaItem>
    ) {
        result.detach()

        serviceScope.launch {
            try {
                val categoryNameInRss = when(parentId) {
                    CAT_POLITICS -> "Thời sự"
                    CAT_WORLD -> "Thế giới"
                    CAT_BUSINESS -> "Kinh doanh"
                    CAT_ENTERTAINMENT -> "Giải trí"
                    CAT_SPORTS -> "Thể thao"
                    CAT_TECH -> "Công nghệ"
                    CAT_HEALTH -> "Sức khỏe"
                    CAT_EDUCATION -> "Giáo dục"
                    CAT_LAW -> "Pháp luật"
                    CAT_CULTURE -> "Văn hóa"
                    else -> null
                }

                if (categoryNameInRss != null) {
                    val allArticles = mutableListOf<ArticleEntity>()

                    listOf("VnExpress", "Tuổi Trẻ", "Thanh Niên", "Dân Trí", "Zing News", "VietnamNet").forEach { source ->
                        try {
                            val articles = rssFeedManager.fetchFeedsByCategory(source, categoryNameInRss)
                            allArticles.addAll(articles)
                        } catch (e: Exception) {
                            // Skip
                        }
                    }

                    if (allArticles.isNotEmpty()) {
                        repository.insertArticles(allArticles)
                    }

                    allArticles.forEach { article ->
                        mediaItems.add(createPlayableMediaItem(article))
                    }
                }

                result.sendResult(mediaItems)
            } catch (e: Exception) {
                result.sendResult(mediaItems)
            }
        }
    }

    private fun createBrowsableMediaItem(
        mediaId: String,
        title: String,
        subtitle: String
    ): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createPlayableMediaItem(article: ArticleEntity): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(article.id)
            .setTitle(article.title)
            .setSubtitle(article.source)
            .setDescription(article.summary)
            .setIconUri(if (article.imageUrl.isNotEmpty()) Uri.parse(article.imageUrl) else null)
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        android.util.Log.d("Service", "onDestroy() called")

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        abandonAudioFocus()

        mediaSession.isActive = false
        mediaSession.release()

        serviceScope.cancel()
    }
}