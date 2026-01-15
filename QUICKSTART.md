# 🚀 QUICK START - NewsReaderApp

## ⚡ Khởi động nhanh

### 1️⃣ Giải nén
```bash
unzip NewsReaderApp.zip
cd NewsReaderApp
```

### 2️⃣ Mở trong Android Studio
- File → Open → Chọn folder NewsReaderApp
- Đợi Gradle sync (2-5 phút)

### 3️⃣ Chạy app
- Kết nối điện thoại hoặc tạo emulator
- Click Run ▶️

## 📱 Sử dụng ngay

1. **Nhấn "Làm mới"** → Tải tin từ VnExpress, Tuổi Trẻ, Thanh Niên
2. **Nhấn "Chọn nguồn"** → Chọn báo muốn đọc
3. **Nhấn "🔊 Đọc"** → Nghe TTS đọc bài

## 🚗 Test Android Auto

### Trên điện thoại thật:
1. Cài Android Auto từ Play Store
2. Kết nối USB với màn hình ô tô
3. Chọn app "Đọc Báo"

### Test trên máy tính (DHU):
```bash
# Cài Desktop Head Unit
sdkmanager "extras;google;auto"

# Chạy DHU
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

## 📂 Cấu trúc dự án

```
NewsReaderApp/
├── app/
│   ├── src/main/
│   │   ├── java/.../newsreader/
│   │   │   ├── MainActivity.kt            # UI chính
│   │   │   ├── NewsReaderAutoService.kt   # Android Auto
│   │   │   ├── parser/
│   │   │   │   └── RssParser.kt          # Parse RSS XML
│   │   │   ├── data/
│   │   │   │   └── RssFeedManager.kt     # Fetch feeds
│   │   │   └── database/
│   │   │       └── NewsDatabase.kt       # Room DB
│   │   ├── res/
│   │   │   ├── layout/                   # XML layouts
│   │   │   ├── values/                   # Strings
│   │   │   └── xml/
│   │   │       └── automotive_app_desc.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle                      # Dependencies
├── build.gradle.kts
├── settings.gradle.kts
├── README.md                             # Chi tiết đầy đủ
└── SETUP_GUIDE.md                        # Hướng dẫn chi tiết
```

## ✨ Tính năng

✅ Parse RSS từ 40+ feeds VN  
✅ VnExpress, Tuổi Trẻ, Thanh Niên, Dân Trí, Zing News  
✅ Text-to-Speech tiếng Việt  
✅ Android Auto support  
✅ Offline storage (Room)  
✅ Đánh dấu đã đọc/yêu thích  

## 🐛 Lỗi thường gặp

**Gradle sync failed:**
```bash
./gradlew clean
File → Invalidate Caches / Restart
```

**App crashes:**
```bash
adb logcat | grep NewsReader
```

**Không load được tin:**
- Kiểm tra Internet
- Xem Logcat để debug

## 📖 Tài liệu

- **README.md**: Tài liệu đầy đủ về RSS parsing, Android Auto
- **SETUP_GUIDE.md**: Hướng dẫn cài đặt chi tiết
- Code comments: Giải thích trong từng file

## 🎯 Test nhanh

```kotlin
// Test RSS Parser
val feedManager = RssFeedManager()
val articles = feedManager.fetchRssFeed(
    "https://vnexpress.net/rss/tin-moi-nhat.rss"
)
// articles sẽ chứa list bài báo đã parse

// Test trong app
1. Nhấn "Làm mới"
2. Kiểm tra RecyclerView hiển thị bài
3. Nhấn "🔊 Đọc" để test TTS
```

## 💡 Tips

- Dùng "Chọn nguồn" để đọc từ báo cụ thể
- "Thêm RSS" cho bất kỳ feed nào
- Bài đã đọc sẽ mờ đi (alpha 0.6)
- Click ❤️ để lưu yêu thích
- Click bài để mở browser

## 📞 Hỗ trợ

Mọi thắc mắc, xem:
1. README.md (trong zip)
2. SETUP_GUIDE.md (trong zip)
3. Code comments trong các file .kt

Chúc bạn thành công! 🎉
