# ImageSplitter 1.4

Native Android application for splitting a photo into 2–20 numbered parts for larger prints. Native Android UI with no WebView. Printing tools work offline; optional AI Style Transfer requires an internet connection.

## Current features

- Pick an image from the Android document picker.
- Scroll through a fully native, hand-drawn interface with hidden scroll indicators.
- Choose horizontal, vertical or grid parts and preview their order with continuous image geometry.
- Long-press any output size for dimensions calculated from the real selected image.
- Export each part as PNG in `Pictures/ImageSplitter/<timestamp>/` at original, 750 px, 1080 px, or a custom longest side.
- EXIF-aware previews and exports keep phone photos visually upright.
- Optional accent-colored join marks at adjacent edges; disable them in Settings.
- Share individual parts or all parts via Android Sharesheet, including Bluetooth and nearby sharing apps if installed.
- Keep named local projects, rename/delete them, remove results, share them, and re-export from saved settings.
- Custom color wheel with recent colors, Arabic/English/Spanish interface, RTL, System/Light/Dark themes, animation and haptic controls.

## Build APK

GitHub → **Actions** → **Android APK** → latest successful run → **ImageSplitter-Debug-APK**. Download the artifact ZIP, extract it and install `ImageSplitter-debug.apk` on Android 10 or later.

The exported parts are individual images. Print them at the same physical scale and assemble in number order. The original image and project metadata stay on device; exporting writes PNG images into Android Pictures. Projects refer to those exported images, so removing the images from the device also removes the underlying project content.

## Source

- `app/src/main/java/com/imagesplitter/app/MainActivity.java`: native UI, projects and settings.
- `app/src/main/java/com/imagesplitter/app/SplitEngine.java`: memory conscious region decoding, slicing and MediaStore export.
- `app/src/main/res/drawable/brand_reference.png`: supplied three strip logo.
- `screen_reference.png`: supplied interface sketch.

The workflow runs split-math unit tests before building and uploading the APK. AI Style Transfer uses the `INTERNET` permission. Images leave the device when Generate is pressed (except Original / strength 0), or through an explicit Android share action. Cloudflare credentials remain on the backend and are never included in the APK.


## AI Style Transfer

Open **AI Style Transfer** below Split & Export. Upload or reuse the current image, choose a style card, adjust strength and generate. Compare with a draggable divider, save to Pictures/ImageSplitter, regenerate, change style, or use the result as a new source for splitting.

All Style Lab presets are available, plus Custom Style (up to 1500 characters) and a separate Style Reference image. Strength is expressed through backend prompting because FLUX.2 Klein has no direct strength parameter. Original and strength 0 preserve the original file locally without an AI request.

The real backend is https://imagesplitter-style-lab.zicozzr.chatgpt.site/api/generate using `@cf/black-forest-labs/flux-2-klein-4b`. Input JPEGs are EXIF oriented, compressed at quality 90, and resized to a maximum side of 504 pixels, without cropping. Output sizes follow the original ratio as closely as model multiples of 16 allow (256–1920); ratios above 7.5:1 are rejected rather than cropped. JPEG, PNG and WEBP originals up to 50 MB are supported. Transparent input is composited on white for the AI.

Preview assets in `app/src/main/assets/style-previews/` were generated with the built-in image generation tool from one cottage/lake/boat scene, then rendered in each named medium. They are labeled approximate examples and consume no Cloudflare quota during browsing. Prompt set: original countryside photograph; graphite/crosshatching; black ink; translucent watercolor; impasto oil; cel-shaded anime; outlined/halftone comic; chunky pixel art; stylized 3D miniature; handmade clay; mid-century travel poster; atmospheric graphic novel; smudged charcoal; painterly digital concept art. Every variant preserves the cottage, lake, red boat, trees and mountain composition, with no text or watermark.

Generation uses the Cloudflare account's shared quota; it is not unlimited and the public experimental backend currently has no per-user billing or durable app-wide quotas. Disabling repeated Generate taps is not a billing limit. Before a broad public app release, add authentication and persistent per-user limits to the backend. The model may change fine details or faces; preserving identity is a goal, not a guarantee.

Busy state, duplicate prevention, network/timeout/quota errors, invalid responses, local save errors and generation diagnostics are handled in the native screen.

Manual **Import Style Transfer and Build APK** workflow dispatch with Run Android emulator QA checked additionally runs the native workflow on an API 35 emulator, at 390×844 and 430×932, including one real Watercolor generation, comparison, save, Custom/Reference controls, Original bypass and Use in Splitter. This consumes one generation from the account quota; ordinary pushes only build and run local unit tests.
