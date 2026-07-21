from telegram import Update
from telegram.ext import Application, CommandHandler, MessageHandler, filters, ContextTypes
import os
import json
from dotenv import load_dotenv

load_dotenv()

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
REGISTRY_FILE = "phone_registry.json"


def load_registry():
    if os.path.exists(REGISTRY_FILE):
        with open(REGISTRY_FILE, 'r') as f:
            return json.load(f)
    return {}


def save_registry(registry):
    with open(REGISTRY_FILE, 'w') as f:
        json.dump(registry, f)


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


async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "🛡️ Welcome to FraudShield AI!\n\n"
        "I will alert you if a suspicious call is detected.\n\n"
        "Please send your 10-digit phone number to register."
    )


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.strip()
    chat_id = update.effective_chat.id

    registry = load_registry()

    # check if this phone number is already registered
    if text in registry:
        await update.message.reply_text("✅ This number is already registered. You will receive scam alerts automatically.")
        return

    if text.isdigit() and len(text) == 10:
        registry[text] = chat_id
        save_registry(registry)
        await update.message.reply_text("✅ Registered! You will receive scam alerts here automatically.")
    elif text.isdigit() and len(text) != 10:
        await update.message.reply_text("❌ Invalid number. Please send a valid 10-digit Indian mobile number.")
    else:
        await update.message.reply_text("Please send your 10-digit phone number to register.")


if __name__ == "__main__":
    app = Application.builder().token(BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))
    print("Bot is running...")
    app.run_polling()