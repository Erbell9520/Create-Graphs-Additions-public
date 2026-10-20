package com.createstats.train.manifest;

// a finished order while typing must not rebase the draft
public final class ManifestDraftState {
    private int version;
    private long revision;
    private long sentRevision;
    private boolean dirty;
    private boolean pending;

    public ManifestDraftState(int version) {
        this.version = version;
    }

    public int version() { return this.version; }
    public boolean dirty() { return this.dirty; }
    public boolean pending() { return this.pending; }

    public void edit() {
        this.revision++;
        this.dirty = true;
    }

    public boolean beginSave() {
        if (this.pending) return false;
        this.sentRevision = this.revision;
        this.pending = true;
        return true;
    }

    public boolean receive(int incomingVersion, boolean saveReply, boolean accepted) {
        if (saveReply && this.pending) {
            this.pending = false;
            if (accepted) {
                this.version = incomingVersion;
                this.dirty = this.revision != this.sentRevision;
                return !this.dirty;
            }

            this.dirty = true;
            return false;
        }
        if (this.dirty || this.pending || incomingVersion == this.version) return false;
        this.version = incomingVersion;
        return true;
    }
}
