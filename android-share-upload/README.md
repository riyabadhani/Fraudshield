# FraudShield AI 🛡️

An AI-powered platform to detect digital arrest scams in India in real time — before any money is lost.

---

## Problem Statement

India registered over 1.14 million cybercrime complaints in 2023. "Digital arrest" scams — where fraudsters impersonate CBI, ED, TRAI, or Customs officers and trap victims in fake video call interrogations — stole over ₹1,776 crore in just 9 months of 2024. Most victims are elderly citizens with limited tech knowledge who have no way to verify if a call is real or fake.

FraudShield AI solves this by analysing suspicious call transcripts instantly and telling the citizen exactly what is happening and what to do — before any money is lost.

---

## How It Works

1. User receives a suspicious call and records it on the app
2. App transcribes the call recording into text using Whisper
3. Transcript is sent to FraudShield AI's Flask API along with the user's phone number
4. Multilingual NLP model classifies the call as SCAM or LEGITIMATE
5. Groq LLM generates a structured plain language explanation — attack type, red flags, and action steps
6. Result is delivered to the user instantly via Telegram bot and shown on the app

---

## Tech Stack

- **Python** — core language
- **Flask** — REST API backend
- **sentence-transformers** — multilingual embeddings (paraphrase-multilingual-MiniLM-L12-v2)
- **scikit-learn** — LinearSVC classifier
- **Groq API** — LLaMA 3.3 70B for structured plain language explanation
- **Telegram Bot API** — instant result delivery to citizen
- **python-telegram-bot** — Telegram bot framework
- **Whisper** — call transcription (friend's side)

---

## Model Performance

Trained on 500+ real and synthesized Indian scam transcripts covering digital arrest, KYC fraud, fake bank calls, customs scams, and more.

| Metric | Score |
|---|---|
| Overall Accuracy | 91% |
| Scam Recall | 98% |
| Legit Precision | 97% |
| Scam F1 | 0.92 |
| Legit F1 | 0.90 |

Supports English, Hindi, and Hinglish transcripts.

---

## API Response Format

```json
{
  "prediction": "SCAM",
  "confidence": 81.09,
  "risk_level": "HIGH",
  "attack_type": "Digital Arrest Scam",
  "red_flags": [
    "impersonates CBI officer",
    "demands money transfer",
    "threatens arrest"
  ],
  "explanation": "Warning: This is a digital arrest scam. Fraudsters are impersonating government officers to steal money.",
  "safe_action": "Do not transfer money. Hang up immediately. Call 1930 or visit cybercrime.gov.in."
}
```

---

## Project Structure

```
fraudshield-ai/
├── model/
│   ├── scam_classifier_v3.pkl
│   └── embedding_model_name.pkl
├── app.py
├── bot.py
├── retrain.ipynb
├── test_api.py
├── test_file.py
├── test_analyse.py
├── fraudshield_dataset.csv
├── requirements.txt
├── .env                  ← never push this
└── .gitignore
```

---

## Setup Instructions

1. Clone the repo:
```bash
git clone https://github.com/riyabadhani/fraudshield-ai.git
cd fraudshield-ai
```

2. Install dependencies:
```bash
pip install -r requirements.txt
```

3. Create a `.env` file in the root folder:
```
GROQ_API_KEY=your_groq_api_key
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
```

4. Start the Flask API:
```bash
python app.py
```

5. Start the Telegram bot:
```bash
python bot.py
```

---

## API Routes

### POST `/analyse-call`
Main endpoint. Takes phone number and transcript, classifies, and sends Telegram alert.

Request:
```json
{
  "phone": "your_phone_no.",
  "transcript": "I am CBI officer. Your Aadhaar is linked to money laundering. Transfer 50000 rupees immediately."
}
```

### POST `/classify`
Takes raw text, returns classification without Telegram alert.

Request:
```json
{"text": "Your Aadhaar is linked to money laundering. Do not disconnect this call."}
```

### POST `/classify_file`
Takes path to a `.txt` transcript file, returns classification.

Request:
```json
{"file_path": "path/to/transcript.txt"}
```

### GET `/health`
Returns API status.

```json
{"status": "running"}
```

---

## Telegram Bot

Citizens register once by sending their 10-digit phone number to the bot at [t.me/fraudsheild_ai_09_bot](https://t.me/fraudsheild_ai_09_bot).

After registration, scam alerts are delivered automatically whenever a suspicious call is detected — no further action needed from the user.

Alert format:
```
🚨 SCAM DETECTED

🎯 Attack Type: Digital Arrest Scam
⚠️ Risk Level: HIGH
📊 Confidence: 92%

🔴 Red Flags:
• Impersonates CBI officer
• Demands money transfer
• Threatens arrest

📢 What's Happening:
Warning: This is a digital arrest scam.

✅ What To Do:
Do not transfer money. Call 1930 or visit cybercrime.gov.in.
```

---

## Dataset

- 500+ samples (scam + legitimate)
- Covers English, Hindi, and Hinglish
- Scam types: digital arrest, fake CBI/ED/TRAI/Customs, KYC fraud, fake bank calls, customs parcel scams
- Legitimate types: real bank calls, government services, insurance, e-commerce, telecom, fraud awareness
- Sources: MHA advisories, cybercrime.gov.in, news articles, victim transcripts, YouTube recordings

---

## Team

- **Riya Badhani** — ML model, dataset, Flask API, Groq integration, Telegram bot
- **Aryan Singh** — call transcription, frontend app, app chatbot
