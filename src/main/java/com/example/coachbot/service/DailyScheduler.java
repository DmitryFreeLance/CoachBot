package com.example.coachbot.service;

import com.example.coachbot.CoachBot;
import com.example.coachbot.Emojis;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.repo.*;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Планировщик:
 *  - 08:00 — общий утренний сценарий (как было) + напоминание админам о клиентах без отчёта за вчера.
 *  - Вечер — индивидуально по каждому администратору (evening_time:<adminId> / evening_time).
 *  - Вечерняя рассылка: только тем пользователям, у кого нет отчёта за «сегодня».
 */
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

            // 08:00 — сценарий на сегодня (одно сообщение с фото 4.png)
            if (TimeUtil.isNow("08:00")) {
                // Пользователи: утренний сценарий (как было)
                List<String> users = UserRepo.allActiveUsers();
                for (String uid : users) {
                    if (!SentRepo.notSentYet("morning", uid, today)) continue;

                    String food = PlanRepo.getNutritionText(uid, today);
                    String wkt  = PlanRepo.getWorkoutText(uid, today);
                    String norm = NormRepo.getNormsText(uid, today);

                    String msg = com.example.coachbot.Texts.morningScenarioTitle() + "\n\n"
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

                // Админы: напоминание о клиентах, кто не прислал отчёт за ВЧЕРА
                LocalDate yesterday = today.minusDays(1);
                List<UserRepo.UserRow> admins = UserRepo.listActiveAdminsDetailed();
                for (UserRepo.UserRow a : admins) {
                    String adminId = a.id;
                    if (!SentRepo.notSentYet("morning_admin", adminId, today)) continue;

                    List<String> groupUsers = getAllUsersOfAdmin(adminId);
                    List<String> noReport = new ArrayList<>();
                    for (String uid : groupUsers) {
                        if (!ReportRepo.existsFor(uid, yesterday)) {
                            noReport.add(uid);
                        }
                    }
                    if (!noReport.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("🔔 Утреннее напоминание для тренера\n")
                                .append("Клиенты без отчёта за ").append(TimeUtil.DATE_FMT.format(yesterday)).append(":\n");
                        int i = 1;
                        for (String uid : noReport) {
                            sb.append(i++).append(". tg_id: ").append(uid).append("\n");
                        }
                        SendMessage sm = new SendMessage(adminId, sb.toString().trim());
                        sm.setReplyMarkup(com.example.coachbot.Keyboards.backToAdmin());
                        bot.safeExecute(sm);
                    }
                    // помечаем, даже если список пуст — чтобы не слать повторно в эту минуту
                    SentRepo.markSent("morning_admin", adminId, today);
                }
            }

            // Вечерняя рассылка — для каждой группы по времени её админа
            List<UserRepo.UserRow> admins = UserRepo.listActiveAdminsDetailed();
            for (UserRepo.UserRow a : admins) {
                String adminId = a.id;
                String time = SettingsRepo.get("evening_time:" + adminId, null);
                if (time == null || time.isBlank()) {
                    time = SettingsRepo.get("evening_time", "19:00"); // общий фолбэк
                }
                if (!TimeUtil.isNow(time)) continue;

                // 2) список пользователей этой группы
                List<String> groupUsers = getAllUsersOfAdmin(adminId);
                for (String uid : groupUsers) {
                    if (!SentRepo.notSentYet("evening:"+adminId, uid, today)) continue;
                    if (ReportRepo.existsFor(uid, today)) continue; // отправляем только тем, у кого нет отчёта

                    String msg = Emojis.SUNSET + " Добрый вечер!\n"
                            + "Вы ещё не загрузили отчёт за сегодня.\n"
                            + "Пожалуйста, нажмите кнопку ниже и заполните дневной отчёт. " + Emojis.MUSCLE;

                    SendPhoto sp = new SendPhoto();
                    sp.setChatId(uid);
                    sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("2.jpg")));
                    sp.setCaption(trimCaption(msg));
                    sp.setReplyMarkup(com.example.coachbot.Keyboards.reportButton());

                    bot.safeExecute(sp);
                    SentRepo.markSent("evening:"+adminId, uid, today);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getAllUsersOfAdmin(String adminId) throws Exception {
        int total = GroupRepo.countUsersOfAdmin(adminId);
        List<String> out = new ArrayList<>(total);
        int size = 200;
        for (int offset = 0; offset < total; offset += size) {
            out.addAll(GroupRepo.usersOfAdmin(adminId, size, offset));
        }
        return out;
    }
}