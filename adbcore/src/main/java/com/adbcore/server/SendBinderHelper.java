package com.adbcore.server;

import android.app.IActivityManager;
import android.content.IContentProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Sends the server binder to the host app's ContentProvider via
 * {@link IActivityManager#getContentProviderExternal(String, int, IBinder, String)}.
 * <p>
 * The host app's provider receives the binder in its
 * {@link android.content.ContentProvider#call(String, String, Bundle)} method
 * with method = "sendBinder" and the Binder stored under EXTRA_BINDER in the
 * extras bundle.
 * <p>
 * Implementation note: the param shapes here mirror Shizuku's
 * starter/ServiceStarter.sendBinder + IContentProviderCompat.call exactly.
 * Notable specifics that several Android forks require:
 *   - getContentProviderExternal(name, userId, null, name)
 *       token must be null; tag must be the authority itself.
 *   - IContentProviderCompat.call(provider, null, null, authority, ...)
 *       callingPkg must be null so AttributionSource is built off the caller's
 *       uid alone — passing a fake string ("shell") makes API 31+ AMS reject
 *       the call as a packageName/uid mismatch.
 */
class SendBinderHelper {

    private static final String TAG = "AdbCoreServer";

    static final String METHOD_SEND_BINDER = "sendBinder";
    static final String EXTRA_BINDER = "binder";

    /**
     * @param authority host app's ContentProvider authority (e.g. "com.foo.adbcore")
     * @param binder    the server's IAdbCoreService binder
     * @return true on success
     */
    static boolean send(String authority, IBinder binder) {
        if (authority == null || authority.isEmpty()) {
            Log.e(TAG, "host authority is empty, cannot send binder");
            return false;
        }

        IActivityManager am = IActivityManager.Stub.asInterface(
                ServiceManager.getService("activity"));
        if (am == null) {
            Log.e(TAG, "ActivityManagerService not available");
            return false;
        }

        IContentProvider provider = null;
        try {
            // 这两条诊断日志降到 debug 级别,因为 server 端会周期性重发(默认 15s 一次),
            // 在 info 级别会刷屏。需要排查时再用 `setprop log.tag.AdbCoreServer DEBUG` 打开。
            Log.d(TAG, "calling getContentProviderExternal(authority=" + authority + ", token=null)");
            Object holder = invokeGetContentProviderExternal(am, authority);
            if (holder == null) {
                Log.w(TAG, "ContentProviderHolder is null for authority=" + authority +
                        " — host App likely not running yet (will retry).");
                return false;
            }
            Log.d(TAG, "got holder of type " + holder.getClass().getName());

            Field providerField;
            try {
                providerField = holder.getClass().getDeclaredField("provider");
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "ContentProviderHolder has no 'provider' field on this ROM. " +
                        "Available fields=" + java.util.Arrays.toString(holder.getClass().getDeclaredFields()), e);
                return false;
            }
            providerField.setAccessible(true);
            provider = (IContentProvider) providerField.get(holder);
            if (provider == null) {
                Log.e(TAG, "IContentProvider is null inside holder");
                return false;
            }
            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "provider binder is dead");
                return false;
            }

            Bundle extras = new Bundle();
            extras.putBinder(EXTRA_BINDER, binder);
            // callingPkg = null (matches Shizuku ServiceStarter.sendBinder).
            // Passing a fake "shell" string would make API 31+ AMS reject the call
            // because the AttributionSource packageName would mismatch the SHELL uid.
            IContentProviderCompat.call(provider, null, null, authority,
                    METHOD_SEND_BINDER, null, extras);

            Log.d(TAG, "binder sent to host provider " + authority);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "failed to send binder", t);
            return false;
        } finally {
            if (provider != null) {
                try {
                    invokeRemoveContentProviderExternal(am, authority);
                } catch (Throwable t) {
                    Log.w(TAG, "removeContentProviderExternal failed", t);
                }
            }
        }
    }

    /**
     * Mirrors Shizuku: pass token = null, tag = authority itself.
     */
    private static Object invokeGetContentProviderExternal(IActivityManager am, String name) throws Throwable {
        // API 30+: getContentProviderExternal(String, int, IBinder, String)
        try {
            Method m = am.getClass().getMethod("getContentProviderExternal",
                    String.class, int.class, IBinder.class, String.class);
            return m.invoke(am, name, 0, null, name);
        } catch (NoSuchMethodException ignored) {
            // older API fallback
            Method m = am.getClass().getMethod("getContentProviderExternal",
                    String.class, int.class, IBinder.class);
            return m.invoke(am, name, 0, null);
        }
    }

    private static void invokeRemoveContentProviderExternal(IActivityManager am, String name) throws Throwable {
        Method m = am.getClass().getMethod("removeContentProviderExternal",
                String.class, IBinder.class);
        m.invoke(am, name, null);
    }
}
