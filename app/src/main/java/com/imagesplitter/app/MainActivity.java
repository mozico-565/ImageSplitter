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
    private void home() { showSketch(0); }

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

    private void askCustom() { sketchInput(tr("Longest side in pixels (100–8000)", "الضلع الأطول بالبكسل (100–8000)", "Lado largo en píxeles (100–8000)"), "100–8000 px", String.valueOf(custom), true, value -> { try { int n=Integer.parseInt(value); if(n<100||n>8000) throw new NumberFormatException(); custom=n; output=-1; home(); } catch(NumberFormatException e) { toast("100–8000 px"); } }); }

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

    private void resultScreen() { resultPage=0; showSketch(4); }

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
    private void chooseProject() { JSONArray projects=storedProjects(); String[] names=new String[Math.min(4,projects.length())+1]; names[0]=tr("+ New project", "+ مشروع جديد", "+ Nuevo proyecto"); for(int i=1;i<names.length;i++) names[i]=projects.optJSONObject(i-1).optString("name"); sketchDialog(tr("Add to project", "إضافة لمشروع", "Añadir al proyecto"), null, names, index -> { if(index==0) newProject(true); else saveProject(index-1); }); }
    private void newProject(boolean addCurrent) { sketchInput(tr("New project", "مشروع جديد", "Nuevo proyecto"), tr("Project name", "اسم المشروع", "Nombre del proyecto"), "", false, name -> { if(name.isEmpty()) { toast(tr("Enter a name", "أدخل اسمًا", "Introduce un nombre")); return; } try { JSONArray projects=storedProjects(); JSONObject project=new JSONObject(); project.put("name",name); project.put("items",new JSONArray()); projects.put(project); prefs.edit().putString("projects",projects.toString()).apply(); if(addCurrent) saveProject(projects.length()-1); else projects(); } catch(Exception e) { toast(e.getMessage()); } }); }

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

    private void projects() { listPage=0; showSketch(2); }
private void projectDetail(int index) { detailIndex=index; listPage=0; showSketch(3); }
private void settings() { showSketch(1); }

