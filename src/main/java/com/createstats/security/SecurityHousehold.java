package com.createstats.security;

import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayCodec;
import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayReading;
import com.createstats.security.relay.RelayRules;
import com.createstats.security.relay.RelayState;
import com.createstats.security.relay.RelaySubscription;
import com.createstats.stormcoil.CoilRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// one household per house, not per panel. the back door needed a second panel
public final class SecurityHousehold {
    public static final int RELEASE_HOLD = 200;

    public static final int HAZARD_HOLD = RELEASE_HOLD;

    public static final int MAX_HOUSE = 24;

    public static final int MAX_DEVICE_NAME = 48;

    public static final int ADMISSION_TICKS = 60;

    public static final int SCHEMA = 2;

    public static final int SIGHTING_FRESH_TICKS = 100;

    public static final int MAX_ENROLLMENTS = 2048;
    public static final int MAX_RELAYS = 256;

    public static final int RECENT_EVENTS = 32;

    private static final int UNSET = -1;

    private final UUID id;

    private final SecurityRoster roster = new SecurityRoster();
    private final SecurityLog log = new SecurityLog();

    private final EndpointTable<Integer> levels = new EndpointTable<>(UNSET);

    private final EndpointTable<String> names = new EndpointTable<>("");

    private final EndpointTable<Boolean> armed = new EndpointTable<>(Boolean.TRUE);

    private final EndpointTable<Integer> reach = new EndpointTable<>(-1);

    private final EndpointTable<Integer> radii = new EndpointTable<>(UNSET);

    // sealing by choice is not a lockdown, don't merge them
    private final EndpointTable<Boolean> sealedBanks = new EndpointTable<>(Boolean.FALSE);

    private final EndpointTable<Boolean> duskBanks = new EndpointTable<>(Boolean.FALSE);

    private final EndpointTable<Boolean> repellersOn = new EndpointTable<>(Boolean.TRUE);

    private final EndpointTable<Boolean> repellersLocked = new EndpointTable<>(Boolean.FALSE);

    private final EndpointTable<Boolean> repellersHear = new EndpointTable<>(Boolean.TRUE);

    private final EndpointTable<Integer> coilRules = new EndpointTable<>(UNSET);

    private final EndpointTable<Boolean> coilsOn = new EndpointTable<>(Boolean.TRUE);

    private final EndpointTable<Boolean> coilsLocked = new EndpointTable<>(Boolean.FALSE);

    private final EndpointTable<Boolean> coilsHear = new EndpointTable<>(Boolean.TRUE);

    @Nullable
    private SecurityEndpoint entrance;

    private boolean failSecure = true;

    private int proximity = 3;

    private SecurityMode mode = SecurityMode.HOME;

    private boolean lockdown;

    private long lockdownAt;

    private String lockdownCause = "";

    // timestamp so the fire release lapses on its own. nobody should have to walk to the panel because the house is on fire
    private long fireAt = Long.MIN_VALUE;

    private long hazardAt = Long.MIN_VALUE;

    private String hazardCause = "";

    private String house = "";

    // self healing like SensorWatch
    private final Set<SecurityEndpoint> panels = new HashSet<>();

    private final LockerCode password = new LockerCode();

    private boolean alwaysAsk;

    private boolean configured;

    private final Map<UUID, Long> admittedUntil = new HashMap<>();

    private final Map<SecurityEndpoint, SecurityEnrollment> enrollments = new HashMap<>();

    private final Map<SecurityEndpoint, SecurityEndpoint> doorways = new HashMap<>();

    private final Set<SecurityEndpoint> released = new HashSet<>();

    private long eventSequence;

    private final Map<UUID, RelaySubscription> relays = new LinkedHashMap<>();

    private final Map<SecurityEndpoint, Sighting> sightings = new HashMap<>();

    private final HazardWatch hazards = new HazardWatch();

    private final Deque<SecurityEvent> recentEvents = new ArrayDeque<>();

    private long lastAdvance = Long.MIN_VALUE;

    private boolean dropped;

    private Runnable onChange = () -> {
    };

    public record Sighting(String kind, Presence presence, boolean breached, boolean impaired, long seenAt) {
    }

    public SecurityHousehold(UUID id) {
        this.id = id;

        this.roster.onChange(() -> dirty());
        this.log.onChange(() -> dirty());
    }

    public UUID id() {
        return this.id;
    }

    public void onChange(Runnable dirty) {
        this.onChange = dirty == null ? () -> {
        } : dirty;
    }

    private void dirty() {
        this.onChange.run();
    }

    public boolean dropped() {
        return this.dropped;
    }

    void markDropped() {
        this.dropped = true;
    }

    public SecurityRoster roster() {
        return this.roster;
    }

    public SecurityRoster rosterCopy() {
        return this.roster.copy();
    }

    public SecurityLog log() {
        return this.log;
    }

    @Nullable
    public String provenDimension() {
        String found = null;
        for (SecurityEndpoint p : this.panels) {
            if (p.isLegacy()) {
                return null;
            }
            if (found == null) {
                found = p.dimension();
            } else if (!found.equals(p.dimension())) {
                return null;
            }
        }
        return found;
    }

    private boolean settled() {
        return provenDimension() != null;
    }

    public boolean settleLegacy() {
        final String dim = provenDimension();
        if (dim == null || !hasLegacy()) {
            return false;
        }
        boolean moved = false;
        for (EndpointTable<?> t : tables()) {
            moved |= t.settle(dim);
        }
        if (this.entrance != null && this.entrance.isLegacy()) {
            this.entrance = this.entrance.in(dim);
            moved = true;
        }
        if (moved) {
            dirty();
        }
        return moved;
    }

