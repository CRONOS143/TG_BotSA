package org.example.informsa_bot.bot;

import org.example.informsa_bot.Config.Config;
import org.example.informsa_bot.Config.SettingsManager;
import org.example.informsa_bot.DB.ParkingDBHandler;
import org.example.informsa_bot.DB.ParkingSpot;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class InformSABot extends TelegramLongPollingBot {
    private final ConcurrentHashMap<String, TelegramUser> phoneToTelegramUser = new ConcurrentHashMap<>();


    private final ExcelEmployeeService employeeService = new ExcelEmployeeService();
    private final Set<Long> registeredChats = new HashSet<>();
    private final WeatherFetcher weatherFetcher;
    private final ParkingDBHandler parkingDBHandler = new ParkingDBHandler();
    // Состояние ожидания ввода запроса на поиск
    private final List<Long> waitingForSearchInput = new ArrayList<>();
    private final SettingsManager settingsManager = new SettingsManager();


    private final String adminChatId = "5026387260";
    private Set<Long> supportModeUsers = new HashSet<>();

    // Хранит накопленные сообщения/медиа от пользователя
    private Map<Long, List<PartialSupportMessage>> supportMessages = new HashMap<>();

    private LocalDate lastWeatherSendDate = null;
    private LocalDate lastBirthdaySendDate = null;

    private final BirthdayChecker birthdayChecker = new BirthdayChecker(employeeService); // передай свой ExcelEmployeeService


    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    public InformSABot(SettingsManager settingsManager) {
        this.weatherFetcher = new WeatherFetcher(settingsManager);
        startWeatherBroadcastScheduler();
        startScheduledTasks();
    }


    @Override
    public String getBotUsername() {
        return "InformSA_bot"; // вставь имя своего бота
    }

    @Override
    public String getBotToken() {
        return "7614955540:AAHKI4LZ8ZQ14mlYlwkkjuCFZ6KvlH4g_3E"; // вставь токен своего бота
    }



    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String username = message.getFrom().getUserName();
            String text = message.hasText() ? message.getText() : null;
            registeredChats.add(chatId);

            if (username == null || username.isEmpty()) {
                username = message.getFrom().getFirstName(); // fallback
            }

            if (update.getMessage().hasSticker()) {
                String fileId = update.getMessage().getSticker().getFileId();
                System.out.println("Sticker file_id: " + fileId);
            }




            // Если сообщение содержит контакт — регистрируем по телефону
            if (message.hasContact()) {
                Contact contact = message.getContact();
                String phone = normalizePhone(contact.getPhoneNumber());

                // Ищем сотрудника по номеру телефона
                List<Employee> employees = employeeService.getEmployees().stream()
                        .filter(e -> normalizePhone(e.getMobilePhone()).equals(phone))
                        .collect(Collectors.toList());

                if (!employees.isEmpty()) {
                    Employee emp = employees.get(0);
                    phoneToTelegramUser.put(phone, new TelegramUser(chatId, username));
                    sendTextMessage(chatId, "Вітаємо, " + emp.getFullName() + " Ви успішно зареєстровані! ✅");
                    sendWelcomeMessage(chatId);
                } else {
                    sendAuthFailedResponse(chatId);
                }
                sendMainMenu(chatId);
                return;  // обработка контакта завершена — можно не идти дальше
            }

            // Если пользователя по телефону еще нет — предлагаем поделиться контактом
            boolean userRegisteredByPhone = phoneToTelegramUser.values().stream()
                    .anyMatch(tu -> tu.getId().equals(chatId));

            if (!userRegisteredByPhone) {
                sendRequestContactButton(chatId, "Будь ласка, поділіться своїм номером телефону для реєстрації.");
                return;  // ждем пока пользователь поделится контактом
            }


            // Если пользователь в режиме поддержки
            if (supportModeUsers.contains(chatId)) {
                if ("Надіслати звернення".equals(text)) {
                    // Отправить поддержку админу
                    sendSupportRequestToAdmin(chatId);
                    supportModeUsers.remove(chatId);
                    supportMessages.remove(chatId);

                    sendTextMessage(chatId, "Ваше звернення надіслано. Дякуємо! 🥳");
                    sendMainMenu(chatId);
                } else if (message.hasPhoto()) {
                    // Добавляем фото в поддержку
                    List<PhotoSize> photos = message.getPhoto();
                    String fileId = photos.get(photos.size() - 1).getFileId(); // самое большое фото

                    supportMessages.computeIfAbsent(chatId, k -> new ArrayList<>())
                            .add(new PartialSupportMessage(PartialSupportMessage.Type.PHOTO, fileId));

                    sendPlainTextMessage(chatId, "Фото отримано, ви можете додати ще або натиснути \"Надіслати звернення\".");
                } else if (text != null && !text.isEmpty()) {
                    // Добавляем текст в поддержку
                    supportMessages.computeIfAbsent(chatId, k -> new ArrayList<>())
                            .add(new PartialSupportMessage(PartialSupportMessage.Type.TEXT, text));

                    sendPlainTextMessage(chatId, "Текст отримано, ви можете додати ще або натиснути \"Надіслати звернення\".");
                } else {
                    sendPlainTextMessage(chatId, "Будь ласка, надішліть текст або фото, або натисніть \"Надіслати звернення\".");
                }
                return;
            }


            registerPhone(chatId, username);

            // Регистрируем по username и chatId (если нужно)
            registerUser(username, chatId);


            if (waitingForSearchInput.contains(chatId)) {
                // Если пришло сообщение — это ввод поискового запроса
                if (text != null && !text.trim().isEmpty()) {
                    waitingForSearchInput.remove(chatId);
                    handleSearchQuery(chatId, text);
                    return;
                } else {
                    // Если пустое сообщение или нет текста — просим ввести запрос
                    sendTextMessage(chatId, "Будь ласка, введіть П.І.Б або відділ співробітника для пошуку:");
                    return;
                }
            }

            // Обработка команд/кнопок
                                                                                                                                     if (text != null) {
                switch (text) {
                    case "Пошук співробітника \uD83E\uDDD0":
                        waitingForSearchInput.add(chatId);
                        // Если пользователь уже был в состоянии ожидания, сбрасываем и заново просим ввести
                        sendTextMessage(chatId, "Введіть П.І.Б або відділ співробітника для пошуку:");
                        break;

                    case "День народження сьогодні \uD83E\uDD73":
                        sendBirthdayEmployees(chatId);
                        break;

                    case "Погода сьогодні \uD83E\uDD79":
                        sendWeather(chatId);
                        break;
                    case "Парковка\uD83D\uDE97":  // новая команда
                        sendParkingMenu(chatId, username);
                        break;
                    case "Технічна підтримка ⚙️":
                        supportModeUsers.add(chatId);            // добавляем пользователя в режим поддержки
                        supportMessages.put(chatId, new ArrayList<>()); // очищаем прошлые сообщения
                        sendTextMessage(chatId, "Опишіть вашу проблему або надішліть фото. Коли будете готові, натисніть кнопку \"Надіслати звернення\"");
                        sendSendSupportButton(chatId);           // отправляем кнопку "Надіслати звернення"
                        break;
                    default:
                        if ("Головне меню".equals(text)) {
                            sendWelcomeMessage(chatId);
                            return;
                        }
                        if (text.startsWith("Місце ")) {
                            int spotId = parseSpotId(text);
                            if (spotId != -1) {
                                handleBookSpot(chatId, username, spotId);
                            } else {
                                sendTextMessage(chatId, "Невірна команда.");
                            }
                        } else if (text.equalsIgnoreCase("Зняти броню")) {
                            handleCancelBooking(chatId, username);
                        } else {
                            sendDefaultMessage(chatId);
                        }
                }
            }
        }
    }




    private void sendAuthFailedResponse(Long chatId) {
        // Сначала отправим стикер
        SendSticker sticker = new SendSticker();
        sticker.setChatId(chatId.toString());
        sticker.setSticker(new InputFile("CAACAgIAAxkBAAIEsmhEUakZbLtD8W9YPvf8QnUse_J7AAIXDgACKHFwSE9AJIrVMVFBNgQ"));

        // Затем текст
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        String userProfileLink = "<a href=\"tg://user?id=" + adminChatId + "\">👤 Адміністратора</a>";
        String text= ("Не вдалося авторизуватись. Користувача не знайдено. " +
                "\nЗверніться до: " +userProfileLink);
        message.setText(text);
        message.setParseMode("HTML"); // Включаем поддержку HTML

        try {
            execute(sticker); // сначала стикер
            execute(message); // потом текст
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendSendSupportButton(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Натисніть кнопку нижче, щоб надіслати звернення:");

        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Надіслати звернення"));

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setKeyboard(Collections.singletonList(row));
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendSupportRequestToAdmin(Long fromChatId) {
        List<PartialSupportMessage> msgs = supportMessages.get(fromChatId);
        if (msgs == null || msgs.isEmpty()) {
            sendTextMessage(fromChatId, "Ви не надсилали жодного повідомлення.");
            return;
        }

        String userInfo = "Звернення від користувача: <a href=\"tg://user?id=" + fromChatId + "\">👤 Профіль користувача</a>\n";

        // Отправляем информацию о пользователе с HTML-разметкой
        SendMessage infoMsg = new SendMessage();
        infoMsg.setChatId(adminChatId);
        infoMsg.setText(userInfo);
        infoMsg.setParseMode("HTML");  // Включаем поддержку HTML

        try {
            execute(infoMsg);

            for (PartialSupportMessage m : msgs) {
                if (m.type == PartialSupportMessage.Type.TEXT) {
                    SendMessage sm = new SendMessage();
                    sm.setChatId(adminChatId);
                    sm.setText("📝 " + m.content);
                    sm.setParseMode("HTML");  // если нужно, можно убрать или добавить для единообразия
                    execute(sm);
                } else if (m.type == PartialSupportMessage.Type.PHOTO) {
                    SendPhoto sp = new SendPhoto();
                    sp.setChatId(adminChatId);
                    sp.setPhoto(new InputFile(m.content));
                    execute(sp);
                }
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendPlainTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        // Здесь не ставим клавиатуру, чтобы не переключать меню
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(getMainMenuKeyboard()); // Можно сразу показать главное меню
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Ось головне меню:");
        message.setReplyMarkup(getMainMenuKeyboard());
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void registerUser(String username, Long chatId) {
        if (username == null || username.isEmpty()) return;

        List<Employee> employees = findEmployeesByTelegramUsername(username);
        if (employees.isEmpty()) {
            System.out.println("Не найден сотрудник с Telegram username: @" + username);
            return;
        }

        Employee employee = employees.get(0);
        String phone = normalizePhone(employee.getMobilePhone());
        if (phone == null || phone.isEmpty()) {
            System.out.println("Невозможно нормализовать номер телефона: " + employee.getMobilePhone());
            return;
        }

        TelegramUser telegramUser = new TelegramUser(chatId, username);
        phoneToTelegramUser.put(phone, telegramUser);

        System.out.println("✅ Зарегистрирован пользователь: " + username +
                ", fullName: " + employee.getFullName() +
                ", нормализованный телефон: " + phone +
                ", chatId: " + chatId);
    }

    private void registerPhone(Long chatId, String phoneNumber) {
        // Нормализуем номер (убираем пробелы, тире, скобки)
        String normalizedPhone = normalizePhone(phoneNumber);

        if (normalizedPhone == null || normalizedPhone.isEmpty()) return;

        // Ищем сотрудника с таким телефоном
        List<Employee> employees = employeeService.getEmployees().stream()
                .filter(e -> {
                    String empPhoneNorm = normalizePhone(e.getMobilePhone());
                    return empPhoneNorm != null && empPhoneNorm.equals(normalizedPhone);
                })
                .collect(Collectors.toList());

        if (employees.isEmpty()) {
            System.out.println("Не найден сотрудник с телефоном: " + normalizedPhone);
            return;
        }

        Employee emp = employees.get(0);

        // Связываем chatId и username сотрудника (если есть)
        String username = emp.getTelegramPhone();
        if (username == null) username = ""; // или можно пустую строку

        TelegramUser tgUser = new TelegramUser(chatId, username);
        phoneToTelegramUser.put(normalizedPhone, tgUser);

        System.out.println("Зарегистрирован пользователь " + emp.getFullName() + " с телефоном " + normalizedPhone);
    }


    public List<Employee> findEmployeesByTelegramUsername(String username) {
        if (username == null) return Collections.emptyList();

        List<Employee> employees = employeeService.getEmployees(); // источник списка сотрудников

        return employees.stream()
                .filter(e -> {
                    if (e.getTelegramPhone() == null) return false;
                    String tgUsername = e.getTelegramPhone().replace("@", "").toLowerCase();
                    return tgUsername.equalsIgnoreCase(username.toLowerCase());
                })
                .collect(Collectors.toList());
    }

    private void sendWelcomeMessage(long chatId) {
        String welcome = "Вітаю!\uD83D\uDC4B Мене звати INFORM\uD83D\uDCAA, \n я помічник компанії Sante-Alko,чим можу допомогти?" +
                "\n Я вмію знаходити наших співробітників, бронювати ваше місце на парковці та показувати погоду." +
                "\n А ще через мене можна повідомити про технічну проблему";
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(welcome);
        message.setReplyMarkup(getMainMenuKeyboard());
        executeMessage(message);
    }

    // Метод отправки кнопки с запросом контакта
    private void sendRequestContactButton(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        KeyboardButton button = new KeyboardButton("Поділитись номером телефону");
        button.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(button);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setKeyboard(Collections.singletonList(row));
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup getMainMenuKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setSelective(true);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        KeyboardButton btnSearch = new KeyboardButton("Пошук співробітника \uD83E\uDDD0");
        KeyboardButton btnBirthday = new KeyboardButton("День народження сьогодні \uD83E\uDD73");
        KeyboardButton btnWeather = new KeyboardButton("Погода сьогодні \uD83E\uDD79");
        KeyboardButton btnParking = new KeyboardButton("Парковка\uD83D\uDE97");  // Новая кнопка
        KeyboardButton btnSupport = new KeyboardButton("Технічна підтримка ⚙️");

        row.add(btnSearch);
        row.add(btnBirthday);
        row.add(btnWeather);
        row.add(btnParking);
        row.add(btnSupport);

        keyboard.add(row);
        markup.setKeyboard(keyboard);

        return markup;
    }



    private void startWeatherBroadcastScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndSendWeatherBroadcast();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void checkAndSendWeatherBroadcast() {
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        LocalTime targetTime = settingsManager.getWeatherTime();
        LocalDate today = LocalDate.now();

        if (lastWeatherSendDate != null && lastWeatherSendDate.equals(today)) {
            return; // Уже отправляли сегодня
        }

        if (now.equals(targetTime)) {
            String weatherReport = weatherFetcher.getDetailedWeather();
            broadcastMessage(weatherReport);
            lastWeatherSendDate = today;
        }
    }


    public void startScheduledTasks() {
        Runnable task = () -> {
            LocalTime now = LocalTime.now().withSecond(0).withNano(0);
            LocalDate today = LocalDate.now();

            // отправка погоды в заданное время
            LocalTime weatherTime = settingsManager.getWeatherTime();
            if (now.equals(weatherTime) && (lastWeatherSendDate == null || !lastWeatherSendDate.equals(today))) {
                String weather = weatherFetcher.getDetailedWeather();
                broadcastMessage(weather);
                lastWeatherSendDate = today;
            }



            // отправка поздравлений в заданное время
            LocalTime birthdayTime = settingsManager.getBirthdayTime();
            if (now.equals(birthdayTime) && (lastBirthdaySendDate == null || !lastBirthdaySendDate.equals(today))) {
                List<Employee> birthdayEmployees = birthdayChecker.getTodayBirthdayEmployees();
                if (!birthdayEmployees.isEmpty()) {
                    StringBuilder msg = new StringBuilder("🎉 Сьогодні день народження у:\n");

                    for (Employee e : birthdayEmployees) {
                        String phoneNorm = normalizePhone(e.getMobilePhone());
                        TelegramUser tgUser = phoneToTelegramUser.get(phoneNorm);

                        String escapedName = escapeHtml(e.getFullName());

                        if (tgUser != null && tgUser.getUsername() != null && !tgUser.getUsername().isEmpty()) {
                            String username = tgUser.getUsername().replace("@", "");
                            msg.append("• <a href=\"https://t.me/").append(username).append("\">")
                                    .append(escapedName).append("</a>\n");
                        } else {
                            msg.append("• ").append(escapedName).append("\n");
                        }
                    }

                    msg.append("Не забудьте привітати! 🥳");

                    broadcastMessageHtml(msg.toString());
                    lastBirthdaySendDate = today;

                }
            }

        };


        scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.MINUTES);
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        // Экранируем только _ * [ ] `
        return text.replaceAll("([_\\*\\[\\]`])", "\\\\$1");
    }
    public void broadcastMessage(String text) {
        String escapedText = escapeMarkdown(text);
        for (Long chatId : registeredChats) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(escapedText);
            message.setParseMode("Markdown");
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    public void broadcastMessageHtml(String text) {
        for (Long chatId : registeredChats) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("HTML");
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }


    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(escapeMarkdown(text));  // Здесь экранируем спецсимволы
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendLongMessage(long chatId, String text) {
        final int MAX_LENGTH = 4000; // чуть меньше лимита Telegram

        if (text.length() <= MAX_LENGTH) {
            sendTextMessage(chatId, text);
        } else {
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + MAX_LENGTH, text.length());
                // Чтобы не обрывать текст посередине строки, можно искать последний \n до end
                int lastNewLine = text.lastIndexOf('\n', end);
                if (lastNewLine <= start) {
                    lastNewLine = end;
                }
                String part = text.substring(start, lastNewLine);
                sendTextMessage(chatId, part);
                start = lastNewLine;
            }
        }
    }


    private void executeMessage(SendMessage message) {
        if (message.getParseMode() != null && message.getParseMode().equals("Markdown")) {
            String escapedText = escapeMarkdown(message.getText());
            message.setText(escapedText);
        }
        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleSearchQuery(long chatId, String query) {
        List<Employee> found = employeeService.findEmployeesByNameOrDepartment(query);
        System.out.println("handleSearchQuery called with query: '" + query + "'");
        if (found.isEmpty()) {
            SendSticker sticker = new SendSticker();
            sticker.setChatId(String.valueOf(chatId));
            sticker.setSticker(new InputFile("CAACAgIAAxkBAAIE3mhEVeBFDtsrIOAgvO_sDHiTcJ3EAAKfSwACsYN5SLedtoC1Y9s1NgQ"));

            try {
                execute(sticker);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

// Отправка текстового сообщения
            sendTextMessage(chatId, "Нічого не знайдено за запитом 🤒: " + query);
        } else {
            StringBuilder sb = new StringBuilder("Результати пошуку:\n");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            for (Employee e : found) {
                sb.append("Ім`я: ").append(e.getFullName()).append("\n")
                        .append("Відділ: ").append(e.getDepartment()).append("\n")
                        .append("Посада:: ").append(e.getPost()).append("\n")
                        .append("Телефон: ").append(e.getMobilePhone()).append("\n")
                        .append("Пошта: ").append(e.getMail()).append("\n")
                        .append("Телеграм: ").append(e.getTelegramPhone()).append("\n")
                        .append("Дата народження: ").append(e.getBirthDate().format(dateFormatter)).append("\n")
                        .append("--------------------------------------------------\n");
            }
            sendLongMessage(chatId, sb.toString());
        }
    }
    private void sendTextMessage(long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        message.setParseMode("Markdown"); // <--- обязательно!
        executeMessage(message);
    }

    private void sendBirthdayEmployees(long chatId) {
        List<Employee> birthdayEmployees = employeeService.getTodayBirthdayEmployees();
        if (birthdayEmployees.isEmpty()) {
            sendTextMessageHtml(chatId, "Сьогодні ніхто з працівників не святкує день народження. 😞");
        } else {
            StringBuilder sb = new StringBuilder("🎉 Сьогодні святкують день народження:\n");
            for (Employee e : birthdayEmployees) {
                String phoneNorm = normalizePhone(e.getMobilePhone());
                TelegramUser tgUser = phoneToTelegramUser.get(phoneNorm);

                System.out.println("🎈 День народження: " + e.getFullName() + ", телефон: " + e.getMobilePhone() +
                        ", нормализований: " + phoneNorm +
                        ", знайдений TelegramUser: " + (tgUser != null ? tgUser.getUsername() : "немає"));

                String escapedName = escapeHtml(e.getFullName());
                String escapedDeptWithBrackets = escapeHtml(" (" + e.getDepartment() + ")");
                String escapedPhone = escapeHtml(e.getMobilePhone());

                if (tgUser != null && tgUser.getUsername() != null && !tgUser.getUsername().isEmpty()) {
                    String username = tgUser.getUsername().replace("@", "");
                    sb.append("• <a href=\"https://t.me/").append(username).append("\">").append(escapedName).append("</a>");
                } else {
                    sb.append("• ").append(escapedName);
                }

                sb.append(escapedDeptWithBrackets)
                        .append("\nТел: ").append(escapedPhone)
                        .append("\n\n");
            }
            sendTextMessageHtml(chatId, sb.toString());
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void sendTextMessageHtml(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("HTML");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



    private String normalizePhone(String phone) {
        if (phone == null) return null;

        // Удалить все символы кроме цифр
        String digits = phone.replaceAll("\\D+", "");

        // Если уже начинается с 380 — оставляем
        if (digits.startsWith("380")) {
            return digits;
        }

        // Если начинается с 0 — добавляем 38 спереди
        if (digits.startsWith("0")) {
            return "38" + digits;
        }

        // Иначе просто вернем что есть
        return digits;
    }
    private void sendWeather(long chatId) {
        String weatherText = weatherFetcher.getDetailedWeather();
        sendTextMessage(chatId, weatherText);
    }

    private void sendDefaultMessage(long chatId) {
        sendTextMessage(chatId, "Виберіть одну з доступних команд, будь ласка. Використайте /start для меню.");
    }

    private void sendParkingMenu(long chatId, String username) {
        List<ParkingSpot> spots = parkingDBHandler.getAllSpots();

        System.out.println("Количество парковочных мест: " + spots.size());
        for (ParkingSpot spot : spots) {
            System.out.println("Місце " + spot.getSpotId() + ", заброньоване: " + spot.isBooked() + ", юзер: " + spot.getBookedByUsername());
        }

        StringBuilder bookedSpotsInfo = new StringBuilder("❌ Заняті місця:\n");
        boolean hasBookedSpots = false;

        List<KeyboardRow> freeSpotRows = new ArrayList<>();
        KeyboardRow currentRow = new KeyboardRow();

        for (ParkingSpot spot : spots) {
            if (spot.isBooked()) {
                hasBookedSpots = true;
                bookedSpotsInfo.append("Місце ")
                        .append(spot.getSpotId())
                        .append(" — ")
                        .append("@").append(spot.getBookedByUsername() == null ? "Невідомо" : spot.getBookedByUsername().replace("@", ""));

                if (spot.getBookedByPhone() != null && !spot.getBookedByPhone().isEmpty()) {
                    bookedSpotsInfo.append(" (").append(spot.getBookedByPhone()).append(")");
                }
                bookedSpotsInfo.append("\n");
                if (spot.getBookedByPhone() != null && !spot.getBookedByPhone().isEmpty()) {
                    bookedSpotsInfo.append(" (").append(spot.getBookedByPhone()).append(")");
                }
                bookedSpotsInfo.append("\n");
            } else {
                currentRow.add("Місце " + spot.getSpotId());
                if (currentRow.size() == 5) {
                    freeSpotRows.add(currentRow);
                    currentRow = new KeyboardRow();
                }
            }
        }
        if (!currentRow.isEmpty()) freeSpotRows.add(currentRow);

        // 🟢 Проверка, есть ли у пользователя бронь
        ParkingSpot userBooking = parkingDBHandler.getBookingByUser(username);
        if (userBooking != null) {
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add("Зняти броню");
            freeSpotRows.add(cancelRow);
        }

        KeyboardRow backRow = new KeyboardRow();
        backRow.add("Головне меню");
        freeSpotRows.add(backRow);

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(freeSpotRows);
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        String messageText = "Оберіть вільне місце для бронювання:";
        if (hasBookedSpots) {
            messageText += "\n\n" + bookedSpotsInfo.toString();
        }

        System.out.println("Отправляю сообщение:\n" + messageText);

        sendTextMessage(chatId, messageText, keyboard);
    }

    private int parseSpotId(String text) {
        try {
            String[] parts = text.split(" ");
            return Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private void handleBookSpot(long chatId, String username, int spotId) {
        // Проверяем, есть ли бронь у пользователя
        // Проверяем, есть ли бронь у пользователя
        var userBooking = parkingDBHandler.getBookingByUser(username);
        if (userBooking != null) {
            // Сначала стикер
            SendSticker sticker = new SendSticker();
            sticker.setChatId(String.valueOf(chatId));
            sticker.setSticker(new InputFile("CAACAgIAAxkBAAIE2mhEVShJTJY5eDajY4FwQo85Sb8PAALzQgACMnN4SPsuRE6dqD_LNgQ"));

            try {
                execute(sticker);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            // Затем сообщение
            sendTextMessage(chatId, "У вас вже є заброньоване місце: " + userBooking.getSpotId() + ". Зніміть бронь перед новим вибором.");
            return; // ⛔ Обязательное завершение метода
        }


        // Попытка забронировать
        boolean success = parkingDBHandler.bookSpot(spotId, username, "");

        if (success) {
            // 🔧 Добавляем 2 кнопки: Зняти броню и Головне меню
            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
            KeyboardRow row1 = new KeyboardRow();
            row1.add("Зняти броню");

            KeyboardRow row2 = new KeyboardRow();
            row2.add("Головне меню");

            keyboard.setKeyboard(List.of(row1, row2));
            keyboard.setResizeKeyboard(true);
            keyboard.setOneTimeKeyboard(true);

            sendTextMessage(chatId, "Ви успішно забронювали місце №" + spotId, keyboard);
        } else {
            sendTextMessage(chatId, "Місце №" + spotId + " вже зайняте або сталася помилка. Спробуйте інше.");
        }
    }

    private void handleCancelBooking(long chatId, String username) {
        boolean canceled = parkingDBHandler.cancelBookingByUser(username);
        if (canceled) {
            sendTextMessage(chatId, "Ваша бронь знята.");
            sendParkingMenu(chatId, username);
        } else {

                // Отправка стикера перед сообщением
                SendSticker sticker = new SendSticker();
                sticker.setChatId(String.valueOf(chatId));
                sticker.setSticker(new InputFile("CAACAgIAAxkBAAIE3mhEVeBFDtsrIOAgvO_sDHiTcJ3EAAKfSwACsYN5SLedtoC1Y9s1NgQ"));

                try {
                    execute(sticker);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                // Текстовое сообщение
                sendTextMessage(chatId, "У вас немає активної броні.");

        }
    }
    private static class PartialSupportMessage {
        enum Type { TEXT, PHOTO }
        Type type;
        String content; // текст или file_id для фото

        PartialSupportMessage(Type type, String content) {
            this.type = type;
            this.content = content;
        }
    }
}

