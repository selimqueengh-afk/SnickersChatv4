// Check for app updates
app.get('/api/app/version', (req, res) => {
    try {
        // Current app version from package.json
        const currentVersion = "1.0.0";
        
        // Latest version info (you'll update this when releasing new versions)
        const latestVersion = {
            version: "1.0.1",
            versionCode: 2,
            downloadUrl: "https://github.com/selimqueengh-afk/SnickersChatv4/releases/download/v1.0.1/app-debug.apk",
            releaseNotes: [
                "🚀 Uygulama içi güncelleme sistemi eklendi",
                "🎨 Şık ve modern güncelleme dialog'u",
                "📥 Otomatik APK indirme ve kurulum",
                "🔗 GitHub Releases entegrasyonu",
                "🐛 Push notification sistemi iyileştirildi",
                "⚡ Performans optimizasyonları"
            ],
            isForceUpdate: false,
            minVersion: "1.0.0"
        };
        
        res.json({
            success: true,
            currentVersion: currentVersion,
            latestVersion: latestVersion
        });
        
    } catch (error) {
        console.error('Error checking app version:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to check app version',
            error: error.message
        });
    }
});