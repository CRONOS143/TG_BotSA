package org.example.informsa_bot.bot;

import java.time.LocalDate;

public class Employee {
    private final String fullName;
    private final LocalDate birthDate;
    private final String department;
    private final String Post;
    private final String mobilePhone;
    private final String mail;
    private final String telegramPhone;

    public Employee(String fullName, LocalDate birthDate, String department, String Post, String mobilePhone, String mail, String telegramPhone) {
        this.fullName = fullName;
        this.birthDate = birthDate;
        this.department = department;
        this.Post = Post;
        this.mobilePhone = mobilePhone;
        this.mail = mail;

        this.telegramPhone = telegramPhone;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getDepartment() {
        return department;
    }

    public String getPost() {
        return Post;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public String getMail() {
        return mail;
    }

    public String getTelegramPhone() {
        return telegramPhone;
    }

    @Override
    public String toString() {
        return fullName + " (" + department + ") Тел: " + mobilePhone;
    }
}