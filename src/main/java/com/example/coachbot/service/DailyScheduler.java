package com.example.coachbot.service;

import com.example.coachbot.CoachBot;
import com.example.coachbot.repo.*;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Texts;
import com.example.coachbot.Emojis;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

public class DailyScheduler {
    private final CoachBot bot;
    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

    public DailyScheduler(CoachBot bot) { this.bot = bot; }

    public void start() {
        ses.scheduleAtFixedRate(this::tick, 3, 30, TimeUnit.SECONDS);
    }

    private static String trimCaption(String s) {
        if (s == null) return "";
        int max = 1000; // запас к лимиту Telegram (1024)
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private void tick() {
        try {
            LocalDate today = TimeUtil.today();

            // 08:00 — сценарий на сегодня (одно сообщение с фото 4.png) — общий для всех
            if (TimeUtil.isNow("08:00")) {
                List<String> users = UserRepo.allActiveUsers();
                for (String uid : users) {
                    if (!SentRepo.notSentYet("morning", uid, today)) continue;

                    String food = PlanRepo.getNutritionText(uid, today);
                    String wkt  = PlanRepo.getWorkoutText(uid, today);
                    String norm = NormRepo.getNormsText(uid, today);

                    String msg = Texts.morningScenarioTitle() + "\n\n"
                            + "🍽 План питания:\n" + food + "\n\n"
                            + "🏋️ Тренировка:\n" + wkt + "\n\n"
                            + "📊 Нормы активности:\n" + norm + "\n\n"
                            + "не забудьте заполнить дневной отчёт 📝";

                    SendPhoto sp = new SendPhoto();
                    sp.setChatId(uid);
                    sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("4.png")));
                    sp.setCaption(trimCaption(msg));

                    bot.safeExecute(sp);
                    SentRepo.markSent("morning", uid, today);
                }
            }

            // Вечерняя рассылка — индивидуальное время для КАЖДОГО админа
            var admins = UserRepo.listActiveAdminsDetailed();
            for (var adm : admins) {
                String eve = SettingsRepo.get("evening_time:" + adm.id, "19:00");
                if (!TimeUtil.isNow(eve)) continue;

                // Берём всех пользователей из группы админа (постранично)
                int total = GroupRepo.countUsersOfAdmin(adm.id);
                int size = 200;
                for (int offset = 0; offset < total; offset += size) {
                    var users = GroupRepo.usersOfAdmin(adm.id, size, offset);
                    for (String uid : users) {
                        if (!SentRepo.notSentYet("evening", uid, today)) continue;
                        if (ReportRepo.existsFor(uid, today)) continue; // отправляем только тем, у кого нет отчёта

                        String msg = Emojis.SUNSET + " Добрый вечер!\n"
                                + "Вы не загрузили отчет за сегодня.\n"
                                + "Пожалуйста, нажмите кнопку ниже, чтобы отправить дневной отчёт. " + Emojis.MUSCLE;

                        SendPhoto sp = new SendPhoto();
                        sp.setChatId(uid);
                        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("2.jpg")));
                        sp.setCaption(trimCaption(msg));
                        sp.setReplyMarkup(com.example.coachbot.Keyboards.reportButton());

                        bot.safeExecute(sp);
                        SentRepo.markSent("evening", uid, today);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}