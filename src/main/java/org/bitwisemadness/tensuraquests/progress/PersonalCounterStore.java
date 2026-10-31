package org.bitwisemadness.tensuraquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * One player's personal task counters, persisted as a NeoForge player
 * attachment (ADR-0002, FR-008).
 *
 * <p>Keyed by the FTB Quests task id: each task instance owns one
 * counter slot per player, so the same personal value is visible in
 * every team the player belongs to - it is their progress, not the
 * team's. Holding nothing team-shaped is what makes membership churn
 * unable to copy or erase counters (SEC-005).</p>
 *
 * <p>The codec is versioned from day one: this is a persistence
 * surface (ADR-0002 consequence). Version {@link #CURRENT_VERSION} is
 * the initial layout; a future migration reads the stored version,
 * rewrites the map and writes the current version back, so migrations
 * stay additive.</p>
 */
public final class PersonalCounterStore {

    /** Layout version written by this build. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Task ids as decimal strings: NBT compounds key by string, so this
     * is the natural on-disk shape. A corrupt key is skipped rather than
     * thrown so one bad entry cannot reset a player's whole store.
     */
    private static final Codec<Map<Long, Long>> COUNTERS_CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
            PersonalCounterStore::decodeCounters,
            PersonalCounterStore::encodeCounters);

    /** The attachment codec: {@code {version: int, counters: {taskId: value}}}. */
    public static final Codec<PersonalCounterStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(store -> store.version),
        COUNTERS_CODEC.fieldOf("counters").forGetter(store -> store.counters)
    ).apply(instance, PersonalCounterStore::new));

    private final int version;
    private final Map<Long, Long> counters;

    private PersonalCounterStore(int version, Map<Long, Long> counters) {
        this.version = version;
        this.counters = counters;
    }

    /** A fresh store at the current layout version. */
    public static PersonalCounterStore empty() {
        return new PersonalCounterStore(CURRENT_VERSION, new HashMap<>());
    }

    /**
     * The player's counter for one task.
     *
     * @param taskId the quest-file unique task id
     * @return the stored value, 0 when the task was never touched
     */
    public long get(long taskId) {
        return counters.getOrDefault(taskId, 0L);
    }

    /**
     * Add to the player's counter for one task. Only ever called from
     * server-side validated intake; nothing client-supplied reaches it.
     *
     * @param taskId the quest-file unique task id
     * @param amount strictly positive increment
     */
    public void increment(long taskId, long amount) {
        counters.merge(taskId, amount, Long::sum);
    }

    /**
     * The stored layout version, kept so future migrations can branch
     * on what was actually read rather than what this build writes.
     *
     * @return the version the data was decoded with
     */
    public int version() {
        return version;
    }

    private static Map<Long, Long> decodeCounters(Map<String, Long> raw) {
        Map<Long, Long> decoded = new HashMap<>();
        raw.forEach((key, value) -> {
            try {
                decoded.put(Long.parseLong(key), value);
            } catch (NumberFormatException ignored) {
                // Corrupt key: keep the rest of the store rather than reset it.
            }
        });
        return decoded;
    }

    private static Map<String, Long> encodeCounters(Map<Long, Long> counters) {
        Map<String, Long> encoded = new HashMap<>();
        counters.forEach((taskId, value) -> encoded.put(Long.toString(taskId), value));
        return encoded;
    }
}