    public int legacyUnresolved() {
        int n = 0;
        for (EndpointTable<?> t : tables()) {
            n += t.legacyCount();
        }
        if (this.entrance != null && this.entrance.isLegacy()) {
            n++;
        }
        return n;
    }

    private boolean hasLegacy() {
        for (EndpointTable<?> t : tables()) {
            if (t.hasLegacy()) {
                return true;
            }
        }
        return this.entrance != null && this.entrance.isLegacy();
    }

    private List<EndpointTable<?>> tables() {
        return List.of(this.levels, this.names, this.armed, this.reach, this.radii, this.sealedBanks,
                this.duskBanks, this.repellersOn, this.repellersLocked, this.repellersHear,
                this.coilRules, this.coilsOn, this.coilsLocked, this.coilsHear);
    }

    public int requiredLevel(SecurityEndpoint anchor) {
        final int set = this.levels.get(anchor);
        return Clearance.clamp(set == UNSET ? Clearance.DEFAULT_REQUIRED : set);
    }

    public void setRequiredLevel(SecurityEndpoint anchor, int level) {
        if (this.levels.set(anchor, Clearance.clamp(level), settled())) {
            dirty();
        }
    }

    public void clearRequiredLevel(SecurityEndpoint anchor) {
        if (this.levels.holds(anchor) && this.levels.set(anchor, UNSET, settled())) {
            dirty();
        }
    }

    public String nameOf(SecurityEndpoint anchor, String fallback) {
        final String custom = this.names.get(anchor);
        return !custom.isBlank() ? custom : fallback;
    }

    public boolean hasName(SecurityEndpoint anchor) {
        return !this.names.get(anchor).isBlank();
    }

    public boolean knows(SecurityEndpoint anchor) {
        for (EndpointTable<?> t : tables()) {
            if (t.holds(anchor)) {
                return true;
            }
        }
        return isEntrance(anchor);
    }

    public void setName(SecurityEndpoint anchor, String name) {
        final String clipped = name == null || name.isBlank() ? "" : SecurityLog.clip(name, MAX_DEVICE_NAME);
        if (this.names.set(anchor, clipped, settled())) {
            dirty();
        }
    }

    public boolean sensorFlag(SecurityEndpoint pos) {
        return this.armed.get(pos);
    }

    public void setSensorFlag(SecurityEndpoint pos, boolean value) {
        if (this.armed.set(pos, value, settled())) {
            dirty();
        }
    }

    public int sensorRadius(SecurityEndpoint pos, int kindDefault) {
        final int set = this.radii.get(pos);
        return SensorKind.clampRadius(set == UNSET ? kindDefault : set);
    }

    public void setSensorRadius(SecurityEndpoint pos, int blocks) {
        if (this.radii.set(pos, SensorKind.clampRadius(blocks), settled())) {
            dirty();
        }
    }

    public int proximityOverride(SecurityEndpoint anchor) {
        return this.reach.get(anchor);
    }

    public void setProximityOverride(SecurityEndpoint anchor, int blocks) {
        if (this.reach.set(anchor, blocks < 0 ? -1 : blocks, settled())) {
            dirty();
        }
    }

    public boolean shutterSealed(SecurityEndpoint anchor) {
        return this.sealedBanks.get(anchor);
    }

    public void setShutterSealed(SecurityEndpoint anchor, boolean sealed) {
        if (this.sealedBanks.set(anchor, sealed, settled())) {
            dirty();
        }
    }

    public boolean shutterDusk(SecurityEndpoint anchor) {
        return this.duskBanks.get(anchor);
    }

    public void setShutterDusk(SecurityEndpoint anchor, boolean dusk) {
        if (this.duskBanks.set(anchor, dusk, settled())) {
            dirty();
        }
    }

    public boolean repellerOn(SecurityEndpoint pos) {
        return this.repellersOn.get(pos);
    }

    public void setRepellerOn(SecurityEndpoint pos, boolean on) {
        if (this.repellersOn.set(pos, on, settled())) {
            dirty();
        }
    }

    public boolean repellerLocked(SecurityEndpoint pos) {
        return this.repellersLocked.get(pos);
    }

    public void setRepellerLocked(SecurityEndpoint pos, boolean locked) {
        if (this.repellersLocked.set(pos, locked, settled())) {
            dirty();
        }
    }

    public boolean repellerHearsRedstone(SecurityEndpoint pos) {
        return this.repellersHear.get(pos);
    }

    public void setRepellerHearsRedstone(SecurityEndpoint pos, boolean hears) {
        if (this.repellersHear.set(pos, hears, settled())) {
            dirty();
        }
    }

    @Nullable
    public CoilRules.Grid coilRules(SecurityEndpoint pos) {
        final int bits = this.coilRules.get(pos);
        return bits == UNSET ? null : new CoilRules.Grid(bits);
    }

    public boolean hasCoilRules(SecurityEndpoint pos) {
        return this.coilRules.get(pos) != UNSET;
    }

    public void setCoilRules(SecurityEndpoint pos, CoilRules.Grid grid) {
        if (grid != null && this.coilRules.set(pos, grid.bits(), settled())) {
            dirty();
        }
    }

    public boolean coilOn(SecurityEndpoint pos) {
        return this.coilsOn.get(pos);
    }

