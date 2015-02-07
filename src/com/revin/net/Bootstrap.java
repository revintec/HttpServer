package com.revin.net;

import com.revin.net.IHttpHandler.IComponentLoader;
import com.revin.net.mod.FS;
import com.revin.net.util.Utils;

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class Bootstrap implements IComponentLoader,IHttpHandler{
    public final HttpServer server;
    public final String fileServerRoot;
    protected IHttpHandler fs;
    protected final Map<String,IHttpHandler>mods=new HashMap<>();
    protected final Compiler compiler;
    public static final boolean enableCompiler=true;
    public Bootstrap(String fileServerRoot)throws Exception{
        this.fileServerRoot=fileServerRoot;
        if(enableCompiler){
//            if(!System.getProperty("os.name").startsWith("Windows "))
//                throw new RuntimeException("Compiler requires Windows OS");
            compiler=new Compiler();
            synchronized(this.compiler){
                compiler.start();
                compiler.wait();
            }
        }else compiler=null;
        attachModules();
        this.server=new HttpServer(this,10080);
    }
    public void attachModules(){
        attachSqliteJDBC();
        // add databases here...
        mods.put("/echo",this);
        if(fileServerRoot!=null){
            fs=new FS(fileServerRoot);
            mods.put("/fs",this);
        }else fs=null;
    }
    @Override
    public void handleRequest(IHttpContext context,HttpRequest req,HttpResponse res,String mod,String fn,String ex)throws Exception{
        switch(mod){
            case "/echo":
                res.writeLine("mod: "+mod);
                res.writeLine("fn:  "+fn);
                res.writeLine("ex:  "+ex);
                res.writeLine("Original request header:");
                res.writeString(req);
                break;
            case "/fs":
                String addr=req.getRemoteAddress();
                if(!"0:0:0:0:0:0:0:1".equals(addr)&&!"127.0.0.1".equals(addr)){
                    if(addr.length()!=13||!req.getRemoteAddress().startsWith("192.168.0.25")){
                        Utils.log("deny fs access from: "+req.getRemoteAddress()); // +req.getRemotePort
                        res.setStatusCodeWithSource(HttpStatusCodes.HTTP403_Forbidden);
                        res.setHeader("Connection","close");
                        res.writeString(403);
                        return;
                    }
                }
                if(fn==null){
                    res.setStatusCodeWithSource(HttpStatusCodes.HTTP302_Found);
                    res.setHeader("Location","/fs!ll?.");
                    return;
                }
                fs.handleRequest(context,req,res,mod,fn,ex);
                break;
            default:
                // should never reaches here
                // our module is being registered by mistake
                res.setStatusCodeWithSource(HttpStatusCodes.HTTP500_InternalServerError);
        }
    }
    @Override
    public IHttpHandler loadComponent(String mod){return mods.get(mod);}
    public static void attachSqliteJDBC(){
        String databaseRoot="databases/";
        try{
            Class.forName("org.sqlite.JDBC");
            Utils.log("database root: "+databaseRoot);
        }catch(Exception e){Utils.err("error loading database driver");}
    }
    public static void main(String[] args)throws Exception{
        Utils.setOutputStream(new FileOutputStream("logs/operations.log",true));
        String fileServerRoot;
        if(args.length==1){
            fileServerRoot=args[0];
            Utils.log("begin file server at: "+fileServerRoot);
            Utils.log("allow connection in 192.168.0.250~255");
        }else fileServerRoot=null;
        Bootstrap loader=new Bootstrap(fileServerRoot);
        loader.server.start();
    }
    @Override
    public void close()throws Exception{
        if(compiler!=null)compiler.interrupt();
    }
}
