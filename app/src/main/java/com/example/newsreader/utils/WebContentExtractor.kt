package com.example.newsreader.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

class WebContentExtractor {
    
    /**
     * Lấy nội dung text từ URL
     */
    suspend fun extractContent(url: String): ArticleContent? = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            parseArticle(document, url)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun parseArticle(document: Document, url: String): ArticleContent {
        // Lấy tiêu đề
        val title = extractTitle(document)
        
        // Lấy nội dung chính
        val content = extractMainContent(document)
        
        // Lấy ảnh đại diện
        val imageUrl = extractMainImage(document, url)
        
        // Lấy tóm tắt
        val summary = extractSummary(document, content)
        
        // Lấy nguồn
        val source = extractSource(document, url)
        
        return ArticleContent(
            title = title,
            content = cleanContent(content),
            summary = summary,
            imageUrl = imageUrl,
            source = source,
            url = url
        )
    }
    
    private fun extractTitle(document: Document): String {
        // Thử nhiều selector khác nhau
        return document.select("h1").first()?.text()
            ?: document.select("meta[property=og:title]").attr("content")
            ?: document.select("title").text()
            ?: "Không có tiêu đề"
    }
    
    private fun extractMainContent(document: Document): String {
        // Loại bỏ các phần tử không cần thiết
        document.select("script, style, nav, header, footer, aside, .advertisement, .ads").remove()
        
        // Thử các selector phổ biến cho nội dung bài viết
        val contentSelectors = listOf(
            "article",
            ".article-content",
            ".post-content",
            ".entry-content",
            ".content-detail",
            ".detail-content",
            "main",
            ".main-content"
        )
        
        for (selector in contentSelectors) {
            val content = document.select(selector).first()
            if (content != null && content.text().length > 100) {
                return content.text()
            }
        }
        
        // Fallback: lấy tất cả các thẻ p
        val paragraphs = document.select("p")
        return paragraphs.joinToString("\n\n") { it.text() }
    }
    
    private fun extractMainImage(document: Document, baseUrl: String): String {
        // Thử Open Graph image
        var imageUrl = document.select("meta[property=og:image]").attr("content")
        
        if (imageUrl.isEmpty()) {
            // Thử Twitter card
            imageUrl = document.select("meta[name=twitter:image]").attr("content")
        }
        
        if (imageUrl.isEmpty()) {
            // Thử tìm ảnh đầu tiên trong article
            val img = document.select("article img, .article-content img").first()
            imageUrl = img?.attr("src") ?: ""
        }
        
        // Chuyển đổi relative URL thành absolute URL
        return if (imageUrl.isNotEmpty() && !imageUrl.startsWith("http")) {
            val base = URL(baseUrl)
            URL(base, imageUrl).toString()
        } else {
            imageUrl
        }
    }
    
    private fun extractSummary(document: Document, content: String): String {
        // Thử lấy meta description
        var summary = document.select("meta[name=description]").attr("content")
        
        if (summary.isEmpty()) {
            summary = document.select("meta[property=og:description]").attr("content")
        }
        
        if (summary.isEmpty() && content.isNotEmpty()) {
            // Lấy 200 ký tự đầu của nội dung
            summary = content.take(200) + "..."
        }
        
        return summary
    }
    
    private fun extractSource(document: Document, url: String): String {
        // Lấy tên nguồn từ Open Graph
        var source = document.select("meta[property=og:site_name]").attr("content")
        
        if (source.isEmpty()) {
            // Lấy từ domain
            try {
                val domain = URL(url).host
                source = domain.replace("www.", "").split(".")[0].capitalize()
            } catch (e: Exception) {
                source = "Unknown"
            }
        }
        
        return source
    }
    
    private fun cleanContent(content: String): String {
        return content
            .replace("\\s+".toRegex(), " ") // Loại bỏ khoảng trắng thừa
            .replace("\\n{3,}".toRegex(), "\n\n") // Loại bỏ xuống dòng thừa
            .trim()
    }
    
    /**
     * Lấy danh sách bài viết từ trang chủ
     */
    suspend fun extractArticleLinks(url: String): List<ArticleLink> = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            val links = mutableListOf<ArticleLink>()
            
            // Tìm tất cả các link bài viết
            document.select("a[href]").forEach { element ->
                val href = element.attr("abs:href")
                val title = element.text()
                
                if (href.isNotEmpty() && title.isNotEmpty() && 
                    href.contains(url) && !href.endsWith("/")) {
                    links.add(ArticleLink(href, title))
                }
            }
            
            links.distinctBy { it.url }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

data class ArticleContent(
    val title: String,
    val content: String,
    val summary: String,
    val imageUrl: String,
    val source: String,
    val url: String
)

data class ArticleLink(
    val url: String,
    val title: String
)
