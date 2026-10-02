package com.imagesplitter.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StyleTransferTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private StyleTransferActivity activity;
    private View search(View root,String value,boolean exact){
        if(root instanceof TextView&&(exact?((TextView)root).getText().toString().equals(value):((TextView)root).getText().toString().contains(value)))return root;
        if(root instanceof EditText && ((EditText)root).getHint()!=null && ((EditText)root).getHint().toString().equals(value))return root;
        if(root.getContentDescription()!=null&&root.getContentDescription().toString().equals(value))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View v=search(group.getChildAt(i),value,exact);if(v!=null)return v;}}
        return null;
    }
    private View find(String value){AtomicReference<View> found=new AtomicReference<>();instrumentation.runOnMainSync(()->{View root=activity.getWindow().getDecorView();View exact=search(root,value,true);found.set(exact!=null?exact:search(root,value,false));});return found.get();}
    private void click(String value){View view=find(value);assertNotNull("Missing control: "+value,view);instrumentation.runOnMainSync(()->{assertTrue("Control disabled: "+value,view.isEnabled());view.performClick();});instrumentation.waitForIdleSync();}
    private void waitFor(String value,long timeout)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<until){if(find(value)!=null)return;Thread.sleep(200);}assertNotNull("Timed out waiting for "+value,find(value));}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor p=instrumentation.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(p)){byte[] b=new byte[4096];while(in.read(b)!=-1){}}}
    private void screenshot(String name)throws Exception{Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);File dir=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"style-qa");assertTrue(dir.isDirectory()||dir.mkdirs());try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void realGenerationCompareSaveAndResponsiveLayout()throws Exception{
        Context context=instrumentation.getTargetContext();context.getSharedPreferences("settings",0).edit().putString("language","en").commit();
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"Style-QA-Original.webp");values.put(MediaStore.Images.Media.MIME_TYPE,"image/webp");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ImageSplitterQA");
        Uri source=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(source);
        try(InputStream in=context.getAssets().open("style-previews/original.webp");OutputStream out=context.getContentResolver().openOutputStream(source)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        try{
            shell("wm size 390x844");shell("wm density 160");
            activity=(StyleTransferActivity)instrumentation.startActivitySync(new Intent(context,StyleTransferActivity.class).setData(source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitFor("Change original image",20000);click("Watercolor");
            View seek=find("Style strength");assertTrue(seek instanceof SeekBar);instrumentation.runOnMainSync(()->((SeekBar)seek).setProgress(65));
            screenshot("390-before");click("Generate");
            View generating=find("Transforming your image");assertNotNull(generating);assertFalse(generating.isEnabled());
            waitFor("Your image is ready.",140000);assertNotNull(find("black-forest-labs/flux-2-klein-4b"));assertNotNull(find("Output: 1024 × 1024"));
            View compare=find("Original and result comparison. Drag horizontally.");assertNotNull(compare);
            instrumentation.runOnMainSync(()->{long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,20,80,0);compare.dispatchTouchEvent(down);down.recycle();MotionEvent move=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_MOVE,300,80,0);compare.dispatchTouchEvent(move);move.recycle();MotionEvent up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,300,80,0);compare.dispatchTouchEvent(up);up.recycle();});
            screenshot("390-result");click("Download / Save image");
            long until=SystemClock.elapsedRealtime()+20000;boolean saved=false;while(SystemClock.elapsedRealtime()<until){try(android.database.Cursor c=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media.DISPLAY_NAME},MediaStore.Images.Media.DISPLAY_NAME+" LIKE ?",new String[]{"ImageSplitter-Style-%"},null)){if(c!=null&&c.moveToFirst()){saved=true;break;}}Thread.sleep(200);}assertTrue("Download must save a real image",saved);
            shell("wm size 430x932");instrumentation.waitForIdleSync();Thread.sleep(400);screenshot("430-result");click("Change Style");click("Custom Style");assertNotNull(find("Hand-drawn watercolor illustration with soft paper texture"));
            click("Reference Image");assertNotNull(find("Upload style reference"));click("Original");
            View strength=find("Style strength");instrumentation.runOnMainSync(()->((SeekBar)strength).setProgress(0));click("Generate");assertNotNull(find("None — original image"));click("Use in Splitter");instrumentation.waitForIdleSync();assertTrue(activity.isFinishing());
        }finally{if(activity!=null)instrumentation.runOnMainSync(()->activity.finish());context.getContentResolver().delete(source,null,null);shell("wm size reset");shell("wm density reset");}
    }
}
