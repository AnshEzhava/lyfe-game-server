package com.ansh.lyfegameserver.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single entry in a user's recent-activity feed. Records income and expense events
 * (wages, bond yield, tax, course completion, trades) so the client can show a running
 * history and the "while you were away" summary has human-readable highlights.
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {

    /** WAGE, BOND_YIELD, TAX, COURSE_COMPLETE, TRADE, IPO */
    private String type;

    /** Epoch ms when the event occurred. */
    private long at;

    /** Signed Branks delta: positive for income, negative for cost/tax. */
    private long amount;

    /** Human-readable label, e.g. "Bond yield" or "Master's completed (+30 INT)". */
    private String label;
}
