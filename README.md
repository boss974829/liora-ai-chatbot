# Liora AI Chatbot

Liora is a voice AI chatbot for Android. She talks with you, remembers facts on the phone, and can change into a few everyday outfits. The conversation stays warm and fully dressed.

## Download the APK

[Download Liora.apk](https://github.com/boss974829/liora-ai-chatbot/releases/download/v1.0.0/Liora.apk)

Or open the [latest release](https://github.com/boss974829/liora-ai-chatbot/releases/latest).

### Install

1. Open the APK on your phone.
2. If Android asks, allow install from this download.
3. Open **Liora**, go to **You**, and paste your own [xAI API key](https://console.x.ai).
4. Talk by typing, or tap **Mic** and allow the microphone.

Her replies are spoken by the phone. Memories and the chosen outfit stay on the device. If she looks something up, the message says so.

## What she can do

- Text and voice chat
- Spoken replies
- Long-term memory (`remember that…` / `forget that…`)
- Six clothed looks: cream knit, white shirt, linen dress, cardigan, camel coat, black blouse
- Web search when a question needs a current fact

## Build from source

You need JDK 17 and Android SDK 34 (`platforms;android-34` and `build-tools;34.0.0`).

```sh
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
sh android/build-apk.sh
```

The signed file is written to `dist/Liora.apk`.
