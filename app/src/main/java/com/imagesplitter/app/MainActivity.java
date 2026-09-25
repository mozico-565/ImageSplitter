package com.imagesplitter.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final int DEFAULT_ACCENT = 0xFF3B8FF5;
    private SharedPreferences prefs;
    private int accent, background, surface, text, muted, line;
    private boolean dark, horizontal = true, processing, aiEnhance, aiExported;
    private volatile int processingPart;
    private String theme = "system";
    private int parts = 3, output = SplitEngine.PX_1080, custom = 1500;
    private int sourceWidth, sourceHeight;
    private Uri source;
    private String sourceName = "";
    private Bitmap preview;
    private List<Uri> exports = new ArrayList<>();
    private List<int[]> exportDimensions = new ArrayList<>();
    private String language = "en";
    private android.graphics.Typeface kalam;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        language = prefs.getString("language", "en");
        theme = prefs.getString("theme", prefs.getBoolean("dark", false) ? "dark" : "system");
        accent = prefs.getInt("accent", DEFAULT_ACCENT);
        if (state != null) {
            String savedSource = state.getString("source");
            if (savedSource != null) source = Uri.parse(savedSource);
            horizontal = state.getBoolean("horizontal", true);
            parts = state.getInt("parts", 3);
            output = state.getInt("output", SplitEngine.PX_1080);
            custom = state.getInt("custom", 1500);
            sketchMode = state.getInt("mode", 0);
        }
        updatePalette();
        if (source != null) loadSelectedImage(source, false);
        else showSketch(sketchMode);
    }

    private void updatePalette() {
        boolean systemDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = theme.equals("dark") || (theme.equals("system") && systemDark);
        background = dark ? 0xFF101820 : 0xFFFBFAF6;
        surface = dark ? 0xFF1A2732 : 0xFFFFFEFA;
        text = dark ? Color.WHITE : 0xFF171A20;
        muted = dark ? 0xFFB8C6D4 : 0xFF62676F;
        line = dark ? 0xFF677787 : 0xFF292B30;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (source != null) out.putString("source", source.toString());
        out.putBoolean("horizontal", horizontal);
        out.putInt("parts", parts);
        out.putInt("output", output);
        out.putInt("custom", custom);
        out.putInt("mode", sketchMode);
    }

    @Override public void onBackPressed() {
        if (sketchMode != 0) home(); else super.onBackPressed();
    }

    private String tr(String en, String ar, String es) {
        return language.equals("ar") ? ar : language.equals("es") ? es : en;
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private android.graphics.Typeface appTypeface() {
        if (kalam == null) {
            try { kalam = getResources().getFont(R.font.kalam_regular); }
            catch (Exception ignored) { kalam = android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL); }
        }
        return kalam;
    }
    private Drawable shape(int color, int border, int radius) {
        return new SketchFrameDrawable(color, border, dp(radius));
    }

    private int accentWash() {
        float amount = dark ? .22f : .13f;
        int r = Math.round(Color.red(background) * (1 - amount) + Color.red(accent) * amount);
        int g = Math.round(Color.green(background) * (1 - amount) + Color.green(accent) * amount);
        int b = Math.round(Color.blue(background) * (1 - amount) + Color.blue(accent) * amount);
        return Color.rgb(r, g, b);
    }

    private TextView label(String s, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(appTypeface(), bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return t;
    }

    private TextView button(String title, boolean selected, Runnable action) {
        TextView t = label(title, 17, false, selected ? Color.WHITE : text);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(54));
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setBackground(shape(selected ? accent : surface, selected ? accent : line, 15));
        t.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(.98f).scaleY(.98f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
            }
            return false;
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private TextView optionButton(String title, boolean selected, Runnable action) {
        TextView t = label((selected ? "✓  " : "") + title, 17, selected, text);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(54));
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setBackground(shape(selected ? accentWash() : surface, selected ? accent : line, 15));
        t.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(.98f).scaleY(.98f).setDuration(70).start();
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
            return false;
        });
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
        Uri selected = data.getData();
        try {
            int granted=data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if(granted!=0)getContentResolver().takePersistableUriPermission(selected,granted);
            loadSelectedImage(selected, true);
        } catch (Exception e) {
            toast(e.getMessage() == null ? tr("Unable to read image", "تعذر قراءة الصورة",
                    "No se pudo leer la imagen") : e.getMessage());
        }
    }

    private void loadSelectedImage(Uri selected, boolean showHome) {
        try {
            int[] size = SplitEngine.dimensions(getContentResolver(), selected);
            Bitmap sampled = SplitEngine.loadPreview(getContentResolver(), selected, 1200);
            if (preview != null && preview != sampled) preview.recycle();
            preview = sampled;
            source = selected;
            sourceName = displayName(selected);
            sourceWidth = size[0];
            sourceHeight = size[1];
            if (showHome) home(); else showSketch(sketchMode);
        } catch (Exception e) {
            source = null;
            sourceName = "";
            sourceWidth = sourceHeight = 0;
            if (showHome) home(); else showSketch(sketchMode);
            toast(tr("The selected image is missing or unsupported",
                    "الصورة المحددة مفقودة أو غير مدعومة",
                    "La imagen seleccionada no existe o no es compatible"));
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value.trim();
            }
        } catch (Exception ignored) {}
        String tail = uri.getLastPathSegment();
        return tail == null || tail.isEmpty() ? tr("Selected image", "الصورة المختارة", "Imagen seleccionada") : tail;
    }

    private void askCustom() { sketchInput(tr("Custom longest side", "الضلع الأطول المخصص", "Lado largo personalizado"), "64–12000 px", String.valueOf(custom), true, value -> { try { int n=Integer.parseInt(value); if(n<64||n>12000) throw new NumberFormatException(); if(sourceWidth>0&&sourceHeight>0){for(int i=0;i<parts;i++){int[] d=SplitEngine.outputPartDimensions(sourceWidth,sourceHeight,horizontal,parts,i,n);if((long)d[0]*d[1]>48_000_000L){sketchDialog(tr("Size is too large","المقاس كبير جدًا","El tamaño es demasiado grande"),tr("Choose a smaller value to avoid running out of memory.","اختر قيمة أصغر لتجنب نفاد الذاكرة.","Elige un valor menor para evitar agotar la memoria."),new String[]{tr("OK","حسنًا","Aceptar")},ignored->{});return;}}} custom=n; output=-1; home(); } catch(NumberFormatException e) { toast("64–12000 px"); } }); }

    private void export() {
        if (source == null) { toast(tr("Choose an image first", "اختر صورة أولًا",
                "Selecciona una imagen primero")); return; }
        if (processing) return;
        final Uri selected = source;
        final int count = parts, target = output == -1 ? custom : output;
        final int selectedWidth=sourceWidth, selectedHeight=sourceHeight, exportAccent=accent;
        final boolean direction = horizontal, hints = prefs.getBoolean("hints", true), enhance = aiEnhance;
        final String folder = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        processing = true;
        if (sketchScreen != null) sketchScreen.invalidate();
        new Thread(() -> {
            try {
                final boolean[] aiFailed = {false};
                AiUpscaler upscaler = null;
                if (enhance) {
                    try { upscaler = new AiUpscaler(getAssets()); }
                    catch (Exception | LinkageError error) { aiFailed[0] = true; }
                }
                List<Uri> result;
                try {
                    try {
                        result = SplitEngine.export(getContentResolver(), selected, count,
                                direction, target, hints, exportAccent, folder, upscaler,
                                new SplitEngine.Progress() {
                                @Override public void onPart(int current, int total) {
                                    processingPart = current;
                                    runOnUiThread(() -> { if (sketchScreen != null) sketchScreen.invalidate(); });
                                }
                                @Override public void onFallback() { aiFailed[0] = true; }
                                });
                    } catch (AiUpscaler.Failed failure) {
                        aiFailed[0] = true;
                        result = SplitEngine.export(getContentResolver(), selected, count,
                                direction, target, hints, exportAccent, folder);
                    }
                } finally { if (upscaler != null) upscaler.close(); }
                final List<Uri> completedResult = result;
                ArrayList<int[]> dimensions = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    int[] d = SplitEngine.outputPartDimensions(selectedWidth, selectedHeight,
                            direction, count, i, target);
                    // The exporter records exact per-part sizes, including individual fallbacks.
                    if (enhance && !aiFailed[0]) { d[0] *= 2; d[1] *= 2; }
                    dimensions.add(d);
                }
                runOnUiThread(() -> {
                    processing = false;
                    aiExported = enhance && !aiFailed[0];
                    exports = completedResult;
                    exportDimensions = dimensions;
                    haptic(HapticFeedbackConstants.CONFIRM);
                    resultScreen();
                    if (aiFailed[0]) toast(tr("AI enhancement failed. Original split images were exported instead.",
                            "تعذر تحسين الصور بالذكاء الاصطناعي. تم تصدير الأجزاء الأصلية.",
                            "Falló la mejora con IA. Se exportaron las partes originales."));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    processing = false;
                    if (sketchScreen != null) sketchScreen.invalidate();
                    sketchDialog(tr("Export failed", "فشل التصدير", "Error al exportar"),
                            e.getMessage() == null ? tr("Check storage space and try again",
                                    "تحقق من مساحة التخزين وحاول مجددًا",
                                    "Comprueba el almacenamiento e inténtalo de nuevo") : e.getMessage(),
                            new String[]{tr("OK", "حسنًا", "Aceptar")}, ignored -> {});
                });
            }
        }).start();
    }

    private void resultScreen() { resultPage=0; showSketch(4); }

