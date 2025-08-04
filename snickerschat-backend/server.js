const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Initialize Firebase Admin SDK
try {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_KEY);
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: "https://snickerschat-4-default-rtdb.europe-west1.firebasedatabase.app"
    });
    console.log('Firebase Admin SDK initialized successfully');
} catch (error) {
    console.error('Error initializing Firebase Admin SDK:', error);
}

// Health check endpoint
app.get('/', (req, res) => {
    res.json({ 
        message: 'SnickersChat Backend is running!',
        timestamp: new Date().toISOString()
    });
});

// Send push notification
app.post('/api/send-notification', async (req, res) => {
    try {
        const { receiverId, senderId, senderName, message, chatRoomId } = req.body;
        
        console.log('Received notification request:', { receiverId, senderId, senderName, message, chatRoomId });
        
        // Get receiver's FCM token from Firestore
        const db = admin.firestore();
        const userDoc = await db.collection('users').doc(receiverId).get();
        
        if (!userDoc.exists) {
            console.log('User not found:', receiverId);
            return res.status(404).json({ success: false, message: 'User not found' });
        }
        
        const userData = userDoc.data();
        const fcmToken = userData.fcmToken;
        
        if (!fcmToken) {
            console.log('No FCM token found for user:', receiverId);
            return res.status(404).json({ success: false, message: 'FCM token not found' });
        }
        
        // Prepare notification message
        const notificationMessage = {
            notification: {
                title: senderName,
                body: message
            },
            data: {
                chatRoomId: chatRoomId,
                senderId: senderId,
                click_action: 'FLUTTER_NOTIFICATION_CLICK'
            },
            token: fcmToken
        };
        
        // Send notification
        const response = await admin.messaging().send(notificationMessage);
        console.log('Successfully sent notification:', response);
        
        res.json({
            success: true,
            messageId: response,
            message: 'Notification sent successfully'
        });
        
    } catch (error) {
        console.error('Error sending notification:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to send notification',
            error: error.message
        });
    }
});

// Get user's FCM token
app.get('/api/user/:userId/token', async (req, res) => {
    try {
        const { userId } = req.params;
        const db = admin.firestore();
        const userDoc = await db.collection('users').doc(userId).get();
        
        if (!userDoc.exists) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }
        
        const userData = userDoc.data();
        res.json({
            userId: userId,
            fcmToken: userData.fcmToken || null
        });
        
    } catch (error) {
        console.error('Error getting user token:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to get user token',
            error: error.message
        });
    }
});

// Update user's FCM token
app.post('/api/user/:userId/token', async (req, res) => {
    try {
        const { userId } = req.params;
        const { fcmToken } = req.body;
        
        const db = admin.firestore();
        await db.collection('users').doc(userId).update({
            fcmToken: fcmToken,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        
        res.json({
            success: true,
            message: 'FCM token updated successfully'
        });
        
    } catch (error) {
        console.error('Error updating user token:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to update user token',
            error: error.message
        });
    }
});

// Check for app updates
app.get('/api/app/version', (req, res) => {
    try {
        // Current app version from package.json
        const currentVersion = "1.0.0";
        
        // Latest version info (you'll update this when releasing new versions)
        const latestVersion = {
            version: "1.0.1",
            versionCode: 2,
            downloadUrl: "https://github.com/selimqueengh-afk/SnickersChatv4/releases/latest/download/app-release.apk",
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

// Start server
app.listen(PORT, () => {
    console.log(`SnickersChat Backend running on port ${PORT}`);
    console.log(`Health check: http://localhost:${PORT}/`);
});