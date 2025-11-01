package com.example.coachbot.service;

import com.example.coachbot.CoachBot;
import com.example.coachbot.repo.*;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Texts;
import com.example.coachbot.Emojis;
import com.example.coachbot.Db;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

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

    private void tick() {
        try {
            String eve = SettingsRepo.get("evening_time", "19:00");
            LocalDate today = TimeUtil.today();

            // 08:00 — сценарий на сегодня
            if (TimeUtil.isNow("08:00")) {
                List<String> users = UserRepo.allActiveUsers();
                for (String uid : users) {
                    if (!SentRepo.notSentYet("morning", uid, today)) continue;
                    String food = PlanRepo.getNutritionText(uid, today);
                    String wkt = PlanRepo.getWorkoutText(uid, today);
                    String norm = NormRepo.getNormsText(uid, today);
                    String msg = Texts.morningScenarioTitle() + "\n\n"
                            + "🍽 План питания:\n" + food + "\n\n"
                            + "🏋️ Тренировка:\n" + wkt + "\n\n"
                            + "📊 Нормы активности:\n" + norm + "\n\n"
                            + Emojis.TARGET + " Каждая тренировка приближает вас к цели! " + Emojis.MUSCLE;
                    bot.safeExecute(new SendMessage(uid, msg));
                    SentRepo.markSent("morning", uid, today);
                }
            }

            // Вечерняя рассылка
            if (TimeUtil.isNow(eve)) {
                List<String> users = UserRepo.allActiveUsers();
                for (String uid : users) {
                    if (!SentRepo.notSentYet("evening", uid, today)) continue;
                    SendMessage sm = new SendMessage(uid, Texts.eveningBroadcast());
                    sm.setReplyMarkup(com.example.coachbot.Keyboards.reportButton());
                    bot.safeExecute(sm);
                    SentRepo.markSent("evening", uid, today);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}