# Project Repository

This repository contains two projects:

1. **Next.js Starter Template** (root) -- A modern web application scaffold optimized for AI-assisted development.
2. **WhatsApp Selective Read Receipts** (`whatsapp-selective-reads/`) -- An Android app for viewing WhatsApp messages without triggering read receipts.

---

## 1. Next.js Starter Template

A minimal Next.js 16 starter template with TypeScript, Tailwind CSS 4, and ESLint. Designed as a clean foundation for building web applications through AI-assisted development.

### Tech Stack

| Technology   | Version | Purpose                         |
| ------------ | ------- | ------------------------------- |
| Next.js      | 16.x    | React framework with App Router |
| React        | 19.x    | UI library                      |
| TypeScript   | 5.9.x   | Type-safe JavaScript            |
| Tailwind CSS | 4.x     | Utility-first CSS               |
| Bun          | Latest  | Package manager & runtime       |

### Prerequisites

- [Bun](https://bun.sh/) installed (`curl -fsSL https://bun.sh/install | bash`)
- Node.js 20+

### Getting Started

```bash
# Install dependencies
bun install

# Start development server (http://localhost:3000)
bun dev

# Production build
bun build

# Start production server
bun start
```

### Available Scripts

| Command          | Purpose                    |
| ---------------- | -------------------------- |
| `bun dev`        | Start development server   |
| `bun build`      | Create production build    |
| `bun start`      | Start production server    |
| `bun lint`       | Run ESLint                 |
| `bun typecheck`  | Run TypeScript type checker|

### Project Structure

```
/
├── .gitignore
├── package.json
├── bun.lock
├── next.config.ts
├── tsconfig.json
├── postcss.config.mjs
├── eslint.config.mjs
├── AGENTS.md
├── .kilocode/
│   ├── recipes/           # Feature recipes (e.g., add database)
│   └── rules/
│       ├── development.md
│       ├── memory-bank-instructions.md
│       └── memory-bank/   # AI context persistence
├── public/
│   └── .gitkeep
└── src/
    └── app/
        ├── layout.tsx     # Root layout + metadata
        ├── page.tsx       # Home page
        ├── globals.css    # Tailwind imports + global styles
        └── favicon.ico
```

### Key Conventions

- **Server Components** by default; add `"use client"` only when interactivity is needed.
- Use `next/image` for images and `next/link` for navigation.
- Path alias `@/*` maps to `src/*`.
- Components: PascalCase (`Button.tsx`). Utilities: camelCase (`utils.ts`).
- Responsive design via Tailwind breakpoints: `sm:`, `md:`, `lg:`, `xl:`.

### Extending the Template

This template uses a **recipe system** for common additions. Check `.kilocode/recipes/` for available recipes:

| Recipe       | File                                | When to Use                      |
| ------------ | ----------------------------------- | -------------------------------- |
| Add Database | `.kilocode/recipes/add-database.md` | When you need data persistence   |

### Environment Variables

None required for the base template. Add `.env.local` as needed for additional features.

---

## 2. WhatsApp Selective Read Receipts

An Android application that lets you view WhatsApp messages without sending read receipts (blue ticks). Built with Kotlin and Material Design 3.

### Features

- **Full Message History** -- Captures all messages from WhatsApp's MessagingStyle notifications.
- **Inline Reply** -- Reply directly without opening WhatsApp (read receipts stay OFF).
- **Audio Messages** -- Play/pause audio with seekbar and progress tracking.
- **Media Handling** -- Preview images, download video/audio/documents.
- **Conversation Grouping** -- Messages grouped by chat contact.
- **Context Menu** -- Long-press for Copy, Reply, Download, Info, Mark Read.
- **Mark Read / Dismiss** -- Per-conversation and bulk actions.

### Tech Stack

| Technology           | Version | Purpose                        |
| -------------------- | ------- | ------------------------------ |
| Kotlin               | 1.9.x   | Programming language           |
| Android Gradle Plugin| 8.2.x   | Build system                   |
| Room                 | -       | Local SQLite database          |
| Material Design 3    | -       | UI components and theming      |
| NotificationListener | -       | Captures WhatsApp notifications|

### How It Works

1. `NotificationListenerService` intercepts WhatsApp notifications.
2. Extracts `MessagingStyle` messages (full conversation threads).
3. Captures media bitmaps and saves to internal storage.
4. Detects reply actions (`RemoteInput`) from notifications.
5. Groups messages by conversation in the main list.
6. Tapping a conversation opens a chat-style detail view with all messages.
7. Inline reply uses Android's `RemoteInput` API -- the chat is not opened, so read receipts stay OFF.

### Project Structure

```
whatsapp-selective-reads/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/java/com/whatsapp/selectivereads/
│       ├── WhatsAppSelectiveReadsApp.kt    # Application class
│       ├── data/
│       │   ├── Message.kt                  # Message + ConversationEntity
│       │   ├── MessageDao.kt               # MessageDao + ConversationDao
│       │   ├── AppDatabase.kt              # Room database
│       │   └── Converters.kt               # Room type converters
│       ├── service/
│       │   ├── WhatsAppNotificationService.kt  # Notification interceptor
│       │   ├── ReplyHelper.kt                  # RemoteInput reply sender
│       │   ├── AudioPlayerManager.kt           # Audio playback singleton
│       │   ├── PreferencesManager.kt           # SharedPreferences wrapper
│       │   └── BootReceiver.kt                 # Boot completed receiver
│       └── ui/
│           ├── MainActivity.kt                 # Conversation list + tabs
│           ├── ConversationDetailActivity.kt   # Chat-style message view
│           ├── MediaViewerActivity.kt          # Full-screen media viewer
│           ├── settings/
│           │   └── SettingsActivity.kt         # Settings screen
│           └── adapter/
│               ├── ConversationAdapter.kt      # Chat list items
│               └── MessageBubbleAdapter.kt     # Message bubble rendering
```

### Building and Running

1. Open `whatsapp-selective-reads/` in **Android Studio**.
2. Sync Gradle and build the project.
3. Run on a **physical device** (not an emulator -- notification access is required).
4. Grant **Notification Access** permission when prompted.
5. Toggle **Enable Service** in Settings.
6. Incoming WhatsApp messages appear as conversations.

### UI Design

The app is a pixel-perfect WhatsApp clone:
- **Chat list**: Conversation items with avatar, unread badge, time, and last message preview.
- **Chat view**: WhatsApp-style interface with green/white bubbles, tick indicators, date dividers, and a full input bar with emoji/attach/camera/mic-toggle.
- **Colors**: WhatsApp palette (green `#128C7E`, chat background `#ECE5DD`).

---

## License

This project is for personal and educational use.
