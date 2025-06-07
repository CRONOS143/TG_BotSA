package org.example.informsa_bot.Config;


import org.example.informsa_bot.bot.*;
import org.example.informsa_bot.bot.InformSABot;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;

public class BotScheduler {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final BirthdayChecker birthdayChecker;
    private final WeatherFetcher weatherFetcher;
    private final InformSABot bot;
    private final SettingsManager settingsManager;

    public BotScheduler(InformSABot bot, ExcelEmployeeService service, SettingsManager settingsManager) {
        this.bot = bot;
        this.birthdayChecker = new BirthdayChecker(service);
        this.weatherFetcher = new WeatherFetcher(settingsManager);
        this.settingsManager = settingsManager;
    }

    public void start() {
        scheduleDailyWeather();
        scheduleDailyBirthdayCheck();
    }

    private void scheduleDailyWeather() {
        LocalTime hour = settingsManager.getWeatherTime(); // читаем из config
        Runnable task = () -> {
            String weather = weatherFetcher.getDetailedWeather();
            bot.broadcastMessage("☀️ Погода в Києві:\n" + weather);
        };
        scheduleAtFixedTime(task, hour);
    }

    private void scheduleDailyBirthdayCheck() {
        Runnable task = () -> {
            List<Employee> list = birthdayChecker.getTodayBirthdayEmployees();
            for (Employee emp : list) {
                bot.broadcastMessage("🎉 Сегодня день рождения у " + emp.getFullName() + "! Поздравляем! 🥳");
            }
        };

        LocalTime birthdayTime = settingsManager.getBirthdayTime();
        scheduleAtFixedTime(task, birthdayTime);
    }

    public void scheduleAtFixedTime(Runnable task, LocalTime time) {
        scheduler.scheduleAtFixedRate(() -> {
            LocalTime now = LocalTime.now().withSecond(0).withNano(0);
            if (now.equals(time)) {
                task.run();
            }
        }, 0, 1, TimeUnit.MINUTES); // проверка каждую минуту
    }
}