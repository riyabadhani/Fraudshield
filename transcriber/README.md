# FraudShield Backend — Audio Upload & Transcription Service

This is the backend service for the FraudShield project. Its job is simple but
critical: **receive a phone call recording from the Android app, transcribe
what was said (in English, Hindi, or a mix of both), and save the result** so
it can later be analyzed for scam/fraud patterns.

This document explains exactly what the code does, file by file, so anyone
picking up this project (including future-you) can understand it without
reverse-engineering it from scratch.

---

## 1. What this backend actually does, in plain English

1. The Android app records or receives a phone call recording (`.wav`/`.mp3`).
2. The app sends that file over the network to this backend via a single
   HTTP endpoint: `POST /upload`.
3. This backend:
   - Saves the raw audio file to disk (`uploads/` folder).
   - Detects what language is being spoken (English, Hindi, etc.).
   - Transcribes the audio into **English text**, regardless of which
     language was actually spoken — so downstream fraud-keyword matching
     (e.g. "OTP", "KYC", "CVV") always works on consistent English text.
   - Saves that transcript as a `.txt` file next to the audio.
   - Prints everything to the server console for live debugging.
   - Responds to the app with `{"status": "ok"}` so the app can mark the
     recording as successfully uploaded (green dot in the app's UI).

---

## 2. Tech stack

| Piece                                             | What it is              | Why it's used                                                           |
| ------------------------------------------------- | ----------------------- | ----------------------------------------------------------------------- |
| **FastAPI**                                       | Python web framework    | Handles the `/upload` HTTP endpoint, receives the file over the network |
| **Uvicorn**                                       | ASGI server             | Actually runs the FastAPI app and listens on a port                     |
| **OpenAI Whisper** (`openai-whisper` pip package) | Speech-to-text AI model | Converts the audio recording into text                                  |
| **PyTorch**                                       | ML framework            | Whisper runs on top of this; handles running the model on CPU or GPU    |
| **ffmpeg**                                        | System-level audio tool | Whisper uses this internally to decode `.wav`/`.mp3` files              |

---

## 3. How a request flows through the system (step by step)

```
Android app
   |
   |  POST /upload  (multipart/form-data, field name "file")
   v
FastAPI endpoint (main.py -> test_upload())
   |
   |  1. Reads the uploaded bytes into memory
   |  2. Writes them to disk at uploads/<original_filename>
   v
Whisper language detection
   |
   |  Loads first ~30s of audio, runs it through the model's
   |  encoder, and guesses which language is being spoken
   |  (returns a short code like "hi" or "en")
   v
Whisper transcription (task="translate")
   |
   |  Runs the FULL audio through Whisper again, this time asking
   |  it to output English text no matter what language was spoken
   v
Save results
   |
   |  Writes uploads/<filename>.txt containing:
   |    [Spoken language: hi]
   |
   |    <the English transcript text>
   v
HTTP response
   |
   |  Returns {"status": "ok"} back to the Android app
   v
Android app marks the recording's status dot GREEN
```

If anything goes wrong at any step (file can't be saved, Whisper crashes,
etc.), FastAPI returns a `500 Internal Server Error`, and the Android app
keeps the recording's status dot RED so the user can retry later.

---

## 4. File-by-file breakdown

### `main.py`

The entire backend currently lives in this one file. It contains:

- **Model loading (runs once, at server startup — not per request)**

  ```python
  model = whisper.load_model("base")
  ```

  This loads the Whisper `"base"` model into memory (and onto GPU if one is
  available). This happens exactly once when the server starts, not on every
  upload — loading the model is slow (can take from a few seconds to a couple
  of minutes depending on model size), so doing it once and reusing it is
  essential for reasonable response times.

  **Why `"base"` specifically:** Whisper offers multiple model sizes
  (`tiny`, `base`, `small`, `medium`, `large`) trading off speed vs accuracy.
  `base` was chosen after testing showed it gives clean, accurate results on
  both English and Hindi call recordings for this use case, while being fast
  enough to run comfortably (a few seconds per call) even on CPU or a modest
  GPU. Bigger models (`small`, `medium`) were tested but were either too slow
  on this hardware or didn't meaningfully improve accuracy for this specific
  kind of audio (short phone calls with banking/fraud vocabulary).

- **The `/upload` endpoint**

  ```python
  @app.post("/upload")
  async def test_upload(file: UploadFile = File(...)):
  ```

  This is the single HTTP route the whole backend exposes. It expects a
  `multipart/form-data` POST request with a file attached under the form
  field name `"file"` — this must match exactly what the Android app sends.

  Inside the function:
  1. **Read + save the file** — the raw bytes are read from the incoming
     request and written to `uploads/<filename>` on disk, using the same
     filename the phone sent.
  2. **Detect spoken language** — a lightweight pass over the first ~30
     seconds of audio using `model.detect_language()`, which returns
     probabilities for every language Whisper knows, and we take the most
     likely one (`hi`, `en`, etc.).
  3. **Transcribe with translation** — the full audio is run through
     `model.transcribe(save_path, task="translate")`. Passing
     `task="translate"` (instead of the default `task="transcribe"`) tells
     Whisper: _no matter what language is spoken, give me the text in
     English._ This is what allows the same downstream fraud-keyword
     matching logic to work regardless of whether a call was in Hindi,
     English, or a mix of both.
  4. **Save the transcript** — written to `uploads/<filename>.txt`, prefixed
     with a line stating which language was detected, e.g.:

     ```
     [Spoken language: hi]

     Hello sir, this call is from the bank. Can you confirm your OTP...
     ```

  5. **Return success** — `{"status": "ok"}` is sent back as the HTTP
     response body with a `200 OK` status, which is how the Android app
     knows the upload succeeded.

### `uploads/` (folder, created automatically)

This is where every received recording and its matching transcript end up.
For every uploaded call you'll find two files:

```
uploads/
├── record-1784267934929.wav      <- the original audio, untouched
└── record-1784267934929.txt      <- the English transcript + detected language
```

This folder is created automatically the first time the server runs, as long
as it exists before the first upload (see Section 6, "Known gotchas").

### `venv/`

A Python virtual environment — an isolated folder containing all the pip
packages this project needs (FastAPI, Whisper, PyTorch, etc.), kept separate
from your system-wide Python installation so this project's dependencies
never conflict with anything else on the machine. This folder is not code
you edit; it's regenerated by running `pip install` inside it.

---

## 5. How to run this backend

```bash
# 1. Activate the virtual environment
source venv/bin/activate

# 2. Start the server
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

- `--host 0.0.0.0` — makes the server listen on all network interfaces, not
  just `localhost`. This matters because the Android phone reaches this
  server through `adb reverse`, which needs the server to be listening
  broadly, not just to `127.0.0.1`.
- `--port 8000` — must match the port the Android app is configured to talk
  to (`Constants.kt` → `BASE_URL` in the app).
- `--reload` — automatically restarts the server whenever you edit and save
  `main.py`, useful during development. **Note:** because the Whisper model
  is loaded at server startup, every reload re-loads the model from scratch,
  which takes a few seconds — wait for the `Model loaded on device: ...` log
  line before testing an upload right after editing code.

Once running, you'll see:

```
INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
INFO:     Application startup complete.
```

To let a phone connected via USB reach this server on your machine:

```bash
adb reverse tcp:8000 tcp:8000
```

---

## 6. Known gotchas / things that have bitten us before

- **`uploads/` folder must exist before the first request** — Python's
  `open()` won't create missing folders automatically. If this folder is
  ever deleted, the very first upload will crash with
  `FileNotFoundError: uploads/...`. Fix: `mkdir -p uploads`, or add
  `os.makedirs("uploads", exist_ok=True)` near the top of `main.py` so the
  server creates it automatically every time it starts.

- **GPU out-of-memory with bigger models** — on machines with a smaller GPU
  (e.g. 6GB VRAM laptops), loading `"medium"` or `"large"` can crash with a
  CUDA out-of-memory error, especially when running inside WSL2, since some
  VRAM is reserved by the host OS. `"base"` and `"small"` fit comfortably;
  bigger models may need `device="cpu"` forced instead, which is slower but
  crash-proof.

- **`--reload` mid-download corrupts the model cache** — if you switch model
  sizes (e.g. `"base"` → `"medium"`) and edit `main.py` while a large model
  is still downloading, `--reload` can restart the server mid-download and
  corrupt the cached model file, causing a checksum error on next load. Fix:
  delete the corrupted file from `~/.cache/whisper/`, then pre-download the
  model manually (`python3 -c "import whisper; whisper.load_model('medium')"`)
  before starting the server.

- **Language auto-detection can misfire on short/ambiguous audio** —
  Whisper only listens to the first ~30 seconds to guess the spoken
  language, and Hindi/Urdu especially can be confused with each other on
  short clips. `task="translate"` mode is more forgiving of this than
  `task="transcribe"` mode, since it's not trying to reproduce the exact
  source-language script.

---

## 7. What's intentionally NOT in this backend

To keep this service focused and easy to reason about, it deliberately does
**not** include:

- User accounts or authentication
- A database (transcripts are just flat `.txt` files on disk)
- The actual fraud-scoring ML model — that lives in a separate repo and is
  meant to be called with the transcript text this service produces as its
  input
- Production-grade networking (HTTPS, public hosting) — this is designed to
  run locally and be reached via `adb reverse` from a USB-connected phone
  during development

---

## 8. Next step: connecting to the fraud-scoring model

This backend currently stops at producing a clean English transcript. The
next integration step is to take `transcript_text` (already available as a
Python variable inside the `/upload` route, right after transcription) and
pass it into the fraud-scoring model from the separate `Fraudshield` model
repo, then include that model's prediction (e.g. a scam-likelihood
percentage) in the JSON response sent back to the app, instead of just
`{"status": "ok"}`.