    public void setCoilOn(SecurityEndpoint pos, boolean on) {
        if (this.coilsOn.set(pos, on, settled())) {
            dirty();
        }
    }

    public boolean coilLocked(SecurityEndpoint pos) {
        return this.coilsLocked.get(pos);
    }

    public void setCoilLocked(SecurityEndpoint pos, boolean locked) {
        if (this.coilsLocked.set(pos, locked, settled())) {
            dirty();
        }
    }

    public boolean coilHearsRedstone(SecurityEndpoint pos) {
        return this.coilsHear.get(pos);
    }

    public void setCoilHearsRedstone(SecurityEndpoint pos, boolean hears) {
        if (this.coilsHear.set(pos, hears, settled())) {
            dirty();
        }
    }

    public boolean hasEntrance() {
        return this.entrance != null;
    }

    @Nullable
    public SecurityEndpoint entrance() {
        return this.entrance;
    }

    public boolean isEntrance(SecurityEndpoint anchor) {
        if (this.entrance == null || anchor == null) {
            return false;
        }
        return this.entrance.equals(anchor)
                || (this.entrance.isLegacy() && this.entrance.pos() == anchor.pos());
    }

    public void setEntrance(SecurityEndpoint anchor) {
        this.entrance = anchor;
        dirty();
    }

    public void clearEntrance() {
        this.entrance = null;
        dirty();
    }

    public boolean failSecure() {
        return this.failSecure;
    }

    public void setFailSecure(boolean value) {
        this.failSecure = value;
        dirty();
    }

    public int proximity() {
        return this.proximity;
    }

    public void setProximity(int blocks) {
        this.proximity = blocks;
        dirty();
    }

    public SecurityMode mode() {
        return this.mode;
    }

    public void setMode(SecurityMode wanted) {
        if (wanted != null) {
            this.mode = wanted;
            dirty();
        }
    }

    public String house() {
        return this.house;
    }

    public void setHouse(String name) {
        this.house = name == null ? "" : SecurityLog.clip(name, MAX_HOUSE);
        dirty();
    }

    public boolean lockdown() {
        return this.lockdown;
    }

    public long lockdownAt() {
        return this.lockdownAt;
    }

    public String lockdownCause() {
        return this.lockdownCause;
    }

    public void raiseLockdown(long now, String cause) {
        if (this.lockdown) {
            return;
        }
        this.lockdown = true;
        this.lockdownAt = now;
        this.lockdownCause = SecurityLog.clip(cause, SecurityLog.MAX_DETAIL);
        dirty();
    }

    public void clearLockdown() {
        this.lockdown = false;
        this.lockdownCause = "";
        dirty();
    }

    public void reportFire(long now) {
        this.fireAt = now;
        dirty();
    }

    public boolean released(long now) {
        return this.fireAt != Long.MIN_VALUE && now - this.fireAt < RELEASE_HOLD;
    }

    public void reportHazard(long now, String cause) {
        this.hazardAt = now;
        this.hazardCause = SecurityLog.clip(cause, SecurityLog.MAX_DETAIL);
        dirty();
    }

    public boolean hazard(long now) {
        return this.hazardAt != Long.MIN_VALUE && now - this.hazardAt < HAZARD_HOLD;
    }

    public String hazardCause(long now) {
        return hazard(now) ? this.hazardCause : "";
    }

    public HazardWatch hazards() {
        return this.hazards;
    }

    public Set<SecurityEndpoint> panels() {
        return Set.copyOf(this.panels);
    }

    public void addPanel(SecurityEndpoint at) {
        boolean changed = this.panels.add(at);
        if (!at.isLegacy()) {
            changed |= this.panels.remove(SecurityEndpoint.legacy(at.pos()));
        }
        if (changed) {
            dirty();
        }
    }

    public boolean removePanel(SecurityEndpoint at) {
        if (this.panels.remove(at)) {
            dirty();
            return true;
        }
        return false;
    }

    public boolean hasPassword() {
        return this.password.isSet();
    }

    public boolean setPassword(String digits) {
        if (!this.password.set(digits)) {
            return false;
        }
        dirty();
        return true;
    }

    public void clearPassword() {
        this.password.clear();
        dirty();
    }

    public boolean alwaysAsk() {
        return this.alwaysAsk;
    }

    public boolean configured() {
        return this.configured;
    }

    public void setConfigured(boolean value) {
        this.configured = value;
        dirty();
    }

    public void setAlwaysAsk(boolean value) {
        this.alwaysAsk = value;
        dirty();
    }

    public LockerCode.Result offerPassword(String typed, long now) {
        final LockerCode.Result result = this.password.offer(typed, now);
        dirty();
        return result;
    }

    public int passwordStrikes() {
        return this.password.strikes();
    }

    public int passwordLockout(long now) {
        return this.password.lockoutRemaining(now);
    }

    public void admit(UUID player, long now) {
        if (player != null) {
            this.admittedUntil.put(player, now + ADMISSION_TICKS);
        }
    }

    public boolean admitted(UUID player, long now) {
        final Long until = player == null ? null : this.admittedUntil.get(player);
        return until != null && now < until;
    }

    public void forgetAdmission(UUID player) {
        if (player != null) {
            this.admittedUntil.remove(player);
        }
    }

    public void refreshAdmission(UUID player, long now) {
        if (admitted(player, now)) {
            this.admittedUntil.put(player, now + ADMISSION_TICKS);
        }
    }

