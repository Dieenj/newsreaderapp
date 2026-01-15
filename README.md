<!-- cspell:disable -->
# 📰 Ứng dụng Đọc Báo Android Auto với RSS Parser

Ứng dụng Android đọc báo tự động hỗ trợ Android Auto - Tích hợp RSS Parser để lấy tin tức từ các trang báo Việt Nam, chuyển thành audio và phát trên màn hình ô tô.

## ✨ Tính năng chính

### 📱 Ứng dụng Mobile
- ✅ **RSS Feed Parser**: Parse XML từ RSS feeds của các trang báo VN
- ✅ **Multi-source Support**: Hỗ trợ VnExpress, Tuổi Trẻ, Thanh Niên, Dân Trí, Zing News
- ✅ **Tự động trích xuất**: Title, Content, Image, PubDate từ RSS
- ✅ **Offline Storage**: Lưu bài báo với Room Database
- ✅ **Smart Categorization**: Tự động phân loại theo nguồn
- ✅ **Đánh dấu đã đọc/yêu thích**
- ✅ **Custom RSS Feed**: Thêm bất kỳ RSS feed nào

### 🚗 Android Auto
- ✅ Hiển thị danh sách bài báo trên màn hình ô tô
- ✅ Text-to-Speech tiếng Việt
- ✅ Điều khiển phát/dừng/tiếp theo/trước đó
- ✅ Hiển thị metadata (tiêu đề, nguồn, ảnh)
- ✅ MediaSession cho điều khiển từ vô lăng

## 🛠️ Cài đặt

### 1. Dependencies

```gradle
dependencies {
    // Core Android
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Lifecycle & Coroutines
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // Media & Android Auto
    implementation 'androidx.media:media:1.7.0'
    
    // Room Database
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    
    // Networking & Web Scraping (dự phòng)
    implementation 'org.jsoup:jsoup:1.17.2'
    
    // Image Loading
    implementation 'com.github.bumptech.glide:glide:4.16.0'
}
```

### 2. Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## 📖 Cấu trúc RSS Parser

### RssParser.kt
Parser chính sử dụng **XmlPullParser** - chuẩn của Android để parse XML:

```kotlin
class RssParser {
    fun parse(inputStream: InputStream): RssParseResult {
        // Parse RSS 2.0 standard
        // Hỗ trợ: title, link, description, pubDate, 
        //         category, guid, author, enclosure, media:content
    }
}
```

**Các thành phần được parse:**
- `<title>` - Tiêu đề bài báo
- `<link>` - URL bài báo gốc
- `<description>` - Mô tả/nội dung (có thể chứa HTML)
- `<pubDate>` - Ngày xuất bản
- `<category>` - Chuyên mục
- `<guid>` - Unique ID
- `<author>` hoặc `<dc:creator>` - Tác giả
- `<enclosure>` - Ảnh đính kèm
- `<media:content>` / `<media:thumbnail>` - Media tags

### RssFeedManager.kt
Quản lý việc fetch và xử lý RSS feeds:

```kotlin
class RssFeedManager {
    suspend fun fetchRssFeed(feedUrl: String): List<ArticleEntity>
    suspend fun fetchMultipleFeeds(feedUrls: List<String>): List<ArticleEntity>
    suspend fun fetchFeedsBySource(sourceName: String): List<ArticleEntity>
}
```

**Built-in RSS Feeds:**
```kotlin
val VIETNAMESE_NEWS_FEEDS = listOf(
    // VnExpress
    NewsFeed("VnExpress", "https://vnexpress.net/rss/tin-moi-nhat.rss"),
    NewsFeed("VnExpress - Thời sự", "https://vnexpress.net/rss/thoi-su.rss"),
    NewsFeed("VnExpress - Công nghệ", "https://vnexpress.net/rss/so-hoa.rss"),
    
    // Tuổi Trẻ
    NewsFeed("Tuổi Trẻ", "https://tuoitre.vn/rss/tin-moi-nhat.rss"),
    NewsFeed("Tuổi Trẻ - Thời sự", "https://tuoitre.vn/rss/thoi-su.rss"),
    
    // Thanh Niên
    NewsFeed("Thanh Niên", "https://thanhnien.vn/rss/home.rss"),
    
    // Dân Trí
    NewsFeed("Dân Trí", "https://dantri.com.vn/rss/trangchinh.rss"),
    
    // Zing News
    NewsFeed("Zing News", "https://zingnews.vn/rss"),
    
    // VietnamNet
    NewsFeed("VietnamNet", "https://vietnamnet.vn/rss/tin-moi-nhat.rss"),
)
```

