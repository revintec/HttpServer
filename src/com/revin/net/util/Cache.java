package com.revin.net.util;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * Created by revintec on 14-1-17.
 */
public class Cache<K,V>{
    private static class Holder<K,V>{
        public final Cache<K,V>cache;
        public final K key;
        private Reference<Holder<K,V>>ref;
        public final V value;
        private Holder(Cache<K,V>cache,K key,V value){
            this.cache=cache;
            this.key=key;
            this.value=value;
        }
        @Override
        protected void finalize()throws Throwable{
            super.finalize();
            if(ref==null)Utils.err("ref==null: ["+key+"]"+value);
            cache.removeReference(key,ref);
        }
    }
    public interface IFetcher<K,V>{public V fetch(Cache<K,V>cache,K key);}
    private final Map<K,Reference<Holder<K,V>>>map=new HashMap<>();
    public synchronized boolean removeReference(K key,Reference ref){
        Reference obj=map.get(key);
        if(obj==null)return false;// the cache has been purged
        if(!obj.equals(ref))return false;// the cache has been replaced
        map.remove(key);
        return true;
    }
    public synchronized boolean containsKey(K key){
        return map.containsKey(key);
    }
    public V fetch(K key,IFetcher<K,V>fetcher,boolean forced){
        if(!forced)
            synchronized(this){
                V cached=get(key);
                if(cached!=null)return cached;
            }
        V fetched=fetcher.fetch(this,key);
        synchronized(this){
            V cached=get(key);
            if(forced||cached==null){
                put(key,fetched);
                return fetched;
            }else return cached;
        }
    }
    public synchronized V put(K key,V val){
        Holder<K,V> holder=new Holder<>(this,key,val);
        Reference<Holder<K,V>>ref=holder.ref=new SoftReference<>(holder);
        Reference<Holder<K,V>>obj=map.put(key,ref);
        if(obj!=null&&(holder=obj.get())!=null)
            return holder.value;
        return null;
    }
    public synchronized V get(K key){
        Holder<K,V>holder;
        Reference<Holder<K,V>>obj=map.get(key);
        if(obj!=null&&(holder=obj.get())!=null)
            return holder.value;
        return null;
    }
    public synchronized V remove(K key){
        Holder<K,V>holder;
        Reference<Holder<K,V>>obj=map.remove(key);
        if(obj!=null&&(holder=obj.get())!=null)
            return holder.value;
        return null;
    }
}
