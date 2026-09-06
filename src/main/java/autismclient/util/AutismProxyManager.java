package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.util.multi.MultiProxyVerifier;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutismProxyManager extends PersistentNbtManager<AutismProxy> implements Iterable<AutismProxy> {
    private static final AutismProxyManager INSTANCE = new AutismProxyManager();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final AtomicBoolean refreshCancelRequested = new AtomicBoolean(false);
    private final AtomicInteger refreshChecked = new AtomicInteger();
    private final AtomicInteger refreshTotal = new AtomicInteger();
    private final AtomicBoolean importing = new AtomicBoolean(false);
    private final AtomicBoolean importCancelRequested = new AtomicBoolean(false);
    private final AtomicInteger importLinesRead = new AtomicInteger();
    private final AtomicInteger importCandidates = new AtomicInteger();
    private final AtomicInteger importAdded = new AtomicInteger();
    private final AtomicBoolean saveWorkerRunning = new AtomicBoolean(false);
    private final AtomicBoolean geoLookupRunning = new AtomicBoolean(false);
    private final AtomicBoolean geoLookupRequestedAgain = new AtomicBoolean(false);
    private int timeoutMs = 3000;
    private int threads = 64;
    private int retries = 0;
    private boolean sortByLatency = true;
    private boolean pruneDead = true;
    private int pruneLatency = 2000;
    private int pruneToCount = 0;
    private volatile long refreshGeneration;
    private volatile long refreshRevision;
    private volatile long refreshCancelNoticeUntilMs;
    private volatile long importGeneration;
    private volatile long importRevision;
    private volatile long importCancelNoticeUntilMs;
    private volatile long geoGeneration;
    private volatile long canceledImportGeneration = Long.MIN_VALUE;
    private volatile long listRevision;
    private volatile long lastRefreshRevisionMs;
    private volatile long lastImportRevisionMs;
    private volatile boolean saveRequested;
    private volatile ExecutorService refreshExecutor;
    private volatile Thread importThread;
    private volatile List<AutismProxy> activeRefreshSnapshot = List.of();

    private static final Pattern PROXY_PATTERN = Pattern.compile("^(?:([\\w\\s]+)=)?((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])(?i:@(socks[45]))?$", Pattern.MULTILINE);
    private static final Pattern PROXY_PATTERN_WEBSHARE = Pattern.compile("^((?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5]):([^:]+)(?::(.+))?$", Pattern.MULTILINE);
    private static final Pattern PROXY_PATTERN_URI = Pattern.compile("^(?:(?<type>socks|socks4|socks5)://)?(?:(?<user>[\\w~-]+)(:(?<pass>[\\w~-]+))?@)?(?<addr>(?:0*(?:\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])(?:\\.(?!:)|)){4}):(?!0)(?<port>\\d{1,4}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$", Pattern.MULTILINE);
    private static final int GEO_BATCH_SIZE = 100;

    public record RefreshStatus(boolean running, boolean canceling, int checked, int total, long generation, long revision) {
    }

    public record ImportStatus(boolean running, boolean canceling, int linesRead, int candidates, int added, long generation, long revision, boolean canceled) {
    }

    private record RefreshTarget(AutismProxy proxy, String key) {
    }

    private record GeoTarget(AutismProxy proxy, String key) {
    }

    private record GeoApplication(GeoTarget target, AutismProxyGeoLookup.GeoResult result) {
    }

    private record RefreshProbe(AutismProxy.CheckResult result, AutismProxyType workingType) {
    }

    private AutismProxyManager() {
    }

    public static AutismProxyManager get() {
        INSTANCE.ensureLoaded();
        AutismMeteorImport.ensureImported();
        INSTANCE.ensureStableIds();
        return INSTANCE;
    }

    private synchronized void ensureStableIds() {
        boolean changed = false;
        for (AutismProxy proxy : items) {
            if (proxy == null) continue;
            proxy.stableId();
            changed |= proxy.generatedStableId;
            proxy.generatedStableId = false;
        }
        if (changed) save();
    }

    public synchronized AutismProxy findById(String id) {
        if (id == null || id.isBlank()) return null;
        for (AutismProxy proxy : items) {
            if (id.equals(proxy.stableId())) return proxy;
        }
        return null;
    }

    @Override
    protected File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "autism-proxies.nbt");
    }

    @Override
    protected String listKey() {
        return "proxies";
    }

    @Override
    protected AutismProxy fromTag(CompoundTag tag) {
        return new AutismProxy().fromTag(tag);
    }

    @Override
    protected CompoundTag toTag(AutismProxy item) {
        return item.toTag();
    }

    @Override
    protected String describe() {
        return "Autism proxies";
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        timeoutMs = tag.getIntOr("timeoutMs", timeoutMs);
        threads = tag.getIntOr("threads", threads);
        retries = tag.getIntOr("retries", retries);
        sortByLatency = tag.getBooleanOr("sortByLatency", sortByLatency);
        pruneDead = tag.getBooleanOr("pruneDead", pruneDead);
        pruneLatency = tag.getIntOr("pruneLatency", pruneLatency);
        pruneToCount = tag.getIntOr("pruneToCount", pruneToCount);
    }

    @Override
    protected void writeExtra(CompoundTag tag) {
        tag.putInt("timeoutMs", timeoutMs);
        tag.putInt("threads", threads);
        tag.putInt("retries", retries);
        tag.putBoolean("sortByLatency", sortByLatency);
        tag.putBoolean("pruneDead", pruneDead);
        tag.putInt("pruneLatency", pruneLatency);
        tag.putInt("pruneToCount", pruneToCount);
    }

    private void scheduleSave() {
        saveRequested = true;
        if (!saveWorkerRunning.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            try {
                do {
                    saveRequested = false;
                    save();
                } while (saveRequested);
            } finally {
                saveWorkerRunning.set(false);
                if (saveRequested) scheduleSave();
            }
        }, "Autism-Proxy-Save");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized int enabledCount() {
        int count = 0;
        for (AutismProxy proxy : items) {
            if (proxy.enabled && proxy.isValid()) count++;
        }
        return count;
    }

    public long listRevision() {
        return listRevision;
    }

    public synchronized boolean add(AutismProxy proxy) {
        if (proxy == null || !proxy.isValid() || items.contains(proxy)) return false;
        if (items.isEmpty()) proxy.enabled = true;
        items.add(proxy);
        bumpListRevision();
        scheduleSave();
        requestGeoLookup(false);
        return true;
    }

    public synchronized void remove(AutismProxy proxy) {
        if (items.remove(proxy)) {
            bumpListRevision();
            scheduleSave();
        }
    }

    public synchronized int clearAll() {
        if (refreshing.get() || importing.get() || items.isEmpty()) return 0;
        int removed = items.size();
        items.clear();
        bumpListRevision();
        scheduleSave();
        return removed;
    }

    public synchronized boolean update(AutismProxy existing, AutismProxy updated) {
        if (existing == null || updated == null || !updated.isValid()) return false;
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            AutismProxy proxy = items.get(i);
            if (proxy == existing) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;
        for (int i = 0; i < items.size(); i++) {
            if (i != index && items.get(i).equals(updated)) return false;
        }
        boolean identityChanged = existing.type != updated.type || existing.port != updated.port || !java.util.Objects.equals(existing.address, updated.address);
        existing.name = updated.name;
        existing.type = updated.type;
        existing.address = updated.address;
        existing.port = updated.port;
        existing.username = updated.username;
        existing.password = updated.password;
        if (identityChanged) {
            existing.status = AutismProxy.Status.UNCHECKED;
            existing.latency = 0L;
            existing.clearGeo();
        }
        bumpListRevision();
        scheduleSave();
        if (identityChanged) requestGeoLookup(false);
        return true;
    }

    public synchronized void setEnabled(AutismProxy proxy, boolean enabled) {
        for (AutismProxy current : items) current.enabled = false;
        if (proxy != null) proxy.enabled = enabled;
        bumpListRevision();
        scheduleSave();
    }

    public synchronized AutismProxy getEnabled() {
        for (AutismProxy proxy : items) {
            if (proxy.enabled && proxy.isValid()) return proxy;
        }
        return null;
    }

    public boolean isRefreshing() {
        return refreshing.get();
    }

    public RefreshStatus refreshStatus() {
        boolean running = refreshing.get();
        boolean canceling = !running && System.currentTimeMillis() < refreshCancelNoticeUntilMs;
        return new RefreshStatus(running, canceling, refreshChecked.get(), refreshTotal.get(), refreshGeneration, refreshRevision);
    }

    public ImportStatus importStatus() {
        boolean running = importing.get();
        boolean canceling = !running && System.currentTimeMillis() < importCancelNoticeUntilMs;
        boolean canceled = !running && importGeneration == canceledImportGeneration;
        return new ImportStatus(running, canceling, importLinesRead.get(), importCandidates.get(), importAdded.get(), importGeneration, importRevision, canceled);
    }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = Math.max(1, v); scheduleSave(); }
    public int getThreads() { return threads; }
    public void setThreads(int v) { this.threads = Math.max(1, v); scheduleSave(); }
    public int getRetries() { return retries; }
    public void setRetries(int v) { this.retries = Math.max(0, v); scheduleSave(); }
    public boolean isSortByLatency() { return sortByLatency; }
    public synchronized void setSortByLatency(boolean v) {
        this.sortByLatency = v;
        if (v) sortByLatencyInternal();
        bumpListRevision();
        scheduleSave();
    }
    public boolean isPruneDead() { return pruneDead; }
    public void setPruneDead(boolean v) { this.pruneDead = v; scheduleSave(); }
    public int getPruneLatency() { return pruneLatency; }
    public void setPruneLatency(int v) { this.pruneLatency = Math.max(0, v); scheduleSave(); }
    public int getPruneToCount() { return pruneToCount; }
    public void setPruneToCount(int v) { this.pruneToCount = Math.max(0, v); scheduleSave(); }

    public synchronized boolean sortByLatencyNow() {
        if (refreshing.get() || importing.get() || items.size() < 2) return false;
        sortByLatencyInternal();
        bumpListRevision();
        scheduleSave();
        return true;
    }

    public void checkProxies(boolean all) {
        startRefresh(all);
    }

    public boolean requestGeoLookup(boolean force) {
        ensureLoaded();
        if (!geoLookupRunning.compareAndSet(false, true)) {
            geoLookupRequestedAgain.set(true);
            return false;
        }
        List<GeoTarget> targets;
        long generation;
        synchronized (this) {
            targets = collectGeoTargets(force);
            if (targets.isEmpty()) {
                geoLookupRunning.set(false);
                return false;
            }
            generation = geoGeneration + 1L;
            geoGeneration = generation;
            long now = System.currentTimeMillis();
            for (GeoTarget target : targets) target.proxy().markGeoLookupPending(now);
            bumpListRevision();
        }
        Thread thread = new Thread(() -> runGeoLookup(generation, targets), "Autism-Proxy-Geo");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public boolean startRefresh(boolean all) {
        return startRefreshInternal(all, "", 0);
    }

    public boolean startRefreshToServer(boolean all, String host, int port) {
        String destination = host == null ? "" : host.trim();
        if (destination.isBlank() || port <= 0 || port > 65535) return false;
        return startRefreshInternal(all, destination, port);
    }

    private boolean startRefreshInternal(boolean all, String destinationHost, int destinationPort) {
        List<RefreshTarget> targets = new ArrayList<>();
        List<AutismProxy> active = new ArrayList<>();
        ExecutorService executor;
        long generation;
        int timeout;
        int retryCount;
        int workerCount;
        synchronized (this) {
            if (refreshing.get() || importing.get() || items.isEmpty()) return false;
            for (AutismProxy proxy : items) {
                if (proxy == null || !proxy.isValid()) continue;
                if (!all && proxy.status != AutismProxy.Status.UNCHECKED) continue;
                targets.add(new RefreshTarget(proxy, proxyKey(proxy)));
                active.add(proxy);
            }
            if (targets.isEmpty()) return false;
            generation = refreshGeneration + 1L;
            refreshGeneration = generation;
            refreshCancelRequested.set(false);
            refreshCancelNoticeUntilMs = 0L;
            refreshChecked.set(0);
            refreshTotal.set(targets.size());
            activeRefreshSnapshot = List.copyOf(active);
            for (AutismProxy proxy : active) {
                proxy.status = AutismProxy.Status.CHECKING;
                proxy.latency = 0L;
            }
            timeout = effectiveRefreshTimeout(targets.size());
            retryCount = effectiveRefreshRetries(targets.size());
            workerCount = effectiveRefreshThreads(targets.size());
            executor = Executors.newFixedThreadPool(workerCount);
            refreshExecutor = executor;
            refreshing.set(true);
            bumpRefreshRevision(true);
        }

        Thread thread = new Thread(() -> runRefreshJob(generation, executor, targets, timeout, retryCount,
            workerCount, destinationHost, destinationPort), "Autism-Proxy-Refresh");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public boolean cancelRefresh() {
        ExecutorService executor;
        List<AutismProxy> active;
        synchronized (this) {
            if (!refreshing.get()) return false;
            refreshCancelRequested.set(true);
            refreshing.set(false);
            refreshCancelNoticeUntilMs = System.currentTimeMillis() + 800L;
            executor = refreshExecutor;
            refreshExecutor = null;
            active = activeRefreshSnapshot;
            activeRefreshSnapshot = List.of();
            for (AutismProxy proxy : active) {
                if (proxy.status == AutismProxy.Status.CHECKING) {
                    proxy.status = AutismProxy.Status.UNCHECKED;
                    proxy.latency = 0L;
                }
            }
            if (sortByLatency) {
                sortByLatencyInternal();
                bumpListRevision();
                scheduleSave();
            }
            bumpRefreshRevision(true);
        }
        if (executor != null) executor.shutdownNow();
        return true;
    }

    private interface ImportSource {
        BufferedReader open() throws Exception;
    }

    public boolean startImport(File file) {
        if (file == null || !file.isFile()) return false;
        return launchImport(() -> new BufferedReader(new FileReader(file)));
    }

    public boolean startImportFromText(String text) {
        if (text == null || text.isBlank()) return false;
        return launchImport(() -> new BufferedReader(new StringReader(text)));
    }

    private boolean launchImport(ImportSource source) {
        long generation;
        synchronized (this) {
            if (importing.get() || refreshing.get()) return false;
            generation = importGeneration + 1L;
            importGeneration = generation;
            canceledImportGeneration = Long.MIN_VALUE;
            importCancelRequested.set(false);
            importCancelNoticeUntilMs = 0L;
            importLinesRead.set(0);
            importCandidates.set(0);
            importAdded.set(0);
            importing.set(true);
            bumpImportRevision(true);
        }
        Thread thread = new Thread(() -> runImportJob(generation, source), "Autism-Proxy-Import");
        thread.setDaemon(true);
        importThread = thread;
        thread.start();
        return true;
    }

    public boolean cancelImport() {
        Thread thread;
        synchronized (this) {
            if (!importing.get()) return false;
            importCancelRequested.set(true);
            importing.set(false);
            canceledImportGeneration = importGeneration;
            importCancelNoticeUntilMs = System.currentTimeMillis() + 800L;
            thread = importThread;
            importThread = null;
            bumpImportRevision(true);
        }
        if (thread != null) thread.interrupt();
        return true;
    }

    private void runImportJob(long generation, ImportSource source) {
        List<AutismProxy> parsed = new ArrayList<>();
        Set<String> knownKeys;
        synchronized (this) {
            knownKeys = new HashSet<>(items.size() + 1024);
            for (AutismProxy proxy : items) knownKeys.add(proxyKey(proxy));
        }
        try (BufferedReader reader = source.open()) {
            String line;
            while (isImportCurrent(generation) && (line = reader.readLine()) != null) {
                importLinesRead.incrementAndGet();
                AutismProxy proxy = parseProxyLine(line.trim());
                if (proxy != null && proxy.isValid() && knownKeys.add(proxyKey(proxy))) {
                    parsed.add(proxy);
                    importCandidates.set(parsed.size());
                }
                bumpImportRevision(false);
            }
        } catch (Exception e) {
            if (isImportCurrent(generation)) AutismClientAddon.LOG.error("Failed to import proxies", e);
        } finally {
            finishImport(generation, parsed);
        }
    }

    private synchronized void finishImport(long generation, List<AutismProxy> parsed) {
        if (importGeneration != generation || !importing.get()) return;
        int added = 0;
        if (!importCancelRequested.get() && parsed != null && !parsed.isEmpty()) {
            Set<String> knownKeys = new HashSet<>(items.size() + parsed.size() + 16);
            for (AutismProxy proxy : items) knownKeys.add(proxyKey(proxy));
            for (AutismProxy proxy : parsed) {
                if (!knownKeys.add(proxyKey(proxy))) continue;

                proxy.enabled = false;
                items.add(proxy);
                added++;
            }
        }
        importAdded.set(added);
        importing.set(false);
        importCancelRequested.set(false);
        importThread = null;
        if (added > 0) {
            bumpListRevision();
            scheduleSave();
            requestGeoLookup(false);
        }
        bumpImportRevision(true);
    }

    private boolean isImportCurrent(long generation) {
        return importing.get() && importGeneration == generation && !importCancelRequested.get();
    }

    private void bumpImportRevision(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastImportRevisionMs < 150L) return;
        lastImportRevisionMs = now;
        importRevision++;
    }

    private void runRefreshJob(long generation, ExecutorService executor, List<RefreshTarget> targets, int timeout,
                               int retryCount, int workerCount, String destinationHost, int destinationPort) {
        boolean completed = false;
        try {
            AtomicInteger nextIndex = new AtomicInteger();
            for (int i = 0; i < workerCount; i++) {
                if (!isRefreshCurrent(generation)) break;
                executor.execute(() -> {
                    while (isRefreshCurrent(generation)) {
                        int index = nextIndex.getAndIncrement();
                        if (index >= targets.size()) break;
                        refreshOne(generation, targets.get(index), timeout, retryCount,
                            destinationHost, destinationPort);
                    }
                });
            }
            executor.shutdown();
            while (true) {
                if (!isRefreshCurrent(generation)) {
                    executor.shutdownNow();
                    break;
                }
                if (executor.awaitTermination(100L, TimeUnit.MILLISECONDS)) {
                    completed = true;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } catch (RejectedExecutionException e) {
            if (isRefreshCurrent(generation)) AutismClientAddon.LOG.error("Proxy refresh rejected a check task", e);
            executor.shutdownNow();
        } catch (RuntimeException e) {
            AutismClientAddon.LOG.error("Proxy refresh failed", e);
            executor.shutdownNow();
        } finally {
            finishRefresh(generation, completed && isRefreshCurrent(generation));
        }
    }

    private void refreshOne(long generation, RefreshTarget target, int timeout, int retryCount,
                            String destinationHost, int destinationPort) {
        if (!isRefreshCurrent(generation)) return;
        RefreshProbe probe = probeRefreshTarget(target.proxy(), timeout, destinationHost, destinationPort);
        int attempts = 0;
        while (probe.result().status() != AutismProxy.Status.ALIVE
            && attempts < retryCount && isRefreshCurrent(generation)) {
            probe = probeRefreshTarget(target.proxy(), timeout, destinationHost, destinationPort);
            attempts++;
        }
        applyRefreshResult(generation, target, probe);
    }

    private static RefreshProbe probeRefreshTarget(AutismProxy proxy, int timeout,
                                                    String destinationHost, int destinationPort) {
        if (destinationHost == null || destinationHost.isBlank() || destinationPort <= 0) {
            return new RefreshProbe(proxy.probeStatus(timeout), null);
        }
        MultiProxyVerifier.Result verified = MultiProxyVerifier.verify(
            proxy, destinationHost, destinationPort, timeout);
        AutismProxy.CheckResult result = verified.ok()
            ? new AutismProxy.CheckResult(AutismProxy.Status.ALIVE, verified.latencyMs(), 1)
            : new AutismProxy.CheckResult(AutismProxy.Status.DEAD, 0L, 2);
        return new RefreshProbe(result, verified.workingType());
    }

    public synchronized boolean applySingleCheck(AutismProxy proxy, AutismProxy.CheckResult result,
                                                  AutismProxyType workingType) {
        if (proxy == null || result == null || refreshing.get() || !items.contains(proxy)) return false;
        if (result.status() == AutismProxy.Status.ALIVE && workingType != null) proxy.type = workingType;
        proxy.applyCheckResult(result);
        sortByLatencyInternal();
        bumpListRevision();
        scheduleSave();
        return true;
    }

    private synchronized void applyRefreshResult(long generation, RefreshTarget target, RefreshProbe probe) {
        if (refreshGeneration != generation || !refreshing.get() || refreshCancelRequested.get()) return;
        AutismProxy proxy = target.proxy();
        if (items.contains(proxy) && target.key().equals(proxyKey(proxy))) {
            if (probe.result().status() == AutismProxy.Status.ALIVE && probe.workingType() != null) {
                proxy.type = probe.workingType();
            }
            proxy.applyCheckResult(probe.result());
        }
        refreshChecked.incrementAndGet();
        bumpRefreshRevision(false);
    }

    private synchronized void finishRefresh(long generation, boolean completed) {
        if (refreshGeneration != generation || !refreshing.get()) return;
        refreshing.set(false);
        refreshCancelRequested.set(false);
        refreshExecutor = null;
        activeRefreshSnapshot = List.of();
        if (completed && sortByLatency) sortByLatencyInternal();
        if (completed && sortByLatency) {
            bumpListRevision();
            scheduleSave();
        }
        if (completed) requestGeoLookup(false);
        bumpRefreshRevision(true);
    }

    private boolean isRefreshCurrent(long generation) {
        return refreshing.get() && refreshGeneration == generation && !refreshCancelRequested.get();
    }

    private synchronized List<GeoTarget> collectGeoTargets(boolean force) {
        long now = System.currentTimeMillis();
        List<GeoTarget> targets = new ArrayList<>();
        for (AutismProxy proxy : items) {
            if (proxy != null && proxy.needsGeoLookup(now, force)) {
                targets.add(new GeoTarget(proxy, proxyKey(proxy)));
            }
        }
        return targets;
    }

    private void runGeoLookup(long generation, List<GeoTarget> targets) {

        if (autismclient.util.AutismLiteVariant.enabled()) return;
        try {
            Map<String, List<GeoTarget>> byIp = new LinkedHashMap<>();
            List<GeoApplication> immediate = new ArrayList<>();
            for (GeoTarget target : targets) {
                if (!isGeoCurrent(generation)) return;
                AutismProxyGeoLookup.ResolveResult resolved = AutismProxyGeoLookup.resolveAddress(target.proxy().address);
                if (resolved.immediateResult() != null) {
                    immediate.add(new GeoApplication(target, resolved.immediateResult()));
                    if (immediate.size() >= GEO_BATCH_SIZE) {
                        applyGeoResults(generation, immediate);
                        immediate.clear();
                    }
                    continue;
                }
                if (!resolved.ip().isBlank()) {
                    byIp.computeIfAbsent(resolved.ip(), ignored -> new ArrayList<>()).add(target);
                }
            }
            if (!immediate.isEmpty()) applyGeoResults(generation, immediate);

            List<String> batch = new ArrayList<>(GEO_BATCH_SIZE);
            for (String ip : byIp.keySet()) {
                if (!isGeoCurrent(generation)) return;
                batch.add(ip);
                if (batch.size() >= GEO_BATCH_SIZE) {
                    lookupGeoBatch(generation, batch, byIp);
                    batch.clear();
                    sleepBetweenGeoBatches();
                }
            }
            if (!batch.isEmpty() && isGeoCurrent(generation)) lookupGeoBatch(generation, batch, byIp);
        } finally {
            finishGeoLookup(generation);
        }
    }

    private void lookupGeoBatch(long generation, List<String> batch, Map<String, List<GeoTarget>> byIp) {
        Map<String, AutismProxyGeoLookup.GeoResult> results = AutismProxyGeoLookup.lookupBatch(List.copyOf(batch));
        List<GeoApplication> applications = new ArrayList<>();
        for (String ip : batch) {
            AutismProxyGeoLookup.GeoResult result = results.getOrDefault(ip, AutismProxyGeoLookup.GeoResult.failed(ip, System.currentTimeMillis()));
            List<GeoTarget> targets = byIp.get(ip);
            if (targets == null) continue;
            for (GeoTarget target : targets) applications.add(new GeoApplication(target, result));
        }
        applyGeoResults(generation, applications);
    }

    private synchronized void applyGeoResults(long generation, List<GeoApplication> applications) {
        if (geoGeneration != generation || applications == null || applications.isEmpty()) return;
        int changed = 0;
        for (GeoApplication application : applications) {
            GeoTarget target = application.target();
            AutismProxy proxy = target.proxy();
            if (items.contains(proxy) && target.key().equals(proxyKey(proxy))) {
                proxy.applyGeoResult(application.result());
                changed++;
            }
        }
        if (changed > 0) {
            bumpListRevision();
            scheduleSave();
        }
    }

    private void finishGeoLookup(long generation) {
        boolean runAgain = false;
        synchronized (this) {
            if (geoGeneration == generation) {
                runAgain = geoLookupRequestedAgain.getAndSet(false);
                geoLookupRunning.set(false);
            }
        }
        if (runAgain) requestGeoLookup(false);
    }

    private boolean isGeoCurrent(long generation) {
        return geoLookupRunning.get() && geoGeneration == generation;
    }

    private static void sleepBetweenGeoBatches() {
        try {
            Thread.sleep(120L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void bumpRefreshRevision(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshRevisionMs < 150L && refreshChecked.get() < refreshTotal.get()) return;
        lastRefreshRevisionMs = now;
        refreshRevision++;
    }

    private void bumpListRevision() {
        listRevision++;
        refreshRevision++;
    }

    private int effectiveRefreshThreads(int targetCount) {
        int configured = Math.max(1, threads);
        int floor = targetCount >= 2000 ? 256 : targetCount >= 500 ? 128 : targetCount >= 100 ? 64 : targetCount >= 32 ? 32 : configured;
        return Math.max(1, Math.min(targetCount, Math.max(configured, floor)));
    }

    private int effectiveRefreshTimeout(int targetCount) {
        int configured = Math.max(1, timeoutMs);
        if (targetCount >= 500) return Math.min(configured, 2500);
        if (targetCount >= 100) return Math.min(configured, 3000);
        return configured;
    }

    private int effectiveRefreshRetries(int targetCount) {
        return targetCount >= 100 ? 0 : Math.max(0, retries);
    }

    private static String proxyKey(AutismProxy proxy) {
        if (proxy == null) return "";
        return (proxy.type == null ? "" : proxy.type.name()) + '\u0000' + proxy.address + '\u0000' + proxy.port;
    }

    public synchronized void clean() {
        if (refreshing.get() || importing.get()) return;
        int before = items.size();
        items.removeIf(proxy -> pruneDead && proxy.status == AutismProxy.Status.DEAD);
        items.removeIf(proxy -> pruneLatency > 0 && proxy.status == AutismProxy.Status.ALIVE && proxy.latency >= pruneLatency);
        List<AutismProxy> sorted = new ArrayList<>(items);
        sorted.sort(AutismProxyManager::compareByLatency);
        if (pruneToCount > 0 && sorted.size() > pruneToCount) {
            sorted.subList(pruneToCount, sorted.size()).clear();
            items.removeIf(proxy -> !sorted.contains(proxy));
        }
        if (sortByLatency) {
            items.clear();
            items.addAll(sorted);
        }
        if (before != items.size() || sortByLatency) bumpListRevision();
        scheduleSave();
    }

    public int importFromFile(File file) {
        List<AutismProxy> parsed = new ArrayList<>();
        int added = 0;
        Set<String> knownKeys;
        synchronized (this) {
            knownKeys = new HashSet<>(items.size() + 1024);
            for (AutismProxy proxy : items) knownKeys.add(proxyKey(proxy));
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                AutismProxy proxy = parseProxyLine(line.trim());
                if (proxy != null && proxy.isValid() && knownKeys.add(proxyKey(proxy))) parsed.add(proxy);
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.error("Failed to import proxies", e);
        }
        if (parsed.isEmpty()) return 0;
        synchronized (this) {
            Set<String> currentKeys = new HashSet<>(items.size() + parsed.size() + 16);
            for (AutismProxy proxy : items) currentKeys.add(proxyKey(proxy));
            for (AutismProxy proxy : parsed) {
                if (!currentKeys.add(proxyKey(proxy))) continue;
                if (items.isEmpty() && added == 0) proxy.enabled = true;
                items.add(proxy);
                added++;
            }
            if (added > 0) {
                bumpListRevision();
                scheduleSave();
                requestGeoLookup(false);
            }
        }
        return added;
    }

    private void sortByLatencyInternal() {
        items.sort(AutismProxyManager::compareByLatency);
    }

    private static int compareByLatency(AutismProxy a, AutismProxy b) {
        boolean aliveA = a != null && a.status == AutismProxy.Status.ALIVE;
        boolean aliveB = b != null && b.status == AutismProxy.Status.ALIVE;
        if (aliveA != aliveB) return aliveA ? -1 : 1;
        if (aliveA) return Long.compare(a.latency, b.latency);
        return 0;
    }

    private static AutismProxy parseProxyLine(String line) {
        if (line.isBlank() || line.startsWith("#")) return null;
        Matcher m = PROXY_PATTERN.matcher(line);
        if (m.find()) return buildProxy(m.group(1), normalizeAddress(m.group(2)), Integer.parseInt(m.group(3)), m.group(4), AutismProxyType.Socks4);
        m = PROXY_PATTERN_WEBSHARE.matcher(line);
        if (m.find()) {
            AutismProxy proxy = buildProxy(null, normalizeAddress(m.group(1)), Integer.parseInt(m.group(2)), null, AutismProxyType.Socks5);
            if (m.group(3) != null) proxy.username = m.group(3);
            if (m.group(4) != null) proxy.password = m.group(4);
            return proxy;
        }
        m = PROXY_PATTERN_URI.matcher(line);
        if (m.find()) {
            String typeName = m.group("type");
            AutismProxyType defaultType = m.group("pass") != null || "socks".equals(typeName) ? AutismProxyType.Socks5 : AutismProxyType.Socks4;
            AutismProxy proxy = buildProxy(null, normalizeAddress(m.group("addr")), Integer.parseInt(m.group("port")), typeName, defaultType);
            if (m.group("user") != null) proxy.username = m.group("user");
            if (m.group("pass") != null) proxy.password = m.group("pass");
            return proxy;
        }
        return null;
    }

    private static String normalizeAddress(String address) {
        return address == null ? "" : address.replaceAll("\\b0+\\B", "");
    }

    private static AutismProxy buildProxy(String name, String address, int port, String typeName, AutismProxyType defaultType) {
        AutismProxy proxy = new AutismProxy();
        proxy.name = name == null ? "" : name.trim();
        proxy.address = address;
        proxy.port = port;
        proxy.type = defaultType == null ? AutismProxyType.Socks5 : defaultType;
        if (typeName != null) {
            String lower = typeName.toLowerCase();
            if (lower.contains("4")) proxy.type = AutismProxyType.Socks4;
            else if (lower.contains("5")) proxy.type = AutismProxyType.Socks5;
        }
        return proxy;
    }

    @Override
    public Iterator<AutismProxy> iterator() {
        return all().iterator();
    }
}