private void shareOne(Uri uri) { share(java.util.Collections.singletonList(uri)); }
    private void share(List<Uri> images) {
        if (images == null || images.isEmpty()) {
            toast(tr("No result to share", "لا توجد نتيجة للمشاركة", "No hay resultado para compartir"));
            return;
        }
        Intent intent = new Intent(images.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        intent.setType("image/png");
        if (images.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, images.get(0));
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(images));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent,
                    tr("Share parts", "مشاركة الأجزاء", "Compartir partes")));
        } catch (Exception e) {
            toast(tr("No compatible sharing app was found", "لم يتم العثور على تطبيق مشاركة مناسب",
                    "No se encontró una aplicación compatible"));
        }
    }

    private void haptic(int constant) {
        if (prefs.getBoolean("haptics", true) && sketchScreen != null) {
            sketchScreen.performHapticFeedback(constant);
        }
    }

    private JSONArray storedProjects() {
        try { return new JSONArray(prefs.getString("projects", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    private void chooseProject() {
        JSONArray projects=storedProjects();
        String[] names=new String[projects.length()+1];
        names[0]=tr("+ Create New Project", "+ إنشاء مشروع جديد", "+ Crear proyecto");
        for(int i=1;i<names.length;i++) names[i]=projects.optJSONObject(i-1).optString("name");
        sketchDialog(tr("Add to project", "إضافة لمشروع", "Añadir al proyecto"), null, names,
                index -> { if(index==0) newProject(true); else saveProject(index-1); });
    }
    private void newProject(boolean addCurrent) {
        sketchInput(tr("New project", "مشروع جديد", "Nuevo proyecto"),
                tr("Project name", "اسم المشروع", "Nombre del proyecto"), "", false, name -> {
            if(name.isEmpty()) { toast(tr("Enter a name", "أدخل اسمًا", "Introduce un nombre")); return; }
            try {
                JSONArray projects=storedProjects();
                JSONObject project=new JSONObject();
                project.put("name",name);
                project.put("createdAt",System.currentTimeMillis());
                project.put("items",new JSONArray());
                projects.put(project);
                prefs.edit().putString("projects",projects.toString()).apply();
                if(addCurrent) saveProject(projects.length()-1); else projects();
            } catch(Exception e) { toast(e.getMessage()); }
        });
    }

    private void saveProject(int index) {
        try {
            JSONArray projects = storedProjects();
            JSONObject project = projects.getJSONObject(index);
            JSONArray items = project.getJSONArray("items");
            JSONObject item = new JSONObject();
            item.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            item.put("createdAt", System.currentTimeMillis());
            item.put("source", source == null ? "" : source.toString());
            item.put("horizontal", horizontal);
            item.put("parts", parts);
            item.put("target", output == -1 ? custom : output);
            item.put("hints", prefs.getBoolean("hints", true));
            item.put("sourceWidth", sourceWidth);
            item.put("sourceHeight", sourceHeight);
            JSONArray uris = new JSONArray();
            for (Uri uri : exports) uris.put(uri.toString());
            item.put("uris", uris);
            JSONArray sizes = new JSONArray();
            for (int[] dimension : exportDimensions) {
                JSONArray pair = new JSONArray();
                pair.put(dimension[0]); pair.put(dimension[1]); sizes.put(pair);
            }
            item.put("sizes", sizes);
            items.put(item);
            prefs.edit().putString("projects", projects.toString()).apply();
            toast(tr("Added to project", "أُضيف للمشروع", "Añadido al proyecto"));
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void renameProject(int index) {
        JSONObject project=storedProjects().optJSONObject(index);
        if(project==null)return;
        sketchInput(tr("Rename project","إعادة تسمية المشروع","Renombrar proyecto"),
                tr("Project name","اسم المشروع","Nombre del proyecto"),
                project.optString("name"),false,name->{
                    if(name.trim().isEmpty())return;
                    try { JSONArray projects=storedProjects(); projects.getJSONObject(index).put("name",name.trim());
                        prefs.edit().putString("projects",projects.toString()).apply(); projectDetail(index);
                    } catch(Exception e){toast(e.getMessage());}
                });
    }

    private void deleteProject(int index) {
        JSONObject project=storedProjects().optJSONObject(index);
        if(project==null)return;
        sketchConfirm(tr("Delete project?","حذف المشروع؟","¿Eliminar proyecto?"),
                tr("Generated files stay in Pictures. Only this workspace is removed.",
                        "ستبقى الصور في مجلد الصور، وسيُحذف تنظيم المشروع فقط.",
                        "Los archivos seguirán en Imágenes; solo se elimina el proyecto."),()->{
                    JSONArray old=storedProjects(), updated=new JSONArray();
                    for(int i=0;i<old.length();i++)if(i!=index)updated.put(old.optJSONObject(i));
                    prefs.edit().putString("projects",updated.toString()).apply(); projects();
                });
    }

    private void removeProjectItem(int projectIndex,int itemIndex) {
        sketchConfirm(tr("Remove result?","إزالة النتيجة؟","¿Quitar resultado?"),
                tr("The exported images will not be deleted.","لن تُحذف الصور المصدّرة.",
                        "Las imágenes exportadas no se eliminarán."),()->{
                    try { JSONArray projects=storedProjects(); JSONObject project=projects.getJSONObject(projectIndex);
                        JSONArray old=project.getJSONArray("items"), updated=new JSONArray();
                        for(int i=0;i<old.length();i++)if(i!=itemIndex)updated.put(old.optJSONObject(i));
                        project.put("items",updated); prefs.edit().putString("projects",projects.toString()).apply();
                        projectDetail(projectIndex);
                    } catch(Exception e){toast(e.getMessage());}
                });
    }

    private void reExportProjectItem(int projectIndex,int itemIndex) {
        JSONObject project=storedProjects().optJSONObject(projectIndex);
        JSONArray items=project==null?null:project.optJSONArray("items");
        JSONObject item=items==null?null:items.optJSONObject(itemIndex);
        if(item==null||item.optString("source").isEmpty()) {
            toast(tr("Original image is missing","الصورة الأصلية مفقودة","Falta la imagen original")); return;
        }
        source=Uri.parse(item.optString("source"));
        horizontal=item.optBoolean("horizontal",true);
        parts=Math.max(2,Math.min(20,item.optInt("parts",3)));
        int target=item.optInt("target",SplitEngine.PX_1080);
        if(target==0||target==750||target==1080)output=target;else{output=-1;custom=target;}
        loadSelectedImage(source,true);
        if(source!=null)export();
    }

    private void projects() { listPage=0; showSketch(2); }
private void projectDetail(int index) { detailIndex=index; listPage=0; showSketch(3); }
private void settings() { showSketch(1); }

private void help() { sketchDialog(tr("How to print", "طريقة الطباعة", "Cómo imprimir"), tr("Pick a photo, choose the direction and number of parts, then export. Print each PNG at the same scale and join the numbered edges. Accent-colored ticks mark matching joins when enabled in Settings.", "اختر الصورة والاتجاه وعدد الأجزاء، ثم صدّرها. اطبع كل جزء بالمقياس نفسه واجمع الحواف حسب ترتيب الأرقام. علامات بلون التطبيق تساعد في المحاذاة ويمكن إيقافها من الإعدادات.", "Elige una imagen, la dirección y las partes; exporta. Imprime cada PNG a la misma escala y une los bordes numerados."), new String[]{tr("OK", "حسنًا", "Aceptar")}, index -> {}); }

    private void toast(String value) {
        Toast toast=new Toast(this);
        TextView message=label(value,16,true,text);
        message.setGravity(Gravity.CENTER_VERTICAL);
        message.setPadding(dp(18),dp(13),dp(18),dp(13));
        message.setBackground(shape(surface,0xFFD64545,16));
        toast.setView(message);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(34));
        toast.show();
    }

    private void showLanguagePicker() {
        String[] labels={"العربية","English","Español"};
        String[] codes={"ar","en","es"};
        int selected=language.equals("ar")?0:language.equals("es")?2:1;
        sketchDialog(tr("Choose Language","اختر اللغة","Elegir idioma"),
                tr("The interface updates immediately","تتحدث الواجهة فورًا","La interfaz se actualiza al instante"),
                labels,selected,index->{language=codes[index];prefs.edit().putString("language",language).apply();settings();});
    }

    private void resetSettings() {
        sketchConfirm(tr("Reset settings?","إعادة ضبط الإعدادات؟","¿Restablecer ajustes?"),
                tr("Projects and exported images will not be deleted.",
                        "لن تُحذف المشاريع أو الصور المصدّرة.",
                        "No se eliminarán proyectos ni imágenes."),()->{
                    String projects=prefs.getString("projects","[]");
                    prefs.edit().clear().putString("projects",projects).apply();
                    accent=DEFAULT_ACCENT;theme="system";language="en";settings();
                });
    }

    private void showAbout() {
        sketchDialog(tr("About Image Splitter","حول Image Splitter","Acerca de Image Splitter"),
                tr("A private, offline-first native Android tool for turning one image into accurate printable parts. No analytics and no image uploads.",
                        "أداة أندرويد أصلية وخاصة تعمل دون اتصال لتحويل صورة واحدة إلى أجزاء دقيقة للطباعة. بلا تحليلات أو رفع للصور.",
                        "Herramienta Android nativa y privada para dividir imágenes en partes imprimibles. Sin analíticas ni subidas."),
                new String[]{tr("Close","إغلاق","Cerrar")},ignored->{});
    }

    private void sketchConfirm(String heading,String message,Runnable confirmed) {
        sketchDialog(heading,message,new String[]{tr("Cancel","إلغاء","Cancelar"),
                tr("Confirm","تأكيد","Confirmar")},index->{if(index==1)confirmed.run();});
    }

    private void showColorPicker() {
        final int original=accent;
        Dialog dialog=new Dialog(this);
        LinearLayout box=column();
        box.setPadding(dp(20),dp(18),dp(20),dp(18));
        box.setBackground(shape(surface,line,22));
        box.setLayoutDirection(language.equals("ar")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        box.addView(label(tr("Accent Color","لون التطبيق","Color de acento"),23,true,text));
        gap(box,8);
        TextView description=label(tr("Drag around the color wheel","اسحب حول دائرة الألوان","Arrastra por la rueda de color"),15,false,muted);
        box.addView(description);
        gap(box,12);
        TextView hex=label(String.format(Locale.US,"#%06X",accent&0xFFFFFF),19,true,text);
        hex.setGravity(Gravity.CENTER);
        ColorWheel wheel=new ColorWheel(value->{
            accent=value;hex.setText(String.format(Locale.US,"#%06X",value&0xFFFFFF));
            if(sketchScreen!=null)sketchScreen.invalidate();
        });
        box.addView(wheel,new LinearLayout.LayoutParams(-1,dp(220)));
        box.addView(hex,new LinearLayout.LayoutParams(-1,dp(44)));
        JSONArray recent;
        try{recent=new JSONArray(prefs.getString("recentColors","[]"));}catch(Exception e){recent=new JSONArray();}
        if(recent.length()>0){
            gap(box,6);box.addView(label(tr("Recent Colors","الألوان الأخيرة","Colores recientes"),16,true,text));gap(box,6);
            LinearLayout swatches=row();
            for(int i=0;i<Math.min(6,recent.length());i++){
                final int color=recent.optInt(i,DEFAULT_ACCENT);
                TextView swatch=label("",1,false,text);swatch.setBackground(shape(color,line,30));
                swatch.setOnClickListener(v->{accent=color;hex.setText(String.format(Locale.US,"#%06X",color&0xFFFFFF));wheel.invalidate();if(sketchScreen!=null)sketchScreen.invalidate();});
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.setMargins(dp(4),0,dp(4),0);swatches.addView(swatch,p);
            }
            box.addView(swatches);
        }
        gap(box,12);
        LinearLayout actions=row();
        TextView reset=button(tr("Default","الافتراضي","Predeterminado"),false,()->{
            accent=DEFAULT_ACCENT;hex.setText(String.format(Locale.US,"#%06X",accent&0xFFFFFF));wheel.invalidate();if(sketchScreen!=null)sketchScreen.invalidate();});
        TextView cancel=button(tr("Cancel","إلغاء","Cancelar"),false,()->{accent=original;dialog.dismiss();settings();});
        TextView apply=button(tr("Apply","تطبيق","Aplicar"),true,()->{
            JSONArray colors=new JSONArray();colors.put(accent);
            try{JSONArray old=new JSONArray(prefs.getString("recentColors","[]"));for(int i=0;i<old.length()&&colors.length()<6;i++){int c=old.optInt(i);if(c!=accent)colors.put(c);}}catch(Exception ignored){}
            prefs.edit().putInt("accent",accent).putString("recentColors",colors.toString()).apply();dialog.dismiss();haptic(HapticFeedbackConstants.CONFIRM);settings();});
        actions.addView(reset,new LinearLayout.LayoutParams(0,dp(55),1));
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(0,dp(55),1);action.setMargins(dp(7),0,0,0);
        actions.addView(cancel,action);actions.addView(apply,action);box.addView(actions);
        dialog.setOnCancelListener(d->{accent=original;settings();});
        dialog.setContentView(box);
        presentSketchDialog(dialog,box);
    }

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
        private final java.util.function.IntConsumer changed;
        ColorWheel(java.util.function.IntConsumer changed) { super(MainActivity.this);this.changed=changed; setContentDescription(tr("Color wheel", "دائرة الألوان", "Rueda de colores")); }
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
            float[] hsv=new float[3];Color.colorToHSV(accent,hsv);
            double angle=Math.toRadians(hsv[0]);
            float cursorRadius=radius*.8f*Math.max(.3f,hsv[1]);
            float cursorX=cx+(float)Math.cos(angle)*cursorRadius;
            float cursorY=cy+(float)Math.sin(angle)*cursorRadius;
            paint.setStyle(Paint.Style.FILL);paint.setColor(accent);canvas.drawCircle(cursorX,cursorY,dp(7),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(Color.WHITE);canvas.drawCircle(cursorX,cursorY,dp(8),paint);
            paint.setColor(line);paint.setStrokeWidth(dp(.8f));canvas.drawCircle(cursorX,cursorY,dp(10),paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() != android.view.MotionEvent.ACTION_DOWN &&
                    event.getAction() != android.view.MotionEvent.ACTION_MOVE) return true;
            float x = event.getX() - getWidth() / 2f, y = event.getY() - getHeight() / 2f;
            float hue = (float)((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            float saturation = Math.max(.3f, Math.min(1f, (float)Math.hypot(x, y) / (radius * .8f)));
            accent = Color.HSVToColor(new float[]{hue, saturation, 1f});
            changed.accept(accent);
            invalidate();
            return true;
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        showSketch(sketchMode);
    }
    private int sketchMode = 0, detailIndex = 0, listPage = 0, resultPage = 0;
    private boolean sizeTip;
    private int sizeTipChoice = 2;
    private SketchScreen sketchScreen;

    private int homeExtra() {
        if (preview == null || preview.getWidth() < 1) return 0;
        return Math.max(0, Math.max(Math.round(604f * preview.getHeight() / preview.getWidth()),
                parts > 5 ? parts * 88 : 545) - 545);
    }

    private void showSketch(int mode) {
        updatePalette();
        sketchMode = mode;
        int contentHeight = mode == 0 ? 2010 + homeExtra() : mode == 1 ? 1450 : mode == 3 ? 1570 : 1450;
        sketchScreen = new SketchScreen(contentHeight);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(48));
        scroll.setBackgroundColor(background);
        scroll.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scroll.addView(sketchScreen, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private final class SketchScreen extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.ArrayList<Zone> zones = new java.util.ArrayList<>();
        private final android.graphics.Typeface handwriting = appTypeface();
        private final Bitmap paperTile;
        private final long start = android.os.SystemClock.uptimeMillis();
        private final int contentHeight;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private float sx, sy, touchX, touchY;
        private boolean draggingSlider, longTriggered;
        private Zone pressedZone;
        private final Runnable longPress = () -> {
            if (pressedZone != null && pressedZone.longAction != null) {
                longTriggered = true;
                haptic(HapticFeedbackConstants.LONG_PRESS);
                pressedZone.longAction.run();
                invalidate();
            }
        };

        SketchScreen(int contentHeight) {
            super(MainActivity.this);
            this.contentHeight = contentHeight;
            int tile = Math.max(8, dp(7));
            paperTile = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888);
            Canvas texture = new Canvas(paperTile);
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(dark ? 0x143D566A : 0x122A2B2E);
            texture.drawCircle(tile * .25f, tile * .25f, Math.max(.7f, dp(.28f)), dot);
            dot.setColor(dark ? 0x0B9DB0BE : 0x0C8C775F);
            texture.drawCircle(tile * .74f, tile * .72f, Math.max(.55f, dp(.22f)), dot);
            setContentDescription("Image Splitter");
            setFocusable(true);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = sketchMode == 0 ? 2010 + homeExtra() : contentHeight;
            setMeasuredDimension(width, Math.max(1, Math.round(width * height / 720f)));
        }

        @Override protected void onDraw(Canvas actual) {
            super.onDraw(actual);
            sx = getWidth() / 720f;
            sy = sx;
            zones.clear();
            actual.drawColor(background);
            p.setShader(new BitmapShader(paperTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            actual.drawRect(0, 0, getWidth(), getHeight(), p);
            p.setShader(null);
            actual.save();
            actual.scale(sx, sy);
            if (sketchMode == 0) homeArt(actual);
            else if (sketchMode == 1) settingsArt(actual);
            else if (sketchMode == 2) projectsArt(actual);
            else if (sketchMode == 3) detailArt(actual);
            else resultArt(actual);
            if (pressedZone != null) {
                RectF r=pressedZone.rect;
                fill(actual,r.left+3,r.top+3,r.right-3,r.bottom-3,12,alpha(accent,dark?55:32));
                outline(actual,r.left+3,r.top+3,r.right-3,r.bottom-3,12,accent);
            }
            actual.restore();
            if (prefs.getBoolean("animations", true)
                    && android.os.SystemClock.uptimeMillis() - start < 1100) postInvalidateDelayed(16);
        }

        private int ink() { return dark ? 0xFFF0F4F8 : 0xFF17191C; }
        private int paper() { return dark ? 0xFF17232F : 0xFFFFFFFF; }
        private int wash() { return blend(background, accent, dark ? .18f : .11f); }
        private int blend(int base,int over,float amount) {
            int r=Math.round(Color.red(base)*(1-amount)+Color.red(over)*amount);
            int g=Math.round(Color.green(base)*(1-amount)+Color.green(over)*amount);
            int b=Math.round(Color.blue(base)*(1-amount)+Color.blue(over)*amount);
            return Color.rgb(r,g,b);
        }
        private int alpha(int color,int value){return (color&0x00FFFFFF)|(value<<24);}
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
            float inset = isPressed(l,t,r,b) ? 2.2f : 0f;
            fill(c, l+inset, t+inset, r-inset, b-inset, 14, active ? accent : paper());
            outline(c, l+inset, t+inset, r-inset, b-inset, 14, active ? accent : ink());
            txt(c, value, (l+r)/2f, (t+b)/2f + 8, r-l-15, 23,
                    active ? Color.WHITE : ink());
            hit(l,t,r,b,click);
        }
        private void selectArt(Canvas c, String value, float l, float t, float r, float b,
                               boolean selected, Runnable click) {
            float inset = isPressed(l,t,r,b) ? 2.2f : 0f;
            fill(c,l+inset,t+inset,r-inset,b-inset,14,selected?wash():(dark?0xFF202D37:0xFFF2F0EA));
            outline(c,l+inset,t+inset,r-inset,b-inset,14,selected?accent:alpha(ink(),170));
            txt(c,value,(l+r)/2f,(t+b)/2f+8,r-l-15,23,ink());
            if(selected){
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.8f);p.setColor(alpha(accent,105));
                c.drawRoundRect(l+3,t+3,r-3,b-3,11,11,p);p.setStyle(Paint.Style.FILL);
            }
            hit(l,t,r,b,click);
        }
        private boolean isPressed(float l,float t,float r,float b){
            if(pressedZone==null)return false;
            RectF q=pressedZone.rect;
            return Math.abs(q.left-l)<.5f&&Math.abs(q.top-t)<.5f&&Math.abs(q.right-r)<.5f&&Math.abs(q.bottom-b)<.5f;
        }
        private void hit(float l,float t,float r,float b,Runnable action) {
            hit(l,t,r,b,action,null);
        }
        private void hit(float l,float t,float r,float b,Runnable action,Runnable longAction) {
            zones.add(new Zone(new RectF(l,t,r,b),action,longAction));
        }
        private void group(Canvas c, int n, Runnable draw) {
            if (!prefs.getBoolean("animations", true)) { draw.run(); return; }
            float progress = Math.max(0f, Math.min(1f,
                    (android.os.SystemClock.uptimeMillis() - start - n*105f) / 440f));
            if (progress <= 0) return;
            float smooth = 1f - (1f-progress)*(1f-progress);
            c.save();
            c.translate(0, (1f-smooth)*24f);
            int layer = c.saveLayerAlpha(0, 0, 720,
                    sketchMode == 0 ? 2010 + homeExtra() : contentHeight, (int)(255*smooth));
            draw.run();
            c.restoreToCount(layer);
            c.restore();
        }
        private void logo(Canvas c,float center,float top,float width,float height) {
            float l=center-width/2f,r=center+width/2f,unit=height/3f;
            float stroke = Math.max(2.2f, width * .034f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);
            p.setColor(ink());p.setStrokeJoin(Paint.Join.ROUND);
            c.drawRoundRect(l+width*.08f,top+stroke,r-width*.08f,top+unit-stroke,8,8,p);
            c.drawRoundRect(l,top+unit+stroke,r,top+unit*2-stroke,8,8,p);
            c.drawRoundRect(l+width*.08f,top+unit*2+stroke,r-width*.08f,top+height-stroke,8,8,p);
            p.setStyle(Paint.Style.FILL);
        }

        private void layersIcon(Canvas c, float cx, float cy, float width, int color) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.9f);p.setColor(color);
            for(int i=0;i<3;i++) {
                float half = i==1 ? width/2f : width*.43f;
                float top=cy-18+i*13;
                c.drawRoundRect(cx-half,top,cx+half,top+9,3.5f,3.5f,p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        private void verticalIcon(Canvas c, float cx, float cy, int color) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.8f);p.setColor(color);
            for (int i=0;i<3;i++) {
                float x=cx-11+i*10;
                Path lens = new Path();lens.moveTo(x,cy-18);
                lens.quadTo(x+13,cy,x,cy+18);
                lens.quadTo(x-13,cy,x,cy-18);
                c.drawPath(lens,p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        private void folder(Canvas c,float x,float y){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(ink());
            Path path=new Path();path.moveTo(x-17,y-11);path.lineTo(x-4,y-11);path.lineTo(x+1,y-6);
            path.lineTo(x+17,y-6);path.lineTo(x+14,y+13);path.lineTo(x-17,y+13);path.close();c.drawPath(path,p);
            line(c,x-15,y-3,x+15,y-3,ink(),1.4f);p.setStyle(Paint.Style.FILL);
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
                buttonArt(c,"",90,12,144,66,false,MainActivity.this::projects);
                folder(c,117,39);
                buttonArt(c,"",644,12,698,66,false,MainActivity.this::help);
                bulb(c,671,39);
                logo(c,360,70,90,68);
                txt(c,"Image Splitter",360,177,670,36,ink());
                txt(c,tr("Turn any image into a big print","حوّل أي صورة إلى طباعة كبيرة",
                        "Convierte cualquier imagen en una impresión grande"),
                        360,215,660,21,muted);
            });
            group(c,1,()->{
                fill(c,22,242,698,497,25,wash());
                outline(c,22,242,698,497,25,ink());
                dashed(c,34,254,686,485);
                if(preview!=null) {
                    RectF thumb=fitRect(preview,52,278,306,458);
                    fill(c,45,270,314,466,18,alpha(background,dark?120:165));
                    c.drawBitmap(preview,null,thumb,p);
                    outline(c,thumb.left,thumb.top,thumb.right,thumb.bottom,12,ink());
                    left(c,tr("Image selected","تم اختيار الصورة","Imagen seleccionada"),342,318,315,20,accent);
                    left(c,sourceName,342,356,320,27,ink());
                    left(c,sourceWidth+" × "+sourceHeight+" px",342,391,315,20,muted);
                    selectArt(c,tr("Change image","تغيير الصورة","Cambiar imagen"),342,414,658,466,true,MainActivity.this::pick);
                } else {
                    pictureIcon(c,360,332);
                    txt(c,tr("Select an Image","اختر صورة","Selecciona una imagen"),
                            360,416,620,30,ink());
                    txt(c,tr("Tap to choose from your gallery","اضغط للاختيار من المعرض",
                            "Toca para elegir de tu galería"),360,455,625,22,muted);
                }
                hit(22,242,698,497,MainActivity.this::pick);
            });
            group(c,2,()->{
                box(c,22,522,698,837,22,paper());
                left(c,tr("Split Direction","اتجاه التقسيم","Dirección de corte"),
                        42,567,620,27,ink());
                selectArt(c,tr("Horizontal","أفقي","Horizontal"),
                        40,587,356,659,horizontal,()->{ horizontal=true;sizeTip=false;haptic(HapticFeedbackConstants.CLOCK_TICK);requestLayout();invalidate();});
                layersIcon(c,94,624,39,ink());
                selectArt(c,tr("Vertical","عمودي","Vertical"),
                        364,587,680,659,!horizontal,()->{horizontal=false;sizeTip=false;haptic(HapticFeedbackConstants.CLOCK_TICK);requestLayout();invalidate();});
                verticalIcon(c,421,624,ink());
                left(c,tr("Number of Parts","عدد الأجزاء","Número de partes"),
                        42,713,380,24,ink());
                float knobX=45+(parts-2)*631f/18;
                fill(c,knobX-20,725,knobX+20,763,10,accent);
                Path pointer=new Path();pointer.moveTo(knobX-7,763);pointer.lineTo(knobX,772);
                pointer.lineTo(knobX+7,763);pointer.close();p.setColor(accent);c.drawPath(pointer,p);
                txt(c,String.valueOf(parts),knobX,752,35,23,Color.WHITE);
                line(c,45,785,676,785,dark?0xFF566572:0xFFD2D5D6,4f);
                line(c,45,785,knobX,785,accent,6f);
                p.setColor(paper());p.setStyle(Paint.Style.FILL);
                c.drawCircle(knobX,785,14,p);
                p.setColor(ink());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);
                c.drawCircle(knobX,785,14,p);p.setStyle(Paint.Style.FILL);
                left(c,"2",42,820,50,19,ink());txt(c,"20",665,820,50,19,ink());
                hit(35,760,685,811,()->{});
            });
            group(c,3,()->{
                box(c,22,875,698,1050,22,paper());
                left(c,tr("Output Size (Longest Side)","مقاس الإخراج (الضلع الأطول)",
                        "Tamaño de salida (lado largo)"),42,916,620,24,ink());
                int[] values={0,750,1080,-1};
                String[] labels={tr("Original","الأصلي","Original"),"750 px","1080 px",
                        tr("Custom","مخصص","Personalizado")};
                for(int i=0;i<4;i++) {
                    final int choice=i;
                    float x=40+i*161f;
                    Runnable select=()->{ if(choice==3) askCustom(); else {output=values[choice];sizeTip=false;
                        haptic(HapticFeedbackConstants.CLOCK_TICK);invalidate();} };
                    selectArt(c,labels[i],x,942,x+151,1023,output==values[i],select);
                    hit(x,942,x+151,1023,select,()->{sizeTipChoice=choice;sizeTip=true;invalidate();});
                }
            });
            group(c,4,()->{
                int extra=homeExtra();
                box(c,22,1080,698,1745+extra,22,paper());
                left(c,tr("Preview","المعاينة","Vista previa"),42,1128,610,29,ink());
                if(preview==null) {
                    pictureIcon(c,360,1370);
                    txt(c,tr("Your split preview will appear here","ستظهر معاينة التقسيم هنا",
                            "La vista previa aparecerá aquí"),360,1460,590,25,muted);
                } else previewTiles(c,preview,58,1160,662,1705+extra);
            });
            group(c,5,()->{
                int extra=homeExtra();
                box(c,22,1762+extra,698,1850+extra,18,paper());
                left(c,tr("AI Enhance 2×","تحسين الجودة بالذكاء الاصطناعي 2×",
                        "Mejorar calidad con IA 2×"),42,1811+extra,490,25,ink());
                toggleArt(c,579,1784+extra,aiEnhance,()->{aiEnhance=!aiEnhance;invalidate();});
                buttonArt(c,tr("Split & Export","تقسيم وتصدير",
                        "Cortar y exportar"),22,1880+extra,698,1982+extra,true,MainActivity.this::export);
                layersIcon(c,172,1930+extra,44,Color.WHITE);
            });
            if(sizeTip) drawSizeTip(c);
            if(processing) processingOverlay(c);
        }
        private RectF fitRect(Bitmap bmp,float l,float t,float r,float b) {
            float scale=Math.min((r-l)/bmp.getWidth(),(b-t)/bmp.getHeight());
            float w=bmp.getWidth()*scale,h=bmp.getHeight()*scale;
            return new RectF((l+r-w)/2f,(t+b-h)/2f,(l+r+w)/2f,(t+b+h)/2f);
        }
        private void drawSizeTip(Canvas c) {
            float anchor=40+sizeTipChoice*161f+75.5f;
            Path notch=new Path();notch.moveTo(anchor-16,1072);notch.lineTo(anchor,1050);
            notch.lineTo(anchor+16,1072);notch.close();p.setColor(dark?0xFF27313B:wash());c.drawPath(notch,p);
            box(c,42,1070,678,1455,22,dark?0xFF27313B:wash());
            String[] labels={tr("Original","الأصلي","Original"),"750 px","1080 px",
                    tr("Custom","مخصص","Personalizado")};
            left(c,tr("Example (","مثال (","Ejemplo (")+labels[sizeTipChoice]+")",65,1122,270,27,ink());
            line(c,350,1100,350,1419,ink(),1.5f);
            if(sourceWidth<2||sourceHeight<2) {
                txt(c,tr("Select an image to calculate real dimensions",
                        "اختر صورة لحساب المقاسات الحقيقية",
                        "Selecciona una imagen para calcular"),193,1240,250,18,muted);
                pictureIcon(c,190,1300);
                left(c,tr("Each part:","كل جزء:","Cada parte:"),380,1190,265,22,muted);
                left(c,tr("Choose an image first","اختر صورة أولًا","Elige una imagen"),380,1230,260,22,ink());
            } else {
                int target=sizeTipChoice==0?0:sizeTipChoice==1?750:sizeTipChoice==2?1080:custom;
                int totalW=0,totalH=0;
                int firstW=0,firstH=0;
                for(int i=0;i<parts;i++){
                    int[] d=SplitEngine.outputPartDimensions(sourceWidth,sourceHeight,horizontal,parts,i,target);
                    if(i==0){firstW=d[0];firstH=d[1];}
                    if(horizontal){totalW=Math.max(totalW,d[0]);totalH+=d[1];}
                    else{totalW+=d[0];totalH=Math.max(totalH,d[1]);}
                }
                previewTiles(c,preview,70,1155,325,1365);
                left(c,"↔  "+firstW+" px",68,1410,155,22,ink());
                left(c,"↕  "+firstH+" px",220,1410,120,22,ink());
                left(c,tr("Each part:","كل جزء:","Cada parte:"),380,1178,260,22,muted);
                left(c,firstW+" × "+firstH+" px",380,1220,270,30,ink());
                left(c,tr("Joined size:","المقاس بعد الجمع:","Tamaño unido:"),380,1295,260,22,muted);
                left(c,totalW+" × "+totalH+" px",380,1338,270,30,ink());
                left(c,"("+parts+" "+tr("parts","أجزاء","partes")+")",380,1380,270,22,ink());
            }
            hit(42,1070,678,1455,()->{sizeTip=false;invalidate();});
        }
        private void processingOverlay(Canvas c) {
            int extra=homeExtra();
            c.save();c.translate(0,extra+100);
            fill(c,42,1640,678,1888,24,alpha(background,238));
            outline(c,42,1640,678,1888,24,accent);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);p.setColor(alpha(accent,80));
            c.drawCircle(360,1715,34,p);p.setColor(accent);
            c.drawArc(new RectF(326,1681,394,1749),-90,120,false,p);p.setStyle(Paint.Style.FILL);
            txt(c,aiEnhance ? tr("AI Enhancing…","جارٍ التحسين بالذكاء الاصطناعي…",
                    "Mejorando con IA…") : tr("Splitting & saving…","جارٍ التقسيم والحفظ…",
                    "Cortando y guardando…"),360,1795,560,28,ink());
            txt(c,processingPart>0 ? tr("Enhancing ","تحسين ","Mejorando ")
                    +processingPart+"/"+parts : tr("Preparing…","جارٍ التجهيز…","Preparando…"),
                    360,1840,560,20,muted);
            c.restore();
        }
        private void previewTiles(Canvas c,Bitmap bmp,float l,float t,float r,float b) {
            if(bmp==null)return;
            int n=Math.min(parts,20);
            if (n > 5 && b-t > 500) {
                float row=(b-t)/n;
                for(int i=0;i<n;i++) {
                    Rect src=SplitEngine.partRect(bmp.getWidth(),bmp.getHeight(),horizontal,n,i);
                    float scale=Math.min((r-l-24)/src.width(),(row-10)/src.height());
                    float w=src.width()*scale,h=src.height()*scale;
                    float cx=(l+r)/2,cy=t+row*(i+.5f);
                    RectF dst=new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
                    fill(c,l+4,cy-row/2+2,r-4,cy+row/2-2,9,wash());
                    c.drawBitmap(bmp,src,dst,p);
                    outline(c,dst.left,dst.top,dst.right,dst.bottom,7,ink());
                    fill(c,l+7,cy-15,l+37,cy+15,15,accent);
                    txt(c,String.valueOf(i+1),l+22,cy+6,28,18,Color.WHITE);
                }
                return;
            }
            RectF full=fitRect(bmp,l,t,r,b);
            float gap=4.5f;
            for(int i=0;i<n;i++){
                Rect src=SplitEngine.partRect(bmp.getWidth(),bmp.getHeight(),horizontal,n,i);
                float x1=full.left+src.left*(full.width()/bmp.getWidth());
                float y1=full.top+src.top*(full.height()/bmp.getHeight());
                float x2=full.left+src.right*(full.width()/bmp.getWidth());
                float y2=full.top+src.bottom*(full.height()/bmp.getHeight());
                if(horizontal){if(i>0)y1+=gap/2;if(i<n-1)y2-=gap/2;}
                else{if(i>0)x1+=gap/2;if(i<n-1)x2-=gap/2;}
                if(x2<=x1||y2<=y1)continue;
                c.save();c.clipRect(x1,y1,x2,y2);
                c.drawBitmap(bmp,null,full,p);
                c.restore();
                outline(c,x1,y1,x2,y2,5,ink());
                float badgeX=x1+15,badgeY=y1+15;
                p.setStyle(Paint.Style.FILL);p.setColor(accent);c.drawCircle(badgeX,badgeY,12,p);
                txt(c,String.valueOf(i+1),badgeX,badgeY+6,20,15,Color.WHITE);
            }
        }
        private void chrome(Canvas c,String heading,Runnable back) {
            buttonArt(c,tr("←","→","←"),22,18,86,82,false,back);
            txt(c,heading,360,66,520,36,ink());
            logo(c,655,22,58,45);
        }
        private void divider(Canvas c,float y){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f);p.setColor(alpha(ink(),70));
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{5,5},0));
            c.drawLine(43,y,677,y,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
        }
        private void toggleArt(Canvas c,float l,float t,boolean on,Runnable action){
            float r=l+76,b=t+42;
            fill(c,l,t,r,b,22,on?accent:(dark?0xFF33414C:0xFFE3E0D8));
            outline(c,l,t,r,b,22,ink());
            p.setColor(on?Color.WHITE:paper());c.drawCircle(on?r-21:l+21,(t+b)/2,15,p);
            outline(c,(on?r-36:l+6),t+6,(on?r-6:l+36),b-6,15,alpha(ink(),130));
            hit(l-8,t-10,r+8,b+10,action);
        }
        private void settingsArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Settings","الإعدادات","Ajustes"),MainActivity.this::home));
            group(c,1,()->{
                left(c,tr("APPEARANCE","المظهر","APARIENCIA"),28,135,650,21,muted);
                box(c,22,150,698,475,22,paper());
                txt(c,"◉",61,213,48,34,ink());
                left(c,tr("Accent Color","لون التطبيق","Color de acento"),96,211,350,25,ink());
                fill(c,568,178,622,226,10,accent);outline(c,568,178,622,226,10,alpha(ink(),135));
                txt(c,"↶",657,215,36,31,ink());
                hit(42,163,678,245,MainActivity.this::showColorPicker);
                divider(c,255);
                txt(c,"☼",61,315,48,34,ink());
                left(c,tr("Theme","السمة","Tema"),96,313,280,25,ink());
                String[] modes={"system","light","dark"};
                String[] names={tr("System","النظام","Sistema"),tr("Light","فاتح","Claro"),tr("Dark","داكن","Oscuro")};
                for(int i=0;i<3;i++){final int ix=i;float x=385+i*97;
                    selectArt(c,names[i],x,278,x+89,337,theme.equals(modes[i]),()->{
                        theme=modes[ix];prefs.edit().putString("theme",theme).apply();haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});}
                divider(c,356);
                boolean animations=prefs.getBoolean("animations",true);
                txt(c,"✎",61,421,48,32,ink());
                left(c,tr("Animations","الحركات","Animaciones"),96,419,400,25,ink());
                toggleArt(c,588,386,animations,()->{prefs.edit().putBoolean("animations",!animations).apply();haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});
            });
            group(c,2,()->{
                left(c,tr("LANGUAGE","اللغة","IDIOMA"),28,523,650,21,muted);
                box(c,22,538,698,642,22,paper());
                txt(c,"文",61,601,48,29,ink());
                String selected=language.equals("ar")?"العربية":language.equals("es")?"Español":"English";
                left(c,selected,96,600,330,25,ink());
                selectArt(c,selected+"  ▾",520,560,674,620,false,MainActivity.this::showLanguagePicker);
            });
            group(c,3,()->{
                left(c,tr("PRINTING / ASSEMBLY","الطباعة / التجميع","IMPRESIÓN / MONTAJE"),28,690,650,21,muted);
                box(c,22,705,698,809,22,paper());
                boolean hints=prefs.getBoolean("hints",true);
                txt(c,"▱",61,768,48,32,ink());
                left(c,tr("Show Joining / Pasting Guidance","إظهار تلميحات الربط واللصق",
                        "Mostrar guías de unión"),96,767,440,24,ink());
                toggleArt(c,588,736,hints,()->{prefs.edit().putBoolean("hints",!hints).apply();haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});
            });
            group(c,4,()->{
                left(c,tr("GENERAL","عام","GENERAL"),28,857,650,21,muted);
                box(c,22,872,698,1179,22,paper());
                boolean haptics=prefs.getBoolean("haptics",true);
                txt(c,"♧",61,932,48,31,ink());
                left(c,tr("Haptic Feedback","الاهتزاز اللمسي","Respuesta háptica"),96,931,420,25,ink());
                toggleArt(c,588,900,haptics,()->{prefs.edit().putBoolean("haptics",!haptics).apply();if(!haptics)haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});
                divider(c,972);
                txt(c,"↶",61,1032,48,32,ink());
                left(c,tr("Reset Settings","إعادة ضبط الإعدادات","Restablecer ajustes"),96,1031,360,25,ink());
                selectArt(c,tr("Reset","إعادة","Restablecer"),505,994,674,1058,false,MainActivity.this::resetSettings);
                divider(c,1073);
                txt(c,"?",61,1137,48,31,ink());
                left(c,tr("About","حول التطبيق","Acerca de"),96,1135,400,25,ink());
                txt(c,"v1.0",640,1135,75,20,muted);hit(42,1080,678,1168,MainActivity.this::showAbout);
                txt(c,tr("Offline-first · No analytics · Images stay on-device","يعمل دون اتصال · بلا تحليلات · صورك تبقى على الجهاز",
                        "Sin conexión · Sin analíticas · Imágenes en el dispositivo"),360,1245,650,19,muted);
            });
        }
        private void projectsArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Projects","المشاريع","Proyectos"),MainActivity.this::home));
            JSONArray projects=storedProjects();
            int pages=Math.max(1,(projects.length()+4)/5);
            if(listPage>=pages)listPage=pages-1;
            for(int i=0;i<5;i++){
                int ix=listPage*5+i;
                JSONObject project=projects.optJSONObject(ix);
                if(project==null)break;
                final int selected=ix;
                final float y=145+i*184;
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
            if(projects.length()==0){
                folder(c,360,330);
                txt(c,tr("No projects yet","لا توجد مشاريع بعد","Aún no hay proyectos"),360,400,635,30,muted);
            }
            group(c,7,()->buttonArt(c,"＋  "+tr("Create Project","إنشاء مشروع","Crear proyecto"),
                    22,1310,698,1394,true,()->newProject(false)));
        }
        private void pager(Canvas c,int index,int pages,Runnable prev,Runnable next) {
            if(pages<=1)return;
            buttonArt(c,"‹",180,1205,270,1275,false,prev);
            txt(c,(index+1)+" / "+pages,360,1248,160,24,ink());
            buttonArt(c,"›",450,1205,540,1275,false,next);
        }
        private void detailArt(Canvas c) {
            JSONObject project=storedProjects().optJSONObject(detailIndex);
            if(project==null){projectsArt(c);return;}
            group(c,0,()->chrome(c,project.optString("name"),MainActivity.this::projects));
            group(c,1,()->{
                buttonArt(c,tr("Rename","إعادة تسمية","Renombrar"),22,130,345,202,false,()->renameProject(detailIndex));
                buttonArt(c,tr("Delete","حذف","Eliminar"),375,130,698,202,false,()->deleteProject(detailIndex));
            });
            JSONArray items=project.optJSONArray("items");
            if(items==null)items=new JSONArray();
            int pages=Math.max(1,(items.length()+3)/4);
            if(listPage>=pages)listPage=pages-1;
            if(items.length()==0){
                pictureIcon(c,360,440);
                txt(c,tr("This project is empty","هذا المشروع فارغ","Este proyecto está vacío"),360,530,600,30,ink());
                txt(c,tr("Export an image, then choose Add to Project",
                        "صدّر صورة ثم اختر إضافة لمشروع","Exporta una imagen y elige Añadir al proyecto"),360,575,610,22,muted);
            }
            for(int i=0;i<4;i++){
                int ix=items.length()-1-listPage*4-i;
                JSONObject entry=items.optJSONObject(ix);
                if(entry==null)break;
                JSONArray uris=entry.optJSONArray("uris");
                if(uris==null)continue;
                java.util.ArrayList<Uri> images=new java.util.ArrayList<>();
                for(int j=0;j<uris.length();j++) images.add(Uri.parse(uris.optString(j)));
                final int itemIndex=ix;
                final float y=225+i*270;
                group(c,i+2,()->{
                    box(c,22,y,698,y+245,19,paper());
                    left(c,entry.optString("date"),48,y+54,580,27,ink());
                    left(c,images.size()+" "+tr("parts","أجزاء","partes")+" · "+
                                    (entry.optBoolean("horizontal",true)?tr("Horizontal","أفقي","Horizontal"):tr("Vertical","عمودي","Vertical")),
                            48,y+94,580,21,muted);
                    if(images.isEmpty()) left(c,tr("Missing result","النتيجة مفقودة","Resultado ausente"),48,y+130,580,21,0xFFD64545);
                    buttonArt(c,tr("Share","مشاركة","Compartir"),42,y+154,246,y+220,false,()->share(images));
                    buttonArt(c,tr("Re-export","إعادة التصدير","Reexportar"),258,y+154,474,y+220,false,()->reExportProjectItem(detailIndex,itemIndex));
                    buttonArt(c,tr("Remove","إزالة","Quitar"),486,y+154,678,y+220,false,()->removeProjectItem(detailIndex,itemIndex));
                });
            }
            if(pages>1){
                buttonArt(c,"‹",180,1340,270,1415,false,()->{listPage--;invalidate();});
                txt(c,(listPage+1)+" / "+pages,360,1387,160,24,ink());
                buttonArt(c,"›",450,1340,540,1415,false,()->{listPage++;invalidate();});
            }
        }
        private void resultArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Your image is ready","صورتك جاهزة",
                    "Tu imagen está lista"),MainActivity.this::home));
            group(c,1,()->{
                txt(c,"✓ "+tr("Saved to Pictures/ImageSplitter",
                        "حُفظت في الصور/ImageSplitter","Guardada en Imágenes/ImageSplitter"),
                        360,155,670,24,accent);
                txt(c,exports.size()+" "+tr("parts exported","أجزاء تم تصديرها","partes exportadas"),360,185,650,20,muted);
            });
            int pages=Math.max(1,(exports.size()+2)/3);
            if(resultPage>=pages)resultPage=pages-1;
            if(exports.isEmpty()){
                pictureIcon(c,360,410);
                txt(c,tr("This result is missing","هذه النتيجة مفقودة","Falta este resultado"),360,500,600,30,ink());
                buttonArt(c,tr("Back to Home","العودة للرئيسية","Volver al inicio"),120,550,600,625,true,MainActivity.this::home);
            }
            for(int i=0;i<3;i++){
                int ix=resultPage*3+i;
                if(ix>=exports.size())break;
                Uri uri=exports.get(ix);
                final float y=210+i*235;
                group(c,i+2,()->{
                    box(c,22,y,698,y+210,21,paper());
                    drawPartThumb(c,ix,48,y+25,235,y+185);
                    left(c,tr("Part ","الجزء ","Parte ")+(ix+1),270,y+68,380,29,ink());
                    int[] d=ix<exportDimensions.size()?exportDimensions.get(ix):new int[]{0,0};
                    left(c,d[0]>0?d[0]+" × "+d[1]+" px":tr("Dimensions unavailable","المقاس غير متاح","Dimensiones no disponibles"),270,y+107,380,21,muted);
                    left(c,tr("Saved successfully","تم الحفظ بنجاح","Guardado correctamente"),270,y+142,380,20,accent);
                    buttonArt(c,tr("Share","مشاركة","Compartir"),470,y+132,675,y+188,
                            false,()->shareOne(uri));
                });
            }
            buttonArt(c,tr("Add to project","إضافة لمشروع","Añadir al proyecto"),
                    22,940,698,1018,true,MainActivity.this::chooseProject);
            buttonArt(c,tr("Save / Download All","حفظ / تنزيل الكل","Guardar / Descargar todo"),
                    22,1035,698,1110,false,()->sketchDialog(tr("Already saved","تم الحفظ","Ya guardado"),
                            tr("All parts are available in Pictures/ImageSplitter.",
                                    "كل الأجزاء موجودة في الصور/ImageSplitter.",
                                    "Todas las partes están en Imágenes/ImageSplitter."),
                            new String[]{tr("OK","حسنًا","Aceptar")},ignored->{}));
            buttonArt(c,tr("Share all parts","مشاركة كل الأجزاء","Compartir todas las partes"),
                    22,1127,698,1202,false,()->share(exports));
            if(pages>1) {
                buttonArt(c,"‹",270,1230,330,1300,false,()->{resultPage--;invalidate();});
                txt(c,(resultPage+1)+"/"+pages,360,1274,85,21,ink());
                buttonArt(c,"›",390,1230,450,1300,false,()->{resultPage++;invalidate();});
            }
        }
        private void drawPartThumb(Canvas c,int index,float l,float t,float r,float b) {
            if(preview==null){pictureIcon(c,(l+r)/2,(t+b)/2);return;}
            Rect src=SplitEngine.partRect(preview.getWidth(),preview.getHeight(),horizontal,parts,index);
            float scale=Math.min((r-l)/src.width(),(b-t)/src.height());
            float width=src.width()*scale,height=src.height()*scale;
            RectF dst=new RectF((l+r-width)/2f,(t+b-height)/2f,(l+r+width)/2f,(t+b+height)/2f);
            c.drawBitmap(preview,src,dst,p);outline(c,l,t,r,b,12,ink());
            outline(c,dst.left,dst.top,dst.right,dst.bottom,10,ink());
            p.setColor(accent);c.drawCircle(dst.left+18,dst.top+18,15,p);
            txt(c,String.valueOf(index+1),dst.left+18,dst.top+24,24,16,Color.WHITE);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            float x=event.getX()/sx, y=event.getY()/sy;
            if(event.getAction()==MotionEvent.ACTION_DOWN) {
                touchX=x;touchY=y;
                draggingSlider=sketchMode==0&&y>=770&&y<=826;
                pressedZone=null;longTriggered=false;
                for(int i=zones.size()-1;i>=0;i--){Zone z=zones.get(i);if(z.rect.contains(x,y)){pressedZone=z;break;}}
                if(draggingSlider){slide(x);return true;}
                if(pressedZone!=null&&pressedZone.longAction!=null)handler.postDelayed(longPress,480);
                invalidate();
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_MOVE) {
                if(draggingSlider){slide(x);return true;}
                if(Math.abs(x-touchX)>18||Math.abs(y-touchY)>18){handler.removeCallbacks(longPress);pressedZone=null;invalidate();}
            }
            if(event.getAction()==MotionEvent.ACTION_UP) {
                handler.removeCallbacks(longPress);
                if(draggingSlider){slide(x);draggingSlider=false;return true;}
                Zone released=pressedZone;pressedZone=null;invalidate();
                if(longTriggered)return true;
                if(Math.abs(x-touchX)>25||Math.abs(y-touchY)>25)return true;
                if(released!=null&&released.rect.contains(x,y)){released.action.run();return true;}
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(longPress);pressedZone=null;draggingSlider=false;invalidate();}
            return true;
        }
        private void slide(float x){
            int next=2+Math.round(Math.max(0,Math.min(1,(x-45)/631f))*18);
            if(next!=parts){parts=next;sizeTip=false;haptic(HapticFeedbackConstants.CLOCK_TICK);requestLayout();}
            invalidate();
        }
        private final class Zone {
            final RectF rect;final Runnable action,longAction;
            Zone(RectF r,Runnable a,Runnable l){rect=r;action=a;longAction=l;}
        }
    }

    private android.app.Dialog sketchDialog(String heading, String message, String[] options, java.util.function.IntConsumer selected) {
        return sketchDialog(heading,message,options,-1,selected);
    }

    private android.app.Dialog sketchDialog(String heading, String message, String[] options,
                                             int selectedIndex, java.util.function.IntConsumer selected) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(surface, line, 22));
        box.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout header=row();
        TextView title = label(heading, 22, true, text);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView close=label("×",30,false,text);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        box.addView(header);
        if (message != null) {
            gap(box, 10);
            TextView info = label(message, 16, false, muted);
            box.addView(info);
        }
        LinearLayout optionBox=options.length==2?row():column();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView choice=optionButton(options[i],i==selectedIndex,() -> { dialog.dismiss(); selected.accept(index); });
            if(options.length==2){
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(56),1);
                if(i==0)p.setMargins(0,0,dp(8),0);optionBox.addView(choice,p);
            } else {
                optionBox.addView(choice,new LinearLayout.LayoutParams(-1,dp(56)));
                if(i<options.length-1)gap(optionBox,8);
            }
        }
        ScrollView optionScroll=new ScrollView(this);
        optionScroll.setVerticalScrollBarEnabled(false);optionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        optionScroll.addView(optionBox);
        box.addView(optionScroll,new LinearLayout.LayoutParams(-1,dp(options.length==2?64:Math.min(430,Math.max(64,options.length*64)))));
        dialog.setContentView(box);
        presentSketchDialog(dialog,box);
        return dialog;
    }

    private void sketchInput(String heading, String hint, String initial, boolean numeric,
                             java.util.function.Consumer<String> confirmed) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(surface, line, 22));
        box.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout header=row();
        header.addView(label(heading,22,true,text),new LinearLayout.LayoutParams(0,dp(44),1));
        TextView close=label("×",30,false,text);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));box.addView(header);
        gap(box, 14);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(initial);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(19);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(shape(background, accent, 12));
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
        presentSketchDialog(dialog,box);
        input.requestFocus();
    }

    private void presentSketchDialog(Dialog dialog, View card) {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams lp=dialog.getWindow().getAttributes();
            lp.dimAmount=.42f;
            dialog.getWindow().setAttributes(lp);
            int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(36),dp(440));
            dialog.getWindow().setLayout(width,ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if(prefs.getBoolean("animations",true)){
            card.setAlpha(0f);card.setScaleX(.96f);card.setScaleY(.96f);card.setTranslationY(dp(12));
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(180).start();
        }
    }

    private final class SketchFrameDrawable extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor,borderColor;
        private final float radius;
        SketchFrameDrawable(int fillColor,int borderColor,float radius){this.fillColor=fillColor;this.borderColor=borderColor;this.radius=radius;}
        @Override public void draw(Canvas canvas){
            Rect bounds=getBounds();RectF outer=new RectF(bounds.left+.8f,bounds.top+.8f,bounds.right-.8f,bounds.bottom-.8f);
            paint.setStyle(Paint.Style.FILL);paint.setColor(fillColor);canvas.drawRoundRect(outer,radius,radius,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.15f));paint.setColor(borderColor);canvas.drawRoundRect(outer,radius,radius,paint);
            paint.setStrokeWidth(Math.max(1f,dp(.38f)));paint.setColor((borderColor&0x00FFFFFF)|0x55000000);
            RectF echo=new RectF(outer.left+dp(.7f),outer.top-dp(.35f),outer.right+dp(.25f),outer.bottom+dp(.55f));
            canvas.drawRoundRect(echo,radius*.96f,radius*.96f,paint);
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }

}
