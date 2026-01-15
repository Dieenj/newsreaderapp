# 📱 Hướng dẫn cài đặt NewsReaderApp

## 🔧 Yêu cầu

- **Android Studio**: Hedgehog (2023.1.1) trở lên
- **JDK**: 17 trở lên
- **Android SDK**: API 23 (Android 6.0) trở lên
- **Gradle**: 8.2.0

## 📥 Cài đặt

### Bước 1: Giải nén dự án

```bash
unzip NewsReaderApp.zip
cd NewsReaderApp
```

### Bước 2: Mở trong Android Studio

1. Mở Android Studio
2. Chọn **File → Open**
3. Chọn folder `NewsReaderApp`
4. Chờ Gradle sync hoàn tất

### Bước 3: Cấu hình SDK

1. **File → Project Structure**
2. Chọn **SDK Location**
3. Đảm bảo Android SDK đã được cài đặt
4. Minimum SDK: API 23
5. Target SDK: API 34

### Bước 4: Sync Gradle

```bash
# Trong Android Studio, click
File → Sync Project with Gradle Files
```

Hoặc chạy từ terminal:
```bash
./gradlew build
```

### Bước 5: Chạy ứng dụng

1. Kết nối thiết bị Android hoặc tạo AVD (Android Virtual Device)
2. Click **Run** (▶️) hoặc nhấn `Shift + F10`
3. Chọn thiết bị để chạy

## 🔑 Cấu hình quan trọng

### Internet Permission
Đã được thêm trong `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### Android Auto
Để test Android Auto:

1. **Cài đặt Android Auto trên điện thoại**
2. **Enable Developer Mode**:
   - Mở Android Auto
   - Nhấn vào biểu tượng hamburger (≡)
   - Scroll xuống "About"
   - Nhấn vào version 10 lần
   - Developer settings sẽ xuất hiện

3. **Desktop Head Unit (DHU)** - Test trên máy tính:
```bash
# Install DHU
sdkmanager "platform-tools" "extras;google;auto"

# Run DHU
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

## 📂 Cấu trúc thư mục

```
NewsReaderApp/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/newsreader/
│   │       │   ├── MainActivity.kt
│   │       │   ├── NewsReaderAutoService.kt
│   │       │   ├── parser/
│   │       │   │   └── RssParser.kt
│   │       │   ├── data/
│   │       │   │   └── RssFeedManager.kt
│   │       │   ├── database/
│   │       │   │   └── NewsDatabase.kt
│   │       │   └── utils/
│   │       │       └── WebContentExtractor.kt
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   ├── activity_main.xml
│   │       │   │   ├── item_article.xml
│   │       │   │   └── dialog_add_url.xml
│   │       │   ├── values/
│   │       │   │   └── strings.xml
│   │       │   └── xml/
│   │       │       └── automotive_app_desc.xml
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## 🐛 Troubleshooting

### Gradle Sync Failed
```bash
# Clear Gradle cache
./gradlew clean
./gradlew build --refresh-dependencies
```

Hoặc trong Android Studio:
```
File → Invalidate Caches / Restart
```

### Dependency Issues
Kiểm tra `app/build.gradle`:
- Kotlin version: 1.9.20
- Compile SDK: 34
- Target SDK: 34
- Min SDK: 23

### Cannot resolve symbol
1. File → Sync Project with Gradle Files
2. Build → Clean Project
3. Build → Rebuild Project

### App crashes on launch
Kiểm tra Logcat:
```bash
adb logcat | grep NewsReader
```

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumentation Tests
```bash
./gradlew connectedAndroidTest
```

### Manual Testing

1. **Test RSS Parser:**
   - Nhấn "Làm mới"
   - Kiểm tra tin tức được load

2. **Test nguồn tin:**
   - Nhấn "Chọn nguồn"
   - Chọn VnExpress, Tuổi Trẻ, v.v.

3. **Test custom feed:**
   - Nhấn "Thêm RSS"
   - Nhập: `https://vnexpress.net/rss/tin-moi-nhat.rss`

4. **Test TTS:**
   - Nhấn "🔊 Đọc" trên bất kỳ bài nào

## 📱 Build APK

### Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK (signed)
1. Tạo keystore:
```bash
keytool -genkey -v -keystore newsreader.jks -keyalg RSA -keysize 2048 -validity 10000 -alias newsreader
```

2. Cập nhật `app/build.gradle`:
```gradle
android {
    signingConfigs {
        release {
            storeFile file("newsreader.jks")
            storePassword "your-password"
            keyAlias "newsreader"
            keyPassword "your-password"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

3. Build:
```bash
./gradlew assembleRelease
```

## 🚀 Deploy

### Google Play Store
1. Tạo signed release APK
2. Tạo app listing trên Google Play Console
3. Upload APK
4. Fill in store listing details
5. Submit for review

### Sideload (Test)
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📞 Support

Nếu gặp vấn đề:
1. Kiểm tra logs: `adb logcat`
2. Xem README.md
3. Check GitHub Issues

## ✅ Checklist trước khi chạy

- [ ] Android Studio đã cài đặt
- [ ] JDK 17+ đã cài đặt
- [ ] Android SDK đã setup
- [ ] Internet connection available
- [ ] Thiết bị test đã kết nối hoặc AVD đã tạo
- [ ] Gradle sync thành công
- [ ] Build successful

Chúc bạn code vui vẻ! 🎉
