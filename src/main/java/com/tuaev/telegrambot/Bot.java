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

import javax.swing.text.html.Option;
import java.io.*;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
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
    private final Map<Long, UserProfiles> aProfileForTheUser = new HashMap<>();
    private final Map<Long, Boolean> viewingProfiles = new HashMap<>();
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
//            for (int i = 101; i <= 200; i++){
//                Random random = new Random();
//                int rad = random.nextInt(1, 100);
//                jdbcTemplate.update("INSERT INTO user_profiles(user_profiles_id, user_profiles_name, user_profiles_age, user_profiles_sex, user_profiles_city, user_description, photo, status) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", i, "", rad, "Парни", "Москва", "", "", true);
//            }
            userResponsesById.remove(user.getId());
            iteratorUser.remove(user.getId());
            questionsForEachUser.remove(user.getId());
            UserIdAndQuestionnaire.remove(user.getId());
            try {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(user.getId()))
                        .text("Привет " + user.getFirstName())
                        .replyMarkup(ReplyKeyboardRemove.builder()
                                .removeKeyboard(true)
                                .build())
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
                            .text("1.Смотреть анкеты\n2.Заполнить анкету заново")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("1"), new KeyboardButton("2"))))
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

            if (message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && message.getText().equals("2")) {
                UserIdAndQuestionnaire.replace(user.getId(), true);
                userResponsesById.put(user.getId(), userResponses);
                iteratorUser.put(user.getId(), 0);
                questionsForEachUser.put(user.getId(), questions);
                jdbcTemplate.update("DELETE FROM user_profiles WHERE user_profiles_id = ?", String.valueOf(user.getId()));
                try {
                    UserProfiles userProfiles = jdbcTemplate.queryForObject("SELECT * FROM user_profiles WHERE user_profiles_id = ?", new UserProfilesRowMapper(), String.valueOf(user.getId()));
                } catch (EmptyResultDataAccessException ignored) {
                    profilesByIdUser.put(user.getId(), null);
                    try {
                        execute(SendMessage.builder()
                                .chatId(String.valueOf(user.getId()))
                                .text("Давайте заполним вашу анкету заново")
                                .replyMarkup(ReplyKeyboardRemove.builder()
                                        .removeKeyboard(true)
                                        .build())
                                .build());
                    }catch (TelegramApiException ignored1){

                    }
                }
            } else if (message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && message.getText().equals("1")) {
                viewingProfiles.put(user.getId(), true);
            } else if (!message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) || message.hasText() && !UserIdAndQuestionnaire.get(user.getId()) && !message.getText().equals("1") && !message.getText().equals("2") && !message.getText().equals("/profile") && !message.getText().equals("/start")) {
                try {
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text("Нет такого варианта ответа")
                            .replyMarkup(ReplyKeyboardMarkup.builder()
                                    .keyboardRow(new KeyboardRow(List.of(new KeyboardButton("1"), new KeyboardButton("2"))))
                                    .resizeKeyboard(true)
                                    .build())
                            .build());
                }catch (TelegramApiException ignored){
                }
            }
            if (viewingProfiles.get(user.getId()) && message.hasText() && message.getText().equals("1")){
                Optional<Integer> theNumberOfRecordsInTheTable = Optional.ofNullable(
                        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profiles WHERE NOT user_profiles_sex = ? AND user_profiles_city = ?\n" +
                                        "AND user_profiles_age BETWEEN ? AND ?",
                                Integer.class,
                                profilesByIdUser.get(user.getId()).getUser_profiles_sex(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_city(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_age() - 5,
                                profilesByIdUser.get(user.getId()).getUser_profiles_age() + 5)
                );
                Optional<UserProfiles> userProfiles = Optional.ofNullable(
                        jdbcTemplate.queryForObject(
                                "SELECT * FROM user_profiles WHERE NOT user_profiles_sex = ? AND user_profiles_city = ?\n" +
                                        "AND user_profiles_age BETWEEN ? AND ?\n" +
                                        "OFFSET FLOOR(random() * ?) LIMIT 1",
                                new UserProfilesRowMapper(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_sex(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_city(),
                                profilesByIdUser.get(user.getId()).getUser_profiles_age() - 5,
                                profilesByIdUser.get(user.getId()).getUser_profiles_age() + 5,
                                theNumberOfRecordsInTheTable.get())
                );
                aProfileForTheUser.put(user.getId(), userProfiles.get());
                try{
                    execute(SendMessage.builder()
                            .chatId(String.valueOf(user.getId()))
                            .text(aProfileForTheUser.get(user.getId()).getUser_profiles_city())
                            .build());
                }catch (TelegramApiException ignored){}
            }

        if (UserIdAndQuestionnaire.get(user.getId()) != null && profilesByIdUser.get(user.getId()) == null || message.hasPhoto() && UserIdAndQuestionnaire.get(user.getId()) != null && profilesByIdUser.get(user.getId()) == null) {
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
                }catch (TelegramApiException ignored){

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
        }
    }
}
