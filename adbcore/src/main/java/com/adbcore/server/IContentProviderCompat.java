package com.adbcore.server;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.system.Os;

/**
 * Cross-version wrapper around the hidden {@link IContentProvider#call} signatures.
 * Adapted from Shizuku's IContentProviderCompat.java.
 */
public class IContentProviderCompat {

    public static Bundle call(IContentProvider provider, String attributeTag, String callingPkg,
                              String authority, String method, String arg, Bundle extras) throws RemoteException {
        Bundle reply;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                reply = provider.call(
                        new AttributionSource.Builder(Os.getuid())
                                .setAttributionTag(attributeTag)
                                .setPackageName(callingPkg)
                                .build(),
                        authority, method, arg, extras);
            } catch (LinkageError e) {
                reply = provider.call(callingPkg, attributeTag, authority, method, arg, extras);
            }
        } else {
            // minSdk = 30
            reply = provider.call(callingPkg, attributeTag, authority, method, arg, extras);
        }
        return reply;
    }
}
