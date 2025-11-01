package com.example.coachbot.repo;

import com.example.coachbot.Db;
import com.example.coachbot.Emojis;

import java.sql.*;
import java.time.LocalDate;

public class NormRepo {
    public static void setNorms(String userId, LocalDate date, Double water, Integer steps, Double sleep, String by) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO activity_norms(user_id,date,water_liters,steps,sleep_hours,set_by) VALUES(?,?,?,?,?,?) " +
                             "ON CONFLICT(user_id,date) DO UPDATE SET water_liters=excluded.water_liters, steps=excluded.steps, sleep_hours=excluded.sleep_hours, set_by=excluded.set_by")) {
            ps.setString(1, userId); ps.setString(2, date.toString());
            ps.setObject(3, water); ps.setObject(4, steps); ps.setObject(5, sleep);
            ps.setString(6, by); ps.executeUpdate();
        }
    }

    public static String getNormsText(String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT water_liters, steps, sleep_hours FROM activity_norms WHERE user_id=? AND date=?")) {
            ps.setString(1, userId); ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.format("%s Вода: %s л\n%s Шаги: %s\n%s Сон: %s ч",
                            Emojis.DROPLET, v(rs.getObject(1)), Emojis.RUNNER, v(rs.getObject(2)), Emojis.SLEEP, v(rs.getObject(3)));
                }
            }
        }
        return "Нормы активности на сегодня не заданы.";
    }

    private static String v(Object o){ return o==null? "—":String.valueOf(o); }
}