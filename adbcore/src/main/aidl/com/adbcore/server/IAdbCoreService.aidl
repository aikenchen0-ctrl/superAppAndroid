// IAdbCoreService.aidl
package com.adbcore.server;

/**
 * Binder interface exposed by adbcore_server (running as uid 2000) to the host app.
 *
 * The implementation is in com.adbcore.server.ServerBinderImpl.
 */
interface IAdbCoreService {

    /**
     * Returns the server's own pid. Useful as a cheap "ping" to verify the
     * binder is alive without doing real work.
     */
    int getPid();

    /**
     * Synchronously executes `sh -c <command>` inside the server process and
     * returns stdout+stderr as a UTF-8 string. Will block until the child
     * process exits (or until [timeoutMs] elapses, whichever comes first).
     *
     * @param command   the command line passed to sh -c
     * @param timeoutMs maximum time to wait (ms); <=0 means "no timeout"
     */
    String exec(String command, long timeoutMs);

    /**
     * Starts a recurring script loop. The server forks a fresh `sh -c <script>`
     * process every iteration, waits for it to exit, then sleeps [intervalMs]
     * before the next run.
     *
     * Behavior:
     *   - If a task with the same [taskId] is already running, this call
     *     returns false (caller should stopScript first if it wants to replace).
     *   - The loop survives the death of the calling process. It only stops
     *     when stopScript / stopAllScripts is called, or the server itself dies.
     *
     * Output (stdout+stderr) of each iteration is logged to logcat under
     * the tag "AdbCoreScript:<taskId>".
     */
    boolean startScript(String taskId, String script, long intervalMs);

    /** Stops a running script loop. Returns true if the task existed. */
    boolean stopScript(String taskId);

    /** Stops every running script loop. */
    void stopAllScripts();

    /** Snapshot of all currently-running script task ids. */
    String[] runningScriptIds();
}
