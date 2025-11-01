package com.example.coachbot;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Часовой пояс бота. Можно переопределить через -Dbot.tz=Asia/Yekaterinburg
    private static final ZoneId ZONE = ZoneId.of(System.getProperty("bot.tz", "Asia/Yekaterinburg"));

    /**
     * Сегодня с учётом границы суток 04:00:
     * всё, что до 04:00 — относится к вчерашнему дню.
     */
    public static LocalDate today() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        if (now.toLocalTime().isBefore(LocalTime.of(4, 0))) {
            return now.toLocalDate().minusDays(1);
        }
        return now.toLocalDate();
    }

    public static LocalTime nowTime() { return LocalTime.now(ZONE); }

    /** Безопасный парсинг dd.MM.yyyy → null, если не дата/неверный формат или пришла команда */
    public static LocalDate parseDate(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("/")) return null;
        s = s.replace('-', '.').replace('/', '.');
        try { return LocalDate.parse(s, DATE_FMT); } catch (Exception ignored) { return null; }
    }

    /** Проверка "сейчас ли HH:mm" в TZ бота */
    public static boolean isNow(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0]); int m = Integer.parseInt(p[1]);
            LocalTime now = nowTime();
            return now.getHour()==h && now.getMinute()==m;
        } catch (Exception e) {
            return false;
        }
    }
}