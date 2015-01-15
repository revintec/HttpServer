package com.revin.net;


public class Cookie{
    public final String key;
    private String value;
    private String path;
    private String domain;
    private int maxAge=-1;
    private boolean httpOnly;
    public Cookie(String key,String value){
        this.key=key;
        this.value=value;
    }
    public boolean getHttpOnly(){
        return httpOnly;
    }
    public boolean setHttpOnly(boolean httpOnly){
        boolean o=getHttpOnly();
        this.httpOnly=httpOnly;
        return o;
    }
    public String getValue(){
        return value;
    }
    public String setValue(String value){
        String o=getValue();
        this.value=value;
        return o;
    }
    public String getPath(){
        return path;
    }
    public String setPath(String path){
        String o=getPath();
        this.path=path;
        return o;
    }
    public String getDomain(){
        return domain;
    }
    public String setDomain(String domain){
        String o=getDomain();
        this.domain=domain;
        return o;
    }
    public int getMaxAge(){
        return maxAge;
    }
    /**
     * @param maxAge in seconds
     * @return original maxAge
     */
    public int setMaxAge(int maxAge){
        int o=getMaxAge();
        this.maxAge=maxAge;
        return o;
    }
    @Override
    public String toString(){
        StringBuilder sb=new StringBuilder();
        sb.append(key).append('=').append(value);
        if(getPath()!=null)sb.append("; path=").append(path);
        if(getDomain()!=null)sb.append("; domain=").append(domain);
        if(getMaxAge()!=-1)sb.append("; max-age=").append(maxAge);
        if(getHttpOnly())sb.append("; HttpOnly");
        return sb.toString();
    }
}
