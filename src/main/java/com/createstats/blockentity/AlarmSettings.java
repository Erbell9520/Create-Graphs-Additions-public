package com.createstats.blockentity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

public class AlarmSettings
{
    private AlarmSound sound;
    private AlarmParticle particle;
    private float volume;
    private float pitch;
    private int soundIntervalTicks;
    private int hearingRange;
    private int particleCount;
    private boolean screenAlert;
    private String alertMessage;
    private AlertVisibility soundVisibility;
    private AlertVisibility textVisibility;
    private int textRange;
    private boolean redstoneEnabled;
    private int scanRadius;
    
    public AlarmSettings() {
        this.sound = AlarmSound.BELL;
        this.particle = AlarmParticle.SMOKE;
        this.volume = 0.6f;
        this.pitch = 1.8f;
        this.soundIntervalTicks = 60;
        this.hearingRange = 32;
        this.particleCount = 3;
        this.screenAlert = true;
        this.alertMessage = "";
        this.soundVisibility = AlertVisibility.ALL_PLAYERS;
        this.textVisibility = AlertVisibility.NEARBY_ONLY;
        this.textRange = 48;
        this.redstoneEnabled = true;
        this.scanRadius = 1;
    }
    
    public AlarmSound getSound() {
        return this.sound;
    }
    
    public void setSound(AlarmSound s) {
        this.sound = s;
    }
    
    public void cycleSound() {
        this.sound = this.sound.next();
    }
    
    public AlarmParticle getParticle() {
        return this.particle;
    }
    
    public void setParticle(AlarmParticle p) {
        this.particle = p;
    }
    
    public void cycleParticle() {
        this.particle = this.particle.next();
    }
    
    public float getVolume() {
        return this.volume;
    }
    
    public void setVolume(float v) {
        this.volume = Math.max(0.0f, Math.min(1.0f, v));
    }
    
    public float getPitch() {
        return this.pitch;
    }
    
    public void setPitch(float p) {
        this.pitch = Math.max(0.5f, Math.min(2.0f, p));
    }
    
    public int getSoundIntervalTicks() {
        return this.soundIntervalTicks;
    }
    
    public void setSoundIntervalTicks(int t) {
        this.soundIntervalTicks = Math.max(20, Math.min(600, t));
    }
    
    public float getSoundIntervalSeconds() {
        return this.soundIntervalTicks / 20.0f;
    }
    
    public int getHearingRange() {
        return this.hearingRange;
    }
    
    public void setHearingRange(int r) {
        this.hearingRange = Math.max(8, Math.min(128, r));
    }
    
    public int getParticleCount() {
        return this.particleCount;
    }
    
    public void setParticleCount(int c) {
        this.particleCount = Math.max(0, Math.min(20, c));
    }
    
    public boolean isScreenAlert() {
        return this.screenAlert;
    }
    
    public void setScreenAlert(boolean a) {
        this.screenAlert = a;
    }
    
    public String getAlertMessage() {
        return this.alertMessage;
    }
    
    public void setAlertMessage(String m) {
        this.alertMessage = ((m != null) ? m : "");
    }
    
    public AlertVisibility getSoundVisibility() {
        return this.soundVisibility;
    }
    
    public void setSoundVisibility(AlertVisibility v) {
        this.soundVisibility = v;
    }
    
    public AlertVisibility getTextVisibility() {
        return this.textVisibility;
    }
    
    public void setTextVisibility(AlertVisibility v) {
        this.textVisibility = v;
    }
    
    public int getTextRange() {
        return this.textRange;
    }
    
    public void setTextRange(int r) {
        this.textRange = Math.max(8, Math.min(256, r));
    }
    
    public boolean isRedstoneEnabled() {
        return this.redstoneEnabled;
    }
    
    public void setRedstoneEnabled(boolean e) {
        this.redstoneEnabled = e;
    }
    
    public int getScanRadius() {
        return this.scanRadius;
    }
    
