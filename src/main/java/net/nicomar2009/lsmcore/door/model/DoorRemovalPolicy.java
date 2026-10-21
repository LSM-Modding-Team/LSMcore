package net.nicomar2009.lsmcore.door.model;

/** Persisted owner drop rule. Creative/failed placement never drop. ANY_BREAK also covers
 * confirmed integrity loss during recovery; choose PLAYER_ONLY if those losses must not drop.
 * Protection still authorizes the entire retirement; no vanilla per-piece drops are permitted.
 */
public enum DoorRemovalPolicy {
    PLAYER_ONLY, ANY_BREAK, NO_DROPS;
    public boolean drops(DoorRecord.Removal reason) {
        return this!=NO_DROPS && (reason==DoorRecord.Removal.PLAYER
                || this==ANY_BREAK && reason==DoorRecord.Removal.INTEGRITY_LOSS);
    }
}