    public boolean enroll(SecurityEndpoint anchor, String kind, long now) {
        final SecurityEnrollment had = this.enrollments.get(anchor);
        if (had == null && this.enrollments.size() >= MAX_ENROLLMENTS) {
            return false;
        }
        final SecurityEnrollment next = had == null || !had.kind().equals(kind)
                ? new SecurityEnrollment(kind, now, SecurityEnrollment.NEVER)
                : had.present();
        boolean changed = !next.equals(had);
        changed |= this.released.remove(anchor);
        if (changed) {
            this.enrollments.put(anchor, next);
            dirty();
        }
        return true;
    }

    public void enrollLeaf(SecurityEndpoint leaf, SecurityEndpoint doorway, String kind, long now) {
        if (leaf.equals(doorway) || !enroll(leaf, kind, now)) {
            return;
        }
        if (!doorway.equals(this.doorways.put(leaf, doorway))) {
            dirty();
        }
    }

    public SecurityEndpoint doorwayOf(SecurityEndpoint at) {
        return this.doorways.getOrDefault(at, at);
    }

    public boolean isEnrolled(SecurityEndpoint anchor) {
        return this.enrollments.containsKey(anchor);
    }

    @Nullable
    public SecurityEnrollment enrollment(SecurityEndpoint anchor) {
        return this.enrollments.get(anchor);
    }

    public Map<SecurityEndpoint, SecurityEnrollment> enrollments() {
        return Map.copyOf(this.enrollments);
    }

    public boolean markMissing(SecurityEndpoint anchor, long now) {
        final SecurityEnrollment had = this.enrollments.get(anchor);
        if (had != null && !had.missing()) {
            this.enrollments.put(anchor, had.markedMissing(now));
            dirty();
            return true;
        }
        return false;
    }

    public boolean retire(SecurityEndpoint anchor, UUID actor, String actorName, long now) {
        if (!this.roster.roleOf(actor).mayEditDevices() || !this.enrollments.containsKey(anchor)) {
            return false;
        }
        final SecurityEnrollment gone = this.enrollments.remove(anchor);
        this.released.add(anchor);
        this.sightings.remove(anchor);

        this.doorways.remove(anchor);
        this.doorways.values().removeIf(anchor::equals);
        this.log.add(now, SecurityLog.clip(actorName, SecurityLog.MAX_WHO), "device",
                "retired a " + gone.kind() + (gone.missing() ? " that was missing" : ""),
                SecurityLog.Verdict.CHANGED);
        dirty();
        return true;
    }

    public boolean released(SecurityEndpoint anchor) {
        return this.released.contains(anchor);
    }

    public boolean requiredForMonitoring(SecurityEndpoint anchor, String kind) {
        if (!SecurityKinds.monitors(kind)) {
            return false;
        }
        if (SecurityKinds.COIL.equals(kind)) {
            return coilOn(anchor);
        }
        return !SecurityKinds.REPELLER.equals(kind) || repellerOn(anchor);
    }

    public void sight(SecurityEndpoint anchor, String kind, Presence presence, boolean breached,
                      boolean impaired, long now) {
        this.sightings.put(anchor, new Sighting(kind, presence, breached, impaired, now));
    }

    @Nullable
    public Sighting sighting(SecurityEndpoint anchor) {
        return this.sightings.get(anchor);
    }

    public Presence presence(SecurityEndpoint anchor, long now) {
        final SecurityEnrollment e = this.enrollments.get(anchor);
        if (e != null && e.missing()) {
            return Presence.MISSING;
        }
        final Sighting s = this.sightings.get(anchor);
        if (s == null || now - s.seenAt() > SIGHTING_FRESH_TICKS || now < s.seenAt()) {
            return Presence.STALE;
        }
        return s.presence();
    }

    public SecuritySituations.Facts facts(long now) {
        final Map<SecurityEndpoint, SecuritySituations.Watched> devices = new HashMap<>();
        for (Map.Entry<SecurityEndpoint, SecurityEnrollment> e : this.enrollments.entrySet()) {
            final SecurityEndpoint at = e.getKey();
            final String kind = e.getValue().kind();
            final Presence p = presence(at, now);
            final Sighting s = this.sightings.get(at);
            final boolean breached = p.available() && s != null && s.breached();
            final boolean impaired = p.available() && s != null && s.impaired();
            devices.put(at, new SecuritySituations.Watched(at, kind, p, breached, impaired,
                    requiredForMonitoring(at, kind), doorwayOf(at)));
        }
        return new SecuritySituations.Facts(this.lockdown, this.mode.key(), now, devices, this.hazards);
    }

    public long eventSequence() {
        return this.eventSequence;
    }

    public SecurityEvent acceptEvent(String kind, SecurityEndpoint source, long now) {
        final SecurityEvent event = new SecurityEvent(++this.eventSequence, kind, source, now);
        this.recentEvents.addFirst(event);
        while (this.recentEvents.size() > RECENT_EVENTS) {
            this.recentEvents.removeLast();
        }
        for (RelaySubscription sub : this.relays.values()) {
            if (!sub.supported()) {
                continue;
            }
            final SecurityEndpoint scope = sub.config().scope();
            final boolean inScope = scope == null || scope.equals(source);
            sub.setState(RelayRules.onEvent(sub.config(), sub.state(), event, inScope));
        }
        dirty();
        return event;
    }

    public List<SecurityEvent> recentEvents() {
        return List.copyOf(this.recentEvents);
    }