## 🔄 Flow hoạt động

### 1. Fetch RSS Feed
```
User nhấn "Làm mới" → RssFeedManager.fetchMultipleFeeds()
  ↓
Mở kết nối HTTP → Lấy XML InputStream
  ↓
RssParser.parse(inputStream) → RssParseResult
  ↓
Convert RssItems → ArticleEntities
  ↓
ArticleRepository.insertArticles() → Room Database
  ↓
Flow<List<ArticleEntity>> → UI update tự động
```

### 2. Parse RSS XML
```xml
<rss version="2.0">
  <channel>
    <title>VnExpress</title>
    <item>
      <title>Tiêu đề bài báo</title>
      <link>https://...</link>
      <description><![CDATA[Nội dung...]]></description>
      <pubDate>Mon, 10 Jan 2026 10:00:00 +0700</pubDate>
      <enclosure url="https://image.jpg" type="image/jpeg"/>
    </item>
  </channel>
</rss>
```

↓ **XmlPullParser** ↓

```kotlin
RssItem(
    title = "Tiêu đề bài báo",
    link = "https://...",
    description = "Nội dung...",
    pubDate = "Mon, 10 Jan 2026 10:00:00 +0700",
    imageUrl = "https://image.jpg"
)
```

↓ **Convert** ↓

```kotlin
ArticleEntity(
    id = UUID,
    title = "Tiêu đề bài báo",
    content = "Nội dung clean (no HTML)",
    summary = "200 ký tự đầu...",
    source = "VnExpress",
    imageUrl = "https://image.jpg",
    publishedDate = 1736478000000L
)
```

## 📖 Hướng dẫn sử dụng

### Trên điện thoại

1. **Làm mới tin tức:**
   - Nhấn "Làm mới" → Tự động fetch từ VnExpress, Tuổi Trẻ, Thanh Niên, Dân Trí, Zing News
   - App sẽ parse RSS và lưu vào database

2. **Chọn nguồn tin cụ thể:**
   - Nhấn "Chọn nguồn"
   - Chọn từ danh sách: VnExpress, Tuổi Trẻ, v.v.
   - Load tất cả feeds từ nguồn đó

