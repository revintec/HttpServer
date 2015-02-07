package com.revin.net;

import com.revin.net.IHttpHandler.IHttpContext;
import com.revin.net.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

@SuppressWarnings("unused")
public class HttpResponse{
    private final IHttpContext context;
//    private final Socket sk;
//    private InputStream ins;
    private OutputStream ous;
    private int statusCode=200;
    private String statusExplained;
    private Map<String,String>headers=new HashMap<>(64,0.8f);
    private Map<String,Cookie>cookies;
    private boolean isResponseClosed;
    private boolean isHeaderSent;
    private DateFormat dfGMT;
    public HttpResponse(IHttpContext context,Socket sk,InputStream ins,OutputStream ous,HttpRequest req){
        this.context=context;
//        this.sk=sk;
//        this.ins=ins;
        this.ous=ous;
        setHeader("Transfer-Encoding","chunked");
        setHeader("Server","NACL "+HttpServer.VERSION);
        dfGMT=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
        dfGMT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    /** use IHttpHandler(IHttpContext).abort() */ @Deprecated
    public void abort(){context.abort();}
    public Map<String,String>getHeaders(){
        return Collections.unmodifiableMap(headers);
    }
    public Map<String,Cookie>getCookies(){
        if(cookies!=null)
            return Collections.unmodifiableMap(cookies);
        else return null;
    }
    public int getStatusCode(){
        return statusCode;
    }
    public void setStatusCode(int sc){
        setStatusCode(sc,null);
    }
    public void setStatusCode(int sc,String status){
        if(isHeaderSent)throw new RuntimeException("Header already sent");
        this.statusCode=sc;
        this.statusExplained=status;
    }
    public void setStatusCodeWithSource(int sc){
        setStatusCode(sc,Utils.getSource(1));
    }
    public Cookie getCookie(String key){
        if(cookies!=null)
            return cookies.get(key);
        else return null;
    }
    public Cookie setCookie(Cookie cookie){
        if(isHeaderSent)throw new RuntimeException("Header already sent");
        if(cookies==null)cookies=new HashMap<>();
        return cookies.put(cookie.key,cookie);
    }
    public Cookie removeCookie(String key){
        if(isHeaderSent)throw new RuntimeException("Header already sent");
        if(cookies!=null)
            return cookies.remove(key);
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
    public String setHeader(String header,String value){
        if(isHeaderSent)throw new RuntimeException("Header already sent");
        return headers.put(header,value);
    }
    public String addHeader(String header,String value){
        throw new UnsupportedOperationException();
    }
    public String removeHeader(String header){
        if(isHeaderSent)throw new RuntimeException("Header already sent");
        return headers.remove(header);
    }
    /**
     * @param maxAge in seconds, use -1 to disable cache
     */
    public void cacheControl(int maxAge){
        if(maxAge<0){
            setHeader("Cache-Control","no-cache");
            setHeader("Expires","0");
        }else{
            setHeader("Cache-Control","max-age="+maxAge);
            setHeader("Expires",dfGMT.format(System.currentTimeMillis()+maxAge*1000));
        }
    }
    public boolean isHeaderSent(){
        return isHeaderSent;
    }
    public void sendHeader()throws IOException{
        if(isHeaderSent)throw new RuntimeException("Header already sent");
        isHeaderSent=true;
        ous.write(("HTTP/1.1 "+statusCode).getBytes());
        if(statusExplained!=null)
            ous.write((" "+statusExplained+"\r\n").getBytes());
        else ous.write("\r\n".getBytes());
        for(Entry<String,String> e:headers.entrySet())
            ous.write((e.getKey()+": "+e.getValue()+"\r\n").getBytes());
        if(cookies!=null)
            for(Entry<String,Cookie> e:cookies.entrySet())
                ous.write(("Set-Cookie: "+e.getValue()+"\r\n").getBytes());
        ous.write("\r\n".getBytes());
        ous.flush();
    }
    public boolean isResponseClosed(){
        return isResponseClosed;
    }
    public void closeResponse()throws IOException{
        if(isResponseClosed)throw new RuntimeException("Response closed");
        if(!isHeaderSent)sendHeader();
        isResponseClosed=true;
        if("chunked".equals(getHeaderString("Transfer-Encoding")))
            ous.write("0\r\n\r\n".getBytes());
        // FIXME when getOutputStream(deprive=true), ous will be null
        ous.flush();
    }
    public void writeBytes(byte[]bytes,int offset,int len)throws IOException{
        if(isResponseClosed)throw new RuntimeException("Response closed");
        if(!isHeaderSent)sendHeader();
        ous.write((Integer.toHexString(len)+"\r\n").getBytes());
        ous.write(bytes,offset,len);
        ous.write("\r\n".getBytes());
        ous.flush();
    }
    public void writeBytes(byte[]bytes)throws IOException{writeBytes(bytes,0,bytes.length);}
    public void writeString(Object string)throws IOException{
        if(string instanceof String)
            writeBytes(((String)string).getBytes("utf8"));
        else writeBytes((""+string).getBytes("utf8"));
    }
    public void writeLine(Object line)throws IOException{
        writeString(line+"\r\n");
    }
    // TODO see http://wiki.nginx.org/XSendfile
    public void sendFile(String uri){
        throw new UnsupportedOperationException("NYI");
    }
    // FIXME when this.ous is deprived, res.closeResponse() will throw NullPointerException
    public OutputStream getOutputStream(boolean cleanup,boolean deprive)throws IOException{
        if(isResponseClosed)throw new RuntimeException("Response closed");
        if(cleanup){
            removeHeader("Transfer-Encoding");
            // if removeHeader(...) successes, header must not sent
            sendHeader();
        }
        if(!deprive)return ous;
        try{return ous;}finally{ous=null;}
    }
}
