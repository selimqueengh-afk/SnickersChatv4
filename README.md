# Snickers Chat Backend

Push notification API for Snickers Chat Android app.

## Features

- 🔔 Send push notifications via Firebase Cloud Messaging (FCM)
- 👤 User FCM token management
- 🔐 Secure Firebase Admin SDK integration
- 🚀 Railway deployment ready

## API Endpoints

### Health Check
```
GET /
```

### Send Notification
```
POST /api/send-notification
```

**Request Body:**
```json
{
  "receiverId": "user123",
  "senderId": "user456", 
  "senderName": "John Doe",
  "message": "Hello!",
  "chatRoomId": "chat123"
}
```

### Get User FCM Token
```
GET /api/user/:userId/token
```

### Update User FCM Token
```
POST /api/user/:userId/token
```

**Request Body:**
```json
{
  "fcmToken": "fcm_token_here"
}
```

## Environment Variables

- `FIREBASE_SERVICE_ACCOUNT_KEY`: Firebase service account JSON (required)
- `PORT`: Server port (optional, default: 3000)

## Deployment

This backend is deployed on Railway. The service automatically:

1. Installs Node.js dependencies
2. Starts the server using `npm start`
3. Uses the `FIREBASE_SERVICE_ACCOUNT_KEY` environment variable

## Local Development

```bash
npm install
npm run dev
```

## How It Works

1. Android app sends FCM token to this backend
2. When a message is sent, backend:
   - Gets receiver's FCM token from Firestore
   - Sends push notification via Firebase Admin SDK
   - Receiver gets notification even when app is closed