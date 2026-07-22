# FraudShield AI Android App

Minimal Android app in Kotlin for receiving shared call recordings, uploading them to a local backend, and showing per-recording scam analysis.

## Features

- Appears in Android's native share sheet for `audio/*`
- Accepts shared `.mp3` and `.wav` recordings
- Saves recordings into app-local storage
- Uploads recordings to local backend over multipart/form-data
- Sends logged-in phone number with each upload
- Shows a dark-mode recordings list
- Opens a per-recording analysis screen on tap
- Shows Telegram bot setup CTA after login

## Current Flow

1. User opens the app.
2. If not logged in, app shows phone number + password screen.
3. If already logged in, app opens the recordings screen directly.
4. User shares a call recording from the Phone app into this app.
5. App saves the file locally.
6. App uploads the file to the upload backend.
7. Upload backend transcribes the call and forwards transcript to FraudShield.
8. App stores the returned analysis against that recording.
9. User taps a recording to open its analysis page.

## Project Location

Android app:

`apps/android-share-upload`

Sample upload backend added in this repo:

`services/transcription-backend/app.py`

## Backend Endpoints Used By The App

### App API / Login

- Base URL: `http://127.0.0.1:5000`
- Used for:
  - `POST /login`

### Upload API

- Base URL: `http://127.0.0.1:8000`
- Used for:
  - `POST /upload`

## Upload Request Format

The app sends multipart form-data to:

`POST http://127.0.0.1:8000/upload`

Fields:

- `phone_number`
- `file`

## Upload Response Format Expected By The App

The app currently expects `/upload` to return JSON like:

```json
{
  "status": "ok",
  "phone_number": "9999999999",
  "transcript": "transcribed call text",
  "detected_language": "hi",
  "fraudshield_result": {
    "prediction": "SCAM",
    "confidence": 81.09,
    "risk_level": "MEDIUM",
    "attack_type": "Digital Arrest Scam",
    "red_flags": [
      "impersonates CBI officer",
      "demand for money transfer",
      "threat of arrest"
    ],
    "explanation": "Warning: This is a digital arrest scam. Fraudsters are impersonating government officers to steal money.",
    "safe_action": "Do not transfer money. Hang up immediately. Call 1930 or visit cybercrime.gov.in."
  },
  "fraudshield_error": null
}
```

### App behavior

- If `fraudshield_result` exists, the app stores and shows the analysis
- If `fraudshield_error` exists, the app marks analysis as failed and shows that error on the detail page

## Login Behavior

Current login is minimal:

- App sends phone number + password to `POST /login`
- If backend returns success, app stores local login session
- Future launches skip login until app data is cleared

## Telegram Bot Setup

After login, the app shows a button that opens:

`https://t.me/fraudsheild_ai_09_bot`

User is instructed to open the bot once and send their phone number so alerts can later be routed by backend systems.

## Run Locally

### 1. Open in Android Studio

Open:

`apps/android-share-upload`

### 2. Start your backends

Make sure:

- login / FraudShield API side is running on `127.0.0.1:5000`
- upload/transcription backend is running on `127.0.0.1:8000`

### 3. Connect phone with USB debugging

Run:

```powershell
adb reverse tcp:5000 tcp:5000
adb reverse tcp:8000 tcp:8000
```

### 4. Build and install

Build and run the app from Android Studio.

## Notes

- Cleartext HTTP is enabled in debug builds only
- Recording files are stored in app-private internal storage
- Per-recording analysis is stored locally with recording metadata
- The app currently uses a dark UI and a dedicated analysis screen

## Main Android Files

- `app/src/main/java/com/example/audioshareupload/MainActivity.kt`
- `app/src/main/java/com/example/audioshareupload/RecordingDetailActivity.kt`
- `app/src/main/java/com/example/audioshareupload/Uploader.kt`
- `app/src/main/java/com/example/audioshareupload/AuthApi.kt`
- `app/src/main/java/com/example/audioshareupload/RecordingStore.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_recording_detail.xml`

## Known Limitations

- No logout flow yet
- No playback UI
- No delete/rename management
- No background worker for deferred retries
- Analysis currently depends on upload response from `/upload`
