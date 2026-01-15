package com.example.newsreader

import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.*
import java.util.*

class NewsReaderAutoService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private var textToSpeech: TextToSpeech? = null
    private var currentArticle: ArticleEntity? = null
    private var isReading = false

    // Coroutine scope cho Service
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Job hiện tại để có thể cancel
    private var currentFetchJob: kotlinx.coroutines.Job? = null

    // Repository
    private lateinit var repository: ArticleRepository
    
    // Content Fetcher
    private val contentFetcher = ArticleContentFetcher()

    companion object {
        const val MEDIA_ROOT_ID = "news_root"
        const val RECENT_NEWS_ID = "recent_news"
    }

    override fun onCreate() {
        super.onCreate()

        // Khởi tạo Repository
        val database = NewsDatabase.getDatabase(this)
        repository = ArticleRepository(database.articleDao())

        // Khởi tạo MediaSession
        mediaSession = MediaSessionCompat(this, "NewsReaderService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP
                )
            setPlaybackState(stateBuilder.build())

            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)
        }

        // Khởi tạo Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("vi", "VN") // Tiếng Việt
                setupTTSListener()
            }
        }
    }

    private fun setupTTSListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isReading = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onDone(utteranceId: String?) {
                isReading = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }

            override fun onError(utteranceId: String?) {
                isReading = false
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            }
        })
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            currentArticle?.let { article ->
                readArticle(article)
            }
        }

        override fun onPause() {
            textToSpeech?.stop()
            isReading = false
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        }

        override fun onStop() {
            textToSpeech?.stop()
            isReading = false
            currentArticle = null
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }

        override fun onSkipToNext() {
            textToSpeech?.stop()
            loadNextArticle()
        }

        override fun onSkipToPrevious() {
            textToSpeech?.stop()
            loadPreviousArticle()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            // Dừng bài đang đọc trước
            textToSpeech?.stop()
            
            // Load và đọc bài mới
            mediaId?.let {
                loadArticleById(it)
            }
        }
    }

    private fun readArticle(article: ArticleEntity) {
        currentArticle = article
        
        android.util.Log.d("TTS_Service", "readArticle called for: ${article.title}")
        android.util.Log.d("TTS_Service", "Has fullContent: ${article.fullContent != null && article.fullContent?.isNotEmpty() == true}")
        
        // Cancel fetch job cũ nếu còn đang chạy
        currentFetchJob?.cancel()
        android.util.Log.d("TTS_Service", "Cancelled previous fetch job")
        
        // Kiểm tra xem đã có fullContent chưa
        if (article.fullContent != null && article.fullContent.isNotEmpty()) {
            // Đã có full content, đọc luôn
            android.util.Log.d("TTS_Service", "Reading from cached fullContent: ${article.fullContent.length} chars")
            speakArticle(article, article.fullContent)
        } else {
            // Chưa có, fetch từ web
            android.util.Log.d("TTS_Service", "Fetching fullContent from web...")
            currentFetchJob = serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val fullContent = contentFetcher.fetchFullContent(article.url)
                    
                    if (!coroutineContext.isActive) {
                        android.util.Log.d("TTS_Service", "Job cancelled, stopping")
                        return@launch
                    }
                    
                    android.util.Log.d("TTS_Service", "Fetch result: ${fullContent?.length ?: 0} chars")
                    
                    if (fullContent != null && fullContent.isNotEmpty()) {
                        // Lưu vào database
                        repository.updateFullContent(article.id, fullContent)
                        android.util.Log.d("TTS_Service", "Saved to DB, now speaking...")
                        
                        // Chuyển về main thread để gọi TTS
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            speakArticle(article, fullContent)
                        }
                    } else {
                        // Fallback: đọc description
                        android.util.Log.d("TTS_Service", "Fetch failed, using description fallback")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            speakArticle(article, article.content)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    android.util.Log.d("TTS_Service", "Fetch cancelled")
                    throw e // Re-throw để coroutine biết là đã bị cancel
                } catch (e: Exception) {
                    android.util.Log.e("TTS_Service", "Error in fetch", e)
                    e.printStackTrace()
                    // Fallback: đọc description
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        speakArticle(article, article.content)
                    }
                }
            }
        }
    }
    
    private fun speakArticle(article: ArticleEntity, content: String) {
        android.util.Log.d("TTS_Service", "speakArticle called")
        android.util.Log.d("TTS_Service", "Content length: ${content.length}")
        android.util.Log.d("TTS_Service", "TTS initialized: ${textToSpeech != null}")
        
        if (textToSpeech == null) {
            android.util.Log.e("TTS_Service", "TTS is null, cannot speak")
            return
        }
        
        // Kiểm tra TTS đang speaking không
        val isSpeaking = textToSpeech?.isSpeaking ?: false
        android.util.Log.d("TTS_Service", "TTS currently speaking: $isSpeaking")
        
        // Nếu đang đọc, dừng lại trước
        if (isSpeaking) {
            android.util.Log.d("TTS_Service", "Stopping current speech...")
            textToSpeech?.stop()
        }
        
        // Tạo text để đọc (tiêu đề + nội dung)
        val fullText = "${article.title}. $content"
        
        // TTS có giới hạn ~4000 characters, cần chia nhỏ nếu quá dài
        val maxChunkSize = 3500
        
        if (fullText.length <= maxChunkSize) {
            // Text ngắn, đọc trực tiếp
            android.util.Log.d("TTS_Service", "Calling TTS.speak() with ${fullText.length} characters")
            
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, article.id)
            }
            
            val result = textToSpeech?.speak(fullText, TextToSpeech.QUEUE_FLUSH, params, article.id)
            android.util.Log.d("TTS_Service", "TTS.speak() returned: $result (SUCCESS=0, ERROR=-1)")
        } else {
            // Text quá dài, chia thành nhiều chunk
            android.util.Log.d("TTS_Service", "Text too long (${fullText.length} chars), splitting into chunks")
            
            val chunks = mutableListOf<String>()
            var currentIndex = 0
            
            while (currentIndex < fullText.length) {
                val endIndex = minOf(currentIndex + maxChunkSize, fullText.length)
                
                // Tìm dấu câu gần nhất để chia tự nhiên hơn
                var splitIndex = endIndex
                if (endIndex < fullText.length) {
                    val nearbyPunctuation = fullText.substring(endIndex - 100, endIndex)
                        .lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
                    if (nearbyPunctuation != -1) {
                        splitIndex = endIndex - 100 + nearbyPunctuation + 1
                    }
                }
                
                chunks.add(fullText.substring(currentIndex, splitIndex).trim())
                currentIndex = splitIndex
            }
            
            android.util.Log.d("TTS_Service", "Split into ${chunks.size} chunks")
            
            // Đọc chunk đầu tiên
            chunks.forEachIndexed { index, chunk ->
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "${article.id}_chunk_$index")
                }
                
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val result = textToSpeech?.speak(chunk, queueMode, params, "${article.id}_chunk_$index")
                android.util.Log.d("TTS_Service", "Chunk $index: ${chunk.length} chars, result=$result")
            }
        }
        
        isReading = true
        
        // Update playback state to PLAYING
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)

        // Cập nhật metadata với content đang được đọc
        updateMediaMetadata(article, content)
        
        // Đánh dấu bài đã đọc
        serviceScope.launch {
            repository.markAsRead(article.id)
        }
    }

    private fun updateMediaMetadata(article: ArticleEntity, content: String) {
        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, article.title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, article.source)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, article.summary)
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION,
                estimateReadingTime(content))
            .build()

        mediaSession.setMetadata(metadata)
    }

    private fun estimateReadingTime(text: String): Long {
        // Ước tính thời gian đọc (khoảng 150 từ/phút)
        val wordCount = text.split("\\s+".toRegex()).size
        return (wordCount * 60000L / 150)
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, 0, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
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
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        when (parentId) {
            MEDIA_ROOT_ID -> {
                // Danh sách các danh mục
                mediaItems.add(createBrowsableMediaItem(
                    RECENT_NEWS_ID,
                    "Tin mới nhất",
                    "Các tin tức mới nhất"
                ))
                result.sendResult(mediaItems)
            }
            RECENT_NEWS_ID -> {
                // Load danh sách bài báo từ database
                serviceScope.launch {
                    val articles = getRecentArticles()
                    articles.forEach { article ->
                        mediaItems.add(createPlayableMediaItem(article))
                    }
                    result.sendResult(mediaItems)
                }

                // Detach result để không block
                result.detach()
            }
            else -> {
                result.sendResult(mediaItems)
            }
        }
    }

    private suspend fun getRecentArticles(): List<ArticleEntity> = withContext(Dispatchers.IO) {
        // Lấy từ Flow và convert thành List
        try {
            // Giả lập - trong thực tế cần collect từ Flow
            emptyList()
        } catch (e: Exception) {
            emptyList()
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
            .setIconUri(Uri.parse(article.imageUrl))
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        mediaSession.release()
        serviceScope.cancel() // Cancel coroutine scope
    }
}