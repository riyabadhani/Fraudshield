# FraudShield AI 🛡️

An AI-powered platform to detect digital arrest scams in India in real time — before any money is lost.

---

## Problem Statement

India registered over 1.14 million cybercrime complaints in 2023. "Digital arrest" scams — where fraudsters impersonate CBI, ED, TRAI, or Customs officers and trap victims in fake video call interrogations — stole over ₹1,776 crore in just 9 months of 2024. Most victims are elderly citizens with limited tech knowledge who have no way to verify if a call is real or fake.

FraudShield AI solves this by analysing suspicious call transcripts instantly and telling the user exactly what is happening and what to do.

---

## How It Works

1. User receives a suspicious call and records it
2. App transcribes the call recording into text
3. Transcript is sent to FraudShield AI's ML model
4. Model classifies the call as SCAM or LEGITIMATE with a confidence score
5. Groq LLM generates a plain language explanation of what is happening and what to do
6. Result is sent back to the user via Telegram

---

## Tech Stack

- **Python** — core language
- **Flask** — REST API backend
- **sentence-transformers** — multilingual embeddings (supports Hindi, Hinglish, English)
- **scikit-learn** — LinearSVC classifier
- **Groq API** — LLaMA 3.3 70B for plain language explanation
- **Telegram Bot API** — result delivery to user
- **python-telegram-bot** — Telegram bot framework

---

## Project Structure
fraudshield-ai/
├── model/
│   ├── scam_classifier_v2.pkl
│   └── embedding_model_name.pkl
├── app.py
├── bot.py
├── retrain.ipynb
├── test_api.py
├── test_file.py
├── fraudshield_dataset.csv
├── hinglish_samples.csv
├── requirements.txt
├── .env                 
└── .gitignore

