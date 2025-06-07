package org.example.informsa_bot;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.example.informsa_bot.Config.SettingsManager;
import org.example.informsa_bot.bot.InformSABot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class AdminPanel extends Application {
    private InformSABot bot;

    @Override
    public void start(Stage stage) {
        if (bot == null) {
            try {
                SettingsManager settingsManager = new SettingsManager();
                bot = new InformSABot(settingsManager);
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                botsApi.registerBot(bot);
                System.out.println("Бот запущен");

                // Выводим простое диалоговое окно с сообщением
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Інформація");
                alert.setHeaderText(null);
                alert.setContentText("Бот працює");
                alert.show();

            } catch (TelegramApiException e) {
                e.printStackTrace();

                // Можно вывести ошибку, если бот не запустился
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Помилка");
                alert.setHeaderText(null);
                alert.setContentText("Помилка при запуску бота:\n" + e.getMessage());
                alert.show();
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
