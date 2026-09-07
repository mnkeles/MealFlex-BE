package com.mealflex.notification.service;

import com.mealflex.user.entity.User;

/** Provider-neutral boundary for external notification vendors. */
public interface NotificationTransport {
    boolean isConfigured(String channel);

    void send(String channel, User recipient, String title, String body, String deepLink);
}
