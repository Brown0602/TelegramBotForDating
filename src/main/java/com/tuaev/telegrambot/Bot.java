package com.tuaev.telegrambot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuaev.telegrambot.entity.UserProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
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
    private final Map<Long, List<Map<String, Object>>> aProfileForTheUser = new HashMap<>();
    private final Map<Long, UserProfiles> profilesByIdUser = new HashMap<>();
    private final Map<String, String> userResponses = new HashMap<>();
    private final Map<Long, Map<String, String>> userResponsesById = new HashMap<>();
    private final List<String> questions = List.of("Как вас зовут?", "Сколько вам лет?", "Вам интересны парни или девушки?", "Из какого вы города?", "Расскажите о себе", "Пришлите для вашей анкеты фото");
    private final Map<Long, Integer> iteratorUser = new HashMap<>();
    private final Map<Long, List<String>> questionsForEachUser = new HashMap<>();
    private final Map<Long, Boolean> creates = new HashMap<>();
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

        if (message.hasText() && message.getText().equals("/profile") && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))) {
            UserProfiles userProfiles = jdbcTemplate.queryForObject("SELECT * FROM user_profiles WHERE user_profiles_id = ?", new UserProfilesRowMapper(), String.valueOf(user.getId()));
            profilesByIdUser.put(user.getId(), userProfiles);
            try {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Так выглядит твоя анкета:")
                        .build());
                execute(SendPhoto.builder()
                        .chatId(String.valueOf(user.getId()))
                        .photo(new InputFile(new File("src/main/resources/static/" + "фото_анкеты_пользователя_id" + user.getId() + ".jpg")))
                        .caption(profilesByIdUser.get(user.getId()).getUser_profiles_name() + ", " + profilesByIdUser.get(user.getId()).getUser_profiles_age() + ", "
                                + profilesByIdUser.get(user.getId()).getUser_profiles_city() + ", " + profilesByIdUser.get(user.getId()).getUser_description())
                        .build());
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Что ты хочешь сделать?")
                        .replyMarkup(ReplyKeyboardMarkup.builder()
                                .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Смотреть анкеты"), new KeyboardButton("Заполнить анкету заново"),
                                        new KeyboardButton("Сделать анкету неактивной"), new KeyboardButton("Сделать анкету активной"))))
                                .resizeKeyboard(true)
                                .build())
                        .build());
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (message.hasText() && message.getText().equals("/profile") &&
                Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))) {
            try {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Отлично! Давайте создадим вашу анкету.")
                        .build());
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            creates.put(user.getId(), true);
            userResponsesById.put(user.getId(), userResponses);
            iteratorUser.put(user.getId(), 0);
            questionsForEachUser.put(user.getId(), questions);
        }

        if (message.hasText() && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId()))) && message.getText().equals("Заполнить анкету заново")) {
            creates.put(user.getId(), true);
            userResponsesById.put(user.getId(), userResponses);
            iteratorUser.put(user.getId(), 0);
            questionsForEachUser.put(user.getId(), questions);
            jdbcTemplate.update("DELETE FROM user_profiles WHERE user_profiles_id = ?", String.valueOf(user.getId()));
            try {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Давайте заполним вашу анкету заново")
                        .replyMarkup(ReplyKeyboardRemove.builder()
                                .removeKeyboard(true)
                                .build())
                        .build());
            } catch (TelegramApiException ignored) {

            }
        }

        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))) {
            if (message.hasText() && message.getText().equals("Смотреть анкеты")) {
                jdbcTemplate.update("UPDATE user_profiles SET viewing = ? WHERE user_profiles_id = ?", true, String.valueOf(user.getId()));
            }
            if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.viewing FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.status FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && message.getText().equals("Смотреть анкеты") || Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.viewing FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.status FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && message.getText().equals("Не нравится") || Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.viewing FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.status FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && message.getText().equals("Нравится")) {
                UserProfiles userProfiles1 = jdbcTemplate.queryForObject("SELECT * FROM user_profiles WHERE user_profiles_id = ?", new UserProfilesRowMapper(), String.valueOf(user.getId()));
                profilesByIdUser.put(user.getId(), userProfiles1);
                Optional<List<Map<String, Object>>> userProfiles = Optional.of(
                        jdbcTemplate.queryForList(
                                "SELECT * FROM user_profiles WHERE NOT user_profiles_sex = ? AND user_profiles_city = ?\n" +
                                        "AND user_profiles_age BETWEEN ? AND ?",
                                profilesByIdUser.get(user.getId()).getUser_profiles_sex(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_city(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_age() - 5,
                                profilesByIdUser.get(user.getId()).getUser_profiles_age() + 5
                        )
                );
                aProfileForTheUser.put(user.getId(), userProfiles.get());
                if (userProfiles.get().size() == 0) {
                    try {
                        execute(SendMessage.builder()
                                .chatId(String.valueOf(user.getId()))
                                .text("К сожалению подходящих анкет для вас ещё нет")
                                .build());
                    } catch (TelegramApiException ignored) {
                    }
                }
                if (userProfiles.get().size() != 0) {
                    try {
                        Random random = new Random();
                        int rand = random.nextInt(0, userProfiles.get().size());
                        execute(SendMessage.builder()
                                .chatId(String.valueOf(user.getId()))
                                .text("Нашёл для тебя кое-кого")
                                .replyMarkup(ReplyKeyboardMarkup.builder()
                                        .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Нравится"), new KeyboardButton("Не нравится"), new KeyboardButton("Прекратить просмотр анкет"))))
                                        .resizeKeyboard(true)
                                        .build())
                                .build());
                        execute(SendPhoto.builder()
                                .chatId(String.valueOf(user.getId()))
                                .photo(new InputFile(new File(aProfileForTheUser.get(user.getId()).get(rand).get("photo").toString())))
                                .caption(aProfileForTheUser.get(user.getId()).get(rand).get("user_profiles_name").toString() + "," + " " +
                                        aProfileForTheUser.get(user.getId()).get(rand).get("user_profiles_age").toString() + "," + " " +
                                        aProfileForTheUser.get(user.getId()).get(rand).get("user_profiles_city").toString() + "," + " " +
                                        aProfileForTheUser.get(user.getId()).get(rand).get("user_description").toString())
                                .build());
                        aProfileForTheUser.remove(user.getId());
                    } catch (TelegramApiException ignored) {
                    }
                }
            }
        }
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId()))) && message.hasText() && message.getText().equals("Смотреть анкеты")){
            if (Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.status FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId()))) && message.hasText() && message.getText().equals("Смотреть анкеты")) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Для просмотра анкет сделайте анкету активной")
                            .build());
                } catch (TelegramApiException ignored) {

                }
            }
    }

        if (!message.hasText() && Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))) {
            if (Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.viewing FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && message.hasText() && !message.getText().equals("Смотреть анкеты") && !message.getText().equals("Заполнить анкету заново") && !message.getText().equals("Сделать анкету неактивной") && !message.getText().equals("Сделать анкету активной") && !message.getText().equals("/profile")) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Нет такого варианта ответа")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Смотреть анкеты"), new KeyboardButton("Заполнить анкету заново"), new KeyboardButton("Сделать анкету неактивной"), new KeyboardButton("Сделать анкету активной"))))
                                    .resizeKeyboard(true)
                                    .build())
                            .build());
                } catch (TelegramApiException ignored) {

                }
            }
        }

        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))) {
            if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.viewing FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && message.hasText() && !message.getText().equals("Нравится") && !message.getText().equals("Не нравится") && !message.getText().equals("Прекратить просмотр анкет") && !message.getText().equals("Смотреть анкеты") && !message.getText().equals("/profile")) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Нет такого варианта ответа")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Нравится"), new KeyboardButton("Не нравится"), new KeyboardButton("Прекратить просмотр анкет"))))
                                    .resizeKeyboard(true)
                                    .build())
                            .build());
                } catch (TelegramApiException ignored) {

                }
            }
        }

        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))){
            if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT user_profiles.viewing FROM user_profiles WHERE user_profiles_id = ?", Boolean.class, String.valueOf(user.getId()))) && message.hasText() && message.getText().equals("Прекратить просмотр анкет")) {
                jdbcTemplate.update("UPDATE user_profiles SET viewing = ? WHERE user_profiles_id = ?", false, String.valueOf(user.getId()));
                UserProfiles userProfiles = jdbcTemplate.queryForObject("SELECT * FROM user_profiles WHERE user_profiles_id = ?", new UserProfilesRowMapper(), String.valueOf(user.getId()));
                profilesByIdUser.put(user.getId(), userProfiles);
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Так выглядит твоя анкета:")
                            .build());
                    execute(SendPhoto.builder()
                            .chatId(String.valueOf(user.getId()))
                            .photo(new InputFile(new File("src/main/resources/static/" + "фото_анкеты_пользователя_id" + user.getId() + ".jpg")))
                            .caption(profilesByIdUser.get(user.getId()).getUser_profiles_name() + ", " + profilesByIdUser.get(user.getId()).getUser_profiles_age() + ", "
                                    + profilesByIdUser.get(user.getId()).getUser_profiles_city() + ", " + profilesByIdUser.get(user.getId()).getUser_description())
                            .build());
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Что ты хочешь сделать?")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Смотреть анкеты"), new KeyboardButton("Заполнить анкету заново"),
                                            new KeyboardButton("Сделать анкету неактивной"), new KeyboardButton("Сделать анкету активной"))))
                                    .resizeKeyboard(true)
                                    .build())
                            .build());
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId()))) && message.hasText()){
            if (message.getText().equals("Сделать анкету неактивной")) {
                jdbcTemplate.update("UPDATE user_profiles SET status = ? WHERE user_profiles_id = ?", false, String.valueOf(user.getId()));
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Теперь твоя анкета неактивна и её никто не увидит")
                            .build());
                } catch (TelegramApiException ignored) {

                }
            }
            if (message.getText().equals("Сделать анкету активной")){
                jdbcTemplate.update("UPDATE user_profiles SET status = ? WHERE user_profiles_id = ?", true, String.valueOf(user.getId()));
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Теперь твоя анкета снова активна")
                            .build());
                } catch (TelegramApiException e) {

                }
            }
        }

        if (creates.get(user.getId()) != null && creates.get(user.getId()) && Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId()))) || message.hasPhoto() && creates.get(user.getId()) != null && creates.get(user.getId()) && Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT * FROM user_profiles WHERE user_profiles_id = ?)", Boolean.class, String.valueOf(user.getId())))) {
            int i = iteratorUser.get(user.getId());
            if (i == 1 && message.hasText()) {
                userResponses.put("Имя", message.getText());
            } else if (i == 1 && !message.hasText()) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Неверный формат данных")
                            .build());
                    i--;
                    iteratorUser.replace(user.getId(), i);
                } catch (TelegramApiException ignored) {

                }
            }
            if (!message.hasText() && i == 2 || i == 2 && message.getText().matches("\\d[0-99]") || i == 2 && !message.getText().matches("\\d[0-99]")) {
                try {
                    if (Integer.parseInt(message.getText()) > 0 && Integer.parseInt(message.getText()) < 100) {
                        userResponses.put("Возраст", message.getText());
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
                                .text("Неверный формат данных. Введите цифры от 1 до 99.")
                                .build());
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                    i--;
                    iteratorUser.replace(user.getId(), i);
                }
            }
            if (i == 3 && message.hasText() && message.getText().equals("Парни") || i == 3 && message.hasText() && message.getText().equals("Девушки")) {
                userResponses.put("Пол", message.getText());
            } else if (!message.hasText() && i == 3 || message.hasText() && i == 3 && !message.getText().equals("Парни") && !message.getText().equals("Девушки")) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Нет такого варианта ответа")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Парни"), new KeyboardButton("Девушки"))))
                                    .resizeKeyboard(true)
                                    .build())
                            .build());
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                i--;
                iteratorUser.replace(user.getId(), i);
            }
            if (i == 4 && message.hasText()) {
                userResponses.put("Город", message.getText());
            } else if (i == 4 && !message.hasText()) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Неверный формат данных")
                            .build());
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                i--;
                iteratorUser.replace(user.getId(), i);
            }
            if (i == 5 && message.hasText()) {
                userResponses.put("Описание", message.getText());
            } else if (i == 5 && !message.hasText()) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Неверный формат данных")
                            .build());
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                i--;
                iteratorUser.replace(user.getId(), i);
            }
            if (i == 6 && message.hasPhoto()) {
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
            } else if (i == 6 && !message.hasPhoto()) {
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
            if (iteratorUser.get(user.getId()) <= questions.size() - 1) {
                if (iteratorUser.get(user.getId()) == 2) {
                    try {
                        execute(SendMessage.builder()
                                .chatId(String.valueOf(user.getId()))
                                .text(questionsForEachUser.get(user.getId()).get(iteratorUser.get(user.getId())))
                                .replyMarkup(ReplyKeyboardMarkup.builder()
                                        .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("Парни"), new KeyboardButton("Девушки"))))
                                        .resizeKeyboard(true)
                                        .build())
                                .build());
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        execute(SendMessage.builder()
                                .chatId(String.valueOf(user.getId()))
                                .text(questionsForEachUser.get(user.getId()).get(iteratorUser.get(user.getId())))
                                .replyMarkup(ReplyKeyboardRemove.builder()
                                        .removeKeyboard(true)
                                        .build())
                                .build());
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
                i++;
                iteratorUser.replace(user.getId(), i);
            } else {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Вы успешно заполнили вашу анкету")
                            .replyMarkup(ReplyKeyboardRemove.builder()
                                    .removeKeyboard(true)
                                    .build())
                            .build());
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                jdbcTemplate.update("INSERT INTO user_profiles(user_profiles_id, user_profiles_name, user_profiles_age, user_profiles_sex, user_profiles_city, user_description, photo, status) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                        user.getId(),
                        userResponsesById.get(user.getId()).get("Имя"),
                        Integer.parseInt(userResponsesById.get(user.getId()).get("Возраст")),
                        userResponsesById.get(user.getId()).get("Пол"),
                        userResponsesById.get(user.getId()).get("Город"),
                        userResponsesById.get(user.getId()).get("Описание"),
                        userResponsesById.get(user.getId()).get("Фото"),
                        true);
                userResponsesById.remove(user.getId());
                iteratorUser.remove(user.getId());
                questionsForEachUser.remove(user.getId());
                creates.remove(user.getId());
            }
        }
    }
}
