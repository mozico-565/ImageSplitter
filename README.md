# ImageSplitter 1.0

Native Android application for splitting a photo into 2–20 numbered parts for larger prints. No WebView, HTML, account, internet permission, or paid service.

## Current features

- Pick an image from the Android document picker.
- Scroll through a fully native, hand-drawn interface with hidden scroll indicators.
- Choose horizontal or vertical strips and preview their order with continuous image geometry.
- Long-press any output size for dimensions calculated from the real selected image.
- Export each part as PNG in `Pictures/ImageSplitter/<timestamp>/` at original, 750 px, 1080 px, or a custom longest side.
- EXIF-aware previews and exports keep phone photos visually upright.
- Optional accent-colored join marks at adjacent edges; disable them in Settings.
- Share individual parts or all parts via Android Sharesheet, including Bluetooth and nearby sharing apps if installed.
- Keep named local projects, rename/delete them, remove results, share them, and re-export from saved settings.
- Custom color wheel with recent colors, Arabic/English/Spanish interface, RTL, System/Light/Dark themes, animation and haptic controls.

## Build APK

GitHub → **Actions** → **Android APK** → latest successful run → **ImageSplitter-APK**. Download the artifact ZIP, extract it and install `ImageSplitter-debug.apk` on Android 10 or later.

The exported parts are individual images. Print them at the same physical scale and assemble in number order. The original image and project metadata stay on device; exporting writes PNG images into Android Pictures. Projects refer to those exported images, so removing the images from the device also removes the underlying project content.

## Source

- `app/src/main/java/com/imagesplitter/app/MainActivity.java`: native UI, projects and settings.
- `app/src/main/java/com/imagesplitter/app/SplitEngine.java`: memory conscious region decoding, slicing and MediaStore export.
- `app/src/main/res/drawable/brand_reference.png`: supplied three strip logo.
- `screen_reference.png`: supplied interface sketch.

The workflow runs split-math unit tests before building and uploading the APK. The application has no `INTERNET` permission; images leave the device only through an explicit Android share action.
