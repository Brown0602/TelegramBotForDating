package com.tuaev.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuaev.telegrambot.entity.UserProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.io.File;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Component
@PropertySource("application.properties")
public class Bot extends TelegramLongPollingBot {

    @Value("${bot.username}")
    String botUsername;
    @Value("${bot.token}")
    String botToken;
    private final Map<Long, UserProfiles> profilesByIdUser = new HashMap<>();
    private final Map<String, String> userResponses = new HashMap<>();
    private final Map<Long, Map<String, String>> userResponsesById = new HashMap<>();
    private final List<String> questions = List.of("Как вас зовут?", "Сколько вам лет?", "Вам интересны парни или девушки?", "Из какого вы города?", "Расскажите о себе", "Пришлите для вашей анкеты фото");
    private final Map<Long, Integer> iteratorUser = new HashMap<>();
    private final Map<Long, List<String>> questionsForEachUser = new HashMap<>();
    private final Map<Long, Boolean> UserIdAndQuestionnaire = new HashMap<>();
    private final Map<Long, String> photos = new HashMap<>();
    JdbcTemplate jdbcTemplate;

    @Autowired
    public Bot(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Bot() {

    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {

        Message message = update.getMessage();
        User user = message.getFrom();

        if (message.hasText() && message.getText().equals("/start")) {
            try {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Привет " + user.getFirstName())
                        .build());
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }

        if (message.hasText() && message.getText().equals("/profile")) {
            Boolean check = jdbcTemplate.queryForObject("SELECT EXISTS(SELECT user_profiles.user_profiles_id FROM user_profiles WHERE user_profiles_id = ?);", Boolean.class, String.valueOf(user.getId()));
            if (Boolean.TRUE.equals(check)) {
                UserProfiles userProfiles = jdbcTemplate.queryForObject("SELECT * FROM user_profiles WHERE user_profiles_id = ?", new UserProfilesRowMapper(), String.valueOf(user.getId()));
                profilesByIdUser.put(user.getId(), userProfiles);
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Так выглядит ваша анкета:")
                            .build());
                    execute(SendPhoto.builder()
                            .chatId(String.valueOf(user.getId()))
                            .photo(new InputFile(new File("src/main/resources/static/" + "фото_анкеты_пользователя_id" + user.getId() + ".jpg")))
                            .caption(profilesByIdUser.get(user.getId()).getUser_profiles_name() + ", " + profilesByIdUser.get(user.getId()).getUser_profiles_age() + ", "
                                    + profilesByIdUser.get(user.getId()).getUser_profiles_city() + ", " + profilesByIdUser.get(user.getId()).getUser_description())
                            .build());
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Хотите ли вы изменить вашу анкету?")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Да"), new KeyboardButton("Нет"))))
                                    .resizeKeyboard(true)
                                    .build())
                            .build());
                    UserIdAndQuestionnaire.put(user.getId(), false);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Отлично! Давайте создадим вашу анкету.")
                            .build());
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                userResponsesById.put(user.getId(), userResponses);
                iteratorUser.put(user.getId(), 0);
                questionsForEachUser.put(user.getId(), questions);
                UserIdAndQuestionnaire.put(user.getId(), true);
            }
        }

        try {
            if (message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && message.getText().equals("Нет")) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Так выглядит ваша анкета:")
                            .replyMarkup(ReplyKeyboardRemove.builder()
                                    .removeKeyboard(true)
                                    .build())
                            .build());
                    execute(SendPhoto.builder()
                            .chatId(String.valueOf(user.getId()))
                            .photo(new InputFile(new File("src/main/resources/static/" + "фото_анкеты_пользователя_id" + user.getId() + ".jpg")))
                            .caption(profilesByIdUser.get(user.getId()).getUser_profiles_name() + ", " + profilesByIdUser.get(user.getId()).getUser_profiles_age() + ", "
                                    + profilesByIdUser.get(user.getId()).getUser_profiles_city() + ", " + profilesByIdUser.get(user.getId()).getUser_description())
                            .build());
                    profilesByIdUser.remove(user.getId());
                } catch (TelegramApiException ignored) {

                }
            }

            if (message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && message.getText().equals("Да")) {
                UserIdAndQuestionnaire.replace(user.getId(), true);
                userResponsesById.put(user.getId(), userResponses);
                iteratorUser.put(user.getId(), 0);
                questionsForEachUser.put(user.getId(), questions);
                jdbcTemplate.update("DELETE FROM user_profiles WHERE user_profiles_id = ?", String.valueOf(user.getId()));
                try {
                    UserProfiles userProfiles = jdbcTemplate.queryForObject("SELECT * FROM user_profiles WHERE user_profiles_id = ?", new UserProfilesRowMapper(), String.valueOf(user.getId()));
                } catch (EmptyResultDataAccessException ignored) {
                    profilesByIdUser.put(user.getId(), null);
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Давайте заполним вашу анкету заново")
                            .replyMarkup(ReplyKeyboardRemove.builder()
                                    .removeKeyboard(true)
                                    .build())
                            .build());
                }
            }

            if (!message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && !message.getText().equals("Нет") && !message.getText().equals("Да") && !message.getText().equals("/profile") && !message.getText().equals("/start")
                    || message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && !message.getText().equals("Нет") && !message.getText().equals("Да") && !message.getText().equals("/profile") && !message.getText().equals("/start")) {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Нет такого варианта ответа")
                        .replyMarkup(ReplyKeyboardMarkup.builder()
                                .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Да"), new KeyboardButton("Нет"))))
                                .resizeKeyboard(true)
                                .build())
                        .build());
            }
        } catch (NullPointerException ignored) {

        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        try {
            if (UserIdAndQuestionnaire.get(user.getId()) != null && profilesByIdUser.get(user.getId()) == null || message.hasPhoto() && UserIdAndQuestionnaire.get(user.getId()) != null && profilesByIdUser.get(user.getId()) == null) {
                int i = iteratorUser.get(user.getId());
                String str = message.getText();
                if (i == 1) {
                    userResponses.put("Имя", str);
                }
                if (i == 2 && str.matches("\\d[0-99]") || i == 2 && !str.matches("\\d[0-99]")) {
                    try {
                        if (Integer.parseInt(str) > 0 && Integer.parseInt(str) < 100) {
                            userResponses.put("Возраст", str);
                        } else {
                            try {
                                execute(SendMessage.builder()
                                        .chatId(String.valueOf(user.getId()))
                                        .text("Введите цифры от 1 до 99.")
                                        .build());
                                i--;
                                iteratorUser.replace(user.getId(), i);
                            } catch (TelegramApiException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } catch (NumberFormatException numberFormatException) {
                        try {
                            execute(SendMessage.builder()
                                    .chatId(String.valueOf(user.getId()))
                                    .text("Неверный формат данных или неверный диапозан возраста. " +
                                            "Введите цифры от 1 до 99.")
                                    .build());
                        } catch (TelegramApiException e) {
                            throw new RuntimeException(e);
                        }
                        i--;
                        iteratorUser.replace(user.getId(), i);
                    }
                }
                if (i == 3) {
                    userResponses.put("Пол", str);
                }
                if (i == 4) {
                    userResponses.put("Город", str);
                }
                if (i == 5) {
                    userResponses.put("Описание", str);
                }
                if (i == 6) {
                    try {
                        photos.put(user.getId(), message.getPhoto().get(message.getPhoto().size() - 1).getFileId());
                        HttpClient httpClient = HttpClient.newHttpClient();
                        HttpRequest httpRequest = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + photos.get(user.getId())))
                                .build();
                        try {
                            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                            ObjectMapper objectMapper = new ObjectMapper();
                            try {
                                String UserPhotoJSON = httpResponse.body();
                                UserPhoto json = objectMapper.readValue(UserPhotoJSON, UserPhoto.class);
                                BufferedInputStream bufferedInputStream = new BufferedInputStream(new URL("https://api.telegram.org/file/bot" + botToken + "/" + json.getResult().getFilePath()).openStream());
                                FileOutputStream fileOutputStream = new FileOutputStream("src/main/resources/static/" + "фото_анкеты_пользователя_id" + user.getId() + ".jpg");
                                byte[] bytes = new byte[1024];
                                int byteRead = -1;
                                while ((byteRead = bufferedInputStream.read(bytes)) != -1) {
                                    fileOutputStream.write(bytes, 0, byteRead);
                                }
                                bufferedInputStream.close();
                                fileOutputStream.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        userResponses.put("Фото", "src/main/resources/static/" + "фото_анкеты_пользователя_id" + user.getId() + ".jpg");
                    } catch (NullPointerException nullPointerException) {
                        try {
                            execute(SendMessage.builder()
                                    .chatId(String.valueOf(user.getId()))
                                    .text("Ошибка!")
                                    .build());
                            i--;
                            iteratorUser.replace(user.getId(), i);
                        } catch (TelegramApiException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                try {
                    if (iteratorUser.get(user.getId()) <= questions.size() - 1) {
                        if (message.hasText() && iteratorUser.get(user.getId()) == 3 && !message.getText().equals("Парни") && !message.getText().equals("Девушки")) {
                            execute(SendMessage.builder()
                                    .chatId(String.valueOf(user.getId()))
                                    .text("Нет такого варианта ответа")
                                    .replyMarkup(ReplyKeyboardMarkup.builder()
                                            .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Парни"), new KeyboardButton("Девушки"))))
                                            .resizeKeyboard(true)
                                            .build())
                                    .build());
                            i--;
                            iteratorUser.replace(user.getId(), i);
                        } else if (!message.hasText()) {
                            execute(SendMessage.builder()
                                    .chatId(String.valueOf(user.getId()))
                                    .text("Неверный формат данных")
                                    .replyMarkup(ReplyKeyboardMarkup.builder()
                                            .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Парни"), new KeyboardButton("Девушки"))))
                                            .resizeKeyboard(true)
                                            .build())
                                    .build());
                            i--;
                            iteratorUser.replace(user.getId(), i);
                        }
                        if (iteratorUser.get(user.getId()) == 2) {
                            execute(SendMessage.builder()
                                    .chatId(String.valueOf(user.getId()))
                                    .text(questionsForEachUser.get(user.getId()).get(iteratorUser.get(user.getId())))
                                    .replyMarkup(ReplyKeyboardMarkup.builder()
                                            .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Парни"), new KeyboardButton("Девушки"))))
                                            .resizeKeyboard(true)
                                            .build())
                                    .build());
                        } else {
                            execute(SendMessage.builder()
                                    .chatId(String.valueOf(user.getId()))
                                    .text(questionsForEachUser.get(user.getId()).get(iteratorUser.get(user.getId())))
                                    .replyMarkup(ReplyKeyboardRemove.builder()
                                            .removeKeyboard(true)
                                            .build())
                                    .build());
                        }
                        i++;
                        iteratorUser.replace(user.getId(), i);
                    } else {
                        execute(SendMessage.builder()
                                .chatId(String.valueOf(user.getId()))
                                .text("Вы успешно заполнили вашу анкету")
                                .replyMarkup(ReplyKeyboardRemove.builder()
                                        .removeKeyboard(true)
                                        .build())
                                .build());
                        jdbcTemplate.update("INSERT INTO user_profiles(user_profiles_id, user_profiles_name, user_profiles_age, user_profiles_sex, user_profiles_city, user_description, photo) VALUES(?, ?, ?, ?, ?, ?, ?)",
                                user.getId(),
                                userResponsesById.get(user.getId()).get("Имя"),
                                Integer.parseInt(userResponsesById.get(user.getId()).get("Возраст")),
                                userResponsesById.get(user.getId()).get("Пол"),
                                userResponsesById.get(user.getId()).get("Город"),
                                userResponsesById.get(user.getId()).get("Описание"),
                                userResponsesById.get(user.getId()).get("Фото"));
                        userResponsesById.remove(user.getId());
                        iteratorUser.remove(user.getId());
                        questionsForEachUser.remove(user.getId());
                        UserIdAndQuestionnaire.remove(user.getId());
                    }
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (NullPointerException ignored) {

        }
    }
}