private void help() { sketchDialog(tr("How to print", "طريقة الطباعة", "Cómo imprimir"), tr("Pick a photo, choose the direction and number of parts, then export. Print each PNG at the same scale and join the numbered edges. Blue ticks mark matching joins when enabled in Settings.", "اختر الصورة والاتجاه وعدد الأجزاء، ثم صدّرها. اطبع كل جزء بالمقياس نفسه واجمع الحواف حسب ترتيب الأرقام. العلامات الزرقاء تساعد في المحاذاة ويمكن إيقافها من الإعدادات.", "Elige una imagen, la dirección y las partes; exporta. Imprime cada PNG a la misma escala y une los bordes numerados."), new String[]{tr("OK", "حسنًا", "Aceptar")}, index -> {}); }

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
    private int sketchMode = 0, detailIndex = 0, listPage = 0, resultPage = 0;
    private boolean previewExpanded = false, sizeTip = true;
    private SketchScreen sketchScreen;

    private void showSketch(int mode) {
        updatePalette();
        sketchMode = mode;
        sketchScreen = new SketchScreen();
        setContentView(sketchScreen);
    }

    private final class SketchScreen extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.ArrayList<Zone> zones = new java.util.ArrayList<>();
        private final android.graphics.Typeface handwriting =
                android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL);
        private final Bitmap brand = BitmapFactory.decodeResource(getResources(), R.drawable.brand_reference);
        private final long start = android.os.SystemClock.uptimeMillis();
        private float sx, sy, touchX, touchY;
        private boolean draggingSlider, draggingColor;

        SketchScreen() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("Image Splitter");
        }

        @Override protected void onDraw(Canvas actual) {
            super.onDraw(actual);
            sx = getWidth() / 720f;
            sy = getHeight() / 1400f;
            zones.clear();
            actual.drawColor(background);
            actual.save();
            actual.scale(sx, sy);
            if (sketchMode == 0) homeArt(actual);
            else if (sketchMode == 1) settingsArt(actual);
            else if (sketchMode == 2) projectsArt(actual);
            else if (sketchMode == 3) detailArt(actual);
            else resultArt(actual);
            actual.restore();
            if (android.os.SystemClock.uptimeMillis() - start < 980) postInvalidateDelayed(16);
        }

        private int ink() { return dark ? 0xFFF0F4F8 : 0xFF17191C; }
        private int paper() { return dark ? 0xFF17232F : 0xFFFFFFFF; }
        private int wash() { return dark ? 0xFF1D3042 : 0xFFF1F8FF; }
        private void fill(Canvas c, float l, float t, float r, float b, float radius, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRoundRect(l, t, r, b, radius, radius, p);
        }
        private void outline(Canvas c, float l, float t, float r, float b, float radius, int color) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setColor(color);
            c.drawRoundRect(l, t, r, b, radius, radius, p);
            p.setStrokeWidth(.45f);
            p.setColor((color & 0x00FFFFFF) | 0x57000000);
            c.drawRoundRect(l + 1.2f, t - .65f, r + .35f, b + .8f, radius, radius, p);
            p.setStyle(Paint.Style.FILL);
        }
        private void box(Canvas c, float l, float t, float r, float b, float radius, int color) {
            fill(c, l, t, r, b, radius, color);
            outline(c, l, t, r, b, radius, ink());
        }
        private void line(Canvas c, float x, float y, float x2, float y2, int color, float stroke) {
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x, y, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }
        private void txt(Canvas c, String value, float center, float baseline, float maxWidth,
                         float size, int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            p.setTypeface(handwriting); p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size);
            float measured = p.measureText(value);
            if (measured > maxWidth && measured > 0) p.setTextSize(size * maxWidth / measured);
            c.drawText(value, center, baseline, p);
        }
        private void left(Canvas c, String value, float x, float baseline, float maxWidth,
                          float size, int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            p.setTypeface(handwriting);
            p.setTextAlign(language.equals("ar") ? Paint.Align.RIGHT : Paint.Align.LEFT);
            p.setTextSize(size);
            float measured = p.measureText(value);
            if (measured > maxWidth && measured > 0) p.setTextSize(size * maxWidth / measured);
            c.drawText(value, language.equals("ar") ? 720 - x : x, baseline, p);
        }
        private void buttonArt(Canvas c, String value, float l, float t, float r, float b,
                               boolean active, Runnable click) {
            fill(c, l, t, r, b, 14, active ? accent : paper());
            outline(c, l, t, r, b, 14, active ? accent : ink());
            txt(c, value, (l+r)/2f, (t+b)/2f + 8, r-l-15, 23,
                    active ? Color.WHITE : ink());
            hit(l,t,r,b,click);
        }
        private void hit(float l,float t,float r,float b,Runnable action) {
            zones.add(new Zone(new RectF(l,t,r,b),action));
        }
        private void group(Canvas c, int n, Runnable draw) {
            float progress = Math.max(0f, Math.min(1f,
                    (android.os.SystemClock.uptimeMillis() - start - n*105f) / 440f));
            if (progress <= 0) return;
            float smooth = 1f - (1f-progress)*(1f-progress);
            c.save();
            c.translate(0, (1f-smooth)*24f);
            int layer = c.saveLayerAlpha(0,0,720,1400,(int)(255*smooth));
            draw.run();
            c.restoreToCount(layer);
            c.restore();
        }
        private void brand(Canvas c,float l,float t,float r,float b) {
            if (brand == null) return;
            p.setColor(Color.WHITE);
            c.drawBitmap(brand, null, new RectF(l,t,r,b),p);
        }
        private void gear(Canvas c,float x,float y) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);p.setColor(ink());
            c.drawCircle(x,y,12,p);c.drawCircle(x,y,4,p);
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4;
                c.drawLine(x+(float)Math.cos(a)*13,y+(float)Math.sin(a)*13,
                        x+(float)Math.cos(a)*18,y+(float)Math.sin(a)*18,p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        private void bulb(Canvas c,float x,float y) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(ink());
            c.drawCircle(x,y-4,12,p);c.drawLine(x-7,y+9,x+7,y+9,p);
            c.drawLine(x-5,y+14,x+5,y+14,p);p.setStyle(Paint.Style.FILL);
        }
        private void pictureIcon(Canvas c,float x,float y) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(ink());
            c.drawRoundRect(x-35,y-30,x+32,y+30,4,4,p);
            c.drawCircle(x+16,y-16,5,p);
            android.graphics.Path mountain=new android.graphics.Path();
            mountain.moveTo(x-33,y+18); mountain.lineTo(x-9,y-6);
            mountain.lineTo(x+5,y+9); mountain.lineTo(x+15,y+1);
            mountain.lineTo(x+30,y+19);
            c.drawPath(mountain,p);p.setStyle(Paint.Style.FILL);
            p.setColor(accent);c.drawCircle(x+36,y+26,15,p);
            line(c,x+29,y+26,x+43,y+26,Color.WHITE,2.6f);
            line(c,x+36,y+19,x+36,y+33,Color.WHITE,2.6f);
        }
        private void dashed(Canvas c,float l,float t,float r,float b) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f);p.setColor(ink());
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{7,6},0));
            c.drawRoundRect(l,t,r,b,20,20,p);
            p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        }
        private void homeArt(Canvas c) {
            group(c,0,()->{
                buttonArt(c,"",22,12,76,66,false,MainActivity.this::settings);
                gear(c,49,39);
                buttonArt(c,"▤  "+tr("Projects","المشاريع","Proyectos"),
                        90,12,630,66,false,MainActivity.this::projects);
                buttonArt(c,"",644,12,698,66,false,MainActivity.this::help);
                bulb(c,671,39);
                brand(c,310,72,410,172);
                txt(c,"Image Splitter",360,192,670,34,ink());
                txt(c,tr("Turn any image into a big print","حوّل أي صورة إلى طباعة كبيرة",
                        "Convierte cualquier imagen en una impresión grande"),
                        360,226,660,23,muted);
            });
            group(c,1,()->{
                fill(c,22,248,698,430,23,wash());
                outline(c,22,248,698,430,23,ink());
                dashed(c,33,259,687,419);
                if(preview!=null) {
                    p.setColor(Color.WHITE);
                    float ratio=(float)preview.getWidth()/preview.getHeight();
                    float w=Math.min(140,ratio*110);
                    c.drawBitmap(preview,null,new RectF(360-w/2,270,360+w/2,345),p);
                } else pictureIcon(c,360,315);
                txt(c,tr("Select an image","اختر صورة","Selecciona una imagen"),
                        360,375,620,29,ink());
                txt(c,tr("Tap to choose from your gallery","اضغط للاختيار من المعرض",
                        "Toca para elegir de tu galería"),360,404,625,21,muted);
                hit(22,248,698,430,MainActivity.this::pick);
            });
            group(c,2,()->{
                box(c,22,443,698,694,22,paper());
                left(c,tr("Split Direction","اتجاه التقسيم","Dirección de corte"),
                        42,476,620,27,ink());
                buttonArt(c,tr("☰  Horizontal","☰  أفقي","☰  Horizontal"),
                        40,492,356,554,horizontal,()->{ horizontal=true;invalidate();});
                buttonArt(c,tr("◫  Vertical","◫  عمودي","◫  Vertical"),
                        364,492,680,554,!horizontal,()->{horizontal=false;invalidate();});
                left(c,tr("Number of Parts","عدد الأجزاء","Número de partes"),
                        42,595,380,24,ink());
                float knobX=45+(parts-2)*631f/18;
                fill(c,knobX-19,564,knobX+19,601,9,accent);
                txt(c,String.valueOf(parts),knobX,591,35,23,Color.WHITE);
                line(c,45,633,676,633,0xFFCED4D9,3f);
                line(c,45,633,45+(parts-2)*631f/18,633,accent,5f);
                p.setColor(paper());p.setStyle(Paint.Style.FILL);
                c.drawCircle(45+(parts-2)*631f/18,633,13,p);
                p.setColor(ink());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);
                c.drawCircle(45+(parts-2)*631f/18,633,13,p);p.setStyle(Paint.Style.FILL);
                left(c,"2",42,669,50,19,ink());txt(c,"20",665,669,50,19,ink());
                hit(35,608,685,660,()->{});
            });
            group(c,3,()->{
                box(c,22,710,698,841,22,paper());
                left(c,tr("Output Size (Longest Side)","مقاس الإخراج (الضلع الأطول)",
                        "Tamaño de salida (lado largo)"),42,746,620,24,ink());
                int[] values={0,750,1080,-1};
                String[] labels={tr("Original","الأصلي","Original"),"750 px","1080 px",
                        tr("Custom","مخصص","Personalizado")};
                for(int i=0;i<4;i++) {
                    final int choice=i;
                    float x=40+i*161f;
                    buttonArt(c,labels[i],x,762,x+151,820,output==values[i],()->{
                        if(choice==3) askCustom(); else {output=values[choice];sizeTip=true;invalidate();}
                    });
                }
            });
            group(c,4,()->{
                box(c,22,855,698,1265,22,paper());
                left(c,tr("Preview","المعاينة","Vista previa"),42,890,350,27,ink());
                previewTiles(c,preview!=null?preview:brand,116,936,545,1228);
                buttonArt(c,tr("⌗ Fit Screen","⌗ ملء الشاشة","⌗ Ajustar"),
                        551,1063,678,1115,false,()->{previewExpanded=!previewExpanded;invalidate();});
            });
            group(c,5,()->{
                buttonArt(c,tr("▱   Split & Export","▱   تقسيم وتصدير",
                        "▱   Cortar y exportar"),22,1283,698,1370,true,MainActivity.this::export);
            });
            if(sizeTip) {
                fill(c,190,834,690,1000,19,dark?0xFF263A4C:0xFFF5FAFF);
                outline(c,190,834,690,1000,19,accent);
                txt(c,tr("Example size","مثال على المقاس","Tamaño de ejemplo"),
                        440,868,470,22,ink());
                txt(c,(output==0?tr("Original size","المقاس الأصلي","Tamaño original"):
                        (output==-1?custom:output)+" px")+
                        "  ×  "+parts+" "+tr("parts","أجزاء","partes"),440,912,465,25,ink());
                txt(c,tr("Tap to close","اضغط للإغلاق","Toca para cerrar"),440,960,470,18,muted);
                hit(190,834,690,1000,()->{sizeTip=false;invalidate();});
            }
            if(previewExpanded) {
                fill(c,20,80,700,1330,24,paper());
                outline(c,20,80,700,1330,24,ink());
                txt(c,tr("Preview · tap to close","المعاينة · اضغط للإغلاق",
                        "Vista previa · toca para cerrar"),360,140,650,26,ink());
                previewTiles(c,preview!=null?preview:brand,45,190,675,1210);
                hit(20,80,700,1330,()->{previewExpanded=false;invalidate();});
            }
        }
        private void previewTiles(Canvas c,Bitmap bmp,float l,float t,float r,float b) {
            if(bmp==null)return;
            int n=Math.min(parts,20);
            float gap=4;
            for(int i=0;i<n;i++){
                Rect src=SplitEngine.partRect(bmp.getWidth(),bmp.getHeight(),horizontal,n,i);
                float x1=horizontal?l:l+(r-l)*i/n+gap;
                float y1=horizontal?t+(b-t)*i/n+gap:t;
                float x2=horizontal?r:l+(r-l)*(i+1)/n-gap;
                float y2=horizontal?t+(b-t)*(i+1)/n-gap:b;
                if(x2<=x1||y2<=y1)continue;
                c.save();c.clipRect(x1,y1,x2,y2);
                p.setColor(Color.WHITE);
                c.drawBitmap(bmp,src,new RectF(x1,y1,x2,y2),p);
                c.restore();
                outline(c,x1,y1,x2,y2,5,ink());
            }
        }
        private void chrome(Canvas c,String heading,Runnable back) {
            buttonArt(c,"←",22,18,86,82,false,back);
            txt(c,heading,360,66,520,36,ink());
            line(c,25,108,695,108,ink(),1.5f);
        }
        private void settingsArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Settings","الإعدادات","Ajustes"),MainActivity.this::home));
            group(c,1,()->{
                box(c,22,140,698,510,22,paper());
                left(c,tr("Accent color","لون التطبيق","Color de acento"),48,185,620,29,ink());
                txt(c,tr("Choose any color","اختر أي لون","Elige cualquier color"),
                        360,223,620,22,muted);
                for(int i=0;i<360;i+=2){
                    p.setColor(Color.HSVToColor(new float[]{i,1,1}));
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(22);
                    c.drawArc(new RectF(241,251,479,489),i,2.5f,false,p);
                }
                p.setStyle(Paint.Style.FILL);p.setColor(paper());
                c.drawCircle(360,370,94,p);
                p.setColor(accent);c.drawCircle(360,370,75,p);
                txt(c,String.format(Locale.US,"#%06X",accent&0xFFFFFF),
                        360,380,150,22,Color.WHITE);
                hit(235,245,485,495,()->{});
            });
            group(c,2,()->{
                box(c,22,530,698,785,22,paper());
                left(c,tr("Language","اللغة","Idioma"),48,572,620,28,ink());
                String[] codes={"ar","en","es"};
                String[] labels={"العربية","English","Español"};
                for(int i=0;i<3;i++){
                    final int ix=i;
                    buttonArt(c,labels[i],45+i*215,615,240+i*215,700,
                            language.equals(codes[i]),()->{
                                language=codes[ix];
                                prefs.edit().putString("language",language).apply();
                                settings();
                            });
                }
            });
            group(c,3,()->{
                box(c,22,805,698,1215,22,paper());
                left(c,tr("Appearance","المظهر","Apariencia"),48,849,620,28,ink());
                buttonArt(c,dark?tr("☾  Dark mode","☾  الوضع الليلي","☾  Modo oscuro"):
                        tr("☀  Light mode","☀  الوضع النهاري","☀  Modo claro"),
                        45,870,675,965,false,()->{
                            dark=!dark;prefs.edit().putBoolean("dark",dark).apply();settings();
                        });
                buttonArt(c,(prefs.getBoolean("hints",true)?"✓  ":"○  ")+
                        tr("Join position hints","تلميح موضع اللصق","Marcas de unión"),
                        45,990,675,1085,false,()->{
                            prefs.edit().putBoolean("hints",!prefs.getBoolean("hints",true)).apply();
                            invalidate();
                        });
                txt(c,tr("Your images stay on this device","صورك تبقى على هذا الجهاز",
                        "Tus imágenes quedan en este dispositivo"),360,1153,600,19,muted);
            });
        }
        private void projectsArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Projects","المشاريع","Proyectos"),MainActivity.this::home));
            group(c,1,()->buttonArt(c,"+  "+tr("New project","مشروع جديد","Nuevo proyecto"),
                    22,141,698,229,true,()->newProject(false)));
            JSONArray projects=storedProjects();
            int pages=Math.max(1,(projects.length()+4)/5);
            if(listPage>=pages)listPage=pages-1;
            for(int i=0;i<5;i++){
                int ix=listPage*5+i;
                JSONObject project=projects.optJSONObject(ix);
                if(project==null)break;
                final int selected=ix;
                final float y=254+i*184;
                group(c,i+2,()->{
                    box(c,22,y,698,y+161,20,paper());
                    left(c,project.optString("name"),48,y+67,560,30,ink());
                    int count=project.optJSONArray("items")==null?0:project.optJSONArray("items").length();
                    left(c,count+" "+tr("exports","صور مقسومة","exportaciones"),
                            48,y+112,520,21,muted);
                    txt(c,"›",652,y+98,50,42,accent);
                    hit(22,y,698,y+161,()->projectDetail(selected));
                });
            }
            pager(c,listPage,pages,()->{listPage--;invalidate();},
                    ()->{listPage++;invalidate();});
            if(projects.length()==0)
                txt(c,tr("Create a project to keep your split images together",
                        "أنشئ مشروعًا لترتيب الصور المقسمة",
                        "Crea un proyecto para organizar tus imágenes"),360,455,635,26,muted);
        }
        private void pager(Canvas c,int index,int pages,Runnable prev,Runnable next) {
            if(pages<=1)return;
            buttonArt(c,"‹",180,1275,270,1350,false,prev);
            txt(c,(index+1)+" / "+pages,360,1322,160,24,ink());
            buttonArt(c,"›",450,1275,540,1350,false,next);
        }
        private void detailArt(Canvas c) {
            JSONObject project=storedProjects().optJSONObject(detailIndex);
            if(project==null){projectsArt(c);return;}
            group(c,0,()->chrome(c,project.optString("name"),MainActivity.this::projects));
            JSONArray items=project.optJSONArray("items");
            if(items==null)return;
            int pages=Math.max(1,(items.length()+4)/5);
            if(listPage>=pages)listPage=pages-1;
            for(int i=0;i<5;i++){
                int ix=items.length()-1-listPage*5-i;
                JSONObject entry=items.optJSONObject(ix);
                if(entry==null)break;
                JSONArray uris=entry.optJSONArray("uris");
                if(uris==null)continue;
                java.util.ArrayList<Uri> images=new java.util.ArrayList<>();
                for(int j=0;j<uris.length();j++) images.add(Uri.parse(uris.optString(j)));
                final float y=145+i*208;
                group(c,i+1,()->{
                    box(c,22,y,698,y+180,19,paper());
                    left(c,entry.optString("date"),48,y+62,580,27,ink());
                    left(c,images.size()+" "+tr("parts","أجزاء","partes"),
                            48,y+113,320,22,muted);
                    buttonArt(c,tr("Share","مشاركة","Compartir"),480,y+102,675,y+158,
                            false,()->share(images));
                });
            }
            pager(c,listPage,pages,()->{listPage--;invalidate();},
                    ()->{listPage++;invalidate();});
        }
        private void resultArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Your image is ready","صورتك جاهزة",
                    "Tu imagen está lista"),MainActivity.this::home));
            group(c,1,()->txt(c,tr("Saved to Pictures/ImageSplitter",
                    "حُفظت في الصور/ImageSplitter","Guardada en Imágenes/ImageSplitter"),
                    360,163,670,24,muted));
            int pages=Math.max(1,(exports.size()+3)/4);
            if(resultPage>=pages)resultPage=pages-1;
            for(int i=0;i<4;i++){
                int ix=resultPage*4+i;
                if(ix>=exports.size())break;
                Uri uri=exports.get(ix);
                final float y=205+i*225;
                group(c,i+2,()->{
                    box(c,22,y,698,y+195,21,paper());
                    txt(c,tr("Part ","الجزء ","Parte ")+(ix+1),220,y+114,340,31,ink());
                    buttonArt(c,tr("Share","مشاركة","Compartir"),465,y+67,675,y+132,
                            false,()->shareOne(uri));
                });
            }
            buttonArt(c,tr("Add to project","إضافة لمشروع","Añadir al proyecto"),
                    22,1121,698,1203,true,MainActivity.this::chooseProject);
            buttonArt(c,tr("Share all parts","مشاركة كل الأجزاء","Compartir todas las partes"),
                    22,1217,698,1280,false,()->share(exports));
            if(pages>1) {
                buttonArt(c,"‹",280,1310,335,1380,false,()->{resultPage--;invalidate();});
                txt(c,(resultPage+1)+"/"+pages,360,1354,85,21,ink());
                buttonArt(c,"›",385,1310,440,1380,false,()->{resultPage++;invalidate();});
            }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            float x=event.getX()/sx, y=event.getY()/sy;
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN) {
                touchX=x;touchY=y;
                draggingSlider=sketchMode==0&&y>=606&&y<=663;
                draggingColor=sketchMode==1&&x>=235&&x<=485&&y>=245&&y<=495;
                if(draggingSlider){slide(x);return true;}
                if(draggingColor){color(x,y);return true;}
                return true;
            }
            if(event.getAction()==android.view.MotionEvent.ACTION_MOVE) {
                if(draggingSlider){slide(x);return true;}
                if(draggingColor){color(x,y);return true;}
            }
            if(event.getAction()==android.view.MotionEvent.ACTION_UP) {
                if(draggingSlider){slide(x);draggingSlider=false;return true;}
                if(draggingColor){color(x,y);draggingColor=false;return true;}
                if(Math.abs(x-touchX)>25||Math.abs(y-touchY)>25)return true;
                for(int i=zones.size()-1;i>=0;i--) {
                    Zone z=zones.get(i);
                    if(z.rect.contains(x,y)){z.action.run();return true;}
                }
                return true;
            }
            return true;
        }
        private void slide(float x){
            parts=2+Math.round(Math.max(0,Math.min(1,(x-45)/631f))*18);
            invalidate();
        }
        private void color(float x,float y) {
            float dx=x-360,dy=y-370;
            if(Math.hypot(dx,dy)<78) return;
            float hue=(float)((Math.toDegrees(Math.atan2(dy,dx))+360)%360);
            accent=Color.HSVToColor(new float[]{hue,.93f,.96f});
            prefs.edit().putInt("accent",accent).apply();
            invalidate();
        }
        private final class Zone {
            final RectF rect;final Runnable action;
            Zone(RectF r,Runnable a){rect=r;action=a;}
        }
    }

    private android.app.Dialog sketchDialog(String heading, String message, String[] options, java.util.function.IntConsumer selected) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(surface, line, 22));
        TextView title = label(heading, 22, true, text);
        box.addView(title);
        if (message != null) {
            gap(box, 10);
            TextView info = label(message, 16, false, muted);
            box.addView(info);
        }
        for (int i = 0; i < options.length; i++) {
            gap(box, 10);
            final int index = i;
            box.addView(button(options[i], false, () -> { dialog.dismiss(); selected.accept(index); }));
        }
        dialog.setContentView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(44), -2);
        }
        dialog.show();
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(44), -2);
        return dialog;
    }

    private void sketchInput(String heading, String hint, String initial, boolean numeric,
                             java.util.function.Consumer<String> confirmed) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(surface, line, 22));
        box.addView(label(heading, 22, true, text));
        gap(box, 14);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(initial);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(19);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(shape(background, line, 12));
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(55)));
        gap(box, 16);
        LinearLayout actions = row();
        TextView cancel = button(tr("Cancel", "إلغاء", "Cancelar"), false, dialog::dismiss);
        TextView confirm = button(tr("Apply", "تطبيق", "Aplicar"), true,
                () -> { String value = input.getText().toString().trim(); dialog.dismiss(); confirmed.accept(value); });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(55), 1);
        half.setMargins(0, 0, dp(8), 0);
        actions.addView(cancel, half);
        actions.addView(confirm, new LinearLayout.LayoutParams(0, dp(55), 1));
        box.addView(actions);
        dialog.setContentView(box);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(44), -2);
    }

}
