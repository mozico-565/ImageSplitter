package com.imagesplitter.app;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Public app backend. Cloudflare credentials exist only on the backend. */
final class StyleApi {
    static final String ENDPOINT = "https://imagesplitter-style-lab.zicozzr.chatgpt.site/api/generate";
    static final class Result {
        final byte[] image; final String model; final long milliseconds;
        Result(byte[] image, String model, long milliseconds) {
            this.image=image; this.model=model; this.milliseconds=milliseconds;
        }
    }
    volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    void cancel() { cancelled=true; HttpURLConnection c=connection; if(c!=null)c.disconnect(); }
    Result generate(byte[] original, byte[] reference, String style, String custom,
                    int strength, int width, int height) throws Exception {
        if(cancelled)throw new InterruptedIOException();
        String boundary="ImageSplitter"+System.nanoTime();
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection=c;
        try {
            c.setConnectTimeout(25000); c.setReadTimeout(125000); c.setRequestMethod("POST");
            c.setDoOutput(true); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
            c.setRequestProperty("Accept","image/*, application/json");
            ByteArrayOutputStream body=new ByteArrayOutputStream();
            file(body,boundary,"original",original);
            if(reference!=null)file(body,boundary,"reference",reference);
            field(body,boundary,"style",style);field(body,boundary,"custom",custom);
            field(body,boundary,"strength",String.valueOf(strength));
            field(body,boundary,"width",String.valueOf(width));field(body,boundary,"height",String.valueOf(height));
            write(body,"--"+boundary+"--\r\n");
            byte[] payload=body.toByteArray();c.setFixedLengthStreamingMode(payload.length);
            if(cancelled)throw new InterruptedIOException();
            long started=System.currentTimeMillis();
            try(OutputStream out=c.getOutputStream()){out.write(payload);}
            int status=c.getResponseCode();
            if(status!=200) {
                String message="";
                try(InputStream in=c.getErrorStream()){
                    if(in!=null)message=new JSONObject(new String(read(in,16384),StandardCharsets.UTF_8)).optString("error");
                }catch(Exception ignored){}
                throw new ApiError(status,message);
            }
            if(c.getContentType()==null||!c.getContentType().startsWith("image/"))
                throw new ApiError(502,"");
            byte[] bytes;
            try(InputStream in=c.getInputStream()){bytes=read(in,16*1024*1024);}
            if(bytes.length<12)throw new ApiError(502,"");
            long elapsed=System.currentTimeMillis()-started;
            try{elapsed=Long.parseLong(c.getHeaderField("X-Generation-Time"));}catch(Exception ignored){}
            String model=c.getHeaderField("X-AI-Model");
            return new Result(bytes,model==null?"FLUX.2 Klein 4B":model,elapsed);
        } finally {c.disconnect();connection=null;}
    }
    static final class ApiError extends IOException {
        final int status; final String userMessage;
        ApiError(int status,String message){super("AI request failed ("+status+")");this.status=status;userMessage=message;}
    }
    private static void write(OutputStream out,String value)throws IOException{out.write(value.getBytes(StandardCharsets.UTF_8));}
    private static void field(OutputStream out,String boundary,String name,String value)throws IOException{
        write(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n");
    }
    private static void file(OutputStream out,String boundary,String name,byte[] bytes)throws IOException{
        write(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"; filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n");
        out.write(bytes);write(out,"\r\n");
    }
    private static byte[] read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Response too large");out.write(buffer,0,n);}
        return out.toByteArray();
    }
}
