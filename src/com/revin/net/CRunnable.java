package com.revin.net;

import com.revin.net.IHttpHandler.IComponentLoader;
import com.revin.net.util.Utils;

import java.io.*;
import java.net.Socket;

public class CRunnable implements IHttpHandler.IHttpContext,Runnable{
    private final IComponentLoader loader;
    private final Socket sk;
    private final InputStream ins;
    private final OutputStream ous;
    private final int maxHeaderLength;
    private boolean isAborted;
    public CRunnable(IComponentLoader loader,Socket sk)throws IOException{
        this.loader=loader;
        this.sk=sk;
        // socket's i/o stream doesn't support mark/reset
        this.ins=new BufferedInputStream(sk.getInputStream());
        this.ous=new BufferedOutputStream(sk.getOutputStream());
        maxHeaderLength=3072;
    }
    @Override
    public void abort(){
        if(!isAborted){
            isAborted=true;
            try{sk.close();}
            catch(IOException e){Utils.err(e);}
        }else Utils.err("Trying to abort more than once");
    }
    @Override
    public void run(){
        try{
            byte[]b=new byte[maxHeaderLength];
            while(true){
                int len=0,l,crlf=0;
                ins.mark(maxHeaderLength);
                readLoop:
                while((l=ins.read(b,len,b.length-len))>0){
                    for(int i=len;i<len+l;++i){
                        if(b[i]=='\r'||b[i]=='\n'){
                            if(++crlf==4){
                                ++i;ins.reset();
                                if(ins.skip(i)!=i)
                                    throw new IOException("ins.skip(i)!=i");
                                len=i;break readLoop;
                            }
                        }else crlf=0;
                    }len+=l;
                }if(l==-1){
                    if(len!=0)
                        throw new EOFException(new String(b,0,len));
                    else return;
                }HttpRequest req=null;HttpResponse res;
                try{
                    if(crlf!=4){
                        res=new HttpResponse(this,sk,ins,ous,null);
                        res.setStatusCodeWithSource(HttpStatusCodes.HTTP431_RequestHeaderFieldsTooLarge);
                    }else{
                        req=new HttpRequest(this,sk,ins,ous,new String(b,0,len-4));//throw
                        res=new HttpResponse(this,sk,ins,ous,req);
                    }
                }catch(HttpRequest.HeaderError e){
                    Utils.err(e);
                    res=new HttpResponse(this,sk,ins,ous,null);
                    res.setStatusCode(e.code,e.getMessage());
                }
                if(req!=null){
                    handleRequest(req,res);
                    if(isAborted)break;
                    req.discardEntity();
                    if(res.isResponseClosed())
                        Utils.log("httpResponse should not be closed at this point");
                    else res.closeResponse();
                    if("close".equals(req.getHeaderString("Connection"))){
                        sk.shutdownInput();
                        sk.shutdownOutput();
                        sk.close();
                        break;
                    }
                }else{abort();break;}
            }
        }catch(Exception e){
            Utils.err(e);
            abort();
        }
    }
    protected void handleRequest(HttpRequest req,HttpResponse res)throws Exception{
        try{
            int i=req.uri.indexOf('!');
            if(i>0){
                int j=req.uri.indexOf('?',i);
                String mod,fn,ex;
                mod=req.uri.substring(0,i);
                if(j>0){
                    fn=req.uri.substring(i+1,j);
                    ex=req.uri.substring(j+1);
                }else{
                    if(req.uri.length()==i+1)fn=null;
                    else fn=req.uri.substring(i+1);
                    ex=null;
                }IHttpHandler handler=loader.loadComponent(mod);
                if(handler==null)
                    res.setStatusCodeWithSource(HttpStatusCodes.HTTP501_NotImplemented);
                else handler.handleRequest(this,req,res,mod,fn,ex);
            }else res.setStatusCodeWithSource(HttpStatusCodes.HTTP400_BadRequest);
        }catch(Exception e){
            if(!res.isHeaderSent()){
                StackTraceElement st;int code;
                if(e instanceof SecurityException){
                    st=e.getStackTrace()[1];
                    code=HttpStatusCodes.HTTP403_Forbidden;
                }else{
                    st=e.getStackTrace()[0];
                    code=HttpStatusCodes.HTTP500_InternalServerError;
                }res.setStatusCode(code,Utils.getSource(st));
            }throw e;
        }
    }
}
