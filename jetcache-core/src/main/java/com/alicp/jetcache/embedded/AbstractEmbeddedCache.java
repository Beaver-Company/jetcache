/**
 * Created on  13-10-17 23:01
 */
package com.alicp.jetcache.embedded;

import com.alicp.jetcache.*;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author <a href="mailto:yeli.hl@taobao.com">huangli</a>
 */
public abstract class AbstractEmbeddedCache<K, V> implements Cache<K, V> {
    protected EmbeddedCacheConfig config;
    private AreaCache areaCache;

    protected abstract AreaCache createAreaCache();

    public AbstractEmbeddedCache(EmbeddedCacheConfig config) {
        this.config = config;
        areaCache = createAreaCache();
    }

    private Object buildKey(K key) {
        Object newKey = key;
        Function<Object, Object> keyConvertor = config.getKeyConvertor();
        if (keyConvertor != null) {
            newKey = keyConvertor.apply(key);
        }
        return newKey;
    }

    protected CacheValueHolder<V> getHolder(K key) {
        try {
            Object newKey = buildKey(key);
            CacheValueHolder<V> holder = null;
            if (config.isWeakValues()) {
                WeakReference<CacheValueHolder<V>> ref = (WeakReference<CacheValueHolder<V>>) areaCache.getValue(newKey);
                if (ref == null) {
                    holder = null;
                } else {
                    holder = ref.get();

                }
            } else if (config.isSoftValues()) {
                SoftReference<CacheValueHolder<V>> ref = (SoftReference<CacheValueHolder<V>>) areaCache.getValue(newKey);
                if (ref == null) {
                    holder = null;
                } else {
                    holder = ref.get();
                }
            } else {
                holder = (CacheValueHolder<V>) areaCache.getValue(newKey);

            }
            return holder;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public CacheGetResult<V> GET(K key) {
        try {
            Object newKey = buildKey(key);
            CacheValueHolder<V> holder = null;
            if (config.isWeakValues()) {
                WeakReference<CacheValueHolder<V>> ref = (WeakReference<CacheValueHolder<V>>) areaCache.getValue(newKey);
                if (ref == null) {
                    return CacheGetResult.NOT_EXISTS_WITHOUT_MSG;
                } else {
                    holder = ref.get();
                    if (holder == null) {
                        return new CacheGetResult(CacheResultCode.NOT_EXISTS, null, "weak ref released");
                    } else {
                        return getImpl(newKey, holder);
                    }
                }
            } else if (config.isSoftValues()) {
                SoftReference<CacheValueHolder<V>> ref = (SoftReference<CacheValueHolder<V>>) areaCache.getValue(newKey);
                if (ref == null) {
                    return CacheGetResult.NOT_EXISTS_WITHOUT_MSG;
                } else {
                    holder = ref.get();
                    if (holder == null) {
                        return new CacheGetResult(CacheResultCode.NOT_EXISTS, null, "soft ref released");
                    } else {
                        return getImpl(newKey, holder);
                    }
                }
            } else {
                CacheValueHolder<V> cacheObject = (CacheValueHolder<V>) areaCache.getValue(newKey);
                if (cacheObject == null) {
                    return CacheGetResult.NOT_EXISTS_WITHOUT_MSG;
                } else {
                    return getImpl(newKey, cacheObject);
                }
            }
        } catch (Exception e) {
            return new CacheGetResult(CacheResultCode.FAIL, null, e.getClass().getName() + ":" + e.getMessage());
        }
    }

    private CacheGetResult<V> getImpl(Object newKey, CacheValueHolder<V> cacheObject) {
        if (System.currentTimeMillis() - cacheObject.getExpireTime() >= 0) {
            areaCache.removeValue(newKey);
            return CacheGetResult.EXPIRED_WITHOUT_MSG;
        } else {
            if (config.isExpireAfterAccess()) {
                long ttlInMillis = cacheObject.getTtlInMillis();
                cacheObject.setExpireTime(System.currentTimeMillis() + ttlInMillis);
            }
            return new CacheGetResult<V>(CacheResultCode.SUCCESS, null, cacheObject.getValue());
        }
    }

    @Override
    public void put(K key, V value) {
        PUT(key, value, config.getDefaultExpireInMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public CacheResult PUT(K key, V value, long expire, TimeUnit timeUnit) {
        CacheValueHolder<V> cacheObject = null;
        cacheObject = new CacheValueHolder(value, System.currentTimeMillis(), timeUnit.toMillis(expire));
        if (config.isWeakValues()) {
            WeakReference<CacheValueHolder<V>> ref = new WeakReference(cacheObject);
            areaCache.putValue(buildKey(key), ref);
        } else if (config.isSoftValues()) {
            SoftReference<CacheValueHolder<V>> ref = new SoftReference(cacheObject);
            areaCache.putValue(buildKey(key), ref);
        } else {
            areaCache.putValue(buildKey(key), cacheObject);
        }
        return CacheResult.SUCCESS_WITHOUT_MSG;
    }

    @Override
    public CacheResult INVALIDATE(K key) {
        areaCache.removeValue(buildKey(key));
        return CacheResult.SUCCESS_WITHOUT_MSG;
    }
}