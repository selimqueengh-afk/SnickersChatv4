# SnickersChat Backend

Push notification backend for SnickersChat Android application.

## Features

- ✅ Send push notifications via Firebase Cloud Messaging (FCM)
- ✅ Get user FCM tokens
- ✅ Update user FCM tokens
- ✅ Health check endpoint

## API Endpoints

### Health Check
```
GET /
```
Returns server status and timestamp.

### Send Notification
```
POST /api/send-notification
```
Send push notification to a user.

**Request Body:**
```json
{
  "receiverId": "user_id",
  "senderId": "sender_id", 
  "senderName": "Sender Name",
  "message": "Message content",
  "chatRoomId": "chat_room_id"
}
```

### Get User Token
```
GET /api/user/:userId/token
```
Get FCM token for a specific user.

### Update User Token
```
POST /api/user/:userId/token
```
Update FCM token for a specific user.

**Request Body:**
```json
{
  "fcmToken": "firebase_fcm_token"
}
```

## Environment Variables

### Required
- `FIREBASE_SERVICE_ACCOUNT_KEY`: Firebase service account JSON key (stringified)

### Optional
- `PORT`: Server port (default: 3000)

## Deployment

### Railway
1. Connect GitHub repository to Railway
2. Add environment variables
3. Deploy automatically

### Local Development
```bash
npm install
npm run dev
```

## How It Works

1. Android app sends notification request to backend
2. Backend gets receiver's FCM token from Firestore
3. Backend sends push notification via Firebase Admin SDK
4. FCM delivers notification to user's device

## Firebase Setup

1. Go to Firebase Console
2. Project Settings → Service Accounts
3. Generate new private key
4. Copy JSON content to `FIREBASE_SERVICE_ACCOUNT_KEY` environment variable