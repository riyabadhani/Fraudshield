from flask import Flask, request, jsonify
import pickle
from sentence_transformers import SentenceTransformer
from groq import Groq
from dotenv import load_dotenv
import os
import numpy as np

load_dotenv()
client_groq = Groq(api_key=os.getenv("GROQ_API_KEY"))

app = Flask(__name__)

# Load classifier
with open('model/scam_classifier_v2.pkl', 'rb') as f:
    classifier = pickle.load(f)

# Load embedding model name and initialize
with open('model/embedding_model_name.pkl', 'rb') as f:
    model_name = pickle.load(f)

embedder = SentenceTransformer(model_name)  


def get_llm_explanation(text, prediction, confidence, risk_level):
    prompt = f"""
## ROLE
You are FraudShield AI, an expert citizen safety assistant deployed by the Ministry of Home Affairs, India. You help ordinary citizens — including elderly people with limited tech knowledge — understand whether they are being scammed, and what to do immediately.

## CONTEXT
India registered over 1.14 million cybercrime complaints in 2023. "Digital arrest" scams — where fraudsters impersonate CBI, ED, TRAI, or Customs officers and trap victims in fake video call interrogations — stole over Rs 1,776 crore in just 9 months of 2024. Your job is to protect citizens at the moment of contact, before any money is lost.

A citizen has just reported the following suspicious message or call to FraudShield AI:
"{text}"

Our detection system has analysed this and returned:
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
        
        # safely parse JSON
        import json
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

    # same logic as /classify from here
    embedding = embedder.encode([text])
    prediction = classifier.predict(embedding)[0]
    label = 'SCAM' if prediction == 1 else 'LEGITIMATE'
    score = classifier.decision_function(embedding)[0]
    confidence = round(min(abs(score) * 40, 99), 2)
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


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'running'})

if __name__ == '__main__':
    app.run(debug=True, port=5000)