from flask import Flask, request, jsonify
import pickle
from sentence_transformers import SentenceTransformer
from groq import Groq
from dotenv import load_dotenv
import os
import numpy as np
import json
import requests as req

load_dotenv()
client_groq = Groq(api_key=os.getenv("GROQ_API_KEY"))

app = Flask(__name__)

REGISTRY_FILE = "phone_registry.json"

def load_registry():
    if os.path.exists(REGISTRY_FILE):
        with open(REGISTRY_FILE, 'r') as f:
            return json.load(f)
    return {}

def format_result(result: dict) -> str:
    prediction = result.get("prediction", "UNKNOWN").upper()
    attack_type = result.get("attack_type", "Unknown")
    risk_level = result.get("risk_level", "Unknown")
    confidence = result.get("confidence", 0)
    red_flags = result.get("red_flags", [])
    explanation = result.get("explanation", "No explanation available.")
    safe_action = result.get("safe_action", "Stay cautious.")

    if prediction == "LEGITIMATE":
        message = "✅ LEGITIMATE\n\n"
    else:
        message = "🚨 SCAM DETECTED\n\n"

    message += (
        f"🎯 Attack Type: {attack_type}\n"
        f"⚠️ Risk Level: {risk_level}\n"
        f"📊 Confidence: {confidence}%\n\n"
    )

    message += "🔴 Red Flags:\n"
    if red_flags:
        for flag in red_flags:
            message += f"• {flag}\n"
    else:
        message += "• None detected\n"

    message += f"\n📢 What's Happening:\n{explanation}\n\n"
    message += f"✅ What To Do:\n{safe_action}"

    return message

# Load classifier
with open('model/scam_classifier_v2.pkl', 'rb') as f:
    classifier = pickle.load(f)

with open('model/embedding_model_name.pkl', 'rb') as f:
    model_name = pickle.load(f)

embedder = SentenceTransformer(model_name)


def get_llm_explanation(text, prediction, confidence, risk_level):
    prompt = f"""
You are FraudShield AI, a citizen safety assistant for India deployed by the Ministry of Home Affairs.

Analyze this call transcript or message and return ONLY a JSON object, nothing else:
"{text}"

Our detection system returned:
- Classification: {prediction}
- Risk Level: {risk_level}

Return this exact JSON structure:
{{
  "risk_level": "{risk_level}",
  "attack_type": "short label like Digital Arrest Scam or KYC Fraud or Fake Bank Call",
  "red_flags": ["specific red flag 1", "specific red flag 2", "specific red flag 3"],
  "explanation": "2-3 sentence plain English explanation a 60-year-old can understand. If SCAM start with Warning: or Caution:. If LEGITIMATE start with This appears to be legitimate.",
  "safe_action": "1-2 specific action steps. Always include call 1930 or visit cybercrime.gov.in if SCAM."
}}

Rules:
- Return ONLY the JSON object, no extra text, no markdown backticks
- Never use technical words like classifier, model, algorithm
- If LEGITIMATE, set red_flags to empty list []
- Keep explanation under 60 words
"""
    try:
        response = client_groq.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=300
        )
        raw = response.choices[0].message.content.strip()
        start = raw.find("{")
        end = raw.rfind("}") + 1
        return json.loads(raw[start:end])

    except Exception as e:
        print("Groq Error:", e)
        return {
            "risk_level": risk_level,
            "attack_type": "Unknown",
            "red_flags": [],
            "explanation": "AI explanation temporarily unavailable.",
            "safe_action": "Call 1930 or visit cybercrime.gov.in if you suspect fraud."
        }


@app.route('/classify', methods=['POST'])
def classify():
    data = request.get_json()
    if not data or 'text' not in data:
        return jsonify({'error': 'No text provided'}), 400

    text = data['text']
    embedding = embedder.encode([text])
    prediction = classifier.predict(embedding)[0]
    label = 'SCAM' if prediction == 1 else 'LEGITIMATE'
    score = classifier.decision_function(embedding)[0]
    confidence = round(min(abs(score) * 55, 99), 2)
    risk_level = ('HIGH' if confidence > 85 and label == 'SCAM'
                  else 'MEDIUM' if label == 'SCAM'
                  else 'LOW')
    analysis = get_llm_explanation(text, label, confidence, risk_level)

    return jsonify({
        'prediction': label,
        'confidence': confidence,
        'risk_level': risk_level,
        'attack_type': analysis.get('attack_type'),
        'red_flags': analysis.get('red_flags'),
        'explanation': analysis.get('explanation'),
        'safe_action': analysis.get('safe_action')
    })


@app.route('/classify_file', methods=['POST'])
def classify_file():
    data = request.get_json()
    if not data or 'file_path' not in data:
        return jsonify({'error': 'No file path provided'}), 400

    file_path = data['file_path']
    if not os.path.exists(file_path):
        return jsonify({'error': 'File not found'}), 404

    with open(file_path, 'r', encoding='utf-8') as f:
        text = f.read().strip()

    if not text:
        return jsonify({'error': 'File is empty'}), 400

    embedding = embedder.encode([text])
    prediction = classifier.predict(embedding)[0]
    label = 'SCAM' if prediction == 1 else 'LEGITIMATE'
    score = classifier.decision_function(embedding)[0]
    confidence = round(min(abs(score) * 55, 99), 2)
    risk_level = ('HIGH' if confidence > 85 and label == 'SCAM'
                  else 'MEDIUM' if label == 'SCAM'
                  else 'LOW')
    analysis = get_llm_explanation(text, label, confidence, risk_level)

    return jsonify({
        'prediction': label,
        'confidence': confidence,
        'risk_level': risk_level,
        'attack_type': analysis.get('attack_type'),
        'red_flags': analysis.get('red_flags'),
        'explanation': analysis.get('explanation'),
        'safe_action': analysis.get('safe_action')
    })


@app.route('/analyse-call', methods=['POST'])
def analyse_call():
    data = request.get_json()
    if not data or 'phone' not in data or 'transcript' not in data:
        return jsonify({'error': 'phone and transcript required'}), 400

    phone = data['phone']
    transcript = data['transcript']

    embedding = embedder.encode([transcript])
    prediction = classifier.predict(embedding)[0]
    label = 'SCAM' if prediction == 1 else 'LEGITIMATE'
    score = classifier.decision_function(embedding)[0]
    confidence = round(min(abs(score) * 55, 99), 2)
    risk_level = ('HIGH' if confidence > 85 and label == 'SCAM'
                  else 'MEDIUM' if label == 'SCAM'
                  else 'LOW')
    analysis = get_llm_explanation(transcript, label, confidence, risk_level)

    result = {
        'prediction': label,
        'confidence': confidence,
        'risk_level': risk_level,
        'attack_type': analysis.get('attack_type'),
        'red_flags': analysis.get('red_flags'),
        'explanation': analysis.get('explanation'),
        'safe_action': analysis.get('safe_action')
    }

    registry = load_registry()
    if phone in registry:
        chat_id = registry[phone]
        message = format_result(result)
        telegram_url = f"https://api.telegram.org/bot{os.getenv('TELEGRAM_BOT_TOKEN')}/sendMessage"
        req.post(telegram_url, json={'chat_id': chat_id, 'text': message})
    else:
        print(f"Phone {phone} not registered on Telegram bot")

    return jsonify(result)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'running'})


if __name__ == '__main__':
    app.run(debug=True, port=5000)