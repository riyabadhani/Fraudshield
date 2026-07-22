from pathlib import Path
import json
from urllib import error, request as urllib_request

from fastapi import FastAPI, File, Form, UploadFile
from pydantic import BaseModel
import whisper

model = whisper.load_model("base")

app = FastAPI()
uploads_dir = Path("uploads")
uploads_dir.mkdir(exist_ok=True)

FRAUDSHIELD_URL = "http://127.0.0.1:5000/analyse-call"


class LoginRequest(BaseModel):
    phone_number: str
    password: str


@app.post("/login")
async def login(payload: LoginRequest):
    return {
        "status": "ok",
        "phone_number": payload.phone_number,
    }


@app.post("/upload")
async def test_upload(
    phone_number: str = Form(...),
    file: UploadFile = File(...),
):
    contents = await file.read()
    save_path = uploads_dir / file.filename

    with open(save_path, "wb") as saved_file:
        saved_file.write(contents)

    print(f"!!! Success: Received file named: {file.filename}")
    print(f"!!! Phone number: {phone_number}")

    result = model.transcribe(str(save_path), language="hi", task="transcribe")
    transcript_text = result["text"].strip()
    detected_language = result["language"]

    print(f"--- Transcript for {file.filename} (detected: {detected_language}) ---")
    print(transcript_text)

    txt_path = save_path.with_suffix(".txt")
    with open(txt_path, "w", encoding="utf-8") as transcript_file:
        transcript_file.write(f"Phone number: {phone_number}\n\n")
        transcript_file.write(transcript_text)

    fraudshield_payload = {
        "phone": phone_number,
        "transcript": transcript_text
    }

    fraudshield_result = None
    fraudshield_error = None

    try:
        req = urllib_request.Request(
            FRAUDSHIELD_URL,
            data=json.dumps(fraudshield_payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        with urllib_request.urlopen(req, timeout=20) as response:
            response_body = response.read().decode("utf-8")
            fraudshield_result = json.loads(response_body) if response_body else {}

    except error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        fraudshield_error = f"FraudShield HTTP {exc.code}: {error_body}"
    except error.URLError as exc:
        fraudshield_error = f"FraudShield unreachable: {exc.reason}"
    except Exception as exc:
        fraudshield_error = str(exc)

    return {
        "status": "ok",
        "phone_number": phone_number,
        "transcript": transcript_text,
        "detected_language": detected_language,
        "fraudshield_result": fraudshield_result,
        "fraudshield_error": fraudshield_error,
    }
