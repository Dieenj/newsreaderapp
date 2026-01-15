package com.example.newsreader.parser

import android.text.Html
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream

/**
 * RSS Parser sử dụng XmlPullParser để parse RSS feeds từ các trang báo Việt Nam
 * Hỗ trợ RSS 2.0 standard
 */
class RssParser {
    
    private val rssItems = ArrayList<RssItem>()
    private var rssItem: RssItem? = null
    private var text: String? = null
    
    /**
     * Parse RSS feed từ InputStream
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream): RssParseResult {
        var channelTitle = ""
        var channelLink = ""
        var channelDescription = ""
        var channelImage = ""
        
        rssItems.clear()
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)
            
            var eventType = parser.eventType
            var insideItem = false
            var insideChannel = true
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when {
                            tagName.equals("item", ignoreCase = true) -> {
                                insideItem = true
                                insideChannel = false
                                rssItem = RssItem()
                            }
                            tagName.equals("channel", ignoreCase = true) -> {
                                insideChannel = true
                            }
                            // Parse image attributes ngay tại START_TAG
                            insideItem -> {
                                when {
                                    // Lấy thumbnail/image
                                    tagName.equals("enclosure", ignoreCase = true) -> {
                                        val url = parser.getAttributeValue(null, "url")
                                        val type = parser.getAttributeValue(null, "type")
                                        // Chỉ lấy nếu là image
                                        if (url != null && url.isNotEmpty() && 
                                            (type == null || type.startsWith("image/"))) {
                                            rssItem?.imageUrl = url
                                        }
                                    }
                                    tagName.equals("media:content", ignoreCase = true) -> {
                                        val url = parser.getAttributeValue(null, "url")
                                        val medium = parser.getAttributeValue(null, "medium")
                                        val type = parser.getAttributeValue(null, "type")
                                        // Lấy nếu là image
                                        if (url != null && url.isNotEmpty() && 
                                            (medium == "image" || type?.startsWith("image/") == true || medium == null)) {
                                            rssItem?.imageUrl = url
                                        }
                                    }
                                    tagName.equals("media:thumbnail", ignoreCase = true) -> {
                                        val url = parser.getAttributeValue(null, "url")
                                        if (url != null && url.isNotEmpty()) {
                                            rssItem?.imageUrl = url
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    XmlPullParser.TEXT -> {
                        text = parser.text
                    }
                    
                    XmlPullParser.END_TAG -> {
                        when {
                            tagName.equals("item", ignoreCase = true) -> {
                                rssItem?.let { 
                                    if (it.title.isNotEmpty()) {
                                        rssItems.add(it) 
                                    }
                                }
                                insideItem = false
                            }
                            
                            // Parse channel metadata
                            insideChannel && !insideItem -> {
                                when {
                                    tagName.equals("title", ignoreCase = true) -> {
                                        channelTitle = text?.trim() ?: ""
                                    }
                                    tagName.equals("link", ignoreCase = true) -> {
                                        channelLink = text?.trim() ?: ""
                                    }
                                    tagName.equals("description", ignoreCase = true) -> {
                                        channelDescription = text?.trim() ?: ""
                                    }
                                    tagName.equals("url", ignoreCase = true) -> {
                                        channelImage = text?.trim() ?: ""
                                    }
                                }
                            }
                            
                            // Parse item content
                            insideItem -> {
                                when {
                                    tagName.equals("title", ignoreCase = true) -> {
                                        val rawTitle = text?.trim() ?: ""
                                        // Decode HTML entities trong title
                                        rssItem?.title = if (rawTitle.contains("&")) {
                                            stripHtml(rawTitle)
                                        } else {
                                            rawTitle
                                        }
                                    }
                                    tagName.equals("link", ignoreCase = true) -> {
                                        rssItem?.link = text?.trim() ?: ""
                                    }
                                    tagName.equals("description", ignoreCase = true) -> {
                                        val desc = text?.trim() ?: ""
                                        rssItem?.description = desc
                                        // Nếu chưa có imageUrl, thử extract từ HTML description
                                        if (rssItem?.imageUrl.isNullOrEmpty()) {
                                            extractImageUrlFromHtml(desc)?.let { imgUrl ->
                                                rssItem?.imageUrl = imgUrl
                                            }
                                        }
                                    }
                                    tagName.equals("pubDate", ignoreCase = true) -> {
                                        rssItem?.pubDate = text?.trim() ?: ""
                                    }
                                    tagName.equals("category", ignoreCase = true) -> {
                                        rssItem?.category = text?.trim() ?: ""
                                    }
                                    tagName.equals("guid", ignoreCase = true) -> {
                                        rssItem?.guid = text?.trim() ?: ""
                                    }
                                    // Hỗ trợ thêm các tag khác
                                    tagName.equals("author", ignoreCase = true) ||
                                    tagName.equals("dc:creator", ignoreCase = true) -> {
                                        rssItem?.author = text?.trim() ?: ""
                                    }
                                    // Thêm hỗ trợ cho image tag (dùng bởi một số RSS feed)
                                    tagName.equals("image", ignoreCase = true) && insideItem -> {
                                        // Có thể là <image>url</image> hoặc tag phức tạp hơn
                                        text?.trim()?.let { url ->
                                            if (url.startsWith("http") && rssItem?.imageUrl.isNullOrEmpty()) {
                                                rssItem?.imageUrl = url
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                eventType = parser.next()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        
        return RssParseResult(
            channelTitle = channelTitle,
            channelLink = channelLink,
            channelDescription = channelDescription,
            channelImage = channelImage,
            items = rssItems
        )
    }
    
    /**
     * Extract text content từ HTML description (loại bỏ tags và decode HTML entities)
     */
    private fun stripHtml(html: String?): String {
        if (html == null) return ""
        // Sử dụng Html.fromHtml để decode HTML entities (đúng cho tiếng Việt)
        val decoded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
        return decoded.toString().trim()
    }
    
