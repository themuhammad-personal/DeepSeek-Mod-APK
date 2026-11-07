package com.themuhammad.deepseekmod.extension;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Measures the response observer without recording any assistant content.
 *
 * The metrics exist for the device test: callback frequency, duplicate events,
 * whether the observed text is the complete accumulated answer, and which thread
 * the callbacks arrive on. Only lengths, timings and a salted digest are kept, so
 * the diagnostics can be shown on a screen without leaking the conversation.
 *
 * The digest is salted with a random value that is created once per process, so a
 * digest cannot be used to look up a known piece of text.
 */
public final class SdsObserverDiagnostics {
    public static final int MAX_EVENTS = 64;
    public static final int MAX_THREADS = 16;
    private static final int DIGEST_CHARS = 16;

    private static final SdsObserverDiagnostics INSTANCE = new SdsObserverDiagnostics();

    private final Object lock = new Object();
    private final byte[] salt = newSalt();
    private final Set<String> seenDigests = new LinkedHashSet<>();
    private final List<String> threads = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();

    private long callbacks;
    private long emptyCallbacks;
    private long duplicateCallbacks;
    private long growthEvents;
    private long shrinkEvents;
    private long sameLengthEvents;
    private long distinctContents;
    private int lastContentChars = -1;
    private int maxContentChars;
    private long firstCallbackAtMs = -1;
    private long lastCallbackAtMs = -1;

    private SdsObserverDiagnostics() {
    }

    public static SdsObserverDiagnostics get() {
        return INSTANCE;
    }

    /** Records one observed response. The content itself is never stored. */
    public void onResponse(String content) {
        if (content == null) return;
        int length = content.length();
        String digest = digest(content);
        long now = System.currentTimeMillis();
        synchronized (lock) {
            callbacks++;
            if (length == 0) {
                emptyCallbacks++;
            }
            if (firstCallbackAtMs < 0) firstCallbackAtMs = now;
            lastCallbackAtMs = now;
            if (length > maxContentChars) maxContentChars = length;

            if (lastContentChars >= 0) {
                if (length > lastContentChars) {
                    growthEvents++;
                    addEvent(now, "grow", length);
                } else if (length < lastContentChars) {
                    shrinkEvents++;
                    addEvent(now, "shrink", length);
                } else {
                    sameLengthEvents++;
                    addEvent(now, "same", length);
                }
            } else {
                addEvent(now, "first", length);
            }
            lastContentChars = length;

            if (!seenDigests.contains(digest)) {
                seenDigests.add(digest);
                distinctContents++;
            } else {
                duplicateCallbacks++;
            }
            String thread = Thread.currentThread().getName();
            if (!threads.contains(thread) && threads.size() < MAX_THREADS) {
                threads.add(thread);
            }
        }
    }

    /** Clears every metric. Used when a new session starts. */
    public void reset() {
        synchronized (lock) {
            callbacks = 0;
            emptyCallbacks = 0;
            duplicateCallbacks = 0;
            growthEvents = 0;
            shrinkEvents = 0;
            sameLengthEvents = 0;
            distinctContents = 0;
            lastContentChars = -1;
            maxContentChars = 0;
            firstCallbackAtMs = -1;
            lastCallbackAtMs = -1;
            seenDigests.clear();
            threads.clear();
            events.clear();
        }
    }

    public long getCallbacks() { synchronized (lock) { return callbacks; } }
    public long getEmptyCallbacks() { synchronized (lock) { return emptyCallbacks; } }
    public long getDuplicateCallbacks() { synchronized (lock) { return duplicateCallbacks; } }
    public long getGrowthEvents() { synchronized (lock) { return growthEvents; } }
    public long getShrinkEvents() { synchronized (lock) { return shrinkEvents; } }
    public long getSameLengthEvents() { synchronized (lock) { return sameLengthEvents; } }
    public long getDistinctContents() { synchronized (lock) { return distinctContents; } }
    public int getLastContentChars() { synchronized (lock) { return lastContentChars; } }
    public int getMaxContentChars() { synchronized (lock) { return maxContentChars; } }
    public long getFirstCallbackAtMs() { synchronized (lock) { return firstCallbackAtMs; } }
    public long getLastCallbackAtMs() { synchronized (lock) { return lastCallbackAtMs; } }

    public List<String> getThreads() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(threads));
        }
    }

    public List<Event> getEvents() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    /** A short, content-free summary for the console. */
    public String summary() {
        synchronized (lock) {
            StringBuilder builder = new StringBuilder();
            builder.append("callbacks ").append(callbacks)
                    .append(" | distinct ").append(distinctContents)
                    .append(" | duplicates ").append(duplicateCallbacks)
                    .append(" | grow ").append(growthEvents)
                    .append(" | shrink ").append(shrinkEvents)
                    .append(" | same ").append(sameLengthEvents)
                    .append('\n');
            builder.append("last ").append(lastContentChars)
                    .append(" chars | max ").append(maxContentChars)
                    .append(" chars | threads ").append(threads.size())
                    .append('\n');
            builder.append("no assistant content is stored by the diagnostics");
            return builder.toString();
        }
    }

    private void addEvent(long atMs, String kind, int chars) {
        events.add(new Event(atMs, kind, chars));
        while (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
    }

    private String digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hash = digest.digest(content.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(DIGEST_CHARS);
            for (int i = 0; i < hash.length && hex.length() < DIGEST_CHARS; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException error) {
            // SHA-256 and UTF-8 are always present; this only keeps the class total.
            return "len:" + content.length();
        }
    }

    private static byte[] newSalt() {
        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);
        return salt;
    }

    /** One recorded observer event. It carries no content, only a length. */
    public static final class Event {
        private final long atMs;
        private final String kind;
        private final int contentChars;

        Event(long atMs, String kind, int contentChars) {
            this.atMs = atMs;
            this.kind = kind;
            this.contentChars = contentChars;
        }

        public long getAtMs() { return atMs; }
        public String getKind() { return kind; }
        public int getContentChars() { return contentChars; }
    }
}
