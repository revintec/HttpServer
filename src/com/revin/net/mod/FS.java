package com.revin.net.mod;

import com.revin.net.*;
import com.revin.net.util.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Created by revintec on 2014/9/5.
 */
public class FS implements IHttpHandler{
    public final Path root;
    public FS(Path root){this.root=root;}
    public FS(String root){this(Paths.get(root));}
    private final Pattern rangePattern=Pattern.compile("bytes=(\\d+)-(\\d+)?");
    protected static final Map<String,String> mimes=new HashMap<>();
    static{
        try{
            InputStream mmx=FS.class.getResourceAsStream("mime.types");
            if(mmx==null)throw new FileNotFoundException("mime.types");
            byte[]buffer=new byte[1048576];
            int len=mmx.read(buffer);
            if(len<=0||len==buffer.length)throw new IOException("mime.types read failure");
            String data=new String(buffer,0,len);
            Pattern pt=Pattern.compile("\\s*(.+?)\\s+(.*?);");
            Matcher mt=pt.matcher(data);
            while(mt.find()){
                String mime=mt.group(1);
                String[]exts=mt.group(2).split("\\s+");
                for(String ext:exts)
                    if(mimes.put(ext,mime)!=null)
                        throw new IllegalArgumentException("multiple mapping for ext: "+ext);
            }
        }catch(Exception e){
            if(e instanceof RuntimeException)
                throw (RuntimeException)e;
            throw new RuntimeException(e);
        }
    }
    public String determineMime(String url){
        int i=url.lastIndexOf('.');
        if(i<=0||i>=url.length())return null;
        url=url.substring(i+1);
        return mimes.get(url.toLowerCase());
    }
    private static String fixedpoint(double x){
        int i=(int)x;
        int f=(int)(x*100)%100;
        if(f<10)return i+".0"+f;
        return i+"."+f;
    }
    public static String formatSizeX(long size){
        if(size<0)throw new IllegalArgumentException("size can't be negative");
        else if(size<1e3)return (int)(size/1e0)+" B";
        else if(size<1e6)return fixedpoint(size/1e3)+" K";
        else if(size<1e9)return fixedpoint(size/1e6)+" M";
        else return fixedpoint(size/1e9)+" G";
    }
    @Override
    public void handleRequest(IHttpContext context,HttpRequest req,HttpResponse res,String mod,String fn,String ex)throws Exception{
//        if(!this.mod.equals(mod))throw new IllegalArgumentException();
        if(fn==null||ex==null){
            res.setStatusCodeWithSource(HttpStatusCodes.HTTP400_BadRequest);
            res.writeString("no fn or no ex");
            return;
        }ex=URLDecoder.decode(ex,"utf-8");
        Path path=root.resolve(ex).normalize();
        if(!path.startsWith(root)){
            res.setStatusCodeWithSource(HttpStatusCodes.HTTP403_Forbidden);
            res.writeString("path escaping");
            return;
        }
        switch(fn){
            case"ls":case"ll":
                if(!Files.isDirectory(path)){
                    res.setStatusCodeWithSource(HttpStatusCodes.HTTP400_BadRequest);
                    res.writeString("should operate on a directory");
                    return;
                }Utils.log("listing: "+path);
                try(DirectoryStream<Path> ds=Files.newDirectoryStream(path)){
                    String response;
                    if("ls".equals(fn)){
                        res.setHeader("Content-Type","text/plain; charset=utf-8");
                        response=ex+"\r\n";
                        for(Path p:ds){
                            String lastModified=Files.getLastModifiedTime(p).toString();
                            long size=Files.size(p);
                            String fileName=p.getFileName().toString();
                            fileName=fileName.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                            response+=String.format("%-30s %10d %s\r\n",lastModified,size,fileName);
                        }
                    }else{
                        res.setHeader("Content-Type","text/html; charset=utf-8");
                        response="<div>"+ex+"</div>";
                        response+="<style>"+
                                "td:nth-child(2),td:nth-child(3){text-align:right;}"+
                                "td:last-child{text-align:center;}"+
                                "tr:nth-child(odd){background:#ccc;}"+
                                "a{margin:0 3px;}"+
                                "</style>";
                        response+="<table>";
                        for(Path p:ds){
                            response+="<tr>";
                            String href=URLEncoder.encode(root.relativize(p).toString(),"utf-8");
                            String lastModified=Files.getLastModifiedTime(p).toString();
                            String fileSize=formatSizeX(Files.size(p));
                            String fileName=p.getFileName().toString();
                            fileName=fileName.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                            response+="<td>"+lastModified+"</td>";
                            if(Files.isDirectory(p)){
                                response+="<td></td>";
                                response+="<td>"+fileName+"/</td>";
                                response+="<td>";
                                response+="<a href=\""+mod+"!ll?"+href+"\">LIST</a>";
                                response+="</td>";
                            }else{
                                response+="<td>"+fileSize+"</td>";
                                response+="<td>"+fileName+"</td>";
                                response+="<td>";
                                response+="<a href=\""+mod+"!down?"+href+"\">DOWN</a>";
                                response+="<a href=\""+mod+"!view?"+href+"\">VIEW</a>";
                                response+="</td>";
                            }response+="</tr>";
                        }response+="</table>";
                    }res.writeString(response);
                }break;
            case "down":case "view":
                if(!Files.isReadable(path)){
                    res.setStatusCodeWithSource(HttpStatusCodes.HTTP403_Forbidden);
                    res.writeString("can read file");
                    return;
                }Utils.log("sending: "+ex);
                try(FileChannel fileChannel=FileChannel.open(path,StandardOpenOption.READ)){
                    String fileName=path.getFileName().toString();
                    String mime=determineMime(fileName);
                    if(mime!=null)res.setHeader("Content-Type",mime);
                    if(fn.equals("down"))res.setHeader("Content-Disposition","attachment; filename=\""+fileName+"\"");
                    String range=req.getHeaderString("Range");
                    long count=fileChannel.size();
                    if(range!=null){
                        Matcher ma=rangePattern.matcher(range);
                        if(!ma.matches()){
                            res.setStatusCodeWithSource(HttpStatusCodes.HTTP400_BadRequest);
                            return;
                        }
                        long position=Long.parseLong(ma.group(1));
                        if(position>=count){
                            res.setStatusCodeWithSource(HttpStatusCodes.HTTP416_RequestedRangeNotSatisfiable);
                            return;
                        }
                        fileChannel.position(position);
                        long last,total=count;
                        if(ma.group(2)!=null){
                            last=Long.parseLong(ma.group(2));
                            if(last>=total){
                                res.setStatusCodeWithSource(HttpStatusCodes.HTTP416_RequestedRangeNotSatisfiable);
                                return;
                            }count=last-position+1;
                        }else{last=total-1;count=total-position;}
                        res.setStatusCodeWithSource(HttpStatusCodes.HTTP206_PartialContent);
                        res.setHeader("Content-Range","bytes "+position+"-"+last+"/"+total);
                    }//else res.setHeader("Accept-Ranges","bytes");
                    res.setHeader("Content-Length",""+count);
                    // TODO when the whole server is updated to use AIO, we can use AIO to speed up the transfer
                    OutputStream ous=res.getOutputStream(true,false);
                    byte[]buffer=new byte[8000];
                    ByteBuffer bb=ByteBuffer.wrap(buffer);
                    while(count>0){
                        if(count<bb.limit())
                            bb.limit((int)count);
                        int len=fileChannel.read(bb);
                        if(len<=0)throw new IOException("fileChannel.read()="+len);
                        try{ous.write(buffer,0,len);}
                        catch(IOException e){
                            Utils.err(e.getMessage());
                            context.abort();
                            return;
                        }
                        bb.position(0);count-=len;
                    }
                    // ous.flush(); // done by res.closeResponse() in CRunnable
                }break;
            default:res.setStatusCodeWithSource(400);
        }
    }
}
