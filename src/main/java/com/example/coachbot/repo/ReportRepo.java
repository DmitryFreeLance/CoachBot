package com.example.coachbot.repo;

import com.example.coachbot.Db;
import com.example.coachbot.Emojis;
import com.example.coachbot.TimeUtil;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportRepo {

    /** Есть ли отчёт у пользователя за указанную дату (с учётом нашей логики суток в TimeUtil.today()) */
    public static boolean existsFor(String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM reports WHERE user_id=? AND date=?")) {
            ps.setString(1, userId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Частичный upsert отчёта за «сегодня» (граница суток 04:00 задаётся в TimeUtil.today()).
     * Любые поля можно передавать null — тогда при upsert они НЕ перезатирают существующие значения.
     */
    public static void insertOrUpdateForToday(
            String userId,
            Double sleep, Integer steps, Double water,
            Integer kcal, Double p, Double f, Double c,
            String note, String photoId
    ) throws Exception {

        LocalDate d = TimeUtil.today();
        long nowTs = System.currentTimeMillis() / 1000L;

        try (Connection cconn = Db.connect();
             PreparedStatement ps = cconn.prepareStatement(
                     "INSERT INTO reports(user_id,date,sleep,steps,water,kcal,p,f,c,note,photo_id,created_at) " +
                             "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) " +
                             "ON CONFLICT(user_id,date) DO UPDATE SET " +
                             " sleep   = COALESCE(excluded.sleep,   reports.sleep)," +
                             " steps   = COALESCE(excluded.steps,   reports.steps)," +
                             " water   = COALESCE(excluded.water,   reports.water)," +
                             " kcal    = COALESCE(excluded.kcal,    reports.kcal)," +
                             " p       = COALESCE(excluded.p,       reports.p)," +
                             " f       = COALESCE(excluded.f,       reports.f)," +
                             " c       = COALESCE(excluded.c,       reports.c)," +
                             " note    = COALESCE(excluded.note,    reports.note)," +
                             " photo_id= COALESCE(excluded.photo_id,reports.photo_id)"
             )) {
            int i = 1;
            ps.setString(i++, userId);
            ps.setString(i++, d.toString());
            if (sleep == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, sleep);
            if (steps == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, steps);
            if (water == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, water);
            if (kcal == null) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, kcal);
            if (p == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, p);
            if (f == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, f);
            if (c == null) ps.setNull(i++, Types.REAL); else ps.setDouble(i++, c);
            if (note == null) ps.setNull(i++, Types.VARCHAR); else ps.setString(i++, note);
            if (photoId == null) ps.setNull(i++, Types.VARCHAR); else ps.setString(i++, photoId);
            ps.setLong(i, nowTs);
            ps.executeUpdate();
        }
    }

    /** 📸 Добавить одно фото еды за конкретную дату (складывается в report_photos) */
    public static void addFoodPhoto(String userId, LocalDate date, String fileId) throws Exception {
        long nowTs = System.currentTimeMillis() / 1000L;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO report_photos(user_id,date,file_id,created_at) VALUES(?,?,?,?)")) {
            ps.setString(1, userId);
            ps.setString(2, date.toString());
            ps.setString(3, fileId);
            ps.setLong(4, nowTs);
            ps.executeUpdate();
        }
    }

    public static int countFoodPhotos(String userId, LocalDate date) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM report_photos WHERE user_id=? AND date=?")) {
            ps.setString(1, userId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static List<String> listFoodPhotos(String userId, LocalDate date) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT file_id FROM report_photos WHERE user_id=? AND date=? ORDER BY created_at, rowid")) {
            ps.setString(1, userId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    /** Сколько отчётов у пользователя всего */
    public static int countByUser(String userId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM reports WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Список строк-карточек отчётов для пользователя, постранично.
     * @param desc true — новые сверху; false — старые сверху
     */
    public static List<String> listByUser(String userId, int page, int size, boolean desc) throws Exception {
        int offset = (Math.max(1, page) - 1) * Math.max(1, size);
        String order = desc ? "DESC" : "ASC";

        String sql = "SELECT date,sleep,steps,water,kcal,p,f,c,note,photo_id " +
                "FROM reports WHERE user_id=? " +
                "ORDER BY date " + order + " LIMIT ? OFFSET ?";

        List<String> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, size);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = LocalDate.parse(rs.getString("date"));
                    String dateTxt = TimeUtil.DATE_FMT.format(d);

                    StringBuilder sb = new StringBuilder();
                    sb.append("📅 *").append(dateTxt).append("*\n");

                    Object sleep = rs.getObject("sleep");
                    Object steps = rs.getObject("steps");
                    Object water = rs.getObject("water");
                    Object kcal  = rs.getObject("kcal");
                    Object pp    = rs.getObject("p");
                    Object ff    = rs.getObject("f");
                    Object cc    = rs.getObject("c");
                    String note  = rs.getString("note");
                    String photo = rs.getString("photo_id");

                    if (sleep != null) sb.append("😴 Сон: ").append(sleep).append(" ч\n");
                    if (steps != null) sb.append("🚶 Шаги: ").append(steps).append("\n");
                    if (water != null) sb.append("💧 Вода: ").append(water).append(" л\n");

                    boolean hasKbju = (kcal != null || pp != null || ff != null || cc != null);
                    if (hasKbju) {
                        sb.append(Emojis.FIRE).append(" Калории: ").append(val(kcal)).append("\n")
                                .append(Emojis.MEAT).append(" Белки: ").append(val(pp)).append("\n")
                                .append(Emojis.AVOCADO).append(" Жиры: ").append(val(ff)).append("\n")
                                .append(Emojis.BREAD).append(" Углеводы: ").append(val(cc)).append("\n");
                    }

                    // Новое: показываем кол-во фото еды за день (из report_photos),
                    // иначе (legacy) — отметку про одиночный скрин.
                    int photosCount = countFoodPhotos(userId, d);
                    if (photosCount > 0) {
                        sb.append("📸 Фото еды: ").append(photosCount).append(" шт.\n");
                    } else if (photo != null && !photo.isBlank()) {
                        sb.append("🖼 Приложен скрин.\n");
                    }

                    if (note != null && !note.isBlank()) {
                        sb.append("📝 Заметка: ").append(note).append("\n");
                    }

                    out.add(sb.toString().trim());
                }
            }
        }
        return out;
    }

    // Удобный вывод null → "—"
    private static String val(Object o) {
        return o == null ? "—" : String.valueOf(o);
    }
}