    @Nullable
    public RelaySubscription relay(UUID relayId) {
        return relayId == null ? null : this.relays.get(relayId);
    }

    public List<RelaySubscription> relays() {
        return List.copyOf(this.relays.values());
    }

    @Nullable
    public RelaySubscription claimRelay(UUID relayId, SecurityEndpoint at, UUID actor, String actorName,
                                        long now) {
        if (relayId == null || at == null || !this.roster.roleOf(actor).mayEditDevices()) {
            return null;
        }
        final RelaySubscription had = this.relays.get(relayId);
        if (had != null) {
            if (!had.endpoint().equals(at)) {
                return null;
            }
            return settleRelayPlace(relayId, at, now);
        }
        final RelaySubscription left = keepOldestRelayAt(at, now);
        if (left != null) {
            final RelaySubscription taken = rekeyRelay(left, relayId);
            enroll(at, SecurityKinds.RELAY, now);
            this.log.add(now, SecurityLog.clip(actorName, SecurityLog.MAX_WHO), "relay",
                    "put back a Security Relay, " + describe(taken.config()), SecurityLog.Verdict.CHANGED);
            dirty();
            return taken;
        }
        if (this.relays.size() >= MAX_RELAYS) {
            return null;
        }
        final RelaySubscription sub = new RelaySubscription(relayId, at, RelayConfig.DEFAULT, 0L,
                RelayState.seed(this.eventSequence), now, null);
        this.relays.put(relayId, sub);
        enroll(at, SecurityKinds.RELAY, now);
        this.log.add(now, SecurityLog.clip(actorName, SecurityLog.MAX_WHO), "relay", "added a Security Relay",
                SecurityLog.Verdict.CHANGED);
        dirty();
        return sub;
    }

    @Nullable
    public RelaySubscription relayStandingAt(UUID relayId, SecurityEndpoint at, long now) {
        final RelaySubscription own = relayId == null ? null : this.relays.get(relayId);
        if (own == null || at == null || !own.endpoint().equals(at)) {
            return own;
        }
        return settleRelayPlace(relayId, at, now);
    }

    private RelaySubscription rekeyRelay(RelaySubscription from, UUID relayId) {
        Object unsupported = from.unsupportedTag();
        if (unsupported instanceof CompoundTag raw) {
            final CompoundTag copy = raw.copy();
            copy.putUUID("Relay", relayId);
            unsupported = copy;
        }
        final RelaySubscription to = new RelaySubscription(relayId, from.endpoint(), from.config(),
                from.revision(), from.state(), from.registeredAt(), unsupported);
        to.setReading(from.reading());
        this.relays.remove(from.relayId());
        this.relays.put(relayId, to);
        return to;
    }

    private RelaySubscription settleRelayPlace(UUID relayId, SecurityEndpoint at, long now) {
        final RelaySubscription keep = keepOldestRelayAt(at, now);
        if (keep != null && !keep.relayId().equals(relayId)) {
            rekeyRelay(keep, relayId);
        }
        return this.relays.get(relayId);
    }

    @Nullable
    private RelaySubscription keepOldestRelayAt(SecurityEndpoint at, long now) {
        RelaySubscription keep = null;
        final List<RelaySubscription> extra = new ArrayList<>(0);
        for (RelaySubscription s : this.relays.values()) {
            if (!s.endpoint().equals(at)) {
                continue;
            }
            if (keep == null) {
                keep = s;
            } else {
                extra.add(s);
            }
        }
        for (RelaySubscription s : extra) {
            this.relays.remove(s.relayId());
            this.log.add(now, "panel", "relay", s.state().pending()
                            ? "dropped a duplicate relay record holding an unresolved " + conditionLabel(s.config())
                            : "dropped a duplicate relay record, " + describe(s.config()),
                    s.state().pending() ? SecurityLog.Verdict.FAULT : SecurityLog.Verdict.CHANGED);
        }
        if (!extra.isEmpty()) {
            dirty();
        }
        return keep;
    }

    public RelayRules.ConfigVerdict configureRelay(UUID relayId, RelayConfig wanted, long expectedRevision,
                                                   UUID actor, String actorName, long now) {
        final RelaySubscription sub = this.relays.get(relayId);
        if (sub == null || wanted == null) {
            return RelayRules.ConfigVerdict.FORBIDDEN;
        }
        final RelayCondition target = wanted.condition();
        final Predicate<SecurityEndpoint> scopeOk = device -> {
            final SecurityEnrollment e = this.enrollments.get(device);
            return target != null && e != null && target.acceptsDevice(e.kind());
        };
        final Predicate<String> modeOk = key -> SecurityMode.byKey(key) != null;
        final RelayRules.ConfigVerdict verdict = RelayRules.mayConfigure(sub.config(), sub.state(),
                sub.revision(), wanted, expectedRevision, this.roster.roleOf(actor).mayEditDevices(),
                scopeOk, modeOk);
        if (!verdict.applied()) {
            return verdict;
        }
        sub.apply(wanted, RelayState.seed(this.eventSequence));
        sub.setReading(RelayReading.NONE);
        this.log.add(now, SecurityLog.clip(actorName, SecurityLog.MAX_WHO), "relay",
                "set to " + describe(wanted), SecurityLog.Verdict.CHANGED);
        dirty();
        return verdict;
    }

