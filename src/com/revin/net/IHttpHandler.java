package com.revin.net;

public interface IHttpHandler{
    public static interface IHttpContext{
        void abort();
    }
    public static interface IComponentLoader{IHttpHandler loadComponent(String mod);}
    /**
     *
     * @param context
     * @param mod /modPath/modName mind the leading slash
     * @param fn can be null
     * @param ex can be null
     * @throws Exception
     */
    public void handleRequest(IHttpContext context,HttpRequest req,HttpResponse res,String mod,String fn,String ex)throws Exception;
}
