package org.example.informsa_bot.Config;

import java.io.*;
import java.time.LocalTime;
import java.util.Properties;

public class SettingsManager {
    private final Properties props = new Properties();
    private final File file = new File("config/settings.properties");

    public SettingsManager() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                props.setProperty("weatherTime", "08:00");
                props.setProperty("birthdayTime", "09:00");
                save();
            } else {
                try (FileReader reader = new FileReader(file)) {
                    props.load(reader);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getApiKey() {
        return "c7a5c5701ef4f61980c345a96a0c7cf3";
    }

    public String getCity() {
        return "Kyiv";
    }

    public LocalTime getWeatherTime() {
        return LocalTime.parse(props.getProperty("weatherTime", "07:30"));
    }

    public void setWeatherTime(LocalTime time) {
        props.setProperty("weatherTime", time.toString());
        save();
    }

    public LocalTime getBirthdayTime() {
        return LocalTime.parse(props.getProperty("birthdayTime", "9:00"));
    }

    public void setBirthdayTime(LocalTime time) {
        props.setProperty("birthdayTime", time.toString());
        save();
    }

    private void save() {
        try (FileWriter writer = new FileWriter(file)) {
            props.store(writer, "Bot settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
