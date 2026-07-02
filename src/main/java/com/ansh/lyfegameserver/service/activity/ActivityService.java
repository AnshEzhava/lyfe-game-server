package com.ansh.lyfegameserver.service.activity;

import com.ansh.lyfegameserver.data.ActivityEvent;
import com.ansh.lyfegameserver.data.Users;

/**
 * Helper for appending events to a user's recent-activity feed. Mutates the user in place;
 * callers are responsible for saving. Trims the feed to {@link Users#MAX_ACTIVITY_EVENTS}.
 */
public final class ActivityService {

    private ActivityService() {}

    public static void record(Users user, String type, long amount, String label) {
        if (user.getRecentActivity() == null) {
            user.setRecentActivity(new java.util.ArrayList<>());
        }
        user.getRecentActivity().add(new ActivityEvent(type, System.currentTimeMillis(), amount, label));
        int overflow = user.getRecentActivity().size() - Users.MAX_ACTIVITY_EVENTS;
        for (int i = 0; i < overflow; i++) {
            user.getRecentActivity().remove(0);
        }
    }
}