    /**
     * Extract image URL từ HTML description
     * Parse thẻ <img src="..."> trong description
     */
    private fun extractImageUrlFromHtml(html: String?): String? {
        if (html.isNullOrEmpty()) return null
        
        // Thử nhiều pattern khác nhau
        val patterns = listOf(
            Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),  // src="..." hoặc src='...'
            Regex("<img[^>]+src=([^\\s>]+)", RegexOption.IGNORE_CASE),           // src=url (không có quotes)
            Regex("https?://[^\\s\"'<>]+\\.(jpg|jpeg|png|gif|webp)", RegexOption.IGNORE_CASE) // URL trực tiếp
        )
        
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val url = match.groups[1]?.value ?: match.value
                // Kiểm tra URL hợp lệ
                if (url.startsWith("http")) {
                    return url
                }
            }
        }
        
        return null
    }
}

/**
 * Data class đại diện cho một RSS item
 */
data class RssItem(
    var title: String = "",
    var link: String = "",
    var description: String = "",
    var pubDate: String = "",
    var category: String = "",
    var guid: String = "",
    var author: String = "",
    var imageUrl: String = ""
) {
    /**
     * Lấy nội dung text sạch (không có HTML tags và decode entities)
     */
    fun getCleanDescription(): String {
        if (description.isEmpty()) return ""
        // Sử dụng Html.fromHtml để decode HTML entities (đúng cho tiếng Việt)
        val decoded = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(description)
        }
        // Loại bỏ các ký tự đặc biệt không mong muốn
        return decoded.toString()
            .replace("\u200B", "") // Zero-width space
            .replace("\u200C", "") // Zero-width non-joiner
            .replace("\u200D", "") // Zero-width joiner
            .replace("\uFEFF", "") // Zero-width no-break space (BOM)
            .replace("�", "")       // Replacement character (box)
            .replace("\u00A0", " ") // Non-breaking space -> space thông thường
            .replace("\\s+".toRegex(), " ") // Nhiều spaces -> 1 space
            .replace("[\\p{So}\\p{Cn}]".toRegex(), "") // Loại bỏ emoji và symbols
            .trim()
    }
    
    /**
     * Lấy summary ngắn (200 ký tự đầu)
     */
    fun getSummary(): String {
        val cleanDesc = getCleanDescription()
        return if (cleanDesc.length > 200) {
            cleanDesc.substring(0, 200) + "..."
        } else {
            cleanDesc
        }
    }
}

/**
 * Kết quả parse RSS feed
 */
data class RssParseResult(
    val channelTitle: String,
    val channelLink: String,
    val channelDescription: String,
    val channelImage: String,
    val items: List<RssItem>
)
