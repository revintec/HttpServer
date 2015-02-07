package com.revin.net;

import com.revin.net.util.Utils;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import static java.nio.file.StandardWatchEventKinds.*;

public class Compiler extends Thread implements FileVisitor<Path>{
    protected boolean uptodate;
    protected boolean purging;
    protected final int osFSEventStall,osFSTransferStall;
    protected final WatchService ws;
    protected final Map<WatchKey,Path>paths;
    protected final Runtime runtime;
    protected final Path root=Paths.get("html");
    protected final Path mins=Paths.get("mins");
    protected final Path ST1=root.resolve("lib/ui.js");
    protected final Pattern pattern=Pattern.compile(".*\\.(htm|css|js|html|png)",Pattern.CASE_INSENSITIVE);
    public Compiler()throws IOException{
        setName("compiler daemon");
        setDaemon(true);
        this.ws=root.getFileSystem().newWatchService();
        this.paths=new HashMap<>();
        WatchKey key=root.register(ws,ENTRY_CREATE,ENTRY_MODIFY,ENTRY_DELETE);
        paths.put(key,root);
        this.runtime=Runtime.getRuntime();
        osFSEventStall=1024;
        osFSTransferStall=300;
    }
    public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attr)throws IOException{
        if(!purging){
            WatchKey key=path.register(ws,ENTRY_CREATE,ENTRY_MODIFY,ENTRY_DELETE);
            if(!paths.containsKey(key))paths.put(key,path);
//            else Utils.err("Already registered: "+path);
        }return CONTINUE;
    }
    public FileVisitResult visitFile(Path path,BasicFileAttributes attr)throws IOException{
        if(purging){
            Path source=root.resolve(mins.relativize(path));
            if(!Files.isRegularFile(source)/*||!attr.lastModifiedTime().equals(Files.getLastModifiedTime(source))*/){
                uptodate=false;
                Utils.log("Deleting: "+path);
                Files.delete(path);
            }return CONTINUE;
        }
        String p=path.toString();
        Matcher ma=pattern.matcher(root.relativize(path).toString());
        if(!ma.find())return CONTINUE;
        String ext=ma.group(1);
        Path minPath=mins.resolve(ma.group());
        Path dir=minPath.getParent();
        if(Files.notExists(dir))Files.createDirectories(dir);
        String min=minPath.toString();
        if(Files.exists(minPath)&&attr.lastModifiedTime().equals(Files.getLastModifiedTime(minPath)))
            return CONTINUE;
        uptodate=false;
        Utils.log("Optimizing: "+p);
//        p="./"+p; // prevents p starting with "-" // p should always starts with html/... not a "-"
        Process process;
        switch(ext){
            case "htm":case "html":
                process=runtime.exec(new String[]{"java","-jar","bin/htmCompiler.jar",
                        p,"-o",min});
                break;
            case "css":
                process=runtime.exec(new String[]{"java","-jar","bin/cssCompiler.jar",
                        p,"-o",min});
                break;
            case "js":
                process=runtime.exec(new String[]{"java","-jar","bin/jsCompiler.jar",
                        "--js",p,"--js_output_file",min});
                break;
            case "png":
                process=runtime.exec(new String[]{"bin/pngquant","-f","-o",min,p});
                break;
            default:throw new IllegalArgumentException("unknown type: "+ext);
        }
        try{
            int exit=process.waitFor();
            if(exit!=0){
                Utils.err("error executing optimizer, using src copy...");
                Files.copy(path,minPath,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
                return CONTINUE;
            }
        }catch(InterruptedException e){
            Utils.err(e);
            process.destroy();
            Files.delete(minPath);
            return TERMINATE;
        }
//        switch(ext){
//            case "js":
//                // minPath should exist, contract from compiler.jar
//                // Special Treatment
//                if(path.equals(ST1))ST1(minPath);
//                break;
//        }
        Files.setLastModifiedTime(minPath,attr.lastModifiedTime());
        return CONTINUE;
    }
    public FileVisitResult visitFileFailed(Path path,IOException e)throws IOException{throw e;}
    public FileVisitResult postVisitDirectory(Path path,IOException e)throws IOException{
        if(e==null&&purging){
            try{Files.delete(path);}
            catch(DirectoryNotEmptyException ignored){}
        }return CONTINUE;
    }
    /**
     * SpecialTreatment for lib/ui.js
     */
    protected void ST1(Path path)throws IOException{
        Utils.log("ST: "+ST1);
        String fileContent=new String(Files.readAllBytes(path));
        Pattern pa=Pattern.compile("function (\\w+)\\(\\w,\\w,\\w,\\w\\)\\{return eval\\(\\w\\)\\}");
        Matcher ma=pa.matcher(fileContent);
        if(!ma.find()) throw new UnsupportedOperationException("can't do ST1");
        fileContent=ma.replaceFirst("function $1(a,win,e,p){return eval(a)}");
        // should match only once!
        if(ma.find())throw new UnsupportedOperationException("can't do ST1");
        Files.write(path,fileContent.getBytes());
    }
    @Override
    public void run(){
        Set<FileVisitOption> fvs=new HashSet<>();
        fvs.add(FileVisitOption.FOLLOW_LINKS);
        WatchKey key;
        while(true){
            try{
                Thread.sleep(osFSEventStall);
                // purge events
                while((key=ws.poll())!=null){
                    key.pollEvents();
                    if(!key.reset())paths.remove(key);
                }uptodate=true;
                purging=true;
                Files.walkFileTree(mins,fvs,Integer.MAX_VALUE,this);
                purging=false;
                Files.walkFileTree(root,fvs,Integer.MAX_VALUE,this);
                if(uptodate)Utils.log("Indexing "+root+": All files are up-to-date");
                synchronized(this){this.notifyAll();}
                if(uptodate){
                    key=ws.take();// wait
                    while(true){
                        do{
                            for(WatchEvent<?> e:key.pollEvents()){
                                WatchEvent.Kind k=e.kind();
                                Path path=paths.get(key).resolve((Path)e.context());
                                if(k.equals(OVERFLOW))          Utils.err("OVERFLOW: "    +path);
//                                else if(k.equals(ENTRY_CREATE)) Utils.log("ENTRY_CREATE: "+path);
//                                else if(k.equals(ENTRY_MODIFY)) Utils.log("ENTRY_MODIFY: "+path);
//                                else if(k.equals(ENTRY_DELETE)) Utils.log("ENTRY_DELETE: "+path);
                            }if(!key.reset())paths.remove(key);
                        }while((key=ws.poll())!=null);
                        // small pause for further updates
                        Thread.sleep(osFSTransferStall);
                        if((key=ws.poll())==null)break;
                    }
                }
            }catch(InterruptedException|IOException e){
                Utils.err(e);
                if(e instanceof InterruptedException)
                    break;
            }
        }
    }
}
