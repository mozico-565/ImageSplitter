package com.imagesplitter.app;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native style workflow, with the same options as Style Lab. */
public final class StyleTransferActivity extends Activity {
    private static final int PICK_ORIGINAL=81, PICK_REFERENCE=82;
    private static final String[] STYLES={"Original","Pencil Sketch","Ink Drawing","Watercolor","Oil Painting","Anime","Comic Book","Pixel Art","3D Illustration","Clay","Vintage Poster","Graphic Novel","Charcoal","Digital Art","Custom Style","Reference Image"};
    private static final String[] AR={"الأصلية","رسم بالقلم الرصاص","رسم بالحبر","ألوان مائية","رسم زيتي","أنمي","كوميكس","فن البكسل","رسم ثلاثي الأبعاد","صلصال","ملصق كلاسيكي","رواية مصورة","فحم","فن رقمي","أسلوب مخصص","صورة مرجعية"};
    private static final String[] ES={"Original","Lápiz","Tinta","Acuarela","Óleo","Anime","Cómic","Pixel art","Ilustración 3D","Arcilla","Póster vintage","Novela gráfica","Carboncillo","Arte digital","Estilo personalizado","Imagen de referencia"};
    private static final String[] ASSETS={"original","pencil","ink","watercolor","oil","anime","comic","pixel","illustration_3d","clay","vintage","graphic_novel","charcoal","digital"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri source,reference,resultSource,saved;
    private Bitmap original,referencePreview,resultImage;
    private int originalW,originalH,selected=3,strength=65,accent,background,surface,ink,muted;
    private int generatedStrength; private String generatedStyle="",model="",custom="",message="";
    private int generatedOriginalW,generatedOriginalH;
    private long generationMs;
    private boolean busy,dead;
    private String language="en",fontStyle="sketch";
    private android.graphics.Typeface typeface;
    private StyleApi api;
    private ScrollView scroll; private HorizontalScrollView stylesScroll;
    private LinearLayout page,styleSection,resultSection;
    private TextView generateButton,status,strengthLabel;
    private ProgressBar progress;
    private final java.util.ArrayList<View> actions=new java.util.ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        android.content.SharedPreferences prefs=getSharedPreferences("settings",MODE_PRIVATE);
        language=prefs.getString("language","en");fontStyle=prefs.getString("fontStyle","sketch");
        accent=prefs.getInt("accent",0xFF3B8FF5);
        String theme=prefs.getString("theme","system");
        boolean dark=theme.equals("dark")||(theme.equals("system")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        background=dark?0xFF101820:0xFFFBFAF6;surface=dark?0xFF1A2732:0xFFFFFEFA;
        ink=dark?Color.WHITE:0xFF171A20;muted=dark?0xFFB8C6D4:0xFF62676F;
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        if(!dark)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try{typeface=fontStyle.equals("clean")?android.graphics.Typeface.DEFAULT:getResources().getFont(R.font.kalam_regular);}catch(Exception e){typeface=android.graphics.Typeface.DEFAULT;}
        if(state!=null){
            selected=state.getInt("style",3);strength=state.getInt("strength",65);custom=state.getString("custom","");
            source=uri(state.getString("source"));reference=uri(state.getString("reference"));resultSource=uri(state.getString("result"));saved=uri(state.getString("saved"));
            generatedStyle=state.getString("generatedStyle","");generatedStrength=state.getInt("generatedStrength");model=state.getString("model","");generationMs=state.getLong("time");
            generatedOriginalW=state.getInt("gw");generatedOriginalH=state.getInt("gh");
        }else source=getIntent().getData();
        render();
        if(source!=null)restoreImages();
    }
    private static Uri uri(String value){return value==null?null:Uri.parse(value);}
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);out.putInt("style",selected);out.putInt("strength",strength);out.putString("custom",custom);
        if(source!=null)out.putString("source",source.toString());if(reference!=null)out.putString("reference",reference.toString());
        if(resultSource!=null)out.putString("result",resultSource.toString());if(saved!=null)out.putString("saved",saved.toString());
        out.putString("generatedStyle",generatedStyle);out.putInt("generatedStrength",generatedStrength);out.putString("model",model);out.putLong("time",generationMs);out.putInt("gw",generatedOriginalW);out.putInt("gh",generatedOriginalH);
    }
    @Override protected void onDestroy(){dead=true;if(api!=null)api.cancel();worker.shutdownNow();super.onDestroy();}
    private String tr(String en,String ar,String es){return language.equals("ar")?ar:language.equals("es")?es:en;}
    private String styleName(int index){return language.equals("ar")?AR[index]:language.equals("es")?ES[index]:STYLES[index];}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private GradientDrawable shape(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(15));d.setStroke(dp(1.5f),stroke);return d;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(ink);t.setTextSize(size);t.setTypeface(typeface,bold?1:0);t.setTextDirection(language.equals("ar")?View.TEXT_DIRECTION_RTL:View.TEXT_DIRECTION_LTR);return t;}
    private void space(LinearLayout parent,int amount){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(amount)));}
    private void heading(LinearLayout parent,String value){space(parent,18);parent.addView(text(value,22,true));space(parent,10);}
    private TextView button(LinearLayout parent,String title,boolean primary,Runnable action){
        TextView t=text(title,17,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(52));t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setTextColor(primary?Color.WHITE:ink);t.setBackground(shape(primary?accent:surface,primary?accent:muted));
        t.setOnClickListener(v->{if(!busy)action.run();});parent.addView(t,new LinearLayout.LayoutParams(-1,-2));space(parent,10);actions.add(t);return t;
    }
    private ImageView preview(LinearLayout parent,Bitmap bitmap){
        ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setMaxHeight(dp(430));image.setBackground(shape(surface,muted));image.setPadding(dp(8),dp(8),dp(8),dp(8));parent.addView(image,new LinearLayout.LayoutParams(-1,-2));return image;
    }
    private void render(){
        int y=scroll==null?0:scroll.getScrollY(),x=stylesScroll==null?dp(selected*136):stylesScroll.getScrollX();actions.clear();
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(background);scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        page=column();page.setPadding(dp(16),dp(14),dp(16),dp(36));page.setLayoutDirection(language.equals("ar")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);scroll.addView(page);setContentView(scroll);
        button(page,tr("‹ Back to ImageSplitter","العودة إلى ImageSplitter","‹ Volver a ImageSplitter"),false,this::finish);
        page.addView(text(tr("AI Style Transfer","تحويل أسلوب الصورة بالذكاء الاصطناعي","Transferencia de estilo con IA"),26,true));
        TextView intro=text(tr("Upload → Choose style → Generate → Compare → Save","ارفع الصورة ← اختر الأسلوب ← ولّد ← قارن ← احفظ","Subir → Elegir → Generar → Comparar → Guardar"),14,false);intro.setTextColor(muted);page.addView(intro);
        heading(page,tr("1. Original Image","1. الصورة الأصلية","1. Imagen original"));
        if(original!=null){preview(page,original);space(page,8);page.addView(text(originalW+" × "+originalH+" px",13,false));}
        button(page,original==null?tr("Upload image","رفع صورة","Subir imagen"):tr("Change original image","تغيير الصورة الأصلية","Cambiar imagen original"),false,()->pick(PICK_ORIGINAL));
        page.addView(text(tr("JPEG · PNG · WEBP, up to 50 MB. No cropping.","JPEG · PNG · WEBP حتى 50 MB. بدون قص.","JPEG · PNG · WEBP, hasta 50 MB. Sin recorte."),12,false));
        heading(page,tr("2. Choose Style","2. اختر أسلوب الرسم","2. Elegir estilo"));
        styleSection=column();page.addView(styleSection);
        stylesScroll=new HorizontalScrollView(this);stylesScroll.setHorizontalScrollBarEnabled(false);stylesScroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.HORIZONTAL);cards.setPadding(0,dp(2),dp(4),dp(8));stylesScroll.addView(cards);styleSection.addView(stylesScroll);
        for(int i=0;i<STYLES.length;i++){
            final int index=i;LinearLayout card=column();card.setPadding(dp(6),dp(6),dp(6),dp(8));card.setGravity(Gravity.CENTER);card.setBackground(shape(i==selected?accent:surface,i==selected?accent:muted));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(126),-2);cp.setMargins(0,0,dp(10),0);cards.addView(card,cp);
            if(i<ASSETS.length){ImageView example=new ImageView(this);try(InputStream in=getAssets().open("style-previews/"+ASSETS[i]+".webp")){example.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception ignored){}example.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(example,new LinearLayout.LayoutParams(dp(110),dp(110)));}
            else {TextView icon=text(i==14?"Aa":"▧",38,true);icon.setGravity(Gravity.CENTER);icon.setTextColor(i==selected?Color.WHITE:ink);card.addView(icon,new LinearLayout.LayoutParams(dp(110),dp(110)));}
            TextView title=text((i==selected?"✓ ":"")+styleName(i),14,true);title.setGravity(Gravity.CENTER);title.setMinHeight(dp(42));title.setTextColor(i==selected?Color.WHITE:ink);card.addView(title,new LinearLayout.LayoutParams(-1,-2));
            card.setContentDescription(styleName(i));card.setOnClickListener(v->{if(!busy){selected=index;message="";render();}});actions.add(card);
        }
        stylesScroll.post(()->stylesScroll.scrollTo(x,0));
        TextView examples=text(tr("Example images illustrate each style. Your AI result may vary.","الصور أمثلة تقريبية للأساليب. قد تختلف نتيجة الذكاء الاصطناعي.","Ejemplos orientativos; tu resultado puede variar."),12,false);examples.setTextColor(muted);styleSection.addView(examples);
        if(selected==14){heading(styleSection,tr("Custom Style","أسلوب مخصص","Estilo personalizado"));EditText edit=new EditText(this);edit.setTextColor(ink);edit.setHintTextColor(muted);edit.setTextSize(16);edit.setMinLines(3);edit.setGravity(Gravity.TOP);edit.setHint("Hand-drawn watercolor illustration with soft paper texture");edit.setBackground(shape(surface,muted));edit.setPadding(dp(12),dp(12),dp(12),dp(12));edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1500)});edit.setText(custom);edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){custom=s.toString();}public void afterTextChanged(Editable e){}});styleSection.addView(edit);actions.add(edit);}
        if(selected==15){heading(styleSection,tr("Style Reference Image","صورة مرجع الأسلوب","Imagen de referencia de estilo"));styleSection.addView(text(tr("Use this image's visual style, keeping the content of your original.","نأخذ الأسلوب البصري من هذه الصورة ونحافظ قدر الإمكان على محتوى الصورة الأصلية.","Usar su estilo visual y conservar el contenido de la imagen original."),14,false));if(referencePreview!=null){space(styleSection,8);preview(styleSection,referencePreview);}space(styleSection,8);button(styleSection,reference==null?tr("Upload style reference","رفع صورة مرجع الأسلوب","Subir referencia"):tr("Change style reference","تغيير مرجع الأسلوب","Cambiar referencia"),false,()->pick(PICK_REFERENCE));}
        heading(page,tr("Style Strength","قوة تطبيق الأسلوب","Intensidad del estilo"));strengthLabel=text(strength+" / 100",18,true);page.addView(strengthLabel);
        SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(strength);seek.setMinHeight(dp(48));seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));seek.setContentDescription(tr("Style strength","قوة الأسلوب","Intensidad"));page.addView(seek,new LinearLayout.LayoutParams(-1,dp(52)));actions.add(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){strength=value;strengthLabel.setText(value+" / 100");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        TextView note=text(tr("Low: preserve more · High: stronger artistic changes. Strength is guided by prompting.","منخفضة: الحفاظ أكثر على الأصل · مرتفعة: تغيير فني أكبر. تُوجّه القوة بوصف الأسلوب.","Baja: conservar más · Alta: cambios mayores. Intensidad mediante instrucciones."),13,false);note.setTextColor(muted);page.addView(note);space(page,18);
        generateButton=button(page,tr("Generate","توليد","Generar"),true,this::generate);
        page.addView(text(tr("AI generation sends the selected images to our server and Cloudflare. Original / 0 strength stays on your device. Usage is limited by the shared account quota.","عند التوليد تُرسل الصور المختارة إلى خادمنا وCloudflare. الأصلية / قوة 0 تبقى على جهازك. التوليد مرتبط بحصة الحساب المشتركة.","La IA envía las imágenes al servidor y Cloudflare. Original / intensidad 0 permanece en el dispositivo. Uso sujeto a la cuota compartida."),12,false));
        progress=new ProgressBar(this);page.addView(progress,new LinearLayout.LayoutParams(-1,dp(44)));status=text(message,15,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
        resultSection=column();page.addView(resultSection);renderResult();setBusy(busy,null);scroll.post(()->scroll.scrollTo(0,y));
    }
    private void renderResult(){
        resultSection.removeAllViews();if(resultImage==null||original==null)return;
        heading(resultSection,tr("3. Original / Result","3. الأصلية / النتيجة","3. Original / Resultado"));
        CompareView compare=new CompareView();resultSection.addView(compare,new LinearLayout.LayoutParams(-1,-2));
        TextView hint=text(tr("Drag the divider to compare","اسحب الفاصل للمقارنة","Arrastra el divisor para comparar"),13,false);hint.setTextColor(muted);resultSection.addView(hint);space(resultSection,12);
        button(resultSection,tr("Download / Save image","تنزيل / حفظ الصورة","Descargar / Guardar"),true,()->saveResult(false));
        button(resultSection,tr("Use in Splitter","استخدام في التقسيم","Usar para dividir"),false,()->saveResult(true));
        button(resultSection,tr("Generate Again","توليد مرة أخرى","Generar de nuevo"),false,this::generate);
        button(resultSection,tr("Change Style","تغيير الأسلوب","Cambiar estilo"),false,()->scroll.smoothScrollTo(0,styleSection.getTop()));
        TextView metrics=text(tr("Generation details","تفاصيل التوليد","Detalles")+"\n"+tr("Model: ","النموذج: ","Modelo: ")+model+"\n"+tr("Generation time: ","زمن التوليد: ","Tiempo: ")+String.format(Locale.US,"%.1f s",generationMs/1000.0)+"\n"+tr("Original: ","الأصلية: ","Original: ")+generatedOriginalW+" × "+generatedOriginalH+"\n"+tr("Output: ","النتيجة: ","Salida: ")+resultImage.getWidth()+" × "+resultImage.getHeight()+"\n"+tr("Style: ","الأسلوب: ","Estilo: ")+generatedStyle+"\n"+tr("Strength: ","القوة: ","Intensidad: ")+generatedStrength+" / 100",13,false);metrics.setPadding(dp(12),dp(12),dp(12),dp(12));metrics.setBackground(shape(surface,muted));resultSection.addView(metrics);
    }
    private void setBusy(boolean value,String label){busy=value;for(View action:actions){action.setEnabled(!value);action.setAlpha(value?.55f:1f);}if(progress!=null)progress.setVisibility(value?View.VISIBLE:View.GONE);if(label!=null){message=label;status.setText(label);}if(generateButton!=null)generateButton.setText(value?tr("Transforming your image…","جارٍ تحويل الصورة…","Transformando tu imagen…"):tr("Generate","توليد","Generar"));}
    private void error(String value){message=value;setBusy(false,value);}
    private void pick(int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"});intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,request);}
    @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if(code!=RESULT_OK||data==null||data.getData()==null)return;if(request!=PICK_ORIGINAL&&request!=PICK_REFERENCE)return;Uri picked=data.getData();try{int flags=data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION;if(flags!=0)getContentResolver().takePersistableUriPermission(picked,flags);}catch(Exception ignored){}loadImage(picked,request==PICK_REFERENCE);}
    private void loadImage(Uri picked,boolean isReference){
        setBusy(true,tr("Loading image…","جارٍ تحميل الصورة…","Cargando imagen…"));worker.execute(()->{
            try{validate(picked);int[] size=SplitEngine.dimensions(getContentResolver(),picked);Bitmap bitmap=SplitEngine.loadPreview(getContentResolver(),picked,1200);
                runOnUiThread(()->{if(dead)return;if(isReference){reference=picked;referencePreview=bitmap;}else{source=picked;original=bitmap;originalW=size[0];originalH=size[1];resultImage=null;resultSource=null;saved=null;}busy=false;message="";render();});
            }catch(OutOfMemoryError e){runOnUiThread(()->{if(!dead)error(tr("Image too large to open. Choose a smaller image.","الصورة كبيرة جدًا. اختر صورة أصغر.","La imagen es demasiado grande."));});}
            catch(Exception e){runOnUiThread(()->{if(!dead)error(e instanceof ImageError?e.getMessage():tr("Cannot open this image. Choose a valid JPEG, PNG or WEBP.","تعذر فتح الصورة. اختر JPEG أو PNG أو WEBP صالحة.","Elige una imagen JPEG, PNG o WEBP válida."));});}
        });
    }
    private void restoreImages(){
        setBusy(true,tr("Loading image…","جارٍ تحميل الصورة…","Cargando imagen…"));worker.execute(()->{try{int[] size=SplitEngine.dimensions(getContentResolver(),source);Bitmap o=SplitEngine.loadPreview(getContentResolver(),source,1200);Bitmap r=reference==null?null:SplitEngine.loadPreview(getContentResolver(),reference,1200);Bitmap result=resultSource==null?null:SplitEngine.loadPreview(getContentResolver(),resultSource,1920);runOnUiThread(()->{if(dead)return;original=o;originalW=size[0];originalH=size[1];referencePreview=r;resultImage=result;busy=false;message="";render();});}catch(Exception e){runOnUiThread(()->{if(!dead){busy=false;message=tr("Re-upload the image to continue.","أعد رفع الصورة للمتابعة.","Vuelve a subir la imagen.");render();}});}});
    }
    private void validate(Uri uri)throws Exception{
        String mime=getContentResolver().getType(uri);if(!"image/jpeg".equals(mime)&&!"image/png".equals(mime)&&!"image/webp".equals(mime))throw new ImageError(tr("Supported images: JPEG, PNG, WEBP.","الصور المدعومة: JPEG وPNG وWEBP.","Formatos: JPEG, PNG, WEBP."));
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0)&&c.getLong(0)>50L*1024*1024)throw new ImageError(tr("Maximum file size is 50 MB.","الحد الأقصى للملف 50 MB.","Máximo 50 MB."));}
    }
    private static final class ImageError extends IOException{ImageError(String message){super(message);}}
    private byte[] prepare(Uri uri)throws Exception{
        Bitmap b=SplitEngine.loadPreview(getContentResolver(),uri,504);Bitmap flat=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(flat);canvas.drawColor(Color.WHITE);canvas.drawBitmap(b,0,0,null);b.recycle();ByteArrayOutputStream out=new ByteArrayOutputStream();flat.compress(Bitmap.CompressFormat.JPEG,90,out);flat.recycle();return out.toByteArray();
    }
    private void generate(){
        if(busy)return;if(original==null){error(tr("Upload an original image first.","ارفع الصورة الأصلية أولًا.","Sube una imagen original."));return;}
        if(selected==14&&custom.trim().isEmpty()&&strength>0){error(tr("Describe your custom style first.","اكتب وصف الأسلوب المخصص أولًا.","Describe el estilo personalizado."));return;}
        if(selected==15&&reference==null&&strength>0){error(tr("Upload a style reference image first.","ارفع صورة مرجع الأسلوب أولًا.","Sube una imagen de referencia."));return;}
        final int selectedNow=selected,strengthNow=strength;final String description=custom;final Uri sourceNow=source,referenceNow=reference;
        if(selectedNow==0||strengthNow==0){resultImage=original;resultSource=source;saved=null;model=tr("None — original image","بدون AI — الصورة الأصلية","Sin IA — original");generationMs=0;record(selectedNow,strengthNow);message="";render();scroll.post(()->scroll.smoothScrollTo(0,resultSection.getTop()));return;}
        final int[] dimensions;
        try{dimensions=StyleDimensions.output(originalW,originalH);}catch(IllegalArgumentException e){error(tr("This image is too panoramic for the model (maximum ratio 7.5:1). Choose another image; we won't crop it.","الصورة بانورامية جدًا لهذا النموذج (أقصى نسبة 7.5:1). اختر صورة أخرى؛ لن نقصّها.","La proporción máxima es 7.5:1. Elige otra imagen, sin recortarla."));return;}
        setBusy(true,tr("Transforming your image… Keep this screen open.","جارٍ تحويل الصورة… أبقِ هذه الشاشة مفتوحة.","Transformando… Mantén esta pantalla abierta."));api=new StyleApi();final StyleApi request=api;
        worker.execute(()->{
            try{byte[] input=prepare(sourceNow),ref=selectedNow==15?prepare(referenceNow):null;StyleApi.Result response=request.generate(input,ref,STYLES[selectedNow],description,strengthNow,dimensions[0],dimensions[1]);Bitmap image=BitmapFactory.decodeByteArray(response.image,0,response.image.length);if(image==null)throw new StyleApi.ApiError(502,"");
                File cache=new File(getCacheDir(),"style-result-"+System.nanoTime()+".png");try(OutputStream out=new FileOutputStream(cache)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();}
                runOnUiThread(()->{if(dead)return;resultImage=image;resultSource=Uri.fromFile(cache);saved=null;model=response.model;generationMs=response.milliseconds;record(selectedNow,strengthNow);busy=false;message=tr("Your image is ready.","صورتك جاهزة.","Tu imagen está lista.");render();scroll.post(()->scroll.smoothScrollTo(0,resultSection.getTop()));});
            }catch(OutOfMemoryError e){runOnUiThread(()->{if(!dead)error(tr("Not enough memory. Try a smaller image.","الذاكرة غير كافية. جرّب صورة أصغر.","Memoria insuficiente."));});}
            catch(Exception e){runOnUiThread(()->{if(!dead)error(errorMessage(e));});}
        });
    }
    private void record(int index,int value){generatedStyle=styleName(index);generatedStrength=value;generatedOriginalW=originalW;generatedOriginalH=originalH;}
    private String errorMessage(Exception e){
        if(e instanceof SocketTimeoutException)return tr("AI timed out. Wait a moment, then try again.","انتهت مهلة AI. انتظر قليلًا وأعد المحاولة.","La IA agotó el tiempo. Inténtalo de nuevo.");
        if(e instanceof StyleApi.ApiError){StyleApi.ApiError a=(StyleApi.ApiError)e;if(language.equals("ar")&&!a.userMessage.isEmpty())return a.userMessage;
            if(a.status==429||a.status==402)return tr("Usage limit reached, or a request is already running. Wait and retry; check the Cloudflare account quota if it continues.","وصلت إلى حد الاستخدام أو يوجد طلب جارٍ. انتظر ثم أعد المحاولة وتحقق من حصة Cloudflare.","Límite de uso o solicitud en curso. Espera y comprueba la cuota de Cloudflare.");
            if(a.status==503)return tr("AI is not configured on the server yet.","AI غير مهيأ على الخادم بعد.","La IA no está configurada.");
            if(a.status==504)return tr("AI timed out. Please try again later.","انتهت مهلة التوليد. حاول لاحقًا.","La IA agotó el tiempo.");
            return tr("AI couldn't return a valid image. Please try again later.","تعذر الحصول على صورة صالحة من AI. حاول لاحقًا.","La IA no devolvió una imagen válida.");}
        return tr("Cannot connect. Check your internet connection and try again.","تعذر الاتصال. تحقق من الإنترنت وأعد المحاولة.","Comprueba tu conexión e inténtalo de nuevo.");
    }
    private void saveResult(boolean use){
        if(busy||resultSource==null)return;if(saved!=null){afterSave(use);return;}
        setBusy(true,tr("Saving image…","جارٍ حفظ الصورة…","Guardando…"));final Uri result=resultSource;worker.execute(()->{Uri created=null;try{
            String mime="file".equals(result.getScheme())?"image/png":getContentResolver().getType(result);if(mime==null)mime="image/png";
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"ImageSplitter-Style-"+System.currentTimeMillis()+(mime.equals("image/jpeg")?".jpg":mime.equals("image/webp")?".webp":".png"));values.put(MediaStore.Images.Media.MIME_TYPE,mime);values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ImageSplitter");values.put(MediaStore.Images.Media.IS_PENDING,1);
            created=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(created==null)throw new IOException();try(InputStream in=getContentResolver().openInputStream(result);OutputStream out=getContentResolver().openOutputStream(created)){if(in==null||out==null)throw new IOException();byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
            values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(created,values,null,null);Uri done=created;runOnUiThread(()->{if(dead)return;saved=done;setBusy(false,"");afterSave(use);});
        }catch(Exception e){if(created!=null)getContentResolver().delete(created,null,null);runOnUiThread(()->{if(!dead)error(tr("Couldn't save the image. Check available storage and retry.","تعذر حفظ الصورة. تحقق من المساحة وأعد المحاولة.","No se pudo guardar; comprueba el espacio disponible."));});}});
    }
    private void afterSave(boolean use){if(use){setResult(RESULT_OK,new Intent().setData(saved));finish();}else Toast.makeText(this,tr("Saved to Pictures / ImageSplitter","تم الحفظ في Pictures / ImageSplitter","Guardada en Pictures / ImageSplitter"),Toast.LENGTH_LONG).show();}

    private final class CompareView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private float fraction=.5f;
        CompareView(){super(StyleTransferActivity.this);setContentDescription(tr("Original and result comparison. Drag horizontally.","مقارنة الأصلية والنتيجة. اسحب أفقيًا.","Comparación; arrastra horizontalmente."));setFocusable(true);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec);int h=Math.max(dp(160),Math.min(dp(470),Math.round(w*original.getHeight()/(float)original.getWidth())));setMeasuredDimension(w,h);}
        private RectF fit(Bitmap b){float scale=Math.min(getWidth()/(float)b.getWidth(),getHeight()/(float)b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;return new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
        @Override protected void onDraw(Canvas c){c.drawColor(surface);c.drawBitmap(resultImage,null,fit(resultImage),paint);float x=getWidth()*fraction;c.save();c.clipRect(0,0,x,getHeight());c.drawBitmap(original,null,fit(original),paint);c.restore();paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(3));c.drawLine(x,0,x,getHeight(),paint);paint.setColor(accent);c.drawCircle(x,getHeight()/2f,dp(22),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(18));paint.setTextAlign(Paint.Align.CENTER);c.drawText("↔",x,getHeight()/2f+dp(6),paint);paint.setColor(0xBB101820);c.drawRoundRect(dp(6),dp(6),dp(98),dp(34),dp(8),dp(8),paint);c.drawRoundRect(getWidth()-dp(98),dp(6),getWidth()-dp(6),dp(34),dp(8),dp(8),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(12));c.drawText(tr("Original","الأصلية","Original"),dp(52),dp(25),paint);c.drawText(tr("Result","النتيجة","Resultado"),getWidth()-dp(52),dp(25),paint);}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){getParent().requestDisallowInterceptTouchEvent(true);fraction=Math.max(0,Math.min(1,e.getX()/Math.max(1,getWidth())));invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onKeyDown(int key,android.view.KeyEvent e){if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){fraction=Math.max(0,Math.min(1,fraction+(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT?-.05f:.05f)));invalidate();return true;}return super.onKeyDown(key,e);}
    }
}
