package com.example.newsreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

/**
 * Fetcher để lấy full content từ webpage
 */
class ArticleContentFetcher {
    
    companion object {
        private const val TIMEOUT = 30000 // 30 seconds
        private const val MAX_RETRIES = 2
        
        // CSS Selectors cho từng trang báo
        private val CONTENT_SELECTORS = mapOf(
            "vnexpress.net" to "article.fck_detail p.Normal",
            "tuoitre.vn" to "div.detail-content p",
            "thanhnien.vn" to "div.detail-content-body p",
            "dantri.com.vn" to "article.singular-content p",
            "zingnews.vn" to "div.the-article-body p",
            "znews.vn" to "div.the-article-body p",
            "vietnamnet.vn" to "div.maincontent p",
            "baomoi.com" to "div.article__body p"
        )
    }
    
    /**
     * Fetch full content từ URL
     */
    suspend fun fetchFullContent(url: String): String? = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        for (attempt in 0 until MAX_RETRIES) {
            try {
                android.util.Log.d("ContentFetcher", "Fetching content from: $url (attempt ${attempt + 1})")
                
                val doc = Jsoup.connect(url)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .get()
                
                // Xác định nguồn từ URL
                val source = extractSourceFromUrl(url)
                android.util.Log.d("ContentFetcher", "Detected source: $source")
                
                // Lấy selector phù hợp
                val selector = CONTENT_SELECTORS[source]
                
                if (selector != null) {
                    // Extract paragraphs
                    val paragraphs = doc.select(selector)
                    android.util.Log.d("ContentFetcher", "Found ${paragraphs.size} paragraphs with selector: $selector")
                    
                    if (paragraphs.isNotEmpty()) {
                        // Ghép các đoạn văn lại
                        val content = paragraphs
                            .map { it.text().trim() }
                            .filter { it.isNotEmpty() && it.length > 20 } // Lọc đoạn quá ngắn
                            .joinToString("\n\n")
                        
                        if (content.length >= 100) {
                            android.util.Log.d("ContentFetcher", "Extracted ${content.length} characters")
                            return@withContext content
                        }
                    }
                }
                
                // Fallback: thử các selector khác
                android.util.Log.d("ContentFetcher", "Primary selector failed, trying fallback")
                val fallbackContent = fallbackExtract(doc)
                if (fallbackContent != null) {
                    return@withContext fallbackContent
                }
                
                // Nếu không có content, thử lại
                if (attempt < MAX_RETRIES - 1) {
                    android.util.Log.d("ContentFetcher", "No content found, will retry...")
                    kotlinx.coroutines.delay(1000) // Đợi 1s trước khi retry
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("ContentFetcher", "Timeout fetching content (attempt ${attempt + 1})", e)
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(2000) // Đợi 2s trước khi retry timeout
                }
            } catch (e: java.io.IOException) {
                android.util.Log.e("ContentFetcher", "Network error fetching content (attempt ${attempt + 1})", e)
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(1000)
                }
            } catch (e: Exception) {
                android.util.Log.e("ContentFetcher", "Error fetching content (attempt ${attempt + 1})", e)
                lastException = e
                // Không retry với các lỗi khác
                return@withContext null
            }
        }
        
        android.util.Log.e("ContentFetcher", "Failed to fetch content after $MAX_RETRIES attempts", lastException)
        return@withContext null
    }
    
    /**
     * Extract source domain từ URL
     */
    private fun extractSourceFromUrl(url: String): String {
        return try {
            val host = URL(url).host.lowercase()
            CONTENT_SELECTORS.keys.find { host.contains(it) } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Fallback extraction khi selector chính thất bại
     */
    private fun fallbackExtract(doc: Document): String? {
        return try {
            // Thử các selector phổ biến
            val fallbackSelectors = listOf(
                "article p",
                "div.content p",
                "div.article-content p",
                "div.post-content p",
                ".entry-content p",
                "div[class*='content'] p",
                "div[class*='article'] p",
                "div[id*='content'] p"
            )
            
            for (selector in fallbackSelectors) {
                val paragraphs = doc.select(selector)
                android.util.Log.d("ContentFetcher", "Fallback trying $selector: ${paragraphs.size} paragraphs")
                
                if (paragraphs.size >= 3) { // Ít nhất 3 đoạn
                    val content = paragraphs
                        .map { it.text().trim() }
                        .filter { it.length > 20 }
                        .joinToString("\n\n")
                    
                    if (content.length > 100) {
                        android.util.Log.d("ContentFetcher", "Fallback success: ${content.length} characters")
                        return content
                    }
                }
            }
            
            android.util.Log.d("ContentFetcher", "All fallback selectors failed")
            null
        } catch (e: Exception) {
            android.util.Log.e("ContentFetcher", "Fallback error", e)
            null
        }
    }
}
