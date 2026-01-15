package com.example.newsreader.data

import com.example.newsreader.database.ArticleEntity
import com.example.newsreader.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager để fetch và quản lý RSS feeds từ các trang báo Việt Nam
 */
class RssFeedManager {
    
    private val rssParser = RssParser()

    companion object {
        // Cấu trúc: Source -> Category -> URL
        val NEWS_SOURCES = mapOf(
            "VnExpress" to mapOf(
                "Tin mới nhất" to "https://vnexpress.net/rss/tin-moi-nhat.rss",
                "Thời sự" to "https://vnexpress.net/rss/thoi-su.rss",
                "Thế giới" to "https://vnexpress.net/rss/the-gioi.rss",
                "Kinh doanh" to "https://vnexpress.net/rss/kinh-doanh.rss",
                "Giải trí" to "https://vnexpress.net/rss/giai-tri.rss",
                "Thể thao" to "https://vnexpress.net/rss/the-thao.rss",
                "Công nghệ" to "https://vnexpress.net/rss/khoa-hoc.rss",
                "Số hóa" to "https://vnexpress.net/rss/so-hoa.rss",
                "Sức khỏe" to "https://vnexpress.net/rss/suc-khoe.rss",
                "Pháp luật" to "https://vnexpress.net/rss/phap-luat.rss",
                "Giáo dục" to "https://vnexpress.net/rss/giao-duc.rss",
                "Du lịch" to "https://vnexpress.net/rss/du-lich.rss",
                "Xe" to "https://vnexpress.net/rss/oto-xe-may.rss"
            ),

            "Tuổi Trẻ" to mapOf(
                "Tin mới nhất" to "https://tuoitre.vn/rss/tin-moi-nhat.rss",
                "Thời sự" to "https://tuoitre.vn/rss/thoi-su.rss",
                "Thế giới" to "https://tuoitre.vn/rss/the-gioi.rss",
                "Kinh doanh" to "https://tuoitre.vn/rss/kinh-doanh.rss",
                "Giải trí" to "https://tuoitre.vn/rss/giai-tri.rss",
                "Thể thao" to "https://tuoitre.vn/rss/the-thao.rss",
                "Công nghệ" to "https://tuoitre.vn/rss/nhip-song-so.rss",
                "Pháp luật" to "https://tuoitre.vn/rss/phap-luat.rss",
                "Giáo dục" to "https://tuoitre.vn/rss/giao-duc.rss",
                "Văn hóa" to "https://tuoitre.vn/rss/van-hoa.rss"
            ),

            "Thanh Niên" to mapOf(
                "Tin mới nhất" to "https://thanhnien.vn/rss/home.rss",
                "Thời sự" to "https://thanhnien.vn/rss/thoi-su.rss",
                "Thế giới" to "https://thanhnien.vn/rss/the-gioi.rss",
                "Kinh doanh" to "https://thanhnien.vn/rss/kinh-doanh.rss",
                "Giải trí" to "https://thanhnien.vn/rss/giai-tri.rss",
                "Thể thao" to "https://thanhnien.vn/rss/the-thao.rss",
                "Công nghệ" to "https://thanhnien.vn/rss/cong-nghe.rss",
                "Giáo dục" to "https://thanhnien.vn/rss/giao-duc.rss",
                "Sức khỏe" to "https://thanhnien.vn/rss/suc-khoe.rss"
            ),

            "Dân Trí" to mapOf(
                "Trang chính" to "https://dantri.com.vn/rss/trangchinh.rss",
                "Xã hội" to "https://dantri.com.vn/rss/xa-hoi.rss",
                "Thế giới" to "https://dantri.com.vn/rss/the-gioi.rss",
                "Kinh doanh" to "https://dantri.com.vn/rss/kinh-doanh.rss",
                "Giải trí" to "https://dantri.com.vn/rss/giai-tri.rss",
                "Thể thao" to "https://dantri.com.vn/rss/the-thao.rss",
                "Công nghệ" to "https://dantri.com.vn/rss/suc-manh-so.rss",
                "Sức khỏe" to "https://dantri.com.vn/rss/suc-khoe.rss",
                "Giáo dục" to "https://dantri.com.vn/rss/giao-duc-huong-nghiep.rss"
            ),

            "Zing News" to mapOf(
                "Trang chính" to "https://zingnews.vn/rss",
                "Thời sự" to "https://zingnews.vn/rss/thoi-su.rss",
                "Xã hội" to "https://zingnews.vn/rss/xa-hoi.rss",
                "Thế giới" to "https://zingnews.vn/rss/the-gioi.rss",
                "Kinh doanh" to "https://zingnews.vn/rss/kinh-doanh-tai-chinh.rss",
                "Công nghệ" to "https://zingnews.vn/rss/cong-nghe.rss",
                "Giải trí" to "https://zingnews.vn/rss/giai-tri.rss",
                "Thể thao" to "https://zingnews.vn/rss/the-thao.rss"
            ),

            "VietnamNet" to mapOf(
                "Trang chính" to "https://vietnamnet.vn/rss/home.rss",
                "Thời sự" to "https://vietnamnet.vn/rss/thoi-su.rss",
                "Thế giới" to "https://vietnamnet.vn/rss/the-gioi.rss",
                "Kinh doanh" to "https://vietnamnet.vn/rss/kinh-doanh.rss",
                "Giải trí" to "https://vietnamnet.vn/rss/giai-tri.rss",
                "Thể thao" to "https://vietnamnet.vn/rss/the-thao.rss",
                "Công nghệ" to "https://vietnamnet.vn/rss/cong-nghe.rss",
                "Giáo dục" to "https://vietnamnet.vn/rss/giao-duc.rss"
            ),

            "Báo Mới" to mapOf(
                "Tin nóng" to "https://baomoi.com/rss/home.rss"
            )
        )
    }