    public RelayRules.ResetVerdict resetRelay(UUID relayId, UUID actor, String actorName,
                                              boolean contactLinked, long now) {
        final RelaySubscription sub = this.relays.get(relayId);
        if (sub == null) {
            return RelayRules.ResetVerdict.FORBIDDEN;
        }

        advance(now);
        final RelayRules.ResetVerdict verdict = RelayRules.mayReset(sub.config(), sub.state(),
                sub.reading().truth(), contactLinked, this.roster.roleOf(actor).mayEditDevices());
        if (!verdict.done()) {
            return verdict;
        }
        sub.setState(sub.state().withPending(false));
        this.log.add(now, SecurityLog.clip(actorName, SecurityLog.MAX_WHO), "relay",
                "reset a held " + conditionLabel(sub.config()), SecurityLog.Verdict.CHANGED);
        dirty();
        return verdict;
    }

    public RelayRules.RetireVerdict retireRelay(UUID relayId, UUID actor, String actorName, long now) {
        final RelaySubscription sub = this.relays.get(relayId);
        if (sub == null) {
            return RelayRules.RetireVerdict.FORBIDDEN;
        }
        final SecurityRole role = this.roster.roleOf(actor);
        final RelayRules.RetireVerdict verdict = RelayRules.mayRetire(sub.state(), role.mayEditDevices(),
                role.isOwner());
        if (verdict != RelayRules.RetireVerdict.RETIRED) {
            return verdict;
        }
        this.relays.remove(relayId);
        this.enrollments.remove(sub.endpoint());
        this.released.add(sub.endpoint());
        this.log.add(now, SecurityLog.clip(actorName, SecurityLog.MAX_WHO), "relay",
                sub.state().pending()
                        ? "decommissioned with an unresolved " + conditionLabel(sub.config())
                        : "retired a Security Relay",
                sub.state().pending() ? SecurityLog.Verdict.FAULT : SecurityLog.Verdict.CHANGED);
        dirty();
        return verdict;
    }

    public void advance(long now) {
        if (now == this.lastAdvance) {
            return;
        }
        this.lastAdvance = now;
        if (this.relays.isEmpty()) {
            return;
        }
        final SecuritySituations.Facts facts = facts(now);
        boolean changed = false;
        for (RelaySubscription sub : this.relays.values()) {
            if (!sub.supported()) {
                sub.setReading(RelayReading.NONE);
                continue;
            }
            final RelayReading reading = SecuritySituations.read(sub.config(), facts);
            sub.setReading(reading);
            final RelayState next = RelayRules.onReading(sub.config(), sub.state(), reading.truth(), now);
            if (next != sub.state()) {
                changed |= RelayRules.savedFieldsDiffer(sub.state(), next);
                sub.setState(next);
            }
        }
        if (changed) {
            dirty();
        }
    }

    private static String describe(RelayConfig c) {
        final StringBuilder out = new StringBuilder(conditionLabel(c)).append(' ').append(c.behavior().key());
        if (c.behavior() == RelayBehavior.PULSE) {
            out.append(' ').append(c.pulseTicks()).append('t');
        }
        return out.toString();
    }

    private static String conditionLabel(RelayConfig c) {
        final RelayCondition cond = c.condition();
        return cond == null ? "unsupported condition" : cond.langKey();
    }

    public boolean isEmpty() {
        if (this.configured) {
            return false;
        }

        for (EndpointTable<?> t : tables()) {
            if (!t.isEmpty()) {
                return false;
            }
        }
        return this.entrance == null && !this.password.isSet() && this.enrollments.isEmpty()
                && this.relays.isEmpty();
    }

    public void prune(String dimension, Predicate<Long> dead) {
        final boolean settled = settled();
        boolean touched = false;
        for (EndpointTable<?> t : tables()) {
            touched |= t.prune(dimension, dead, settled);
        }
        if (this.entrance != null
                && (this.entrance.inDimension(dimension) || (settled && this.entrance.isLegacy()))
                && dead.test(this.entrance.pos())) {
            this.entrance = null;
            touched = true;
        }
        if (touched) {
            dirty();
        }
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putInt("Schema", SCHEMA);
        final ListTag panelList = new ListTag();
        this.panels.stream().sorted().forEach(p -> panelList.add(endpointTag(p)));
        tag.put("PanelEndpoints", panelList);
        writePolicy(tag);
        writeMembership(tag);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.panels.clear();
        if (tag.contains("PanelEndpoints", Tag.TAG_LIST)) {
            final ListTag list = tag.getList("PanelEndpoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                final SecurityEndpoint e = endpointOf(list.getCompound(i));
                if (e != null) {
                    this.panels.add(e);
                }
            }
        }

        for (long key : tag.getLongArray("Panels")) {
            this.panels.add(SecurityEndpoint.legacy(key));
        }
        readPolicy(tag);
        readMembership(tag);
    }

    public void loadLegacyPanel(CompoundTag panelTag, @Nullable String dimension) {
        readPolicy(panelTag);

        this.configured = true;
        if (dimension != null && !dimension.isEmpty()) {
            for (EndpointTable<?> t : tables()) {
                t.settle(dimension);
            }
            if (this.entrance != null && this.entrance.isLegacy()) {
                this.entrance = this.entrance.in(dimension);
            }
        }
    }

    public void loadLegacyPanel(CompoundTag panelTag) {
        loadLegacyPanel(panelTag, null);
    }

