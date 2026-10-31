package org.bitwisemadness.tensuraquests.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's personal task counters and partner sets, persisted as
 * a NeoForge player attachment (ADR-0002, FR-008).
 *
 * <p>Keyed by the FTB Quests task id: each task instance owns one
 * counter slot per player, so the same personal value is visible in
 * every team the player belongs to - it is their progress, not the
 * team's. Holding nothing team-shaped is what makes membership churn
 * unable to copy or erase counters (SEC-005). Distinct-partner tasks
 * (FR-042) additionally keep a per-task set of counterparty UUIDs
 * here: the counter store is the one per-player persistence surface,
 * so distinctness rides it instead of a second store.</p>
 *
 * <p>The codec is versioned: this is a persistence surface (ADR-0002
 * consequence). Version {@code 1} was counters-only; version
 * {@link #CURRENT_VERSION} adds the optional {@code partners} map -
 * the first additive change the version field was introduced for:
 * a v1 save decodes with empty partner sets, no rewrite required, and
 * a corrupt partner entry is skipped rather than thrown so one bad
 * value cannot reset a player's whole store.</p>
 */
public final class PersonalCounterStore {

    /** Layout version written by this build (1 = counters only). */
    public static final int CURRENT_VERSION = 2;

    /**
     * Task ids as decimal strings: NBT compounds key by string, so this
     * is the natural on-disk shape. A corrupt key is skipped rather than
     * thrown so one bad entry cannot reset a player's whole store.
     */
    private static final Codec<Map<Long, Long>> COUNTERS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
        .xmap(PersonalCounterStore::decodeCounters, PersonalCounterStore::encodeCounters);

    /** Partner sets per task: uuid strings, corrupt entries skipped the same way. */
    private static final Codec<Map<Long, Set<UUID>>> PARTNERS_CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.list(Codec.STRING))
            .xmap(PersonalCounterStore::decodePartners, PersonalCounterStore::encodePartners);

    /** The attachment codec: {@code {version: int, counters, partners?}}. */
    public static final Codec<PersonalCounterStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(store -> store.version),
        COUNTERS_CODEC.fieldOf("counters").forGetter(store -> store.counters),
        PARTNERS_CODEC.optionalFieldOf("partners", Map.of()).forGetter(store -> store.partners)
    ).apply(instance, PersonalCounterStore::new));

    private final int version;
    private final Map<Long, Long> counters;
    private final Map<Long, Set<UUID>> partners;

    private PersonalCounterStore(int version, Map<Long, Long> counters, Map<Long, Set<UUID>> partners) {
        this.version = version;
        this.counters = counters;
        this.partners = partners;
    }

    /** A fresh store at the current layout version. */
    public static PersonalCounterStore empty() {
        return new PersonalCounterStore(CURRENT_VERSION, new HashMap<>(), new HashMap<>());
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
     * Record a counterparty for a distinct-partner task (FR-042).
     *
     * @param taskId the quest-file unique task id
     * @param partner the counterparty's player id
     * @return true when this partner was new for this player and task
     *         (the caller grants progress only then)
     */
    public boolean addPartner(long taskId, UUID partner) {
        return partners.computeIfAbsent(taskId, id -> new HashSet<>()).add(partner);
    }

    /**
     * Whether a counterparty is already recorded for a task.
     *
     * @param taskId the quest-file unique task id
     * @param partner the counterparty's player id
     * @return true when previously recorded
     */
    public boolean hasPartner(long taskId, UUID partner) {
        return partners.getOrDefault(taskId, Set.of()).contains(partner);
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

    private static Map<Long, Set<UUID>> decodePartners(Map<String, List<String>> raw) {
        Map<Long, Set<UUID>> decoded = new HashMap<>();
        raw.forEach((key, uuids) -> {
            long taskId;
            try {
                taskId = Long.parseLong(key);
            } catch (NumberFormatException ignored) {
                return; // Corrupt task key: skip this entry only.
            }
            Set<UUID> partners = new HashSet<>();
            for (String uuid : uuids) {
                try {
                    partners.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                    // Corrupt partner: skip it, keep the rest.
                }
            }
            if (!partners.isEmpty()) {
                decoded.put(taskId, partners);
            }
        });
        return decoded;
    }

    private static Map<String, List<String>> encodePartners(Map<Long, Set<UUID>> partners) {
        Map<String, List<String>> encoded = new HashMap<>();
        partners.forEach((taskId, uuids) -> {
            List<String> values = uuids.stream().map(UUID::toString).sorted().toList();
            if (!values.isEmpty()) {
                encoded.put(Long.toString(taskId), values);
            }
        });
        return encoded;
    }
}
