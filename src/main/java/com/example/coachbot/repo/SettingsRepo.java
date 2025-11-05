package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;

/**
 * Расширенный SettingsRepo:
 *  - базовые get/set по ключу;
 *  - "персональные" настройки для админов: ключи вида key:adminId;
 *  - отдельные хелперы для времени рассылки (evening_time) per-admin
 *    с глобальным дефолтом (ключ "evening_time").
 */
public class SettingsRepo {

    /* ================= базовые k/v ================= */

    public static String get(String key, String def) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : def;
            }
        }
    }

    public static void set(String key, String value) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO settings(key,value) VALUES(?,?) " +
                             "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /* ================= перс-ключи для админов ================= */

    private static String keyForAdmin(String baseKey, String adminId) {
        return baseKey + ":" + adminId;
    }

    public static String getForAdmin(String baseKey, String adminId, String def) throws Exception {
        String k = keyForAdmin(baseKey, adminId);
        return get(k, def);
    }

    public static void setForAdmin(String baseKey, String adminId, String value) throws Exception {
        String k = keyForAdmin(baseKey, adminId);
        set(k, value);
    }

    /* ================= evening_time (персонально для админа) ================= */

    /** Глобальный дефолт, если у админа значение не задано. */
    public static String getEveningTimeDefault() throws Exception {
        return get("evening_time", "19:00");
    }

    public static String getEveningTimeForAdmin(String adminId) throws Exception {
        String perAdmin = getForAdmin("evening_time", adminId, null);
        if (perAdmin != null && !perAdmin.isBlank()) return perAdmin;
        return getEveningTimeDefault();
    }

    public static void setEveningTimeForAdmin(String adminId, String hhmm) throws Exception {
        setForAdmin("evening_time", adminId, hhmm);
    }

    /**
     * Утилита: получить вечернее время для конкретного пользователя (по его тренеру).
     * Если пользователь не прикреплён — вернётся глобальный дефолт.
     */
    public static String getEveningTimeForUser(String userId) throws Exception {
        String admin = GroupRepo.adminOf(userId);
        if (admin == null) return getEveningTimeDefault();
        return getEveningTimeForAdmin(admin);
    }
}
