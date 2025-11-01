package com.example.coachbot;

import java.sql.*;
import java.time.ZoneId;
import java.util.Arrays;

public class Db {
    private static String url;
    public static ZoneId ZONE;

    public static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
        }
        return c;
    }

    public static void init(String dbPath, String tz, String superAdminsCsv) throws Exception {
        url = "jdbc:sqlite:" + dbPath;
        ZONE = ZoneId.of(tz);
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users( tg_id TEXT PRIMARY KEY, username TEXT, first_name TEXT, role TEXT NOT NULL DEFAULT 'USER', added_at DATETIME DEFAULT CURRENT_TIMESTAMP )");
            st.execute("CREATE TABLE IF NOT EXISTS admin_groups( admin_id TEXT NOT NULL, user_id TEXT NOT NULL UNIQUE, PRIMARY KEY(admin_id, user_id) )");
            st.execute("CREATE TABLE IF NOT EXISTS reports( id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, date TEXT NOT NULL, sleep_hours REAL, steps INTEGER, water_liters REAL, calories INTEGER, proteins REAL, fats REAL, carbs REAL, text TEXT, photo_file_id TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id, date) )");
            st.execute("CREATE TABLE IF NOT EXISTS nutrition_plans( id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, date TEXT NOT NULL, calories INTEGER, proteins REAL, fats REAL, carbs REAL, set_by TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id, date) )");
            st.execute("CREATE TABLE IF NOT EXISTS workout_plans( id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, date TEXT NOT NULL, text TEXT NOT NULL, set_by TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id, date) )");
            st.execute("CREATE TABLE IF NOT EXISTS activity_norms( id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, date TEXT NOT NULL, water_liters REAL, steps INTEGER, sleep_hours REAL, set_by TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id, date) )");
            st.execute("CREATE TABLE IF NOT EXISTS contacts( admin_id TEXT PRIMARY KEY, text TEXT NOT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP )");
            st.execute("CREATE TABLE IF NOT EXISTS settings( key TEXT PRIMARY KEY, value TEXT )");
            st.execute("CREATE TABLE IF NOT EXISTS processed_updates( update_id INTEGER PRIMARY KEY, received_at DATETIME DEFAULT CURRENT_TIMESTAMP )");
            st.execute("CREATE TABLE IF NOT EXISTS sent_notifications( type TEXT NOT NULL, user_id TEXT NOT NULL, date TEXT NOT NULL, PRIMARY KEY(type, user_id, date) )");
            st.execute("CREATE TABLE IF NOT EXISTS user_states( user_id TEXT PRIMARY KEY, type TEXT, step INTEGER, payload TEXT )");
        }

        // дефолт вечернего времени (19:00), если не задано
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO settings(key,value) VALUES('evening_time','19:00')")) {
            ps.executeUpdate();
        }

        // зарегистрировать супер‑админов в таблице users
        if (superAdminsCsv != null && !superAdminsCsv.isBlank()) {
            for (String id : Arrays.stream(superAdminsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList()) {
                try (Connection c = connect();
                     PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO users(tg_id,role) VALUES(?, 'SUPERADMIN')")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
        }
    }
}