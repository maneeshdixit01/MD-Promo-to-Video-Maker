# MD Promo to Video Maker

Offline-first Capacitor Android starter project.

## Current starter functionality
- Hindi/English prompt input
- Offline rule-based storyboard generation (5 scenes / 30 seconds)
- 9:16 and 16:9 format selection
- Local photo/video selection and preview
- Gemini integration intentionally left for the secure integration phase
- Native MP4 export intentionally not enabled yet; it must be implemented and tested with Android MediaCodec/MediaMuxer before claiming gallery export works.

## Build using GitHub Actions
1. Create a GitHub repository.
2. Upload the contents of this folder (not the outer folder).
3. Push to `main`.
4. GitHub Actions will generate the Android project and build a debug APK.
5. Download the APK from the workflow artifact.

## Important
Do not put a Gemini API key in this repository, HTML, JavaScript, or a public APK. A production Gemini integration should use a secure backend proxy or a deliberately designed user-managed secret flow. A Gemini consumer subscription login does not grant third-party apps access to consumer subscription quota as an API.

## Next implementation phases
1. Native Android media export: actual image/video composition, transitions, text overlays, audio mixing, MP4 muxing and gallery save.
2. Gemini API adapter with timeouts, error handling, response validation and automatic offline fallback.
3. Scene editor, audio controls, template library, render progress and cancellation.
