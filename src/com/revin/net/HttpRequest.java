package com.revin.net;

import com.revin.net.IHttpHandler.IHttpContext;
import com.revin.net.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest{
    public static class HeaderError extends Exception{
        public final int code;
        public HeaderError(int code){
            this(code,Utils.getSource(1));
        }
        public HeaderError(int code,String message){
            super(message);
            this.code=code;
        }
        @Override public String toString(){return code+", "+super.toString();}
    }
    private final IHttpContext context;
    private final Socket sk;
    private final String headerRaw;
    private int entityLeft;
    private InputStream ins;
//    private OutputStream ous;
    public final String method;
    public final String uri;
    public final String httpVer;
    private final Map<String,String>headers;
    private final Map<String,String>cookies;
    public HttpRequest(IHttpContext context,Socket sk,InputStream ins,OutputStream ous,String header)throws HeaderError{
        this.context=context;
        this.sk=sk;
        this.headerRaw=header;
        this.ins=ins;
//        this.ous=ous;
        Map<String,String>headers=new HashMap<>(64,0.8f);
        String[]lines=headerRaw.split("\r\n");
        if(lines.length>50)throw new HeaderError(HttpStatusCodes.HTTP431_RequestHeaderFieldsTooLarge);
        String[] parts=lines[0].split(" ");
        if(parts.length!=3)throw new HeaderError(HttpStatusCodes.HTTP400_BadRequest);
        switch(parts[2]){
            case"HTTP/1.1": httpVer="1.1";break;
            case"HTTP/1.0": httpVer="1.0";break;
            default:throw new HeaderError(HttpStatusCodes.HTTP400_BadRequest);
        }method=parts[0];uri=parts[1];
        for(int i=1;i<lines.length;++i){
            parts=lines[i].split(": ",2);
            if(parts.length!=2)
                throw new HeaderError(HttpStatusCodes.HTTP400_BadRequest,"Invalid header: "+lines[i]);
            if(headers.put(parts[0],parts[1])!=null)
                throw new HeaderError(HttpStatusCodes.HTTP400_BadRequest,"Duplicate header: "+lines[i]);
        }String css=headers.get("Cookie");
        if(css!=null){
            Map<String,String>cookies=new HashMap<>();
            for(String cookie:css.split("; ")){
                parts=cookie.split("=",2);
                if(parts.length!=2)
                    throw new HeaderError(HttpStatusCodes.HTTP400_BadRequest,"Invalid cookie: "+cookie);
                if(cookies.put(parts[0],parts[1])!=null)
                    throw new HeaderError(HttpStatusCodes.HTTP400_BadRequest,"Duplicate cookie: "+parts[0]);
            }this.cookies=cookies;
        }else this.cookies=null;
        this.headers=headers;
        entityLeft=getContentLength();
    }
    /** use IHttpHandler(IHttpContext).abort() */ @Deprecated
    public void abort(){context.abort();}
    public Map<String,String>getHeaders(){
        return Collections.unmodifiableMap(headers);
    }
    public Map<String,String>getCookies(){
        if(cookies!=null)
            return Collections.unmodifiableMap(cookies);
        else return null;
    }
    public String getHeaderString(String header){
        return headers.get(header);
    }
    public String getHeaderString(String header,String defaultValue){
        String str=getHeaderString(header);
        if(str==null)return defaultValue;
        return str;
    }
    public int getHeaderInt(String header){
        String str=getHeaderString(header);
        return Integer.parseInt(str);
    }
    public int getHeaderInt(String header,int defaultValue){
        try{
            return getHeaderInt(header);
        }catch(NumberFormatException e){
            return defaultValue;
        }
    }
    public String getRemoteAddress(){
        InetAddress addr=sk.getInetAddress();
        if(!addr.isLoopbackAddress())return addr.getHostAddress();
        String xff=getHeaderString("X-Forwarded-For");
        if(xff!=null)return xff;
        return addr.getHostAddress();
    }
    public int getContentLength(){
        return getHeaderInt("Content-Length",-1);
    }
    public InputStream getInputStream(){
        InputStream ins=this.ins;
        this.ins=null;
        this.entityLeft=-1;
        return ins;
    }
    public byte[]readEntityBytes(int contentLength){
        if(contentLength>entityLeft)throw new IllegalArgumentException();
        try{
            int off=0;byte[]b=new byte[contentLength];
            while(off<contentLength){
                int len=ins.read(b,off,b.length-off);
                if(len<=0)return null;
                off+=len;
            }entityLeft-=contentLength;
            return b;
        }catch(IOException e){
            Utils.err(e);
            entityLeft=-1;
            return null;
        }
    }
    public String readEntityString(int contentLength){
        byte[]bytes=readEntityBytes(contentLength);
        if(bytes==null)return null;
        return new String(bytes);
    }
    public void discardEntity()throws IOException{
        if(entityLeft>0){
            if(entityLeft!=ins.skip(entityLeft))
                throw new IOException();
        }
    }
    public String getCookie(String key){
        return cookies.get(key);
    }
    public String getCookie(String key,String defaultValue){
        String cookie=getCookie(key);
        if(cookie==null)
            return defaultValue;
        return cookie;
    }
    @Override
    public String toString() {return headerRaw;}
}
