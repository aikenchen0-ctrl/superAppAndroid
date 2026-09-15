package com.paifa.univerge.gesture.server;

import android.os.Bundle;

/** Stable cross-process contract for the low-frequency gesture snapshot bridge. */
interface IGestureServer {
    /** Applies a serialized snapshot and reports whether the version was accepted. */
    boolean applySnapshotRemote(in Bundle snapshot);

    /** Returns the currently accepted snapshot, or null when none has been applied. */
    Bundle currentSnapshotRemote();

    /** Returns the currently accepted snapshot version, or zero when none exists. */
    long currentVersionRemote();

    /** Keeps the server-owned snapshot alive across a client Binder disconnect. */
    Bundle binderDisconnectedRemote();
}
