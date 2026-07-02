package com.ansh.lyfegameserver.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Per-user preferences controlling the AFK / idle automation behaviour.
 */
@Getter @Setter
public class UserSettings {

    /** When true, pending wages are auto-claimed when the player returns. */
    private boolean autoClaimWages;

    /** When true, auto-claimed wages are auto-invested into the government bond. */
    private boolean autoReinvest;

    public UserSettings() {
        this.autoClaimWages = false;
        this.autoReinvest = false;
    }
}