    /**
     * Fetch RSS feed từ URL
     */
    suspend fun fetchRssFeed(feedUrl: String): List<ArticleEntity> = withContext(Dispatchers.IO) {
        try {
            val url = URL(feedUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val inputStream = connection.getInputStream()
            val result = rssParser.parse(inputStream)
            inputStream.close()
            
            // Convert RssItems to ArticleEntities
            result.items.mapNotNull { rssItem ->
                try {
                    ArticleEntity(
                        id = rssItem.guid.ifEmpty { UUID.randomUUID().toString() },
                        title = rssItem.title,
                        content = rssItem.getCleanDescription(),
                        summary = rssItem.getSummary(),
                        source = extractSourceFromUrl(feedUrl),
                        imageUrl = rssItem.imageUrl,
                        url = rssItem.link,
                        publishedDate = parsePubDate(rssItem.pubDate),
                        addedDate = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Fetch nhiều RSS feeds cùng lúc
     */
    suspend fun fetchMultipleFeeds(feedUrls: List<String>): List<ArticleEntity> = withContext(Dispatchers.IO) {
        feedUrls.flatMap { url ->
            try {
                fetchRssFeed(url)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }.sortedByDescending { it.publishedDate }
    }
    
    /**
     * Fetch tất cả feeds từ một nguồn (VD: tất cả feeds VnExpress)
     */
    suspend fun fetchFeedsBySource(sourceName: String): List<ArticleEntity> = withContext(Dispatchers.IO) {
        val categories = NEWS_SOURCES[sourceName] ?: return@withContext emptyList()
        val feedUrls = categories.values.toList()
        fetchMultipleFeeds(feedUrls)
    }
    
    /**
     * Fetch tin từ một category cụ thể của một source
     */
    suspend fun fetchFeedsByCategory(sourceName: String, categoryName: String): List<ArticleEntity> = withContext(Dispatchers.IO) {
        val feedUrl = NEWS_SOURCES[sourceName]?.get(categoryName) ?: return@withContext emptyList()
        fetchRssFeed(feedUrl)
    }
    
    /**
     * Extract tên nguồn từ feed URL
     */
    private fun extractSourceFromUrl(feedUrl: String): String {
        return when {
            feedUrl.contains("vnexpress") -> "VnExpress"
            feedUrl.contains("tuoitre") -> "Tuổi Trẻ"
            feedUrl.contains("thanhnien") -> "Thanh Niên"
            feedUrl.contains("dantri") -> "Dân Trí"
            feedUrl.contains("zingnews") -> "Zing News"
            feedUrl.contains("vietnamnet") -> "VietnamNet"
            feedUrl.contains("baomoi") -> "Báo Mới"
            else -> {
                try {
                    URL(feedUrl).host.split(".")[0].replaceFirstChar { 
                        if (it.isLowerCase()) it.titlecase() else it.toString() 
                    }
                } catch (e: Exception) {
                    "Unknown"
                }
            }
        }
    }
    
    /**
     * Parse pubDate string thành timestamp
     * Hỗ trợ nhiều format date khác nhau
     */
    private fun parsePubDate(pubDate: String): Long {
        if (pubDate.isEmpty()) return System.currentTimeMillis()
        
        val dateFormats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        )
        
        for (format in dateFormats) {
            try {
                return format.parse(pubDate)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        return System.currentTimeMillis()
    }
}

/**
 * Data class đại diện cho một news feed
 */
data class NewsFeed(
    val name: String,
    val url: String
)
