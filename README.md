# 📱 SnickersChatv4 - Olağanüstü Şık Kotlin Android Sohbet Uygulaması

Modern, şık tasarımlı ve Firebase Firestore tabanlı Android sohbet uygulaması. Kotlin ve Jetpack Compose ile geliştirilmiştir.

## ✨ Özellikler

### 🎨 Tasarım ve Animasyon
- **Material 3** tabanlı modern tasarım
- **Pembe ve mor** renk paleti ile şık görünüm
- **Lottie animasyonları** ve geçiş efektleri
- **Neumorphism/Glassmorphism** görsel stil
- **Yuvarlatılmış köşeler** ve pastel renkler
- **Gece-gündüz tema** desteği

### 💬 Mesajlaşma Özellikleri
- **Gerçek zamanlı mesajlaşma** (Firestore)
- **Mesaj durumu** (gönderildi, iletildi, okundu)
- **Mesaj silme** ve kopyalama
- **Tarih/zaman etiketleri**
- **Scroll to bottom** butonu
- **Sadece metin mesajları** (Firebase Storage kullanılmaz)

### 👥 Arkadaş Sistemi
- **Kullanıcı arama** (kullanıcı adına göre)
- **Arkadaşlık istekleri** (gönderme, kabul etme, reddetme)
- **Çevrim içi durumu** ve son görülme
- **Arkadaş listesi** yönetimi

### 🔐 Güvenlik ve Kimlik Doğrulama
- **Firebase Authentication** (anonim ve kullanıcı adı tabanlı)
- **Güvenlik kuralları** (kullanıcı sadece kendi verisini görebilir)
- **Firestore** tabanlı veri yönetimi

## 🛠️ Teknoloji Stack

- **Kotlin** - Programlama dili
- **Jetpack Compose** - Modern UI framework
- **Firebase Firestore** - Gerçek zamanlı veritabanı
- **Firebase Authentication** - Kimlik doğrulama
- **MVVM** - Mimari pattern
- **Coroutines** - Asenkron işlemler
- **Material 3** - Tasarım sistemi
- **Lottie** - Animasyonlar

## 📦 Kurulum

### Gereksinimler
- Android Studio Arctic Fox veya üzeri
- Kotlin 1.9.10
- Android SDK 24+
- Firebase projesi

### Adımlar

1. **Projeyi klonlayın**
   ```bash
   git clone https://github.com/yourusername/SnickersChatv4.git
   cd SnickersChatv4
   ```

2. **Firebase projesi oluşturun**
   - [Firebase Console](https://console.firebase.google.com/)'a gidin
   - Yeni proje oluşturun
   - Android uygulaması ekleyin
   - `google-services.json` dosyasını indirin

3. **Firebase konfigürasyonu**
   - İndirdiğiniz `google-services.json` dosyasını `app/` klasörüne kopyalayın
   - Firebase Console'da Firestore Database'i etkinleştirin
   - Authentication'da Anonim girişi etkinleştirin

4. **Projeyi derleyin**
   ```bash
   ./gradlew build
   ```

5. **Uygulamayı çalıştırın**
   ```bash
   ./gradlew installDebug
   ```

## 🏗️ Proje Yapısı

```
app/src/main/java/com/snickerschat/app/
├── data/
│   ├── model/          # Veri modelleri
│   └── repository/     # Firebase repository
├── ui/
│   ├── screens/        # UI ekranları
│   ├── state/          # UI state'leri
│   ├── theme/          # Tema ve renkler
│   └── viewmodel/      # ViewModel'ler
└── MainActivity.kt     # Ana aktivite
```

## 🔥 Firebase Firestore Koleksiyonları

- `users` - Kullanıcı bilgileri
- `messages` - Mesajlar
- `chat_rooms` - Sohbet odaları
- `requests` - Arkadaşlık istekleri

## 🤖 GitHub Actions

Proje, GitHub Actions ile otomatik APK build'i destekler:

- **Otomatik build** - Her push'ta
- **APK artifact** - Release APK'sı otomatik oluşturulur
- **Java 17** - Modern JDK kullanımı

## 🎯 Özellikler Detayı

### Giriş Ekranı
- Şık animasyonlu logo
- Kullanıcı adı ile giriş
- Anonim giriş seçeneği
- Gradient arka plan

### Ana Ekran
- Bottom navigation
- Sohbet listesi
- Arkadaş yönetimi
- Ayarlar

### Sohbet Ekranı
- Gerçek zamanlı mesajlaşma
- Mesaj durumu göstergeleri
- Scroll to bottom
- Modern mesaj tasarımı

## 📱 Ekran Görüntüleri

*Ekran görüntüleri buraya eklenecek*

## 🤝 Katkıda Bulunma

1. Fork yapın
2. Feature branch oluşturun (`git checkout -b feature/AmazingFeature`)
3. Commit yapın (`git commit -m 'Add some AmazingFeature'`)
4. Push yapın (`git push origin feature/AmazingFeature`)
5. Pull Request oluşturun

## 📄 Lisans

Bu proje MIT lisansı altında lisanslanmıştır. Detaylar için `LICENSE` dosyasına bakın.

## 🙏 Teşekkürler

- [Firebase](https://firebase.google.com/) - Backend servisleri
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI
- [Material Design](https://material.io/) - Tasarım sistemi
- [Lottie](https://lottiefiles.com/) - Animasyonlar

## 📞 İletişim

- **Proje Linki**: [https://github.com/yourusername/SnickersChatv4](https://github.com/yourusername/SnickersChatv4)

---

⭐ Bu projeyi beğendiyseniz yıldız vermeyi unutmayın!