    private void writePolicy(CompoundTag tag) {
        tag.putBoolean("FailSecure", this.failSecure);
        tag.putInt("Proximity", this.proximity);
        tag.putInt("Mode", this.mode.ordinal());
        tag.putBoolean("Lockdown", this.lockdown);
        tag.putLong("LockdownAt", this.lockdownAt);
        tag.putString("LockdownCause", this.lockdownCause);
        tag.putLong("FireAt", this.fireAt);
        tag.putLong("HazardAt", this.hazardAt);
        tag.putString("HazardCause", this.hazardCause);
        if (this.entrance != null) {
            tag.put("EntranceAt", endpointTag(this.entrance));
        }
        tag.putString("House", this.house);
        tag.put("Roster", this.roster.save());
        tag.put("Log", this.log.save());
        tag.put("Password", this.password.save());
        tag.putBoolean("AlwaysAsk", this.alwaysAsk);
        tag.putBoolean("Configured", this.configured);

        final CompoundTag policy = new CompoundTag();
        policy.put("Levels", this.levels.save((row, v) -> row.putInt("V", v)));
        policy.put("Names", this.names.save((row, v) -> row.putString("V", v)));
        policy.put("Armed", this.armed.save((row, v) -> row.putBoolean("V", v)));
        policy.put("Reach", this.reach.save((row, v) -> row.putInt("V", v)));
        policy.put("Radii", this.radii.save((row, v) -> row.putInt("V", v)));
        policy.put("Sealed", this.sealedBanks.save((row, v) -> row.putBoolean("V", v)));
        policy.put("Dusk", this.duskBanks.save((row, v) -> row.putBoolean("V", v)));
        policy.put("RepellerOn", this.repellersOn.save((row, v) -> row.putBoolean("V", v)));
        policy.put("RepellerLocked", this.repellersLocked.save((row, v) -> row.putBoolean("V", v)));
        policy.put("RepellerHears", this.repellersHear.save((row, v) -> row.putBoolean("V", v)));
        policy.put("CoilRules", this.coilRules.save((row, v) -> row.putInt("V", v)));
        policy.put("CoilOn", this.coilsOn.save((row, v) -> row.putBoolean("V", v)));
        policy.put("CoilLocked", this.coilsLocked.save((row, v) -> row.putBoolean("V", v)));
        policy.put("CoilHears", this.coilsHear.save((row, v) -> row.putBoolean("V", v)));
        tag.put("Devices", policy);
    }

    private void readPolicy(CompoundTag tag) {
        this.failSecure = !tag.contains("FailSecure") || tag.getBoolean("FailSecure");
        this.proximity = tag.contains("Proximity") ? tag.getInt("Proximity") : 3;
        this.mode = tag.contains("Mode") ? SecurityMode.byOrdinal(tag.getInt("Mode")) : SecurityMode.HOME;
        this.lockdown = tag.getBoolean("Lockdown");
        this.lockdownAt = tag.getLong("LockdownAt");
        this.lockdownCause = SecurityLog.clip(tag.getString("LockdownCause"), SecurityLog.MAX_DETAIL);
        this.fireAt = tag.contains("FireAt") ? tag.getLong("FireAt") : Long.MIN_VALUE;
        this.hazardAt = tag.contains("HazardAt") ? tag.getLong("HazardAt") : Long.MIN_VALUE;
        this.hazardCause = SecurityLog.clip(tag.getString("HazardCause"), SecurityLog.MAX_DETAIL);
        this.house = SecurityLog.clip(tag.getString("House"), MAX_HOUSE);
        this.roster.load(tag.getCompound("Roster"));
        this.log.load(tag.getList("Log", SecurityLog.listType()));
        this.password.load(tag.getCompound("Password"));
        this.alwaysAsk = tag.getBoolean("AlwaysAsk");
        this.configured = tag.getBoolean("Configured");

        if (tag.contains("EntranceAt", Tag.TAG_COMPOUND)) {
            this.entrance = endpointOf(tag.getCompound("EntranceAt"));
        } else if (tag.contains("Entrance")) {
            this.entrance = SecurityEndpoint.legacy(tag.getLong("Entrance"));
        } else {
            this.entrance = null;
        }

        if (tag.contains("Devices", Tag.TAG_COMPOUND)) {
            final CompoundTag policy = tag.getCompound("Devices");
            this.levels.load(list(policy, "Levels"), row -> row.getInt("V"));
            this.names.load(list(policy, "Names"), row -> row.getString("V"));
            this.armed.load(list(policy, "Armed"), row -> row.getBoolean("V"));
            this.reach.load(list(policy, "Reach"), row -> row.getInt("V"));
            this.radii.load(list(policy, "Radii"), row -> row.getInt("V"));
            this.sealedBanks.load(list(policy, "Sealed"), row -> row.getBoolean("V"));
            this.duskBanks.load(list(policy, "Dusk"), row -> row.getBoolean("V"));
            this.repellersOn.load(list(policy, "RepellerOn"), row -> row.getBoolean("V"));
            this.repellersLocked.load(list(policy, "RepellerLocked"), row -> row.getBoolean("V"));
            this.repellersHear.load(list(policy, "RepellerHears"), row -> row.getBoolean("V"));
            this.coilRules.load(list(policy, "CoilRules"), row -> row.getInt("V"));
            this.coilsOn.load(list(policy, "CoilOn"), row -> row.getBoolean("V"));
            this.coilsLocked.load(list(policy, "CoilLocked"), row -> row.getBoolean("V"));
            this.coilsHear.load(list(policy, "CoilHears"), row -> row.getBoolean("V"));
            return;
        }
        readLegacyPolicy(tag);
    }

