package com.imagesplitter.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
    private boolean dark, horizontal = true, processing;
    private String theme = "system";
    private int parts = 3, output = SplitEngine.PX_1080, custom = 1500;
    private int sourceWidth, sourceHeight;
    private Uri source;
    private Bitmap preview;
    private List<Uri> exports = new ArrayList<>();
    private List<int[]> exportDimensions = new ArrayList<>();
    private String language = "en";

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
            sourceWidth = size[0];
            sourceHeight = size[1];
            if (showHome) home(); else showSketch(sketchMode);
        } catch (Exception e) {
            source = null;
            sourceWidth = sourceHeight = 0;
            if (showHome) home(); else showSketch(sketchMode);
            toast(tr("The selected image is missing or unsupported",
                    "الصورة المحددة مفقودة أو غير مدعومة",
                    "La imagen seleccionada no existe o no es compatible"));
        }
    }

    private void askCustom() { sketchInput(tr("Longest side in pixels (100–8000)", "الضلع الأطول بالبكسل (100–8000)", "Lado largo en píxeles (100–8000)"), "100–8000 px", String.valueOf(custom), true, value -> { try { int n=Integer.parseInt(value); if(n<100||n>8000) throw new NumberFormatException(); custom=n; output=-1; home(); } catch(NumberFormatException e) { toast("100–8000 px"); } }); }

    private void export() {
        if (source == null) { toast(tr("Choose an image first", "اختر صورة أولًا",
                "Selecciona una imagen primero")); return; }
        if (processing) return;
        final Uri selected = source;
        final int count = parts, target = output == -1 ? custom : output;
        final int selectedWidth=sourceWidth, selectedHeight=sourceHeight, exportAccent=accent;
        final boolean direction = horizontal, hints = prefs.getBoolean("hints", true);
        final String folder = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        processing = true;
        if (sketchScreen != null) sketchScreen.invalidate();
        new Thread(() -> {
            try {
                List<Uri> result = SplitEngine.export(getContentResolver(), selected, count,
                        direction, target, hints, exportAccent, folder);
                ArrayList<int[]> dimensions = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    dimensions.add(SplitEngine.outputPartDimensions(selectedWidth, selectedHeight,
                            direction, count, i, target));
                }
                runOnUiThread(() -> {
                    processing = false;
                    exports = result;
                    exportDimensions = dimensions;
                    haptic(HapticFeedbackConstants.CONFIRM);
                    resultScreen();
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

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private void showLanguagePicker() {
        String[] labels={"العربية","English","Español"};
        String[] codes={"ar","en","es"};
        sketchDialog(tr("Choose Language","اختر اللغة","Elegir idioma"),
                tr("The interface updates immediately","تتحدث الواجهة فورًا","La interfaz se actualiza al instante"),
                labels,index->{language=codes[index];prefs.edit().putString("language",language).apply();settings();});
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
        dialog.setContentView(box);dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(36),-2);}
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

    private void showSketch(int mode) {
        updatePalette();
        sketchMode = mode;
        int contentHeight = mode == 0 ? 1960 : mode == 1 ? 1450 : mode == 3 ? 1570 : 1450;
        sketchScreen = new SketchScreen(contentHeight);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(28));
        scroll.setBackgroundColor(background);
        scroll.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scroll.addView(sketchScreen, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private final class SketchScreen extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.ArrayList<Zone> zones = new java.util.ArrayList<>();
        private final android.graphics.Typeface handwriting =
                android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL);
        private final Bitmap brand = BitmapFactory.decodeResource(getResources(), R.drawable.brand_reference);
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
            setContentDescription("Image Splitter");
            setFocusable(true);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, Math.max(1, Math.round(width * contentHeight / 720f)));
        }

        @Override protected void onDraw(Canvas actual) {
            super.onDraw(actual);
            sx = getWidth() / 720f;
            sy = sx;
            zones.clear();
            actual.drawColor(background);
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
            fill(c, l, t, r, b, 14, active ? accent : paper());
            outline(c, l, t, r, b, 14, active ? accent : ink());
            txt(c, value, (l+r)/2f, (t+b)/2f + 8, r-l-15, 23,
                    active ? Color.WHITE : ink());
            hit(l,t,r,b,click);
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
                fill(c,22,255,698,510,25,wash());
                outline(c,22,255,698,510,25,ink());
                dashed(c,34,267,686,498);
                if(preview!=null) {
                    RectF thumb=fitRect(preview,260,282,460,410);
                    c.drawBitmap(preview,null,thumb,p);
                    outline(c,thumb.left,thumb.top,thumb.right,thumb.bottom,12,ink());
                    txt(c,tr("Change image","تغيير الصورة","Cambiar imagen"),360,450,620,29,ink());
                    txt(c,sourceWidth+" × "+sourceHeight+" px",360,482,625,21,muted);
                } else {
                    pictureIcon(c,360,345);
                    txt(c,tr("Select an Image","اختر صورة","Selecciona una imagen"),
                            360,429,620,30,ink());
                    txt(c,tr("Tap to choose from your gallery","اضغط للاختيار من المعرض",
                            "Toca para elegir de tu galería"),360,468,625,22,muted);
                }
                hit(22,255,698,510,MainActivity.this::pick);
            });
            group(c,2,()->{
                box(c,22,535,698,850,22,paper());
                left(c,tr("Split Direction","اتجاه التقسيم","Dirección de corte"),
                        42,580,620,27,ink());
                buttonArt(c,tr("☰  Horizontal","☰  أفقي","☰  Horizontal"),
                        40,600,356,672,horizontal,()->{ horizontal=true;sizeTip=false;haptic(HapticFeedbackConstants.CLOCK_TICK);invalidate();});
                buttonArt(c,tr("◫  Vertical","◫  عمودي","◫  Vertical"),
                        364,600,680,672,!horizontal,()->{horizontal=false;sizeTip=false;haptic(HapticFeedbackConstants.CLOCK_TICK);invalidate();});
                left(c,tr("Number of Parts","عدد الأجزاء","Número de partes"),
                        42,726,380,24,ink());
                float knobX=45+(parts-2)*631f/18;
                fill(c,knobX-20,738,knobX+20,776,10,accent);
                Path pointer=new Path();pointer.moveTo(knobX-7,776);pointer.lineTo(knobX,785);
                pointer.lineTo(knobX+7,776);pointer.close();p.setColor(accent);c.drawPath(pointer,p);
                txt(c,String.valueOf(parts),knobX,765,35,23,Color.WHITE);
                line(c,45,798,676,798,dark?0xFF566572:0xFFD2D5D6,4f);
                line(c,45,798,knobX,798,accent,6f);
                p.setColor(paper());p.setStyle(Paint.Style.FILL);
                c.drawCircle(knobX,798,14,p);
                p.setColor(ink());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);
                c.drawCircle(knobX,798,14,p);p.setStyle(Paint.Style.FILL);
                left(c,"2",42,833,50,19,ink());txt(c,"20",665,833,50,19,ink());
                hit(35,773,685,824,()->{});
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
                    buttonArt(c,labels[i],x,942,x+151,1023,output==values[i],select);
                    hit(x,942,x+151,1023,select,()->{sizeTipChoice=choice;sizeTip=true;invalidate();});
                }
            });
            group(c,4,()->{
                box(c,22,1080,698,1745,22,paper());
                left(c,tr("Preview","المعاينة","Vista previa"),42,1128,610,29,ink());
                if(preview==null) {
                    pictureIcon(c,360,1370);
                    txt(c,tr("Your split preview will appear here","ستظهر معاينة التقسيم هنا",
                            "La vista previa aparecerá aquí"),360,1460,590,25,muted);
                } else previewTiles(c,preview,58,1160,662,1705);
            });
            group(c,5,()->{
                buttonArt(c,tr("▱   Split & Export","▱   تقسيم وتصدير",
                        "▱   Cortar y exportar"),22,1780,698,1882,true,MainActivity.this::export);
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
            notch.lineTo(anchor+16,1072);notch.close();p.setColor(wash());c.drawPath(notch,p);
            box(c,42,1070,678,1348,22,wash());
            String[] labels={tr("Original","الأصلي","Original"),"750 px","1080 px",
                    tr("Custom","مخصص","Personalizado")};
            txt(c,tr("Example (","مثال (","Ejemplo (")+labels[sizeTipChoice]+")",360,1110,590,24,ink());
            if(sourceWidth<2||sourceHeight<2) {
                txt(c,tr("Select an image to calculate real dimensions",
                        "اختر صورة لحساب المقاسات الحقيقية",
                        "Selecciona una imagen para calcular"),360,1210,570,24,muted);
            } else {
                int target=sizeTipChoice==0?0:sizeTipChoice==1?750:sizeTipChoice==2?1080:custom;
                int totalW=horizontal?0:0,totalH=horizontal?0:0;
                int firstW=0,firstH=0;
                for(int i=0;i<parts;i++){
                    int[] d=SplitEngine.outputPartDimensions(sourceWidth,sourceHeight,horizontal,parts,i,target);
                    if(i==0){firstW=d[0];firstH=d[1];}
                    if(horizontal){totalW=Math.max(totalW,d[0]);totalH+=d[1];}
                    else{totalW+=d[0];totalH=Math.max(totalH,d[1]);}
                }
                previewTiles(c,preview,70,1130,315,1305);
                line(c,344,1138,344,1314,ink(),1f);
                left(c,tr("Each part:","كل جزء:","Cada parte:"),375,1178,260,21,muted);
                left(c,firstW+" × "+firstH+" px",375,1212,260,25,ink());
                left(c,tr("Total joined:","الإجمالي بعد الجمع:","Total unido:"),375,1255,260,21,muted);
                left(c,totalW+" × "+totalH+" px · "+parts+" "+tr("parts","أجزاء","partes"),375,1292,275,22,ink());
            }
            hit(42,1070,678,1348,()->{sizeTip=false;invalidate();});
        }
        private void processingOverlay(Canvas c) {
            fill(c,42,1640,678,1888,24,alpha(background,238));
            outline(c,42,1640,678,1888,24,accent);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);p.setColor(alpha(accent,80));
            c.drawCircle(360,1715,34,p);p.setColor(accent);
            c.drawArc(new RectF(326,1681,394,1749),-90,120,false,p);p.setStyle(Paint.Style.FILL);
            txt(c,tr("Splitting & saving…","جارٍ التقسيم والحفظ…","Cortando y guardando…"),360,1795,560,28,ink());
            txt(c,tr("Large images may take a moment","قد تستغرق الصور الكبيرة لحظات",
                    "Las imágenes grandes pueden tardar"),360,1840,560,20,muted);
        }
        private void previewTiles(Canvas c,Bitmap bmp,float l,float t,float r,float b) {
            if(bmp==null)return;
            int n=Math.min(parts,20);
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
            buttonArt(c,"←",22,18,86,82,false,back);
            txt(c,heading,360,66,520,36,ink());
            line(c,25,108,695,108,ink(),1.5f);
        }
        private void settingsArt(Canvas c) {
            group(c,0,()->chrome(c,tr("Settings","الإعدادات","Ajustes"),MainActivity.this::home));
            group(c,1,()->{
                box(c,22,135,698,630,22,paper());
                left(c,tr("APPEARANCE","المظهر","APARIENCIA"),48,178,620,22,accent);
                box(c,42,198,678,286,16,wash());
                p.setColor(accent);c.drawCircle(82,242,22,p);outline(c,60,220,104,264,22,ink());
                left(c,tr("Accent Color","لون التطبيق","Color de acento"),125,237,410,26,ink());
                left(c,String.format(Locale.US,"#%06X",accent&0xFFFFFF),125,267,410,19,muted);
                txt(c,"›",644,255,40,38,ink());hit(42,198,678,286,MainActivity.this::showColorPicker);
                left(c,tr("Theme","السمة","Tema"),48,335,620,24,ink());
                String[] modes={"system","light","dark"};
                String[] names={tr("System","النظام","Sistema"),tr("Light","فاتح","Claro"),tr("Dark","داكن","Oscuro")};
                for(int i=0;i<3;i++){final int ix=i;float x=42+i*212;
                    buttonArt(c,names[i],x,355,x+202,425,theme.equals(modes[i]),()->{
                        theme=modes[ix];prefs.edit().putString("theme",theme).apply();haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});}
                boolean animations=prefs.getBoolean("animations",true);
                buttonArt(c,(animations?"✓  ":"○  ")+tr("Animations","الحركات","Animaciones"),42,452,350,518,animations,()->{
                    prefs.edit().putBoolean("animations",!animations).apply();haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});
                boolean haptics=prefs.getBoolean("haptics",true);
                buttonArt(c,(haptics?"✓  ":"○  ")+tr("Haptic Feedback","الاهتزاز اللمسي","Respuesta háptica"),370,452,678,518,haptics,()->{
                    prefs.edit().putBoolean("haptics",!haptics).apply();if(!haptics)haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});
            });
            group(c,2,()->{
                box(c,22,655,698,825,22,paper());
                left(c,tr("LANGUAGE","اللغة","IDIOMA"),48,698,620,22,accent);
                String selected=language.equals("ar")?"العربية":language.equals("es")?"Español":"English";
                buttonArt(c,selected+"   ›",42,725,678,800,false,MainActivity.this::showLanguagePicker);
            });
            group(c,3,()->{
                box(c,22,850,698,1025,22,paper());
                left(c,tr("PRINTING / ASSEMBLY","الطباعة / التجميع","IMPRESIÓN / MONTAJE"),48,894,620,22,accent);
                boolean hints=prefs.getBoolean("hints",true);
                buttonArt(c,(hints?"✓  ":"○  ")+tr("Show Joining / Pasting Guidance",
                        "إظهار تلميحات الربط واللصق","Mostrar guías de unión"),42,920,678,997,hints,()->{
                    prefs.edit().putBoolean("hints",!hints).apply();haptic(HapticFeedbackConstants.CLOCK_TICK);settings();});
            });
            group(c,4,()->{
                box(c,22,1050,698,1325,22,paper());
                left(c,tr("GENERAL","عام","GENERAL"),48,1094,620,22,accent);
                buttonArt(c,tr("Reset Settings","إعادة ضبط الإعدادات","Restablecer ajustes"),42,1120,678,1192,false,MainActivity.this::resetSettings);
                buttonArt(c,tr("About Image Splitter","حول Image Splitter","Acerca de Image Splitter"),42,1210,678,1282,false,MainActivity.this::showAbout);
                txt(c,tr("Offline-first · No analytics · Images stay on-device",
                        "يعمل دون اتصال · بلا تحليلات · صورك تبقى على الجهاز",
                        "Sin conexión · Sin analíticas · Imágenes en el dispositivo"),360,1380,650,19,muted);
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
            if(projects.length()==0){
                pictureIcon(c,360,400);
                txt(c,tr("No projects yet","لا توجد مشاريع بعد","Aún no hay proyectos"),360,500,635,30,ink());
                txt(c,tr("Create a project to keep each print job organized",
                        "أنشئ مشروعًا لترتيب كل عملية طباعة",
                        "Crea un proyecto para organizar cada impresión"),360,545,635,22,muted);
            }
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
            RectF dst=new RectF(l,t,r,b);
            c.drawBitmap(preview,src,dst,p);outline(c,l,t,r,b,12,ink());
            p.setColor(accent);c.drawCircle(l+18,t+18,15,p);
            txt(c,String.valueOf(index+1),l+18,t+24,24,16,Color.WHITE);
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
            if(next!=parts){parts=next;sizeTip=false;haptic(HapticFeedbackConstants.CLOCK_TICK);}
            invalidate();
        }
        private final class Zone {
            final RectF rect;final Runnable action,longAction;
            Zone(RectF r,Runnable a,Runnable l){rect=r;action=a;longAction=l;}
        }
    }

    private android.app.Dialog sketchDialog(String heading, String message, String[] options, java.util.function.IntConsumer selected) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(surface, line, 22));
        box.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TextView title = label(heading, 22, true, text);
        box.addView(title);
        if (message != null) {
            gap(box, 10);
            TextView info = label(message, 16, false, muted);
            box.addView(info);
        }
        LinearLayout optionBox=column();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            optionBox.addView(button(options[i], false, () -> { dialog.dismiss(); selected.accept(index); }));
            if(i<options.length-1)gap(optionBox,8);
        }
        ScrollView optionScroll=new ScrollView(this);
        optionScroll.setVerticalScrollBarEnabled(false);optionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        optionScroll.addView(optionBox);
        box.addView(optionScroll,new LinearLayout.LayoutParams(-1,dp(Math.min(430,Math.max(64,options.length*64)))));
        dialog.setContentView(box);
        dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(44), -2);}
        return dialog;
    }

    private void sketchInput(String heading, String hint, String initial, boolean numeric,
                             java.util.function.Consumer<String> confirmed) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(shape(surface, line, 22));
        box.setLayoutDirection(language.equals("ar") ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
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
