# Active Context: WhatsApp Selective Read Receipts

## Current State

**Project Status**: ✅ Complete - Enhanced Android app with full messaging features

An Android application in `whatsapp-selective-reads/` that provides selective read receipts for WhatsApp with full message viewing, media handling, and inline reply.

## Recently Completed

- [x] Android project structure with Kotlin and Material Design 3
- [x] NotificationListenerService capturing MessagingStyle messages, media bitmaps, and reply actions
- [x] Room database with Message and ConversationEntity tables
- [x] Conversation-based grouping in main list (not flat message list)
- [x] ConversationDetailActivity with full chat-style message bubbles
- [x] Inline reply via RemoteInput API (keeps read receipts OFF)
- [x] MediaViewerActivity for image/video/audio/document preview and download
- [x] ReplyHelper utility for sending replies without opening WhatsApp
- [x] Settings Activity with service toggles and preferences
- [x] Adaptive launcher icons
- [x] All layout XML files for 3 activities + conversation/item layouts
- [x] Badge drawables and chat-style UI elements

## Project Structure: whatsapp-selective-reads/

| File/Directory | Purpose |
|----------------|---------|
| **Data Layer** | |
| `.../data/Message.kt` | Message entity (with media fields) + ConversationEntity |
| `.../data/MessageDao.kt` | MessageDao + ConversationDao |
| `.../data/AppDatabase.kt` | Room database (v2, 2 tables) |
| `.../data/Converters.kt` | Room type converters |
| **Service Layer** | |
| `.../service/WhatsAppNotificationService.kt` | Intercepts WA notifications, extracts MessagingStyle, media, reply actions |
| `.../service/ReplyHelper.kt` | Sends replies via RemoteInput + PendingIntent |
| `.../service/PreferencesManager.kt` | SharedPreferences wrapper |
| `.../service/BootReceiver.kt` | Boot completed receiver |
| **UI Layer** | |
| `.../ui/MainActivity.kt` | Conversation list with tabs (Pending/History) |
| `.../ui/ConversationDetailActivity.kt` | Full chat view with inline reply + media preview |
| `.../ui/MediaViewerActivity.kt` | Full-screen media viewer with download |
| `.../ui/adapter/ConversationAdapter.kt` | Chat list with Reply/Mark Read/Dismiss actions |
| `.../ui/adapter/MessageBubbleAdapter.kt` | Chat-style message bubbles |
| `.../ui/settings/SettingsActivity.kt` | Settings screen |

## How It Works

1. **NotificationListenerService** intercepts WhatsApp notifications
2. Extracts **MessagingStyle** messages (full conversation thread)
3. Captures **media bitmaps** and saves to internal storage
4. Detects **reply actions** (RemoteInput) from notifications
5. Groups messages by **conversation** in main list
6. Tapping a conversation opens **chat-style detail view** with all messages
7. **Inline reply** uses Android RemoteInput API to reply directly:
   - Reply is sent through WhatsApp's notification action
   - Chat is NOT opened = read receipt stays OFF (gray ticks)
8. **Media viewer** allows previewing and downloading images/video/audio/docs
9. Status badges: Pending (blue) | Read (green) | Dismissed (gray) | Replied (purple)

## Key Features

- **Full Messages**: Chat-style bubbles showing complete conversation thread
- **Inline Reply**: Reply without opening WhatsApp (read receipt stays OFF)
- **Media Handling**: View notification images, download all media types
- **Conversation Grouping**: Messages grouped by chat, not flat list
- **Reply Indicator**: Shows when a reply was sent through the app
- **Mark Read / Dismiss**: Per-conversation actions
- **Mark All Read**: Bulk action via FAB

## To Build & Run

1. Open `whatsapp-selective-reads/` in Android Studio
2. Sync Gradle, build, and run on device (not emulator)
3. Grant **Notification Access** permission when prompted
4. Toggle **Enable Service** in Settings
5. Incoming WhatsApp messages appear as conversations
6. Tap to view full messages, reply, or handle media

## Session History

| Date | Changes |
|------|---------|
| Initial | Next.js template created |
| 2026-03-26 | Android WhatsApp selective read receipts app built |
| 2026-03-26 | Enhanced: full messages, media handling, inline reply, conversation grouping |
