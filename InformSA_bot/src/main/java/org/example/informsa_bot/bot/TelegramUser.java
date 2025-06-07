package org.example.informsa_bot.bot;

public class TelegramUser {
    private final Long id;
    private final String username;

    public TelegramUser(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }


    @Override
    public String toString() {
        return "TelegramUser{id=" + id + ", username='" + username + "'}";
    }
}