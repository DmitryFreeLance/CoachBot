package com.example.coachbot.repo;

import com.example.coachbot.Db;
import com.example.coachbot.Roles;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserRepo {

    public static class UserRow {
        public final String id;
        public final String username;   // @tag без @ (как в Telegram)
        public final String firstName;  // отображаемое имя

        public UserRow(String id, String username, String firstName) {
            this.id = id;
            this.username = username;
            this.firstName = firstName;
        }
    }

    public static void upsertUser(String id, String username, String firstName) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("""
                         INSERT INTO users(id, username, first_name, role, active)
                         VALUES(?, ?, ?, COALESCE((SELECT role FROM users WHERE id=?),'USER'), 1)
                         ON CONFLICT(id) DO UPDATE SET
                           username=excluded.username,
                           first_name=excluded.first_name,
                           active=1
                     """)) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, firstName);
            ps.setString(4, id);
            ps.executeUpdate();
        }
    }

    public static Roles role(String id) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT role FROM users WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Roles.valueOf(rs.getString(1));
                    } catch (Exception ignored) {
                    }
                }
                return Roles.USER;
            }
        }
    }

    public static void setRole(String id, Roles r) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("""
                         INSERT INTO users(id, role, active)
                         VALUES(?, ?, 1)
                         ON CONFLICT(id) DO UPDATE SET role=excluded.role, active=1
                     """)) {
            ps.setString(1, id);
            ps.setString(2, r.name());
            ps.executeUpdate();
        }
    }

    public static void ensureAdmin(String id) throws Exception {
        Roles cur = role(id);
        if (cur != Roles.SUPERADMIN) setRole(id, Roles.ADMIN);
    }

    public static int countUsers() throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE active=1")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Старый метод (если где-то используется) — только id
     */
    public static List<String> allUsersPaged(int limit, int offset) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM users WHERE active=1 ORDER BY rowid ASC LIMIT ? OFFSET ?")) {
            ps.setInt(1, Math.max(1, limit));
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    /**
     * Новый детальный метод — для красивого списка: {first_name} | @username | tg_id
     */
    public static List<UserRow> allUsersPagedDetailed(int limit, int offset) throws Exception {
        List<UserRow> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, first_name FROM users WHERE active=1 ORDER BY rowid ASC LIMIT ? OFFSET ?")) {
            ps.setInt(1, Math.max(1, limit));
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UserRow(
                            rs.getString("id"),
                            rs.getString("username"),
                            rs.getString("first_name")
                    ));
                }
            }
        }
        return out;
    }

    public static List<String> allActiveUsers() throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM users WHERE active=1 AND (role IS NULL OR role='USER') ORDER BY rowid ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }
}