3. **Thêm RSS Feed tùy chỉnh:**
   - Nhấn "Thêm RSS"
   - Nhập link RSS (VD: https://vnexpress.net/rss/tin-moi-nhat.rss)
   - App tự động parse và thêm

4. **Đọc bài báo:**
   - Nhấn "🔊 Đọc" → Text-to-Speech đọc nội dung
   - Nhấn vào bài → Mở browser với link gốc

### Trên Android Auto

1. **Kết nối:** USB/Wireless với màn hình ô tô
2. **Chọn app:** "Đọc Báo" trong Android Auto
3. **Điều khiển:**
   - Play/Pause, Next/Previous
   - Sử dụng vô lăng hoặc màn hình

## 🏗️ Cấu trúc Code

```
app/
├── NewsReaderAutoService.kt    # Android Auto MediaBrowserService
├── MainActivity_Updated.kt     # UI với RSS feed selection
├── parser/
│   └── RssParser.kt           # XML Parser (XmlPullParser)
├── data/
│   └── RssFeedManager.kt      # RSS feed manager & fetcher
├── database/
│   └── NewsDatabase.kt        # Room Database, DAO, Repository
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   └── item_article.xml
    └── xml/
        └── automotive_app_desc.xml
```

## 🌐 RSS Feeds hỗ trợ

### VnExpress
- Tin mới nhất: `https://vnexpress.net/rss/tin-moi-nhat.rss`
- Thời sự: `https://vnexpress.net/rss/thoi-su.rss`
- Thế giới: `https://vnexpress.net/rss/the-gioi.rss`
- Kinh doanh: `https://vnexpress.net/rss/kinh-doanh.rss`
- Công nghệ: `https://vnexpress.net/rss/so-hoa.rss`
- Giải trí: `https://vnexpress.net/rss/giai-tri.rss`
- Thể thao: `https://vnexpress.net/rss/the-thao.rss`

### Tuổi Trẻ
- Tin mới nhất: `https://tuoitre.vn/rss/tin-moi-nhat.rss`
- Thời sự: `https://tuoitre.vn/rss/thoi-su.rss`
- Thế giới: `https://tuoitre.vn/rss/the-gioi.rss`
- Công nghệ: `https://tuoitre.vn/rss/cong-nghe.rss`

### Thanh Niên
- Trang chủ: `https://thanhnien.vn/rss/home.rss`
- Thời sự: `https://thanhnien.vn/rss/thoi-su.rss`
- Công nghệ: `https://thanhnien.vn/rss/cong-nghe.rss`

### Dân Trí
- Trang chính: `https://dantri.com.vn/rss/trangchinh.rss`
- Xã hội: `https://dantri.com.vn/rss/xa-hoi.rss`
- Sức mạnh số: `https://dantri.com.vn/rss/suc-manh-so.rss`

### Zing News
- Trang chủ: `https://zingnews.vn/rss`
- Xã hội: `https://zingnews.vn/rss/xa-hoi.rss`
- Công nghệ: `https://zingnews.vn/rss/cong-nghe.rss`

## 🔧 Kỹ thuật parse RSS

### XmlPullParser Events
```kotlin
XmlPullParser.START_TAG    → Bắt đầu một tag (<item>)
XmlPullParser.TEXT         → Nội dung text
XmlPullParser.END_TAG      → Kết thúc tag (</item>)
XmlPullParser.END_DOCUMENT → Hết document
```

### Parse Flow
```kotlin
while (eventType != XmlPullParser.END_DOCUMENT) {
    when (eventType) {
        START_TAG -> {
            if (tagName == "item") { 
                insideItem = true 
            }
        }
        TEXT -> { 
            text = parser.text 
        }
        END_TAG -> {
            if (insideItem && tagName == "title") {
                rssItem.title = text
            }
        }
    }
    eventType = parser.next()
}
```

### HTML Cleaning
```kotlin
fun stripHtml(html: String): String {
    return html
        .replace("<[^>]*>".toRegex(), "")  // Remove tags
        .replace("&nbsp;", " ")             // HTML entities
        .replace("&amp;", "&")
        .trim()
}
```

## 🚀 Performance

### Async Operations
- Sử dụng Kotlin Coroutines với `Dispatchers.IO`
- Network calls không block UI thread
- Flow cho reactive updates

### Caching
- Room Database cache offline
- Glide cache images
- Parse kết quả được lưu ngay

### Error Handling
```kotlin
try {
    val articles = feedManager.fetchRssFeed(url)
    repository.insertArticles(articles)
} catch (e: Exception) {
    // Show error toast
}
```

## 📝 TODO

- [ ] Background sync service (định kỳ fetch tin mới)
- [ ] Push notification cho tin mới
- [ ] Podcast mode (đọc liên tục)
- [ ] Custom TTS voice/speed controls
- [ ] Export audio file
- [ ] Share article
- [ ] Search trong bài báo
- [ ] Category filters
- [ ] RSS feed management screen
- [ ] OPML import/export

## 🐛 Troubleshooting

### Không load được tin
- Kiểm tra kết nối Internet
- Kiểm tra RSS feed URL còn hoạt động
- Xem Logcat để debug

### Parse lỗi
- Một số trang có RSS format khác nhau
- Thêm xử lý đặc biệt trong RssParser

### Android Auto không hiển thị
- Kiểm tra `automotive_app_desc.xml`
- Enable Developer mode trong Android Auto
- Xem logs: `adb logcat | grep MediaBrowser`

## 📄 License

MIT License - Tự do sử dụng và chỉnh sửa

---

**Lưu ý:** Tuân thủ điều khoản sử dụng của các trang báo khi sử dụng RSS feeds.
"# newsreaderapp" 

# Mở Command Prompt
cd C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools

# Kiểm tra device
adb devices

# Forward port
adb forward tcp:5277 tcp:5277

# Chạy DHU
cd C:\Users\Admin\AppData\Local\Android\Sdk\extras\google\auto
desktop-head-unit.exe