package autismclient.util.multi.captcha;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class CaptchaSolver {

    public interface Host {
        void sendCaptchaChat(String message);
        void sendCaptchaCommand(String command);
        default void captchaNote(String note) {}
    }

    private static final long WINDOW_MS = 120_000;
    private static final long MIN_DELAY_MS = 600;
    private static final long MAX_DELAY_MS = 1900;
    private static final long RESEND_GUARD_MS = 1500;
    private static final long ADVANCE_TIMEOUT_MS = 3500;

    private static final double MAP_CONFIDENCE = 0.001;
    private static final int MAX_CANDIDATES = 3;
    private static final int CACHE_MAX = 8192;

    private static final Map<Long, String> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final Host host;
    private final Executor worker;
    private final CaptchaChatSolver chatSolver = new CaptchaChatSolver();
    private final Random random = new Random();

    private volatile long windowUntil;
    private volatile long lastAnswerAt;
    private volatile String lastAnswer = "";

    private volatile CaptchaChatSolver.Answer pending;
    private volatile long sendAt;

    private volatile long solvingHash = Long.MIN_VALUE;

    private volatile byte[] pendingColors;
    private volatile long pendingHash;
    private volatile String pendingCachedFirst;
    private volatile long challengeHash;
    private volatile String[] candidates;
    private volatile int candidateIndex;
    private volatile long advanceDeadline;
    private volatile boolean submittedWasNudged;
    private volatile boolean timeoutAdvanced;
    private volatile String submittedText;

    public CaptchaSolver(Host host, Executor worker) {
        this.host = host;
        this.worker = worker;
    }

    public void reset(long now) {

        if (challengeHash != 0 && submittedText != null && !submittedWasNudged && !timeoutAdvanced) {
            if (IMAGE_CACHE.size() < CACHE_MAX) IMAGE_CACHE.putIfAbsent(challengeHash, submittedText);
            host.captchaNote("Solved captcha");
        }
        windowUntil = now + WINDOW_MS;
        pending = null;
        sendAt = 0;
        solvingHash = Long.MIN_VALUE;
        pendingColors = null;
        pendingHash = 0;
        pendingCachedFirst = null;
        challengeHash = 0;
        candidates = null;
        candidateIndex = 0;
        advanceDeadline = 0;
        submittedWasNudged = false;
        timeoutAdvanced = false;
        submittedText = null;
    }

    private boolean armed(long now) {
        return now < windowUntil;
    }

    public boolean hasActiveChallenge() {
        return challengeHash != 0 || pending != null || candidates != null;
    }

    public boolean isActivelySolving(long now) {
        return armed(now) && hasActiveChallenge();
    }

    private void extend(long now) {
        windowUntil = now + WINDOW_MS;
    }

    public void onChat(Component component, long now) {
        if (component == null) return;

        if (challengeHash != 0 && isWrongNudge(component)) {
            submittedWasNudged = true;
            IMAGE_CACHE.remove(challengeHash);
            advanceCandidate(now);
            return;
        }
        if (!armed(now)) return;
        CaptchaChatSolver.Answer answer = chatSolver.onChat(component);
        if (answer != null) {
            extend(now);
            queue(answer, now);
        }
    }

    private static boolean isWrongNudge(Component c) {
        String s = c.getString().toLowerCase(Locale.ROOT);
        return s.contains("incorrect") || s.contains("wrong") || (s.contains("try again") && !s.contains("➤"));
    }

    private void advanceCandidate(long now) {
        String[] cands = candidates;
        int idx = candidateIndex + 1;
        candidateIndex = idx;
        advanceDeadline = 0;
        if (cands != null && idx < cands.length) {
            extend(now);
            queue(new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, cands[idx]), now);
        }
    }

    public void onMapData(byte[] colors, long now) {
        if (!armed(now) || colors == null || colors.length != CaptchaMapImage.SIZE * CaptchaMapImage.SIZE) return;
        long hash = fnv1a(colors);
        if (solvingHash == hash || hash == challengeHash) return;

        challengeHash = hash;
        candidateIndex = 0;
        candidates = null;
        submittedWasNudged = false;
        timeoutAdvanced = false;
        submittedText = null;
        extend(now);

        String cached = IMAGE_CACHE.get(hash);
        if (cached != null) {

            candidates = new String[]{cached};
            queue(new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, cached), now);
        }

        if (solvingHash != Long.MIN_VALUE) {
            pendingColors = colors;
            pendingHash = hash;
            pendingCachedFirst = cached;
            return;
        }
        solvingHash = hash;
        submitOcr(colors, hash, cached, now);
    }

    private void submitOcr(byte[] colors, long hash, String cachedFirst, long now) {
        worker.execute(() -> {
            try {
                CaptchaMapImage img = CaptchaMapImage.fromMapColors(colors);
                List<CaptchaMapOcr.OcrResult> ranked = CaptchaMapOcr.detectAndSolveRanked(img, MAX_CANDIDATES);
                List<String> list = new ArrayList<>();
                if (cachedFirst != null) list.add(cachedFirst);
                for (int i = 0; i < ranked.size(); i++) {
                    CaptchaMapOcr.OcrResult r = ranked.get(i);
                    if (!r.ok()) continue;

                    boolean isBest = list.isEmpty();
                    if (!isBest && r.confidence() < MAP_CONFIDENCE) continue;
                    if (!list.contains(r.text())) list.add(r.text());
                }
                if (!list.isEmpty() && hash == challengeHash) {
                    candidates = list.toArray(new String[0]);
                    extend(now);

                    if (cachedFirst == null) {
                        int idx = Math.min(candidateIndex, candidates.length - 1);
                        queue(new CaptchaChatSolver.Answer(CaptchaChatSolver.Kind.CHAT, candidates[idx]), now);
                    }
                }
            } catch (Throwable ignored) {

            } finally {
                drainNextOcr(hash);
            }
        });
    }

    private void drainNextOcr(long finishedHash) {
        byte[] next = pendingColors;
        long nextHash = pendingHash;
        String nextCached = pendingCachedFirst;
        pendingColors = null;
        pendingHash = 0;
        pendingCachedFirst = null;
        if (next != null && nextHash == challengeHash && armed(System.currentTimeMillis())) {
            solvingHash = nextHash;
            submitOcr(next, nextHash, nextCached, System.currentTimeMillis());
        } else if (solvingHash == finishedHash) {
            solvingHash = Long.MIN_VALUE;
        }
    }

    public void onTitle(Component component, long now) {
        if (component == null) return;
        String s = component.getString().toLowerCase(Locale.ROOT);
        if (s.contains("captcha") || s.contains("code") || s.contains("verif")) extend(now);
    }

    private void queue(CaptchaChatSolver.Answer answer, long now) {

        if (answer.text().equals(lastAnswer) && now - lastAnswerAt < RESEND_GUARD_MS) return;
        pending = answer;
        sendAt = now + MIN_DELAY_MS + (long) (random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
    }

    public void tick(long now) {

        String[] cands = candidates;
        if (advanceDeadline != 0 && now >= advanceDeadline && armed(now)
            && cands != null && candidateIndex + 1 < cands.length) {
            timeoutAdvanced = true;
            advanceCandidate(now);
        }

        CaptchaChatSolver.Answer answer = pending;
        if (answer == null || now < sendAt) return;
        pending = null;
        lastAnswer = answer.text();
        lastAnswerAt = now;
        try {
            if (answer.kind() == CaptchaChatSolver.Kind.COMMAND) {
                host.sendCaptchaCommand(answer.text());
            } else {
                host.sendCaptchaChat(answer.text());
            }

            if (challengeHash != 0) {
                submittedText = answer.text();
                submittedWasNudged = false;
                advanceDeadline = now + ADVANCE_TIMEOUT_MS;
            }
        } catch (Throwable ignored) {
        }
    }

    private static long fnv1a(byte[] data) {
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h ^= (b & 0xFF);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
