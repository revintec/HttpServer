package com.revin.net;

import com.revin.net.IHttpHandler.IComponentLoader;
import com.revin.net.util.Utils;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;

public class HttpServer extends Thread{
    public static final double VERSION=1.1;
    public static class Handler implements RejectedExecutionHandler{
        @Override
        public void rejectedExecution(Runnable r,ThreadPoolExecutor executor){
            CRunnable cr=(CRunnable)r;
            Utils.err("RequestOverdrive! abort");
            cr.abort();
        }
    }
    public static class ShutdownHook extends Thread{
        protected final HttpServer server;
        public ShutdownHook(HttpServer server){
            this.server=server;
        }
        @Override
        public void run(){
            if(!server.shutdown(3072))
                Utils.err("httpServer shutdown still in progress");
        }
    }
    protected volatile boolean shuttingDown;
    protected ThreadPoolExecutor tpe;
    protected final IComponentLoader loader;
    protected final ServerSocket ssk;
    protected final int port;
    protected final Thread shutdownHook;
    public HttpServer(IComponentLoader loader,int port)throws IOException{
        this.loader=loader;
        this.ssk=new ServerSocket(port);
        this.port=port;
        setName("HttpServer localhost:"+port);
        shutdownHook=new ShutdownHook(this);
    }
    public void run(){
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        RejectedExecutionHandler handler=new Handler();
        BlockingQueue<Runnable> workQueue=new LinkedBlockingQueue<>(1024);
        tpe=new ThreadPoolExecutor(8,64,
                65536,TimeUnit.MILLISECONDS,
                workQueue,handler);
        Utils.log("httpServer running at port "+port);
        while(true){
            try{
                Socket sk=ssk.accept();
                Runnable cr=new CRunnable(loader,sk);
                tpe.execute(cr);
            }catch(IOException e){
                if(!shuttingDown||!(e instanceof SocketException))
                    Utils.err(e);
                try{
                    tpe.shutdown();
                    ssk.close();
                    tpe.awaitTermination(Long.MAX_VALUE,TimeUnit.DAYS);
                    Utils.log("httpServer shutdown completed");
                }catch(InterruptedException|IOException e1){
                    Utils.err(e1);
                }break;
            }
        }
    }
    public boolean shutdown(long wait){
        if(shuttingDown)throw new IllegalStateException("server already shutting down");
        shuttingDown=true;
        try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}
        catch(IllegalStateException ignored){}
        Utils.log("httpServer shutting down...");
        try{
            ssk.close();// actually triggers the shutdown
            if(wait==0)return true;
            this.join(wait==-1?0:wait);
            return !this.isAlive();
        }catch(InterruptedException|IOException e){
            Utils.err(e);
            return false;
        }
    }
}
