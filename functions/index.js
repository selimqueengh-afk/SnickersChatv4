const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp();

// Send push notification when new message is created
exports.sendNotification = functions.firestore
  .document('messages/{messageId}')
  .onCreate(async (snap, context) => {
    try {
      const messageData = snap.data();
      const { senderId, receiverId, content, chatRoomId } = messageData;
      
      console.log('New message created:', { senderId, receiverId, content, chatRoomId });
      
      // Get sender's name
      const senderDoc = await admin.firestore().collection('users').doc(senderId).get();
      const senderName = senderDoc.exists ? senderDoc.data().username : 'Kullanıcı';
      
      // Get receiver's FCM token
      const receiverDoc = await admin.firestore().collection('users').doc(receiverId).get();
      
      if (!receiverDoc.exists) {
        console.log('Receiver not found:', receiverId);
        return null;
      }
      
      const receiverData = receiverDoc.data();
      const fcmToken = receiverData.fcmToken;
      
      if (!fcmToken) {
        console.log('No FCM token found for receiver:', receiverId);
        return null;
      }
      
      // Prepare notification message
      const notificationMessage = {
        notification: {
          title: senderName,
          body: content
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
      
      return response;
      
    } catch (error) {
      console.error('Error sending notification:', error);
      return null;
    }
  });

// HTTP endpoint for manual notification sending (backup)
exports.sendManualNotification = functions.https.onRequest(async (req, res) => {
  // Enable CORS
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Methods', 'GET, POST');
  res.set('Access-Control-Allow-Headers', 'Content-Type');
  
  if (req.method === 'OPTIONS') {
    res.status(204).send('');
    return;
  }
  
  try {
    const { receiverId, senderId, senderName, message, chatRoomId } = req.body;
    
    console.log('Manual notification request:', { receiverId, senderId, senderName, message, chatRoomId });
    
    // Get receiver's FCM token
    const receiverDoc = await admin.firestore().collection('users').doc(receiverId).get();
    
    if (!receiverDoc.exists) {
      console.log('Receiver not found:', receiverId);
      return res.status(404).json({ success: false, message: 'User not found' });
    }
    
    const receiverData = receiverDoc.data();
    const fcmToken = receiverData.fcmToken;
    
    if (!fcmToken) {
      console.log('No FCM token found for receiver:', receiverId);
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
    console.log('Successfully sent manual notification:', response);
    
    res.json({
      success: true,
      messageId: response,
      message: 'Notification sent successfully'
    });
    
  } catch (error) {
    console.error('Error sending manual notification:', error);
    res.status(500).json({
      success: false,
      message: 'Failed to send notification',
      error: error.message
    });
  }
});

// Health check endpoint
exports.healthCheck = functions.https.onRequest((req, res) => {
  res.set('Access-Control-Allow-Origin', '*');
  res.json({ 
    message: 'SnickersChat Firebase Functions are running!',
    timestamp: new Date().toISOString()
  });
});