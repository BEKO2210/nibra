package org.gradle.cache.internal.locklistener;

import java.util.function.Consumer;
import org.gradle.cache.FileLockReleasedSignal;
import org.gradle.internal.concurrent.ExecutorFactory;

public class DefaultFileLockContentionHandler implements FileLockContentionHandler {
    public DefaultFileLockContentionHandler(
            ExecutorFactory executorFactory,
            InetAddressProvider inetAddressProvider) {}

    @Override public void start(long lockId, Consumer<FileLockReleasedSignal> action) {}
    @Override public void stop(long lockId) {}
    @Override public int reservePort() { return 0; }
    @Override public boolean maybePingOwner(
            int port,
            long lockId,
            String displayName,
            long time,
            FileLockReleasedSignal signal) { return false; }
    @Override public boolean isRunning() { return false; }
}
