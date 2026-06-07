# SmartBIM — Where Hands Speak

An interactive **Bahasa Isyarat Malaysia (BIM)** learning tool with built-in two-way
communication support, built for Android. SmartBIM teaches Malaysian Sign Language through
structured, hands-on practice with real-time feedback — not just by watching videos — and
doubles as a sign ↔ speech/text translator for everyday short conversations.

> Final Year Project · Bachelor of Computer Science (Hons) Software Engineering, UNITEN
> Muhammad Ikmal Bin Azis


![SmartBIM demo](assets/demo.gif)


## The Problem

Over 100,000 deaf Malaysians rely on BIM as their primary language, yet existing learning
tools fall short: video dictionaries (MyBIM, SLEM) give no feedback on whether *you* signed
correctly, gamified apps (ASL Bloom, PopSign) teach the wrong sign language entirely, and no
tool lets a learner practise BIM and verify their signing. SmartBIM closes that gap.

## Features

Six features mapped to three learning stages:

**Discover & Reference**
- **Speech / Text-to-Sign** — speak or type a word and see the BIM gesture video instantly
- **Words Library** — browse and search all BIM gestures by category

**Practice with Feedback**
- **Sign-to-Text** — perform a gesture in front of the camera; real-time recognition with a
  confidence score tells you whether you got it right

**Test & Apply**
- **Quiz Mode** — three difficulty levels for structured assessment
- **Sign Blitz** — a 60-second timed game with a top-5 leaderboard

## How It Works

**Gesture recognition pipeline (Sign-to-Text):**
Camera frame → MediaPipe Hand Landmarker (GPU delegate) → dual-hand landmark + curl extraction
→ 8-frame sliding window (start/end keyframes) → 262-feature vector → Random Forest classifier
(TFLite, knowledge-distilled) across 84 gesture classes. Inference runs in under 100 ms on a
low-end device (Samsung Galaxy A30).

**Dual-hand feature vector:** a single 262-feature model handles both one-hand and two-hand
gestures (the secondary hand slot is zero-filled when only one hand is used), avoiding the need
for two separate models.

**Context-aware translation:** BIM has its own grammar, so users sign naturally. Output is
translated only at the end — via Google Gemini (online) for natural Malay sentences, or a
rule-based SVO Arranger (offline fallback), with letter-by-letter fingerspelling for unknown
words.

## Tech Stack

| Area | Technology |
|------|-----------|
| Language / Platform | Kotlin, Android |
| Hand tracking | MediaPipe Hand Landmarker |
| Classification | TensorFlow Lite (Random Forest, knowledge-distilled) |
| Sentence construction | Google Gemini API (`gemini-2.5-flash`) + offline SVO Arranger |
| Video playback / cache | ExoPlayer (250 MB LRU cache) |
| Media delivery | Cloudinary CDN |
| Speech | Android `RecognizerIntent` (ms-MY / en-US) + Android TTS |
| Local storage | `gesture.json`, SharedPreferences |

## Architecture

Four layers, on-device first with cloud-assisted enrichment:

```
UI Layer            Camera Preview · Word Buffer · Video Player · Quiz/Game UI
Application Logic   MediaPipe Hand Landmarker · TFLite Classifier · SVO Arranger
Service & API       Google Gemini API · Cloudinary CDN · Android TTS
Data & Storage      gesture.json · ExoPlayer Cache · SharedPreferences
```

## Results

- **99.80%** model accuracy on 504 clean test samples (1 misclassification)
- **84** BIM gesture classes from the BIM Sign Bank
- **21/21** functional test cases passed, 0 critical defects
- Validated in the field at OTS Coffee (Dataran Merdeka) — a first-time user ordered a drink in
  BIM after ~1 minute of practice, understood by a deaf vendor without written clarification
- Methodology: split-before-augment (80/20) so the test set stays 100% original — accuracy
  reflects genuine generalisation, not data leakage

## Setup

> Requires Android Studio and a device/emulator running Android (tested on Samsung Galaxy A30).

```bash
git clone https://github.com/IkmalAzis/smartbim.git
```

1. Open the project in Android Studio and let Gradle sync.
2. Add your API keys to `local.properties` (this file is git-ignored — see Configuration below).
3. Run on a physical device with a camera for gesture recognition.

### Configuration (API keys)

SmartBIM uses the Google Gemini API for online sentence construction. **The key is not
included in the repo.** Add it to `local.properties` (this file is git-ignored):

```properties
GEMINI_API_KEY=your_gemini_api_key_here
```

`build.gradle` reads this into `BuildConfig.GEMINI_API_KEY` at build time, so the key never
appears in source. BIM gesture videos are delivered via public Cloudinary URLs stored in
`gesture.json` — no additional credentials required.

## Limitations & Future Work

**Current scope:** 84 gestures · minor RF→TFLite distillation gap · online dependency for
Gemini/Speech/CDN · recognition slower than fluent signing · single-environment training data.

**Planned:** full BIM vocabulary · multi-demographic training data · facial-expression
recognition · continuous sign recognition · on-device LLM for offline sentences · iOS + web.

## Acknowledgements

BIM gesture references from the Malaysian Sign Language and Deaf Studies Association and the BIM
Sign Bank. Field validation conducted with the deaf vendors of OTS Coffee.

## License

See `LICENSE` for details.
