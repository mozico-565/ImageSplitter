package com.imagesplitter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private SharedPreferences prefs;
    private int accent, background, surface, text, muted, line;
    private boolean dark, horizontal = true;
    private int parts = 3, output = SplitEngine.PX_1080, custom = 1500;
    private Uri source;
    private Bitmap preview;
    private List<Uri> exports = new ArrayList<>();
    private String language = "en";
    private LinearLayout page;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        language = prefs.getString("language", "en");
        dark = prefs.getBoolean("dark", false);
        accent = prefs.getInt("accent", 0xFF2F80ED);
        updatePalette();
        home();
    }

    private void updatePalette() {
        background = dark ? 0xFF111820 : Color.WHITE;
        surface = dark ? 0xFF1C2833 : 0xFFF8FBFF;
        text = dark ? Color.WHITE : 0xFF171A20;
        muted = dark ? 0xFFB8C6D4 : 0xFF62676F;
        line = dark ? 0xFF677787 : 0xFF292B30;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private String tr(String en, String ar, String es) {
        return language.equals("ar") ? ar : language.equals("es") ? es : en;
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), border);
        return d;
    }

    private TextView label(String s, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(android.graphics.Typeface.create("casual", bold ? 1 : 0));
        return t;
    }

    private TextView button(String title, boolean selected, Runnable action) {
        TextView t = label(title, 17, false, selected ? Color.WHITE : text);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(54));
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setBackground(shape(selected ? accent : surface, selected ? accent : line, 15));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }
    private LinearLayout row() {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        return box;
    }
    private void gap(LinearLayout box, int height) {
        View v = new View(this);
        box.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void title(LinearLayout box, String value) {
        TextView t = label(value, 21, true, text);
        box.addView(t);
        gap(box, 12);
    }
    private void card(LinearLayout parent, View child) {
        LinearLayout wrap = column();
        wrap.setPadding(dp(16), dp(14), dp(16), dp(14));
        wrap.setBackground(shape(surface, line, 20));
        wrap.addView(child);
        parent.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
    }
    private void screen() {
        updatePalette();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        page = column();
        page.setPadding(dp(18), dp(14), dp(18), dp(26));
        page.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scroll.addView(page);
        setContentView(scroll);
    }

    private void home() {
        screen();
        LinearLayout header = row();
        header.addView(button("⚙", false, this::settings), new LinearLayout.LayoutParams(dp(55), dp(54)));
        TextView project = button(tr("Projects", "المشاريع", "Proyectos"), false, this::projects);
        LinearLayout.LayoutParams projectParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        projectParams.setMargins(dp(10), 0, dp(10), 0);
        header.addView(project, projectParams);
        header.addView(button("?", false, this::help), new LinearLayout.LayoutParams(dp(55), dp(54)));
        page.addView(header);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.imagesplitter.app.R.drawable.brand_reference);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(shape(Color.WHITE, dark ? line : Color.WHITE, 12));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(-1, dp(90));
        logoParams.topMargin = dp(6);
        page.addView(logo, logoParams);
        TextView name = label("Image Splitter", 30, true, text);
        name.setGravity(Gravity.CENTER);
        page.addView(name);
        TextView slogan = label(tr("Turn any image into a big print", "حوّل أي صورة إلى طباعة كبيرة",
                "Convierte cualquier imagen en una impresión grande"), 16, false, muted);
        slogan.setGravity(Gravity.CENTER);
        page.addView(slogan);
        gap(page, 17);

        LinearLayout picker = column();
        picker.setPadding(dp(14), dp(15), dp(14), dp(15));
        picker.setGravity(Gravity.CENTER);
        GradientDrawable pickBorder = shape(dark ? surface : 0xFFF0F7FF, accent, 22);
        pickBorder.setStroke(dp(1), accent, dp(8), dp(5));
        picker.setBackground(pickBorder);
        if (preview != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(preview);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            picker.addView(image, new LinearLayout.LayoutParams(-1, dp(145)));
        } else {
            TextView icon = label("▧ +", 42, false, accent);
            icon.setGravity(Gravity.CENTER);
            picker.addView(icon);
        }
        TextView choose = label(tr("Select an image", "اختر صورة", "Selecciona una imagen"), 21, true, text);
        choose.setGravity(Gravity.CENTER);
        picker.addView(choose);
        TextView tap = label(tr("Tap to choose from your gallery", "اضغط للاختيار من المعرض",
                "Toca para elegir de tu galería"), 15, false, muted);
        tap.setGravity(Gravity.CENTER);
        picker.addView(tap);
        picker.setOnClickListener(v -> pick());
        page.addView(picker);
        gap(page, 12);

        LinearLayout options = column();
        title(options, tr("Split Direction", "اتجاه التقسيم", "Dirección de corte"));
        LinearLayout direction = row();
        direction.addView(button(tr("☰  Horizontal", "☰  أفقي", "☰  Horizontal"), horizontal,
                () -> {horizontal = true; home();}), new LinearLayout.LayoutParams(0, dp(58), 1));
        gapHorizontal(direction, 8);
        direction.addView(button(tr("◫  Vertical", "◫  عمودي", "◫  Vertical"), !horizontal,
                () -> {horizontal = false; home();}), new LinearLayout.LayoutParams(0, dp(58), 1));
        options.addView(direction);
        gap(options, 18);
        TextView count = label(tr("Number of Parts: ", "عدد الأجزاء: ", "Número de partes: ") + parts, 19, true, text);
        options.addView(count);
        SeekBar seek = new SeekBar(this);
        seek.setMax(18);
        seek.setProgress(parts - 2);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                parts = p + 2;
                count.setText(tr("Number of Parts: ", "عدد الأجزاء: ", "Número de partes: ") + parts);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) { home(); }
        });
        options.addView(seek);
        LinearLayout range = row();
        range.addView(label("2", 14, false, muted), new LinearLayout.LayoutParams(0, -2, 1));
        range.addView(label("20", 14, false, muted));
        options.addView(range);
        card(page, options);
        gap(page, 12);

        LinearLayout sizes = column();
        title(sizes, tr("Output Size (Longest Side)", "مقاس الإخراج (الضلع الأطول)",
                "Tamaño de salida (lado largo)"));
        LinearLayout sizeRow = row();
        int[] values = {0, 750, 1080, -1};
        String[] titles = {tr("Original", "الأصلي", "Original"), "750 px", "1080 px",
                tr("Custom", "مخصص", "Personalizado")};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            sizeRow.addView(button(titles[i], output == values[i], () -> {
                if (index == 3) askCustom();
                else { output = values[index]; home(); }
            }), new LinearLayout.LayoutParams(0, dp(56), 1));
            if (i < 3) gapHorizontal(sizeRow, 5);
        }
        sizes.addView(sizeRow);
        card(page, sizes);
        gap(page, 12);

        LinearLayout previewCard = column();
        title(previewCard, tr("Preview", "المعاينة", "Vista previa"));
        if (preview == null) {
            TextView empty = label(tr("Choose an image to see the split", "اختر صورة لرؤية التقسيم",
                    "Selecciona una imagen para ver el corte"), 16, false, muted);
            empty.setGravity(Gravity.CENTER);
            previewCard.addView(empty, new LinearLayout.LayoutParams(-1, dp(120)));
        } else {
            previewCard.addView(new PreviewView(), new LinearLayout.LayoutParams(-1, dp(220)));
            try {
                int[] d = SplitEngine.dimensions(getContentResolver(), source);
                int[] part = SplitEngine.outputDimensions(
                        SplitEngine.partRect(d[0], d[1], horizontal, parts, 0),
                        output == -1 ? custom : output);
                TextView dimensions = label(tr("Each part about ", "كل جزء تقريبًا ", "Cada parte aprox. ")
                        + part[0] + " × " + part[1] + " px", 14, false, muted);
                dimensions.setGravity(Gravity.CENTER);
                previewCard.addView(dimensions);
            } catch (Exception ignored) {}
        }
        card(page, previewCard);
        gap(page, 16);
        page.addView(button(tr("▱  Split & Export", "▱  تقسيم وتصدير", "▱  Cortar y exportar"),
                true, this::export), new LinearLayout.LayoutParams(-1, dp(65)));
    }

    private void gapHorizontal(LinearLayout box, int width) {
        box.addView(new View(this), new LinearLayout.LayoutParams(dp(width), 1));
    }

    private void pick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        source = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(source)) {
                BitmapFactory.decodeStream(input, null, opts);
            }
            if (opts.outWidth < 2 || opts.outHeight < 2) throw new Exception("Unsupported image");
            opts.inSampleSize = Math.max(1, (int)Math.ceil(Math.max(opts.outWidth, opts.outHeight) / 900.0));
            opts.inJustDecodeBounds = false;
            if (preview != null) preview.recycle();
            try (InputStream input = getContentResolver().openInputStream(source)) {
                preview = BitmapFactory.decodeStream(input, null, opts);
            }
            if (preview == null) throw new Exception("Unable to read image");
            home();
        } catch (Exception e) {
            source = null;
            toast(e.getMessage());
        }
    }

    private void askCustom() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(custom));
        new AlertDialog.Builder(this)
                .setTitle(tr("Longest side in pixels (100–8000)", "الضلع الأطول بالبكسل (100–8000)",
                        "Lado largo en píxeles (100–8000)"))
                .setView(input)
                .setNegativeButton(tr("Cancel", "إلغاء", "Cancelar"), null)
                .setPositiveButton(tr("Apply", "تطبيق", "Aplicar"), (d, w) -> {
                    try {
                        int value = Integer.parseInt(input.getText().toString().trim());
                        if (value < 100 || value > 8000) throw new NumberFormatException();
                        custom = value;
                        output = -1;
                        home();
                    } catch (NumberFormatException e) { toast("100–8000 px"); }
                }).show();
    }

    private void export() {
        if (source == null) { toast(tr("Choose an image first", "اختر صورة أولًا",
                "Selecciona una imagen primero")); return; }
        final Uri selected = source;
        final int count = parts, target = output == -1 ? custom : output;
        final boolean direction = horizontal, hints = prefs.getBoolean("hints", true);
        final String folder = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        toast(tr("Exporting…", "جارٍ التصدير…", "Exportando…"));
        new Thread(() -> {
            try {
                List<Uri> result = SplitEngine.export(getContentResolver(), selected, count,
                        direction, target, hints, folder);
                runOnUiThread(() -> { exports = result; resultScreen(); });
            } catch (Exception e) {
                runOnUiThread(() -> toast(e.getMessage() == null ? "Export failed" : e.getMessage()));
            }
        }).start();
    }

    private void resultScreen() {
        screen();
        page.addView(button("←  " + tr("Back", "رجوع", "Volver"), false, this::home));
        gap(page, 18);
        title(page, tr("Your image is ready", "صورتك جاهزة", "Tu imagen está lista"));
        TextView saved = label(tr("Saved to Pictures/ImageSplitter · ", "حُفظت في الصور/ImageSplitter · ",
                "Guardada en Imágenes/ImageSplitter · ") + exports.size(), 16, false, muted);
        page.addView(saved);
        gap(page, 16);
        for (int i = 0; i < exports.size(); i++) {
            final Uri uri = exports.get(i);
            LinearLayout item = row();
            TextView number = label(tr("Part ", "الجزء ", "Parte ") + (i + 1), 17, true, text);
            item.addView(number, new LinearLayout.LayoutParams(0, dp(54), 1));
            item.addView(button(tr("Share", "مشاركة", "Compartir"), false, () -> shareOne(uri)));
            card(page, item);
            gap(page, 8);
        }
        gap(page, 10);
        page.addView(button(tr("Add to project", "إضافة لمشروع", "Añadir al proyecto"), true,
                this::chooseProject), new LinearLayout.LayoutParams(-1, dp(60)));
        gap(page, 10);
        page.addView(button(tr("Share all parts", "مشاركة كل الأجزاء", "Compartir todas las partes"),
                false, () -> share(exports)));
    }

    private void shareOne(Uri uri) { share(java.util.Collections.singletonList(uri)); }
    private void share(List<Uri> images) {
        Intent intent = new Intent(images.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType("image/png");
        if (images.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, images.get(0));
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(images));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, tr("Share parts", "مشاركة الأجزاء", "Compartir partes")));
    }

    private JSONArray storedProjects() {
        try { return new JSONArray(prefs.getString("projects", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    private void chooseProject() {
        JSONArray projects = storedProjects();
        String[] names = new String[projects.length() + 1];
        names[0] = tr("+ New project", "+ مشروع جديد", "+ Nuevo proyecto");
        for (int i = 0; i < projects.length(); i++)
            names[i + 1] = projects.optJSONObject(i).optString("name");
        new AlertDialog.Builder(this).setTitle(tr("Add to project", "إضافة لمشروع", "Añadir al proyecto"))
                .setItems(names, (dialog, which) -> {
                    if (which == 0) newProject(true);
                    else saveProject(which - 1);
                }).show();
    }
    private void newProject(boolean addCurrent) {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setHint(tr("Project name", "اسم المشروع", "Nombre del proyecto"));
        new AlertDialog.Builder(this).setTitle(tr("New project", "مشروع جديد", "Nuevo proyecto"))
                .setView(input)
                .setNegativeButton(tr("Cancel", "إلغاء", "Cancelar"), null)
                .setPositiveButton(tr("Create", "إنشاء", "Crear"), (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { toast(tr("Enter a name", "أدخل اسمًا", "Introduce un nombre")); return; }
                    try {
                        JSONArray projects = storedProjects();
                        JSONObject project = new JSONObject();
                        project.put("name", name);
                        project.put("items", new JSONArray());
                        projects.put(project);
                        prefs.edit().putString("projects", projects.toString()).apply();
                        if (addCurrent) saveProject(projects.length() - 1);
                        else projects();
                    } catch (Exception e) { toast(e.getMessage()); }
                }).show();
    }
    private void saveProject(int index) {
        try {
            JSONArray projects = storedProjects();
            JSONObject project = projects.getJSONObject(index);
            JSONArray items = project.getJSONArray("items");
            JSONObject item = new JSONObject();
            item.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            JSONArray uris = new JSONArray();
            for (Uri uri : exports) uris.put(uri.toString());
            item.put("uris", uris);
            items.put(item);
            prefs.edit().putString("projects", projects.toString()).apply();
            toast(tr("Added to project", "أُضيف للمشروع", "Añadido al proyecto"));
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void projects() {
        screen();
        page.addView(button("←  " + tr("Home", "الرئيسية", "Inicio"), false, this::home));
        gap(page, 18);
        title(page, tr("Projects", "المشاريع", "Proyectos"));
        page.addView(button("+  " + tr("New project", "مشروع جديد", "Nuevo proyecto"), true,
                () -> newProject(false)));
        gap(page, 14);
        JSONArray projects = storedProjects();
        for (int i = 0; i < projects.length(); i++) {
            final int index = i;
            JSONObject project = projects.optJSONObject(i);
            if (project == null) continue;
            int count = project.optJSONArray("items").length();
            TextView entry = button(project.optString("name") + "  ·  " + count, false,
                    () -> projectDetail(index));
            page.addView(entry);
            gap(page, 8);
        }
    }
    private void projectDetail(int index) {
        JSONArray projects = storedProjects();
        JSONObject project = projects.optJSONObject(index);
        if (project == null) { projects(); return; }
        screen();
        page.addView(button("←  " + tr("Projects", "المشاريع", "Proyectos"), false, this::projects));
        gap(page, 16);
        title(page, project.optString("name"));
        JSONArray items = project.optJSONArray("items");
        if (items == null) return;
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            List<Uri> images = new ArrayList<>();
            JSONArray uris = item.optJSONArray("uris");
            if (uris == null) continue;
            for (int j = 0; j < uris.length(); j++) images.add(Uri.parse(uris.optString(j)));
            LinearLayout entry = row();
            entry.addView(label(item.optString("date") + "  ·  " + images.size(), 16, false, text),
                    new LinearLayout.LayoutParams(0, -2, 1));
            entry.addView(button(tr("Share", "مشاركة", "Compartir"), false, () -> share(images)));
            card(page, entry);
            gap(page, 8);
        }
    }

    private void settings() {
        screen();
        page.addView(button("←  " + tr("Home", "الرئيسية", "Inicio"), false, this::home));
        gap(page, 18);
        title(page, tr("Settings", "الإعدادات", "Ajustes"));
        title(page, tr("Accent color", "لون التطبيق", "Color de acento"));
        TextView sample = label("●  " + tr("Drag in the color wheel", "اسحب داخل دائرة الألوان",
                "Arrastra en el círculo de colores"), 16, false, accent);
        page.addView(sample);
        gap(page, 8);
        page.addView(new ColorWheel(), new LinearLayout.LayoutParams(-1, dp(240)));
        gap(page, 18);
        title(page, tr("Language", "اللغة", "Idioma"));
        for (String[] option : new String[][]{{"ar", "العربية"}, {"en", "English"}, {"es", "Español"}})
            page.addView(button((language.equals(option[0]) ? "✓  " : "") + option[1],
                    language.equals(option[0]), () -> {
                        language = option[0];
                        prefs.edit().putString("language", language).apply();
                        settings();
                    }));
        gap(page, 14);
        title(page, tr("Appearance", "المظهر", "Apariencia"));
        page.addView(button(dark ? tr("☾ Dark mode: On", "☾ الوضع الليلي: مفعّل", "☾ Modo oscuro: Sí")
                : tr("☀ Light mode: On", "☀ الوضع النهاري: مفعّل", "☀ Modo claro: Sí"), false,
                () -> {
                    dark = !dark;
                    prefs.edit().putBoolean("dark", dark).apply();
                    settings();
                }));
        gap(page, 12);
        boolean hints = prefs.getBoolean("hints", true);
        page.addView(button((hints ? "✓  " : "○  ") +
                tr("Join position hints on exports", "تلميح موضع اللصق على الأجزاء",
                        "Marcas de unión en exportaciones"), false, () -> {
            prefs.edit().putBoolean("hints", !prefs.getBoolean("hints", true)).apply();
            settings();
        }));
        gap(page, 12);
        TextView note = label(tr("Images remain on your device. No account or internet is required.",
                "الصور تبقى على جهازك. لا تحتاج إلى حساب أو إنترنت.",
                "Las imágenes permanecen en tu dispositivo. No necesitas cuenta ni internet."),
                14, false, muted);
        page.addView(note);
    }

    private void help() {
        new AlertDialog.Builder(this).setTitle(tr("How to print", "طريقة الطباعة", "Cómo imprimir"))
                .setMessage(tr("Pick a photo, choose the direction and number of parts, then export. Print each PNG at the same scale and join the numbered edges. Blue ticks mark matching joins when enabled in Settings.",
                        "اختر الصورة والاتجاه وعدد الأجزاء، ثم صدّرها. اطبع كل جزء بالمقياس نفسه واجمع الحواف حسب ترتيب الأرقام. العلامات الزرقاء تساعد في المحاذاة ويمكن إيقافها من الإعدادات.",
                        "Elige una imagen, la dirección y las partes; exporta. Imprime cada PNG a la misma escala y une los bordes numerados. Las marcas azules ayudan a alinearlos."))
                .setPositiveButton(tr("OK", "حسنًا", "Aceptar"), null).show();
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private final class PreviewView extends View {
        private final Paint paint = new Paint(3);
        PreviewView() { super(MainActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (preview == null) return;
            float scale = Math.min((getWidth() - dp(24f)) / (float)preview.getWidth(),
                    (getHeight() - dp(14f)) / (float)preview.getHeight());
            float w = preview.getWidth() * scale, h = preview.getHeight() * scale;
            float x = (getWidth() - w) / 2f, y = (getHeight() - h) / 2f;
            for (int i = 0; i < parts; i++) {
                Rect src = SplitEngine.partRect(preview.getWidth(), preview.getHeight(), horizontal, parts, i);
                RectF dst = new RectF(x + src.left * scale, y + src.top * scale,
                        x + src.right * scale, y + src.bottom * scale);
                canvas.drawBitmap(preview, src, dst, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(line);
                paint.setStrokeWidth(dp(1));
                canvas.drawRoundRect(dst, dp(5), dp(5), paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accent);
                canvas.drawCircle(dst.left + dp(13), dst.top + dp(13), dp(12), paint);
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(13));
                canvas.drawText(String.valueOf(i + 1), dst.left + dp(13), dst.top + dp(18), paint);
            }
        }
    }

    private final class ColorWheel extends View {
        private final Paint paint = new Paint(3);
        ColorWheel() { super(MainActivity.this); setContentDescription(tr("Color wheel", "دائرة الألوان", "Rueda de colores")); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(8);
            for (int i = 0; i < 360; i++) {
                paint.setColor(Color.HSVToColor(new float[]{i, 1f, 1f}));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(18));
                RectF ring = new RectF(cx - radius + dp(9), cy - radius + dp(9),
                        cx + radius - dp(9), cy + radius - dp(9));
                canvas.drawArc(ring, i - 0.5f, 1.5f, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            canvas.drawCircle(cx, cy, radius * .57f, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(18));
            canvas.drawText(String.format(Locale.US, "#%06X", accent & 0xFFFFFF),
                    cx, cy + dp(6), paint);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN &&
                    event.getAction() != android.view.MotionEvent.ACTION_MOVE) return true;
            float x = event.getX() - getWidth() / 2f, y = event.getY() - getHeight() / 2f;
            float hue = (float)((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            float saturation = Math.max(.3f, Math.min(1f, (float)Math.hypot(x, y) / (radius * .8f)));
            accent = Color.HSVToColor(new float[]{hue, saturation, 1f});
            prefs.edit().putInt("accent", accent).apply();
            invalidate();
            return true;
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        home();
    }
}
