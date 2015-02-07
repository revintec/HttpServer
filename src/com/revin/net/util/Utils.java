package com.revin.net.util;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Utils{
    private static class TLS{
        public final long tid=Thread.currentThread().getId();
        public final DateFormat df=new SimpleDateFormat("MMM.dd HH:mm:ss.SSS",Locale.US);
    }
    private static ThreadLocal<TLS>tls=new ThreadLocal<>();
    private static OutputStream ous;
    public static boolean printStackTrace=true;
    /** mute output to System.err/out, but still tries to write to log file */
    public static boolean muteOutput;
    public static synchronized OutputStream setOutputStream(OutputStream output){
        OutputStream ous=Utils.ous;
        Utils.ous=output;
        return ous;
    }
    public static synchronized OutputStream getOutputStream(){return ous;}
    public static void errf(String format,Object... args){err(1,String.format(format,args));}
    public static void logf(String format,Object... args){log(1,String.format(format,args));}
    public static void err(Object message){err(1,message);}
    public static void log(Object message){log(1,message);}
    public static void err(int dp,Object message){
        if(printStackTrace&&message instanceof Throwable)((Throwable)message).printStackTrace(System.err);
        output(dp+1,true,message);
    }
    public static void log(int dp,Object message){
        // if(printStackTrace&&message instanceof Throwable)((Throwable)message).printStackTrace(System.err);
        output(dp+1,false,message);
    }
    private static synchronized void output(int dp,boolean error,Object message){
        TLS t=tls.get();
        if(t==null)tls.set((t=new TLS()));
        String out=String.format((error?"E ":"  ")+"%s[%4d] %-32s %s%n",
                t.df.format(System.currentTimeMillis()),t.tid,getSource(dp+1),message);
        if(ous!=null)try{
            ous.write(out.getBytes());
        }catch(IOException e){
            e.printStackTrace();
        }if(!muteOutput)(error?System.err:System.out).print(out);
    }
    /** @return milliseconds */
    public static long clock(){
        long clock=System.nanoTime();
        if(clock<=0)throw new IllegalStateException("clock()<=0");
        return clock/1000000;
    }
    public static String escapeSqlField(String str){
        if(str==null)return null;
        return '\''+str.replace("'","''")+'\'';
    }
    public static String escapeLineField(String str){
        if(str==null) return null;
        return str.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").
                replace("\\","\\\\").replace(",","\\c").replace("\r","\\r").replace("\n","\\n");
    }
    /**
     * @return milliseconds with GMT+8 fix
     */
    public static long sqlTime(String timestamp){
        try{
            DateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return df.parse(timestamp).getTime()+8*3600*1000;
        }catch(ParseException e){
            Utils.err(e);
            return -1;
        }
    }
    /**
     * @return 2012-12-31 23:59:59 with GMT+8 fix
     */
    public static String sqlTime(long timestamp){
        if(timestamp==0) return null;
        DateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return '\''+df.format(timestamp-8*3600*1000)+'\'';
    }
    /**
     * return the original string if it has passed all the checks
     * @param str to be checked
     * @return the original string
     * @throws NullPointerException if str is null
     * @throws SecurityException if any check on str has failed
     */
    public static String strictFieldCheck(String str){
        for(char c:str.toCharArray()){
            if(!(('a'<=c && c<='z')||
                 ('A'<=c && c<='Z')||
                 ('0'<=c && c<='9')
            ))throw new SecurityException("strict field check: "+str);
        }return str;
    }
    public static String stripClassName(String fqn){
        int i=fqn.lastIndexOf('$');
        if(i<=0) i=fqn.lastIndexOf('.');
        if(i>0) fqn=fqn.substring(i+1);
        return fqn;
    }
    /**
     * @return ShortClassName#100:methodName
     */
    public static String getSource(StackTraceElement st){
        return String.format("%s#%d:%s",stripClassName(st.getClassName()),st.getLineNumber(),st.getMethodName());
    }
    public static String getSource(int frame){
        StackTraceElement[]sts=new Throwable().getStackTrace();
        frame+=1;
        if(sts.length<=frame)
            return "####"; // java obfuscator present, we may be inlined.
        return getSource(sts[frame]);
    }
    public static String getSource(){
        return getSource(1);
    }
}
