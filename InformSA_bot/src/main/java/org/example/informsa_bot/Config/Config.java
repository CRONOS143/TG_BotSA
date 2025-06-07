package org.example.informsa_bot.Config;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static Properties props = new Properties();

    static {
        try (InputStream is = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) {
                System.err.println("config.properties не найден в classpath!");
            } else {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения config.properties");
            e.printStackTrace();
        }
    }

    public static String getContactFilePath() {
        return props.getProperty("contact.file.path", "Contact.xlsx");
    }
}