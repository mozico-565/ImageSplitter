# ImageSplitter

Native Android application for splitting a photo into 2–20 numbered parts for larger prints. No WebView, HTML, account, internet permission, or paid service.

## Current features

- Pick an image from the Android document picker.
- Choose horizontal or vertical strips and preview their order.
- Export each part as PNG in `Pictures/ImageSplitter/<timestamp>/` at original, 750 px, 1080 px, or a custom longest side.
- Optional blue join marks at adjacent edges; disable them in Settings.
- Share individual parts or all parts via Android Sharesheet, including Bluetooth and nearby sharing apps if installed.
- Keep named local projects, each containing separate export groups and share them later.
- Accent color wheel, Arabic/English/Spanish interface, light and dark themes.

## Build APK

GitHub → **Actions** → **Android APK** → latest successful run → **ImageSplitter-debug-APK**. Download the artifact ZIP, extract it and install `app-debug.apk` on Android 10 or later.

The exported parts are individual images. Print them at the same physical scale and assemble in number order. The original image and project metadata stay on device; exporting writes PNG images into Android Pictures. Projects refer to those exported images, so removing the images from the device also removes the underlying project content.

## Source

- `app/src/main/java/com/imagesplitter/app/MainActivity.java`: native UI, projects and settings.
- `app/src/main/java/com/imagesplitter/app/SplitEngine.java`: memory conscious region decoding, slicing and MediaStore export.
- `app/src/main/res/drawable/brand_reference.png`: supplied three strip logo.
- `screen_reference.png`: supplied interface sketch.

First implementation. The initial design follows the sketch's structure and controls; device visual review and print alignment testing are still needed.
