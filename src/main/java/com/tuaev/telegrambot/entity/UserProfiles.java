package com.tuaev.telegrambot.entity;

public class UserProfiles{

    private String user_profiles_id;
    private String user_profiles_name;
    private Integer user_profiles_age;
    private String user_profiles_sex;
    private String user_profiles_city;
    private String user_description;
    private String photo;
    private boolean status;

    public void setId(int id) {
        this.id = id;
    }

    private int id;

    public int getId() {
        return id;
    }

    public String getUser_profiles_id() {
        return user_profiles_id;
    }

    public void setUser_profiles_id(String user_profiles_id) {
        this.user_profiles_id = user_profiles_id;
    }

    public String getUser_profiles_name() {
        return user_profiles_name;
    }

    public void setUser_profiles_name(String user_profiles_name) {
        this.user_profiles_name = user_profiles_name;
    }

    public Integer getUser_profiles_age() {
        return user_profiles_age;
    }

    public void setUser_profiles_age(Integer user_profiles_age) {
        this.user_profiles_age = user_profiles_age;
    }

    public String getUser_profiles_sex() {
        return user_profiles_sex;
    }

    public void setUser_profiles_sex(String user_profiles_sex) {
        this.user_profiles_sex = user_profiles_sex;
    }

    public String getUser_profiles_city() {
        return user_profiles_city;
    }

    public void setUser_profiles_city(String user_profiles_city) {
        this.user_profiles_city = user_profiles_city;
    }

    public String getUser_description() {
        return user_description;
    }

    public void setUser_description(String user_description) {
        this.user_description = user_description;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }
}
