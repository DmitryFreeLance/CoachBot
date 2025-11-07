package com.example.coachbot.repo;

import com.example.coachbot.Db;

import java.sql.*;

/**
 * Хранилище параметров пользователя (текущие значения, перезапись при обновлении).
 *
 * Итоговая целевая схема (с авто-добавлением недостающих колонок):
 *   user_params(
 *     user_id TEXT PRIMARY KEY,
 *     weight REAL,
 *     waist_navel REAL,         -- талия на уровне пупка (старая "waist")
 *     waist_max REAL,           -- талия максимальный обхват
 *     chest_exhale REAL,
 *     chest_relaxed REAL,
 *     chest_inhale REAL,
 *     biceps_left_relaxed REAL,
 *     biceps_left_flex REAL,
 *     biceps_right_relaxed REAL,
 *     biceps_right_flex REAL,
 *     thigh_left REAL,          -- левое бедро (верхняя треть / максимум)
 *     thigh_right REAL,         -- правое бедро (верхняя треть / максимум)
 *     hips REAL,                -- ягодицы (по пику)
 *     galife REAL,              -- «галифе» (при наличии)
 *     photo_id TEXT,
 *     updated_at INTEGER
 *   )
 */
public class ParamsRepo {

    /** Создаём таблицу при отсутствии и гарантируем наличие всех нужных колонок. */
    private static void ensureTable(Connection c) throws SQLException {
        // Базовая таблица (с минимальным набором колонок)
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS user_params(
                  user_id TEXT PRIMARY KEY,
                  weight REAL,
                  waist_navel REAL,
                  waist_max REAL,
                  chest_exhale REAL,
                  chest_relaxed REAL,
                  chest_inhale REAL,
                  biceps_left_relaxed REAL,
                  biceps_left_flex REAL,
                  biceps_right_relaxed REAL,
                  biceps_right_flex REAL,
                  thigh_left REAL,
                  thigh_right REAL,
                  hips REAL,
                  galife REAL,
                  photo_id TEXT,
                  updated_at INTEGER
                )
            """);
        }

        // Поддержка старых схем: добавляем отсутствующие колонки безопасно
        addColumnIfMissing(c, "user_params", "waist_navel", "REAL"); // было 'waist' в старых версиях
        addColumnIfMissing(c, "user_params", "waist_max", "REAL");

        addColumnIfMissing(c, "user_params", "chest_exhale", "REAL");
        addColumnIfMissing(c, "user_params", "chest_relaxed", "REAL");
        addColumnIfMissing(c, "user_params", "chest_inhale", "REAL");

        // Раньше были только biceps_relaxed / biceps_flex — теперь храним отдельно по рукам
        addColumnIfMissing(c, "user_params", "biceps_left_relaxed", "REAL");
        addColumnIfMissing(c, "user_params", "biceps_left_flex", "REAL");
        addColumnIfMissing(c, "user_params", "biceps_right_relaxed", "REAL");
        addColumnIfMissing(c, "user_params", "biceps_right_flex", "REAL");

        addColumnIfMissing(c, "user_params", "thigh_left", "REAL");
        addColumnIfMissing(c, "user_params", "thigh_right", "REAL");
        addColumnIfMissing(c, "user_params", "hips", "REAL");
        addColumnIfMissing(c, "user_params", "galife", "REAL");

        addColumnIfMissing(c, "user_params", "photo_id", "TEXT");
        addColumnIfMissing(c, "user_params", "updated_at", "INTEGER");

        // Совместимость со старым именем "waist" -> переносим в waist_navel при чтении/записи (делаем алиас на уровне upsert/getPretty)
        if (tableHasColumn(c, "user_params", "waist")) {
            // Ничего не дропаем — просто оставляем, чтобы не ломать старые данные; на запись используем новые поля
            // при чтении будем пытаться взять waist_navel, иначе legacy "waist".
        }
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

    private static boolean tableHasColumn(Connection c, String table, String col) {
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (col.equalsIgnoreCase(rs.getString("name"))) return true;
                }
            }
        } catch (SQLException ignore) {}
        return false;
    }

    /* ================= записи ================= */

    /**
     * Upsert числа (все поля nullable). Время обновления проставляется всегда.
     */
    public static void upsertNumbers(String userId,
                                     Double weight,
                                     Double waistNavel,
                                     Double waistMax,
                                     Double chEx, Double chRl, Double chIn,
                                     Double biL_Rl, Double biL_Fx,
                                     Double biR_Rl, Double biR_Fx,
                                     Double thighL, Double thighR,
                                     Double hips, Double galife) throws Exception {
        try (Connection c = Db.connect()) {
            ensureTable(c);
            long now = System.currentTimeMillis() / 1000L;
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO user_params(
                  user_id, weight, waist_navel, waist_max,
                  chest_exhale, chest_relaxed, chest_inhale,
                  biceps_left_relaxed, biceps_left_flex,
                  biceps_right_relaxed, biceps_right_flex,
                  thigh_left, thigh_right, hips, galife, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET
                  weight=excluded.weight,
                  waist_navel=excluded.waist_navel,
                  waist_max=excluded.waist_max,
                  chest_exhale=excluded.chest_exhale,
                  chest_relaxed=excluded.chest_relaxed,
                  chest_inhale=excluded.chest_inhale,
                  biceps_left_relaxed=excluded.biceps_left_relaxed,
                  biceps_left_flex=excluded.biceps_left_flex,
                  biceps_right_relaxed=excluded.biceps_right_relaxed,
                  biceps_right_flex=excluded.biceps_right_flex,
                  thigh_left=excluded.thigh_left,
                  thigh_right=excluded.thigh_right,
                  hips=excluded.hips,
                  galife=excluded.galife,
                  updated_at=excluded.updated_at
            """)) {
                int i = 1;
                ps.setString(i++, userId);
                setNullable(ps, i++, weight);
                setNullable(ps, i++, waistNavel);
                setNullable(ps, i++, waistMax);
                setNullable(ps, i++, chEx);
                setNullable(ps, i++, chRl);
                setNullable(ps, i++, chIn);
                setNullable(ps, i++, biL_Rl);
                setNullable(ps, i++, biL_Fx);
                setNullable(ps, i++, biR_Rl);
                setNullable(ps, i++, biR_Fx);
                setNullable(ps, i++, thighL);
                setNullable(ps, i++, thighR);
                setNullable(ps, i++, hips);
                setNullable(ps, i++, galife);
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
                SELECT
                  weight,
                  COALESCE(waist_navel, waist) as waist_navel_compat, -- поддержка legacy
                  waist_max,
                  chest_exhale, chest_relaxed, chest_inhale,
                  biceps_left_relaxed, biceps_left_flex,
                  biceps_right_relaxed, biceps_right_flex,
                  thigh_left, thigh_right, hips, galife,
                  updated_at
                FROM user_params WHERE user_id=?
            """)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;

                    Double w   = box(rs.getObject(1));
                    Double wNv = box(rs.getObject(2));
                    Double wMx = box(rs.getObject(3));
                    Double chE = box(rs.getObject(4));
                    Double chR = box(rs.getObject(5));
                    Double chI = box(rs.getObject(6));
                    Double biLR= box(rs.getObject(7));
                    Double biLF= box(rs.getObject(8));
                    Double biRR= box(rs.getObject(9));
                    Double biRF= box(rs.getObject(10));
                    Double thL = box(rs.getObject(11));
                    Double thR = box(rs.getObject(12));
                    Double hp  = box(rs.getObject(13));
                    Double glf = box(rs.getObject(14));

                    StringBuilder sb = new StringBuilder();
                    sb.append("📏 *Текущие параметры:*\n");
                    if (w   != null) sb.append("⚖️ Вес: ").append(trim(w)).append(" кг\n");
                    if (wNv != null) sb.append("📍 Талия (уровень пупка): ").append(trim(wNv)).append(" см\n");
                    if (wMx != null) sb.append("📍 Талия (макс. обхват): ").append(trim(wMx)).append(" см\n");

                    boolean hasChest = (chE!=null||chR!=null||chI!=null);
                    if (hasChest) {
                        sb.append("🫁 Грудь:\n");
                        sb.append(" • на выдохе: ").append(val(chE)).append(" см\n");
                        sb.append(" • расслабл.: ").append(val(chR)).append(" см\n");
                        sb.append(" • на вдохе: ").append(val(chI)).append(" см\n");
                    }

                    if (thL!=null || thR!=null) {
                        sb.append("🦵 Бедро:\n");
                        sb.append(" • левое: ").append(val(thL)).append(" см\n");
                        sb.append(" • правое: ").append(val(thR)).append(" см\n");
                    }
                    if (hp!=null)  sb.append("🍑 Ягодицы: ").append(trim(hp)).append(" см\n");
                    if (glf!=null) sb.append("〰️ Галифе: ").append(trim(glf)).append(" см\n");

                    boolean hasBiL = (biLR!=null || biLF!=null);
                    boolean hasBiR = (biRR!=null || biRF!=null);
                    if (hasBiL || hasBiR) {
                        sb.append("💪 Бицепс:\n");
                        if (hasBiL) {
                            sb.append(" • левый — расслабл.: ").append(val(biLR))
                                    .append(" см; напр.: ").append(val(biLF)).append(" см\n");
                        }
                        if (hasBiR) {
                            sb.append(" • правый — расслабл.: ").append(val(biRR))
                                    .append(" см; напр.: ").append(val(biRF)).append(" см\n");
                        }
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