package org.example.informsa_bot.Config;
import java.io.*;
import java.net.URL;

public class ExcelFileLoader {

    /**
     * Ищет Excel-файл по указанному пути:
     * - сначала пытается открыть как файл на диске (абсолютный или относительный)
     * - если не найден, пытается загрузить из ресурсов classpath
     *
     * @param path путь к файлу, например "data/Contact.xlsx" или "Contact.xlsx"
     * @return InputStream файла, или null если не найден
     */
    public static InputStream getExcelInputStream(String path) {
        // 1. Пытаемся открыть файл на диске
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace(); // Маловероятно, но пусть будет
            }
        }

        // 2. Попытка загрузить из classpath
        InputStream resourceStream = ExcelFileLoader.class.getClassLoader().getResourceAsStream(path);
        if (resourceStream != null) {
            return resourceStream;
        }

        // 3. Дополнительная попытка без подкаталогов (если путь задан с '/')
        if (path.startsWith("/")) {
            String trimmedPath = path.substring(1); // Убираем ведущий '/'
            resourceStream = ExcelFileLoader.class.getClassLoader().getResourceAsStream(trimmedPath);
            if (resourceStream != null) {
                return resourceStream;
            }
        }

        System.err.println("Файл не найден ни на диске, ни в classpath: " + path);
        return null;
    }
}