    private void readLegacyPolicy(CompoundTag tag) {
        for (EndpointTable<?> t : tables()) {
            t.clear();
        }
        readLegacyInts(tag, "Levels", "L", this.levels);
        readLegacyInts(tag, "Reach", "R", this.reach);
        readLegacyInts(tag, "Radii", "R", this.radii);

        final ListTag nameList = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < nameList.size(); i++) {
            final CompoundTag row = nameList.getCompound(i);
            final String n = row.getString("N");
            if (!n.isBlank()) {
                this.names.load(SecurityEndpoint.legacy(row.getLong("P")), n);
            }
        }
        final ListTag disarmedList = tag.getList("Disarmed", Tag.TAG_COMPOUND);
        for (int i = 0; i < disarmedList.size(); i++) {
            this.armed.load(SecurityEndpoint.legacy(disarmedList.getCompound(i).getLong("P")), Boolean.FALSE);
        }
        readLegacyFlags(tag, "SealedBanks", this.sealedBanks, Boolean.TRUE);
        readLegacyFlags(tag, "DuskBanks", this.duskBanks, Boolean.TRUE);
        readLegacyFlags(tag, "RepellersOff", this.repellersOn, Boolean.FALSE);
        readLegacyFlags(tag, "RepellersLocked", this.repellersLocked, Boolean.TRUE);
        readLegacyFlags(tag, "RepellersDeaf", this.repellersHear, Boolean.FALSE);
    }

    private static void readLegacyInts(CompoundTag tag, String listKey, String valueKey,
                                       EndpointTable<Integer> into) {
        final ListTag list = tag.getList(listKey, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag row = list.getCompound(i);
            into.load(SecurityEndpoint.legacy(row.getLong("P")), row.getInt(valueKey));
        }
    }

    private static void readLegacyFlags(CompoundTag tag, String key, EndpointTable<Boolean> into, Boolean value) {
        for (long pos : tag.getLongArray(key)) {
            into.load(SecurityEndpoint.legacy(pos), value);
        }
    }

    private void writeMembership(CompoundTag tag) {
        final ListTag enrolled = new ListTag();
        this.enrollments.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            final CompoundTag row = endpointTag(e.getKey());
            row.putString("K", e.getValue().kind());
            row.putLong("At", e.getValue().enrolledAt());
            if (e.getValue().missing()) {
                row.putLong("Miss", e.getValue().missingSince());
            }
            final SecurityEndpoint doorway = this.doorways.get(e.getKey());
            if (doorway != null) {
                row.putLong("Door", doorway.pos());
            }
            enrolled.add(row);
        });
        tag.put("Enrolled", enrolled);

        final ListTag let = new ListTag();
        this.released.stream().sorted().forEach(e -> let.add(endpointTag(e)));
        tag.put("Released", let);

        tag.putLong("EventSeq", this.eventSequence);
        final ListTag relayList = new ListTag();
        for (RelaySubscription sub : this.relays.values()) {
            relayList.add(RelayCodec.save(sub));
        }
        tag.put("Relays", relayList);
    }

    private void readMembership(CompoundTag tag) {
        this.enrollments.clear();
        this.doorways.clear();
        final ListTag enrolled = tag.getList("Enrolled", Tag.TAG_COMPOUND);
        for (int i = 0; i < enrolled.size() && this.enrollments.size() < MAX_ENROLLMENTS; i++) {
            final CompoundTag row = enrolled.getCompound(i);
            final SecurityEndpoint at = endpointOf(row);
            if (at == null || at.isLegacy()) {
                continue;
            }
            this.enrollments.put(at, new SecurityEnrollment(row.getString("K"), row.getLong("At"),
                    row.contains("Miss") ? row.getLong("Miss") : SecurityEnrollment.NEVER));

            if (row.contains("Door") && row.getLong("Door") != at.pos()) {
                this.doorways.put(at, SecurityEndpoint.of(at.dimension(), row.getLong("Door")));
            }
        }
        this.released.clear();
        final ListTag let = tag.getList("Released", Tag.TAG_COMPOUND);
        for (int i = 0; i < let.size(); i++) {
            final SecurityEndpoint at = endpointOf(let.getCompound(i));
            if (at != null) {
                this.released.add(at);
            }
        }
        this.eventSequence = Math.max(0L, tag.getLong("EventSeq"));
        this.relays.clear();
        final ListTag relayList = tag.getList("Relays", Tag.TAG_COMPOUND);
        for (int i = 0; i < relayList.size() && this.relays.size() < MAX_RELAYS; i++) {
            final RelaySubscription sub = RelayCodec.load(relayList.getCompound(i));
            if (sub != null && !this.relays.containsKey(sub.relayId())) {
                this.relays.put(sub.relayId(), sub);
            }
        }
    }

    private static ListTag list(CompoundTag tag, String key) {
        return tag.getList(key, Tag.TAG_COMPOUND);
    }

    private static CompoundTag endpointTag(SecurityEndpoint e) {
        final CompoundTag row = new CompoundTag();
        if (!e.isLegacy()) {
            row.putString("D", e.dimension());
        }
        row.putLong("P", e.pos());
        return row;
    }

    @Nullable
    private static SecurityEndpoint endpointOf(CompoundTag row) {
        if (!row.contains("P", Tag.TAG_LONG)) {
            return null;
        }
        return row.contains("D", Tag.TAG_STRING)
                ? SecurityEndpoint.of(row.getString("D"), row.getLong("P"))
                : SecurityEndpoint.legacy(row.getLong("P"));
    }
}
