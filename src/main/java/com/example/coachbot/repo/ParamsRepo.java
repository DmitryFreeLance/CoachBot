package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;

/**
 * Хранилище параметров пользователя (текущие значения, перезапись при обновлении).
 *
 * Таблица создаётся/обновляется автоматически (ensureTable()):
 *   user_params(
 *     user_id TEXT PRIMARY KEY,
 *     weight REAL, waist REAL,
 *     chest_exhale REAL, chest_relaxed REAL, chest_inhale REAL,
 *     biceps_relaxed REAL, biceps_flex REAL,
 *     photo_id TEXT,
 *     updated_at INTEGER
 *   )
 */
public class ParamsRepo {

    /** Создаём таблицу при отсутствии (и гарантируем нужные колонки). */
    private static void ensureTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS user_params(
                  user_id TEXT PRIMARY KEY,
                  weight REAL,
                  waist REAL,
                  chest_exhale REAL,
                  chest_relaxed REAL,
                  chest_inhale REAL,
                  biceps_relaxed REAL,
                  biceps_flex REAL,
                  photo_id TEXT,
                  updated_at INTEGER
                )
            """);
        }
        // На случай старых схем без chest_relaxed / biceps_flex — добавим недостающие колонки.
        addColumnIfMissing(c, "user_params", "chest_relaxed", "REAL");
        addColumnIfMissing(c, "user_params", "biceps_flex",  "REAL");
    }

    private static void addColumnIfMissing(Connection c, String table, String col, String ddlType) {
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")")) {
            boolean has = false;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (col.equalsIgnoreCase(rs.getString("name"))) { has = true; break; }
                }
            }
            if (!has) {
                try (Statement st = c.createStatement()) {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + ddlType);
                }
            }
        } catch (SQLException ignore) { /* безопасно проигнорируем */ }
    }

    public static void upsertNumbers(String userId,
                                     Double weight, Double waist,
                                     Double chEx, Double chRl, Double chIn,
                                     Double biRl, Double biFx) throws Exception {
        try (Connection c = Db.connect()) {
            ensureTable(c);
            long now = System.currentTimeMillis()/1000L;
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO user_params(user_id,weight,waist,chest_exhale,chest_relaxed,chest_inhale,biceps_relaxed,biceps_flex,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET
                  weight=excluded.weight,
                  waist=excluded.waist,
                  chest_exhale=excluded.chest_exhale,
                  chest_relaxed=excluded.chest_relaxed,
                  chest_inhale=excluded.chest_inhale,
                  biceps_relaxed=excluded.biceps_relaxed,
                  biceps_flex=excluded.biceps_flex,
                  updated_at=excluded.updated_at
            """)) {
                int i=1;
                ps.setString(i++, userId);
                setNullable(ps, i++, weight);
                setNullable(ps, i++, waist);
                setNullable(ps, i++, chEx);
                setNullable(ps, i++, chRl);
                setNullable(ps, i++, chIn);
                setNullable(ps, i++, biRl);
                setNullable(ps, i++, biFx);
                ps.setLong(i, now);
                ps.executeUpdate();
            }
        }
    }

    public static void setPhoto(String userId, String photoId) throws Exception {
        try (Connection c = Db.connect()) {
            ensureTable(c);
            long now = System.currentTimeMillis()/1000L;
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO user_params(user_id, photo_id, updated_at)
                VALUES(?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET photo_id=excluded.photo_id, updated_at=excluded.updated_at
            """)) {
                ps.setString(1, userId);
                ps.setString(2, photoId);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        }
    }

    public static String getPhotoId(String userId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT photo_id FROM user_params WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next()? rs.getString(1) : null; }
        }
    }

    /** Красиво отформатированные параметры (или null, если записей нет). */
    public static String getPretty(String userId) throws Exception {
        try (Connection c = Db.connect()) {
            ensureTable(c);
            try (PreparedStatement ps = c.prepareStatement("""
                SELECT weight,waist,chest_exhale,chest_relaxed,chest_inhale,biceps_relaxed,biceps_flex,updated_at
                FROM user_params WHERE user_id=?
            """)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    Double w   = box(rs.getObject(1));
                    Double ws  = box(rs.getObject(2));
                    Double chE = box(rs.getObject(3));
                    Double chR = box(rs.getObject(4));
                    Double chI = box(rs.getObject(5));
                    Double biR = box(rs.getObject(6));
                    Double biF = box(rs.getObject(7));

                    StringBuilder sb = new StringBuilder();
                    sb.append("📏 *Текущие параметры:*\n");
                    if (w  != null) sb.append("⚖️ Вес: ").append(trim(w)).append(" кг\n");
                    if (ws != null) sb.append("📍 Талия (уровень пупка): ").append(trim(ws)).append(" см\n");
                    boolean hasChest = (chE!=null||chR!=null||chI!=null);
                    if (hasChest) {
                        sb.append("🫁 Грудь:\n");
                        sb.append(" • на выдохе: ").append(val(chE)).append(" см\n");
                        sb.append(" • расслабл.: ").append(val(chR)).append(" см\n");
                        sb.append(" • на вдохе: ").append(val(chI)).append(" см\n");
                    }
                    boolean hasBi = (biR!=null||biF!=null);
                    if (hasBi) {
                        sb.append("💪 Бицепс:\n");
                        sb.append(" • расслабл.: ").append(val(biR)).append(" см\n");
                        sb.append(" • напряжён.: ").append(val(biF)).append(" см\n");
                    }
                    return sb.toString().trim();
                }
            }
        }
    }

    /**
     * Метод-алиас под имя, которое ожидает остальной код.
     * Возвращает тот же текст, что и getPretty(userId).
     */
    public static String getParamsText(String userId) throws Exception {
        return getPretty(userId);
    }

    /* ================= helpers ================= */

    private static void setNullable(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.REAL); else ps.setDouble(idx, v);
    }

    @SuppressWarnings("unchecked")
    private static Double box(Object o) { try { return o==null?null:((Number)o).doubleValue(); } catch(Exception e){ return null; } }
    private static String val(Double d){ return d==null? "—" : trim(d).toString(); }
    private static Number trim(Double d){
        if (d==null) return null;
        if (Math.abs(d - Math.rint(d)) < 1e-9) return Math.round(d);
        return d;
    }
}