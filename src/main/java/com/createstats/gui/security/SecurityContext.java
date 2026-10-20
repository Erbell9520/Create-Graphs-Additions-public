package com.createstats.gui.security;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.network.SecurityActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.security.Clearance;
import com.createstats.security.SecurityRole;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// client greys out, server decides
@OnlyIn(Dist.CLIENT)
public final class SecurityContext {
    public final HomeSecurityPanelBE panel;
    public final Font font;

    private SecuritySyncPacket view;

    private int syncs;

    public BlockPos selectedDevice;

    public UUID selectedPerson;

    public BlockPos cellDevice;
    public UUID cellPerson;

    public SecurityContext(HomeSecurityPanelBE panel, Font font) {
        this.panel = panel;
        this.font = font;

        this.view = new SecuritySyncPacket(panel.getBlockPos(), false, true, 3, "",
                SecurityRole.MEMBER.ordinal(),
                com.createstats.security.SecurityMode.HOME.ordinal(), false, "", false, false, "",
                true, false, false, 1, 0, "", List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), 0, 0, List.of(), true);
    }

    public SecuritySyncPacket view() {
        return this.view;
    }

    public void accept(SecuritySyncPacket packet) {
        this.view = packet;
        this.syncs++;

        if (this.selectedDevice != null && device(this.selectedDevice) == null) {
            this.selectedDevice = null;
        }
        if (this.selectedPerson != null && person(this.selectedPerson) == null) {
            this.selectedPerson = null;
        }
        if (this.cellDevice != null && device(this.cellDevice) == null) {
            this.cellDevice = null;
            this.cellPerson = null;
        }
    }

    public List<SecuritySyncPacket.Row> devices() {
        return this.view.devices();
    }

    public List<SecuritySyncPacket.Person> people() {
        return this.view.people();
    }

    public List<SecuritySyncPacket.Online> online() {
        return this.view.online();
    }

    public List<SecuritySyncPacket.Line> log() {
        return this.view.log();
    }

    public List<SecuritySyncPacket.Row> devicesOf(com.createstats.power.SecurityDevice.DeviceClass cls) {
        final List<SecuritySyncPacket.Row> out = new ArrayList<>();
        for (SecuritySyncPacket.Row r : this.view.devices()) {
            if (r.classOf() == cls) {
                out.add(r);
            }
        }
        return out;
    }

    public SecuritySyncPacket.Row device(BlockPos pos) {
        for (SecuritySyncPacket.Row r : this.view.devices()) {
            if (r.pos().equals(pos)) {
                return r;
            }
        }
        return null;
    }

    public int syncCount() {
        return this.syncs;
    }

    public List<SecuritySyncPacket.RelayRow> relays() {
        return this.view.relays();
    }

    public SecuritySyncPacket.RelayRow relay(BlockPos pos) {
        for (SecuritySyncPacket.RelayRow r : this.view.relays()) {
            if (r.pos().equals(pos)) {
                return r;
            }
        }
        return null;
    }

    public List<SecuritySyncPacket.CoilRow> coils() {
        return this.view.coils();
    }

    public SecuritySyncPacket.CoilRow coil(BlockPos pos) {
        for (SecuritySyncPacket.CoilRow c : this.view.coils()) {
            if (c.pos().equals(pos)) {
                return c;
            }
        }
        return null;
    }

    public com.createstats.stormcoil.CoilRules.HouseState houseState() {
        return com.createstats.stormcoil.CoilRules.HouseState.of(this.view.modeOf(), this.view.lockdown());
    }

    public boolean pvp() {
        return this.view.pvp();
    }

    public int hiddenRows() {
        return this.view.hiddenRows();
    }

    public int legacyUnresolved() {
        return this.view.legacyUnresolved();
    }

    public SecuritySyncPacket.Person person(UUID id) {
        for (SecuritySyncPacket.Person p : this.view.people()) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public SecuritySyncPacket.Row selectedOr(com.createstats.power.SecurityDevice.DeviceClass cls) {
        if (this.selectedDevice != null) {
            final SecuritySyncPacket.Row r = device(this.selectedDevice);
            if (r != null && r.classOf() == cls) {
                return r;
            }
        }
        final List<SecuritySyncPacket.Row> rows = devicesOf(cls);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public SecuritySyncPacket.Person selectedPersonOr() {
        if (this.selectedPerson != null) {
            final SecuritySyncPacket.Person p = person(this.selectedPerson);
            if (p != null) {
                return p;
            }
        }
        return this.view.people().isEmpty() ? null : this.view.people().get(0);
    }

    public boolean passes(SecuritySyncPacket.Person person, SecuritySyncPacket.Row device) {
        return person.isOwner() || person.level() >= device.required();
    }

    public boolean hasFault() {
        for (SecuritySyncPacket.Row row : this.view.devices()) {
            if (row.stateOf().isFault() || blind(row) || silent(row) || coilFault(row)) {
                return true;
            }
        }
        return false;
    }

    public static boolean silent(SecuritySyncPacket.Row row) {
        return row.isRepeller()
                && row.stateOf() == com.createstats.power.SecurityDevice.DeviceState.UNPOWERED;
    }

    public static boolean coilFault(SecuritySyncPacket.Row row) {
        final com.createstats.power.SecurityDevice.DeviceState s = row.stateOf();
        return row.isCoil() && (s == com.createstats.power.SecurityDevice.DeviceState.UNPOWERED
                || s == com.createstats.power.SecurityDevice.DeviceState.DAMAGED);
    }

    public static boolean blind(SecuritySyncPacket.Row row) {
        return row.isSensor()
                && row.stateOf() == com.createstats.power.SecurityDevice.DeviceState.UNPOWERED;
    }

    public int gatedDeviceCount() {
        int n = 0;
        for (SecuritySyncPacket.Row row : this.view.devices()) {
            if (row.clearanceGated()) {
                n++;
            }
        }
        return n;
    }

    public SecurityRole role() {
        return this.view.viewerRoleOf();
    }

    public boolean mayEditDevices() {
        return role().mayEditDevices();
    }

    public boolean mayEditRoster() {
        return role().mayEditRoster();
    }

    public boolean mayEditPanel() {
        return role().mayEditPanel();
    }

    public boolean isOwner() {
        return role() == SecurityRole.OWNER;
    }

    public boolean houseSealed() {
        return mode().sealsHouse();
    }

    public boolean hasPassword() {
        return this.view.hasPassword();
    }

    public String levelName(int level) {
        return Clearance.plain(level);
    }

    public void send(SecurityActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public void setLevel(BlockPos device, int level) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(), SecurityActionPacket.SET_LEVEL,
                device, level));
    }

    public void addPlayer(String name) {
        send(SecurityActionPacket.panel(this.panel.getBlockPos(), SecurityActionPacket.ADD_PLAYER, 0,
                name));
    }

    public void setPersonLevel(UUID id, int level) {
        send(SecurityActionPacket.person(this.panel.getBlockPos(),
                SecurityActionPacket.SET_PLAYER_LEVEL, id, level));
    }

    public void setPersonRole(UUID id, SecurityRole role) {
        send(SecurityActionPacket.person(this.panel.getBlockPos(),
                SecurityActionPacket.SET_PLAYER_ROLE, id, role.ordinal()));
    }

    public com.createstats.security.SecurityMode mode() {
        return this.view.modeOf();
    }

    public boolean lockedDown() {
        return this.view.lockdown();
    }

    public boolean released() {
        return this.view.released();
    }

    public boolean hazard() {
        return this.view.hazard();
    }

    public String hazardCause() {
        return this.view.hazardCause();
    }

    public String lockdownCause() {
        return this.view.lockdownCause();
    }

    public void setMode(com.createstats.security.SecurityMode wanted) {
        send(SecurityActionPacket.panel(this.panel.getBlockPos(), SecurityActionPacket.SET_MODE,
                wanted.ordinal(), ""));
    }

    public void clearLockdown() {
        send(SecurityActionPacket.panel(this.panel.getBlockPos(),
                SecurityActionPacket.CLEAR_LOCKDOWN, 0, ""));
    }

    public void setEntrance(BlockPos device) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_ENTRANCE, device, 0));
    }

    public void setSensorArmed(BlockPos device, boolean armed) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_SENSOR_ARMED, device, armed ? 1 : 0));
    }

    public void setSensorRange(BlockPos device, int blocks) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_SENSOR_RANGE, device, blocks));
    }

    public void setDoorRange(BlockPos device, int blocks) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_DOOR_RANGE, device, blocks));
    }

    public void setShutterSealed(BlockPos device, boolean sealed) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SEAL_SHUTTER, device, sealed ? 1 : 0));
    }

    public void setShutterDusk(BlockPos device, boolean dusk) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_SHUTTER_DUSK, device, dusk ? 1 : 0));
    }

    public void sealAllShutters(boolean sealed) {
        send(SecurityActionPacket.panel(this.panel.getBlockPos(),
                SecurityActionPacket.SEAL_ALL_SHUTTERS, sealed ? 1 : 0, ""));
    }

    public void setLockerCode(BlockPos device, String digits) {
        send(new SecurityActionPacket(this.panel.getBlockPos(), SecurityActionPacket.SET_LOCKER_CODE,
                device, java.util.Optional.empty(), 0, digits));
    }

    public void setRepellerOn(BlockPos device, boolean on) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_REPELLER_ON, device, on ? 1 : 0));
    }

    public void setRepellerLocked(BlockPos device, boolean locked) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_REPELLER_LOCK, device, locked ? 1 : 0));
    }

    public void setRepellerRedstone(BlockPos device, boolean hears) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SET_REPELLER_REDSTONE, device, hears ? 1 : 0));
    }

    public void tuneRepeller(BlockPos device, String station, int range) {
        send(new SecurityActionPacket(this.panel.getBlockPos(), SecurityActionPacket.TUNE_REPELLER,
                device, java.util.Optional.empty(), range, station));
    }

    public void switchAllRepellers(boolean on) {
        send(SecurityActionPacket.panel(this.panel.getBlockPos(),
                SecurityActionPacket.SWITCH_ALL_REPELLERS, on ? 1 : 0, ""));
    }

    public void setCoilOn(BlockPos device, boolean on) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(), SecurityActionPacket.SET_COIL_ON, device,
                on ? 1 : 0));
    }

    public void setCoilLocked(BlockPos device, boolean locked) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(), SecurityActionPacket.SET_COIL_LOCK, device,
                locked ? 1 : 0));
    }

    public void setCoilRedstone(BlockPos device, boolean hears) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(), SecurityActionPacket.SET_COIL_REDSTONE, device,
                hears ? 1 : 0));
    }

    public void setCoilRule(BlockPos device, com.createstats.stormcoil.CoilRules.HouseState state,
                            com.createstats.stormcoil.CoilRules.Rule rule) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(), SecurityActionPacket.SET_COIL_RULE, device,
                com.createstats.stormcoil.CoilRules.edit(state, rule)));
    }

    public void switchAllCoils(boolean on) {
        send(SecurityActionPacket.panel(this.panel.getBlockPos(), SecurityActionPacket.SWITCH_ALL_COILS,
                on ? 1 : 0, ""));
    }

    public void surveyRepeller(BlockPos device) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(),
                SecurityActionPacket.SURVEY_REPELLER, device, 0));
    }

    public void forgetDevice(BlockPos device) {
        send(SecurityActionPacket.device(this.panel.getBlockPos(), SecurityActionPacket.FORGET_DEVICE,
                device, 0));
    }

    public void configureRelay(BlockPos relay, com.createstats.security.relay.RelayConfig wanted,
                               BlockPos scope, long revision) {
        PacketDistributor.sendToServer(com.createstats.network.SecurityRelayActionPacket.configure(
                this.panel.getBlockPos(), true, relay, wanted, scope, revision));
    }

    public void relayAction(BlockPos relay, int action) {
        PacketDistributor.sendToServer(com.createstats.network.SecurityRelayActionPacket.simple(
                this.panel.getBlockPos(), true, relay, action));
    }

    public void poll() {
        send(SecurityActionPacket.refresh(this.panel.getBlockPos()));
    }
}
