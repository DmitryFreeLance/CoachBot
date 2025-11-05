package com.example.coachbot;

import com.example.coachbot.service.DailyScheduler;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;

public class App {

    public static void main(String[] args) throws Exception {
        // ENV с дефолтами
        final String token        = System.getenv().getOrDefault("BOT_TOKEN",    "8571487160:AAFrBM6eVKmN8lf31RoGLYeZDKDLg7UsepI");
        final String username     = System.getenv().getOrDefault("BOT_USERNAME", "@justheckbot45_bot");
        final String superAdmins  = System.getenv().getOrDefault("SUPERADMINS",  "726773708");
        final String dbPath       = System.getenv().getOrDefault("DB_PATH",      "./data/bot.db");
        final String tz           = System.getenv().getOrDefault("TZ",           "Asia/Yekaterinburg");

        // Проставляем системные свойства, которые использует код (например, TimeUtil.today() читает bot.tz)
        System.setProperty("bot.tz", tz);
        System.setProperty("bot.db", dbPath);        // если где-то читается через System.getProperty
        System.setProperty("super.admins", superAdmins); // на случай, если будет нужно из других мест

        // Создадим каталог под БД (на случай локального запуска без Docker volume)
        File dbFile = new File(dbPath).getAbsoluteFile();
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        System.out.printf("Starting CoachBot… tz=%s db=%s superAdmins=%s%n", tz, dbPath, superAdmins);

        // Инициализация БД и служебных таблиц/настроек (внутри Db.init добавь OutboxRepo.init(), CommandGuardRepo.init(), и т.п.)
        Db.init();

        // Регистрация бота
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        CoachBot bot = new CoachBot(username, token);
        api.registerBot(bot);

        // Планировщик (08:00 и вечернее время)
        DailyScheduler scheduler = new DailyScheduler(bot);
        scheduler.start();
    }
}