    public void setScanRadius(int r) {
        this.scanRadius = Math.max(1, Math.min(8, r));
    }
    
    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Sound", this.sound.name());
        tag.putString("Particle", this.particle.name());
        tag.putFloat("Volume", this.volume);
        tag.putFloat("Pitch", this.pitch);
        tag.putInt("SoundInterval", this.soundIntervalTicks);
        tag.putInt("HearRange", this.hearingRange);
        tag.putInt("ParticleCount", this.particleCount);
        tag.putBoolean("ScreenAlert", this.screenAlert);
        tag.putString("AlertMsg", this.alertMessage);
        tag.putString("SoundVis", this.soundVisibility.name());
        tag.putString("TextVis", this.textVisibility.name());
        tag.putInt("TextRange", this.textRange);
        tag.putBoolean("Redstone", this.redstoneEnabled);
        tag.putInt("ScanRadius", this.scanRadius);
        return tag;
    }
    
    public static AlarmSettings load(CompoundTag tag) {
        final AlarmSettings s = new AlarmSettings();
        try {
            s.sound = AlarmSound.valueOf(tag.getString("Sound"));
        }
        catch (Exception ex) {}
        try {
            s.particle = AlarmParticle.valueOf(tag.getString("Particle"));
        }
        catch (Exception ex2) {}
        if (tag.contains("Volume")) {
            s.volume = tag.getFloat("Volume");
        }
        if (tag.contains("Pitch")) {
            s.pitch = tag.getFloat("Pitch");
        }
        if (tag.contains("SoundInterval")) {
            s.soundIntervalTicks = tag.getInt("SoundInterval");
        }
        if (tag.contains("HearRange")) {
            s.hearingRange = tag.getInt("HearRange");
        }
        if (tag.contains("ParticleCount")) {
            s.particleCount = tag.getInt("ParticleCount");
        }
        if (tag.contains("ScreenAlert")) {
            s.screenAlert = tag.getBoolean("ScreenAlert");
        }
        if (tag.contains("AlertMsg")) {
            s.alertMessage = tag.getString("AlertMsg");
        }
        try {
            s.soundVisibility = AlertVisibility.valueOf(tag.getString("SoundVis"));
        }
        catch (Exception ex3) {}
        try {
            s.textVisibility = AlertVisibility.valueOf(tag.getString("TextVis"));
        }
        catch (Exception ex4) {}
        if (tag.contains("TextRange")) {
            s.textRange = tag.getInt("TextRange");
        }
        if (tag.contains("Redstone")) {
            s.redstoneEnabled = tag.getBoolean("Redstone");
        }
        if (tag.contains("ScanRadius")) {
            s.scanRadius = tag.getInt("ScanRadius");
        }
        return s;
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }
    
    public static AlarmSettings decode(FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return (tag != null) ? load(tag) : new AlarmSettings();
    }
    
    public enum AlarmSound
    {
        BELL("Bell"), 
        NOTE_PLING("Note Pling"), 
        ANVIL("Anvil Land"), 
        RAID_HORN("Raid Horn"), 
        WITHER_SPAWN("Wither Spawn"), 
        NONE("Silent");
        
        private final String displayName;
        
        private AlarmSound(String d) {
            this.displayName = d;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
        
        public AlarmSound next() {
            final AlarmSound[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
    
    public enum AlarmParticle
    {
        SMOKE("Smoke"), 
        FLAME("Flame"), 
        SOUL_FIRE("Soul Fire"), 
        REDSTONE("Redstone Dust"), 
        SPARK("Electric Spark"), 
        NONE("None");
        
        private final String displayName;
        
        private AlarmParticle(String d) {
            this.displayName = d;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
        
        public AlarmParticle next() {
            final AlarmParticle[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
    
    public enum AlertVisibility
    {
        ALL_PLAYERS("All Players"), 
        NEARBY_ONLY("Nearby Only"), 
        OWNER_ONLY("Owner Only"), 
        NOBODY("Nobody");
        
        private final String displayName;
        
        private AlertVisibility(String d) {
            this.displayName = d;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
        
        public AlertVisibility next() {
            final AlertVisibility[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
}
