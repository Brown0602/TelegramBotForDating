package com.tuaev.telegrambot;

import com.tuaev.telegrambot.entity.UserProfiles;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserProfilesRowMapper implements RowMapper<UserProfiles> {
    @Override
    public UserProfiles mapRow(ResultSet rs, int rowNum) throws SQLException {
        UserProfiles userProfiles = new UserProfiles();
        userProfiles.setUser_profiles_id(rs.getString("user_profiles_id"));
        userProfiles.setUser_profiles_name(rs.getString("user_profiles_name"));
        userProfiles.setUser_profiles_age(rs.getInt("user_profiles_age"));
        userProfiles.setUser_profiles_sex(rs.getString("user_profiles_sex"));
        userProfiles.setUser_profiles_city(rs.getString("user_profiles_city"));
        userProfiles.setUser_description(rs.getString("user_description"));
        userProfiles.setPhoto(rs.getString("photo"));
        return userProfiles;
    }
}
