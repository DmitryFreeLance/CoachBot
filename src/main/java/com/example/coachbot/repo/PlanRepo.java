package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;
import java.time.LocalDate;

public class PlanRepo {

    public static void setNutrition(String userId, LocalDate date, Integer kcal, Double p, Double f, Double c, String by) throws Exception {
        try (Connection conn = Db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO nutrition_plans(user_id,date,calories,proteins,fats,carbs,set_by) " +
                             "VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,date) DO UPDATE SET calories=excluded.calories, proteins=excluded.proteins, fats=excluded.fats, carbs=excluded.carbs, set_by=excluded.set_by")) {
            ps.setString(1, userId); ps.setString(2, date.toString()); ps.setObject(3, kcal);
            ps.setObject(4, p); ps.setObject(5, f); ps.setObject(6, c); ps.setString(7, by);
            ps.executeUpdate();
        }
    }

    public static String getNutritionText(String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT calories,proteins,fats,carbs FROM nutrition_plans WHERE user_id=? AND date=?")) {
            ps.setString(1, userId); ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.format("%s Калории: %s\n%s Белки: %s\n%s Жиры: %s\n%s Углеводы: %s",
                            com.example.coachbot.Emojis.FIRE, (rs.getObject(1)),
                            com.example.coachbot.Emojis.MEAT, (rs.getObject(2)),
                            com.example.coachbot.Emojis.AVOCADO, (rs.getObject(3)),
                            com.example.coachbot.Emojis.BREAD, (rs.getObject(4)));
                }
            }
        }
        return "План питания на сегодня не задан.";
    }

    public static void addWorkoutLine(String userId, LocalDate date, String line, String by) throws Exception {
        String existing = getWorkoutRaw(userId, date);
        String merged = existing==null || existing.isBlank() ? line : existing + "\n" + line;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO workout_plans(user_id,date,text,set_by) VALUES(?,?,?,?) " +
                             "ON CONFLICT(user_id,date) DO UPDATE SET text=excluded.text, set_by=excluded.set_by")) {
            ps.setString(1, userId); ps.setString(2, date.toString());
            ps.setString(3, merged); ps.setString(4, by);
            ps.executeUpdate();
        }
    }

    public static String getWorkoutRaw(String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement("SELECT text FROM workout_plans WHERE user_id=? AND date=?")) {
            ps.setString(1, userId); ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next()?rs.getString(1):null; }
        }
    }

    public static String getWorkoutText(String userId, LocalDate date) throws Exception {
        String raw = getWorkoutRaw(userId, date);
        if (raw==null || raw.isBlank()) return "План тренировки на сегодня не задан.";
        StringBuilder sb = new StringBuilder();
        int i=1;
        for (String line : raw.split("\\n")) {
            if (!line.isBlank()) sb.append(com.example.coachbot.Emojis.CHECK).append(" ").append(i++).append(". ").append(line.trim()).append("\n");
        }
        return sb.toString().trim();
    }
}