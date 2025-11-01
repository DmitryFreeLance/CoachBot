package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupRepo {

    /** Добавить пользователя к админу. Вернёт true, если добавили; false — если уже прикреплён к какому-то тренеру (в т.ч. к этому). */
    public static boolean addToAdmin(String adminId, String userId) throws Exception {
        try (Connection c = Db.connect()) {

            // 1) уже есть запись для этого user_id?
            String owner = adminOf(userId, c);
            if (owner != null) {
                // Уже есть тренер — запрещаем (максимум один тренер на пользователя)
                return false;
            }

            // 2) вставить
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO groups(user_id, admin_id) VALUES(?,?)")) {
                ps.setString(1, userId);
                ps.setString(2, adminId);
                ps.executeUpdate();
                return true;
            }
        }
    }

    /** Удалить пользователя из своей группы. True — если удалили; false — если такой пары нет. */
    public static boolean removeFromAdmin(String adminId, String userId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM groups WHERE user_id=? AND admin_id=?")) {
            ps.setString(1, userId);
            ps.setString(2, adminId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Сколько пользователей у этого админа. */
    public static int countUsersOfAdmin(String adminId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM groups WHERE admin_id=?")) {
            ps.setString(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Список user_id у админа (постранично). */
    public static List<String> usersOfAdmin(String adminId, int limit, int offset) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id FROM groups WHERE admin_id=? ORDER BY user_id LIMIT ? OFFSET ?")) {
            ps.setString(1, adminId);
            ps.setInt(2, Math.max(1, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    /** Узнать тренера пользователя. null — если не прикреплён. */
    public static String adminOf(String userId) throws Exception {
        try (Connection c = Db.connect()) {
            return adminOf(userId, c);
        }
    }

    // Внутренняя перегрузка — чтобы не открывать коннект дважды.
    private static String adminOf(String userId, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT admin_id FROM groups WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}