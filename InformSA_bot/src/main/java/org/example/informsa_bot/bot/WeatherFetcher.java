package org.example.informsa_bot.bot;

import org.example.informsa_bot.Config.SettingsManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class WeatherFetcher {
    private final String apiKey;
    private final String city;

    public WeatherFetcher(SettingsManager settingsManager) {
        this.apiKey = settingsManager.getApiKey();
        this.city = settingsManager.getCity();
    }

    // Метод получения подробного прогноза (на сегодня 2 таймслота, 7:30 и 12:00)
    public String getDetailedWeather() {
        try {
            String urlString = "https://api.openweathermap.org/data/2.5/forecast?q=" + city
                    + "&appid=" + apiKey + "&units=metric&lang=ua";
            URL apiUrl = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String json = in.lines().collect(Collectors.joining());
            in.close();

            JSONObject obj = new JSONObject(json);
            JSONArray list = obj.getJSONArray("list");

            // Форматируем дату сегодня
            LocalDate today = LocalDate.now();

            JSONObject closestTo730 = null;
            long minDiffTo730 = Long.MAX_VALUE;

            JSONObject forecast1200 = null;

            JSONObject closestTo1800 = null;
            long minDiffTo1800 = Long.MAX_VALUE;

            for (int i = 0; i < list.length(); i++) {
                JSONObject entry = list.getJSONObject(i);
                String dt_txt = entry.getString("dt_txt");
                LocalDateTime dateTime = LocalDateTime.parse(dt_txt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                if (!dateTime.toLocalDate().equals(today)) continue;

                int hour = dateTime.getHour();
                int minute = dateTime.getMinute();

                // Проверяем для 7:30
                int currentMinutes = hour * 60 + minute;
                int targetMinutes730 = 7 * 60 + 30;
                long diff730 = Math.abs(currentMinutes - targetMinutes730);
                if (diff730 < minDiffTo730) {
                    minDiffTo730 = diff730;
                    closestTo730 = entry;
                }

                // Проверяем для 12:00 (точное совпадение)
                if (hour == 12 && forecast1200 == null) {
                    forecast1200 = entry;
                }

                // Проверяем для 18:00
                int targetMinutes1800 = 18 * 60;
                long diff1800 = Math.abs(currentMinutes - targetMinutes1800);
                if (diff1800 < minDiffTo1800) {
                    minDiffTo1800 = diff1800;
                    closestTo1800 = entry;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Вітаю сьогодні ").append(today.format(DateTimeFormatter.ofPattern("dd.MM"))).append("\n");

            if (closestTo730 != null) {
                sb.append(formatForecast("7:30", closestTo730)).append("\n");
            }

            if (forecast1200 != null) {
                sb.append(formatForecast("12:00", forecast1200)).append("\n");
            }

            if (closestTo1800 != null) {
                sb.append(formatForecast("18:00", closestTo1800)).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "Не вдалося отримати погоду.";
        }
    }

    private String formatForecast(String time, JSONObject forecast) {
        JSONObject main = forecast.getJSONObject("main");
        JSONArray weatherArr = forecast.getJSONArray("weather");
        JSONObject weather = ((JSONArray) weatherArr).getJSONObject(0);
        JSONObject wind = forecast.getJSONObject("wind");

        String desc = weather.getString("description");
        double temp = main.getDouble("temp");
        double windSpeed = wind.getDouble("speed");
        int humidity = main.getInt("humidity");

        // Используем эмодзи по погоде (упрощенно)
        String emoji = switch (desc) {
            case "хмарно" -> "☁";
            case "Рвані хмари" -> "☁";
            case "дощ" -> "☔";
            case "ясно" -> "☀";
            case "Чисте небо" -> "☀";
            default -> "";
        };

        return "Прогноз погоди на " + time + "\n"
                + capitalize(desc) + " " + emoji + "\n"
                + "Температура повітря: " + (temp > 0 ? "+" : "") + String.format("%.0f", temp) + "°C\n"
                + "Швидкість вітру: " + String.format("%.2f", windSpeed) + "м/с\n"
                + "Вологість повітря: " + humidity + "%\n"
                + "--------------------------------";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}

