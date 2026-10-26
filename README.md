<div align="center">

# Nova Messages

A fast, lightweight, privacy-focused SMS/MMS app for Android.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://theown68.github.io/Nova-Web/)
[![Website](https://img.shields.io/badge/website-Nova--Web-informational)](https://theown68.github.io/Nova-Web/)

[Website](https://theown68.github.io/Nova-Web/) • [Releases](https://github.com/TheOwn68/Nova-Messages/releases) • [Issues](https://github.com/TheOwn68/Nova-Messages/issues)

</div>

---

## About

Nova Messages is an open-source SMS/MMS client built for people who want speed and control over their messaging. No ads, no trackers, no unnecessary permissions.

## Features

### Messaging
- Send and receive SMS/MMS
- Group messaging
- Attach photos, videos, audio, and files
- Image compression for MMS (automatic, configurable size limit)
- Video compression (MediaCodec, no extra app size)
- Scheduled messages
- Message reactions
- Swipe to reply
- Nova-to-Nova message encryption
- Delivery and read receipts

### Privacy & Security
- No internet connection required
- No ads or trackers
- App lock (PIN/password/biometric)
- Block unwanted numbers
- Phrase filtering (block messages containing specific words)
- Export/import blocked numbers list

### Customization
- 10 bubble shapes
- Custom colors for every part of the UI (top bar, bubbles, backgrounds, outlines)
- Custom fonts
- Dark/light themes
- UI scaling
- Conversation sorting (manual, alphabetical, recent)
- Pinned conversations
- Archived conversations
- Recycle bin for deleted messages

### Other
- Powerful search across all messages
- Home screen widget (conversation list)
- Quick greetings
- Contact details viewer
- Message export/import
- Notification customization per conversation
- High refresh rate support

## Installation

1. Download the latest APK from [Releases](https://github.com/TheOwn68/Nova-Messages/releases).
2. Enable installs from unknown sources if prompted.
3. Install the APK.
4. Open Nova Messages and set it as your default SMS app.

### Sideloading issues?

Some devices require enabling Restricted Settings. If the app doesn't work after install:

1. Allow restricted settings when prompted
2. Go to App Info → Storage
3. Clear Cache
4. Force Stop
5. Reopen the app

## Screenshots

<div align="center">
<img alt="Nova Messages" src="/images/Image1.png" width="32%">
<img alt="Nova Messages" src="/images/Image2.png" width="32%">
<img alt="Nova Messages" src="/images/Image3.png" width="32%">
</div>

## Building from source

Requirements: Android Studio, JDK 17, Android SDK 36

```bash
git clone https://github.com/TheOwn68/Nova-Messages.git
cd Nova-Messages
./gradlew assembleCoreDebug
```

The APK will be in `app/build/outputs/apk/core/debug/`.

## Contributing

Bug reports and feature requests are welcome. Open an issue or submit a pull request.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE) for details.