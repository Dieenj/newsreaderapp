package com.example.newsreader

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.newsreader.data.RssFeedManager
import com.example.newsreader.database.ArticleEntity
import com.example.newsreader.database.ArticleRepository
import com.example.newsreader.database.NewsDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var sourceNameText: TextView
    private lateinit var selectSourceButton: Button
    private lateinit var refreshButton: ImageButton
    private lateinit var emptyView: TextView
    private lateinit var adapter: ArticleAdapter
    private lateinit var repository: ArticleRepository
    private val feedManager = RssFeedManager()

    private var currentSource: String? = null
    private var currentCategory: String? = null
    
    // MediaBrowser và MediaController để điều khiển TTS
    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var currentReadingArticleId: String? = null
    private var isPlaying = false
    
    // SharedPreferences để lưu nguồn đã chọn
    private val prefs by lazy { 
        getSharedPreferences("NewsReaderPrefs", MODE_PRIVATE) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Khởi tạo database và repository
        val database = NewsDatabase.getDatabase(this)
        repository = ArticleRepository(database.articleDao())

        // Setup UI
        recyclerView = findViewById(R.id.recyclerView)
        sourceNameText = findViewById(R.id.textSourceName)
        selectSourceButton = findViewById(R.id.btnSelectSource)
        refreshButton = findViewById(R.id.btnRefresh)
        emptyView = findViewById(R.id.textEmpty)

        setupRecyclerView()
        setupButtons()
        loadArticles()
        
        // Kết nối với MediaBrowserService
        connectMediaBrowser()

        // Load lại nguồn đã chọn trước đó (auto-load)
        val savedSource = prefs.getString("last_source", null)
        val savedCategory = prefs.getString("last_category", null)
        if (savedSource != null && savedCategory != null) {
            currentSource = savedSource
            currentCategory = savedCategory
            sourceNameText.text = savedSource
            selectSourceButton.text = savedCategory
            refreshButton.visibility = View.VISIBLE
            // Auto-load tin từ nguồn và category đã lưu
            loadCategory(savedSource, savedCategory)
        } else {
            // Hiển thị dialog chọn nguồn nếu chưa có lưu
            showSourceSelectionDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = ArticleAdapter(
            onItemClick = { article ->
                // Click vào card để đọc/dừng
                startReading(article)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Thêm scroll listener để disable click khi đang scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                // Disable click khi đang scroll, enable khi dừng
                val clickable = newState == RecyclerView.SCROLL_STATE_IDLE
                adapter.setClickable(clickable)
            }
        })
    }

    private fun setupButtons() {
        // Click vào tên trang báo để đổi nguồn
        sourceNameText.setOnClickListener {
            // Dừng đọc nếu đang đọc
            if (isPlaying) {
                mediaController?.transportControls?.stop()
                currentReadingArticleId = null
            }
            showSourceSelectionDialog()
        }
        
        // Click vào nút chủ đề để đổi category
        selectSourceButton.setOnClickListener {
            // Dừng đọc nếu đang đọc
            if (isPlaying) {
                mediaController?.transportControls?.stop()
                currentReadingArticleId = null
            }
            if (currentSource != null) {
                showCategorySelectionDialog(currentSource!!)
            } else {
                showSourceSelectionDialog()
            }
        }
        
        refreshButton.setOnClickListener {
            if (currentSource != null && currentCategory != null) {
                // Dừng đọc nếu đang đọc
                if (isPlaying) {
                    mediaController?.transportControls?.stop()
                    currentReadingArticleId = null
                }
                loadCategory(currentSource!!, currentCategory!!)
                // Scroll lên đầu danh sách
                recyclerView.scrollToPosition(0)
            }
        }
    }
    
    /**
     * Kết nối với MediaBrowserService để điều khiển TTS
     */
    private fun connectMediaBrowser() {
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, NewsReaderAutoService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    mediaBrowser?.sessionToken?.let { token ->
                        mediaController = MediaControllerCompat(this@MainActivity, token)
                        MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
                        
                        // Lắng nghe thay đổi trạng thái playback
                        mediaController?.registerCallback(object : MediaControllerCompat.Callback() {
                            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                                isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
                                // Cập nhật UI adapter
                                adapter.updatePlayingState(currentReadingArticleId, isPlaying)
                            }
                        })
                    }
                }
                
                override fun onConnectionFailed() {
                    Toast.makeText(
                        this@MainActivity,
                        "Không thể kết nối với dịch vụ đọc báo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            null
        )
        mediaBrowser?.connect()
    }

    private fun loadArticles() {
        lifecycleScope.launch {
            repository.allArticles.collectLatest { articles ->
                if (articles.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(articles)
                }
            }
        }
    }

    /**
     * Dialog chọn nguồn tin
     */
    private fun showSourceSelectionDialog() {
        val sources = arrayOf(
            "VnExpress",
            "Tuổi Trẻ",
            "Thanh Niên",
            "Dân Trí",
            "Zing News",
            "BBC",
            "The Guardian",
            "NY Times"
        )

        AlertDialog.Builder(this)
            .setTitle("Chọn nguồn tin")
            .setItems(sources) { _, which ->
                val sourceName = sources[which]
                // Hiển thị dialog chọn category
                showCategorySelectionDialog(sourceName)
            }
            .setCancelable(currentSource != null && currentCategory != null)
            .show()
    }
    
    /**
     * Dialog chọn chủ đề
     */
    private fun showCategorySelectionDialog(sourceName: String) {
        val categories = RssFeedManager.NEWS_SOURCES[sourceName]?.keys?.toList() ?: emptyList()
        
        if (categories.isEmpty()) {
            Toast.makeText(this, "⚠️ Không có chủ đề cho nguồn này", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Chọn chủ đề - $sourceName")
            .setItems(categories.toTypedArray()) { _, which ->
                val categoryName = categories[which]
                loadCategory(sourceName, categoryName)
            }
            .setNegativeButton("Quay lại") { _, _ ->
                showSourceSelectionDialog()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Load tin từ một category cụ thể
     */
    private fun loadCategory(sourceName: String, categoryName: String) {
        lifecycleScope.launch {
            try {
                // Cập nhật UI
                currentSource = sourceName
                currentCategory = categoryName
                sourceNameText.text = sourceName
                selectSourceButton.text = categoryName
                refreshButton.visibility = View.VISIBLE
                
                // Scroll về đầu danh sách
                recyclerView.scrollToPosition(0)
                
                // Lưu nguồn và category đã chọn
                prefs.edit()
                    .putString("last_source", sourceName)
                    .putString("last_category", categoryName)
                    .apply()

                // XÓA HẾT tin cũ trong database
                repository.deleteAllArticles()

                // Fetch tin mới từ category được chọn
                val articles = feedManager.fetchFeedsByCategory(sourceName, categoryName)

                if (articles.isNotEmpty()) {
                    // Lưu vào database
                    repository.insertArticles(articles)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startReading(article: ArticleEntity) {
        // Kiểm tra MediaController đã connect chưa
        if (mediaController == null) {
            Toast.makeText(
                this,
                "Đang kết nối dịch vụ đọc báo, vui lòng thử lại",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            repository.markAsRead(article.id)
        }

        // Kiểm tra nếu đang đọc bài này
        if (currentReadingArticleId == article.id && isPlaying) {
            // Dừng đọc
            mediaController?.transportControls?.pause()
            currentReadingArticleId = null
            Toast.makeText(
                this,
                "Đã dừng đọc",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Dừng bài cũ (nếu đang đọc) và bắt đầu đọc bài mới
            if (isPlaying) {
                mediaController?.transportControls?.stop()
            }
            
            currentReadingArticleId = article.id
            
            // Gửi lệnh đọc bài này tới Service
            mediaController?.transportControls?.playFromMediaId(article.id, null)
            
            Toast.makeText(
                this,
                "Bắt đầu đọc: ${article.title}",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Cập nhật UI
        adapter.updatePlayingState(currentReadingArticleId, true)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser?.disconnect()
        mediaController?.unregisterCallback(object : MediaControllerCompat.Callback() {})
    }
}

/**
 * Adapter hiển thị danh sách bài báo
 */
class ArticleAdapter(
    private val onItemClick: (ArticleEntity) -> Unit  // Click card để đọc/dừng
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {

    private var articles = listOf<ArticleEntity>()
    private var currentPlayingId: String? = null
    private var isCurrentlyPlaying = false
    private var isClickable = true  // Flag để control click

    fun submitList(newArticles: List<ArticleEntity>) {
        articles = newArticles
        notifyDataSetChanged()
    }
    
    fun setClickable(clickable: Boolean) {
        isClickable = clickable
    }
    
    fun updatePlayingState(playingArticleId: String?, playing: Boolean) {
        val oldPlayingId = currentPlayingId
        currentPlayingId = playingArticleId
        isCurrentlyPlaying = playing
        
        // Cập nhật item cũ và item mới
        articles.forEachIndexed { index, article ->
            if (article.id == oldPlayingId || article.id == playingArticleId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        return ArticleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount() = articles.size

    inner class ArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageArticle)
        private val titleView: TextView = itemView.findViewById(R.id.textTitle)
        private val summaryView: TextView = itemView.findViewById(R.id.textSummary)
        private val sourceView: TextView = itemView.findViewById(R.id.textSource)

        fun bind(article: ArticleEntity) {
            titleView.text = article.title
            summaryView.text = article.summary
            sourceView.text = article.source
            
            // Kiểm tra xem bài này có đang được đọc không
            val isThisArticlePlaying = (article.id == currentPlayingId && isCurrentlyPlaying)
            
            // Thay đổi màu và style khi đang đọc
            if (isThisArticlePlaying) {
                // Đang đọc: background xanh nhạt, tiêu đề xanh đậm
                itemView.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9")) // Xanh nhạt
                titleView.setTextColor(android.graphics.Color.parseColor("#2E7D32")) // Xanh đậm
                titleView.text = "${article.title}" // Thêm icon speaker
            } else {
                // Bình thường: background trắng, tiêu đề đen
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                titleView.setTextColor(android.graphics.Color.parseColor("#212121"))
            }

            // Load image
            if (article.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(article.imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            // Bài đã đọc sẽ mờ đi
            itemView.alpha = if (article.isRead) 0.6f else 1.0f

            // Click card để đọc/dừng - chỉ hoạt động khi không scroll
            itemView.setOnClickListener { 
                if (isClickable) {
                    onItemClick(article)
                }
            }
        }
    }
}