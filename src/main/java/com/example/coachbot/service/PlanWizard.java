package com.example.coachbot.service;

import com.example.coachbot.repo.PlanRepo;
import com.example.coachbot.repo.StateRepo;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Keyboards;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;

public class PlanWizard {

    // Хелпер для Markdown-сообщений
    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN); // при необходимости можно сменить на "MarkdownV2"
        return sm;
    }

    public static SendMessage start(String adminId, long chatId, String userId, LocalDate date) throws Exception {
        String payload = userId + "|" + date.toString();
        StateRepo.set(adminId, "SET_PLAN", 1, payload);

        SendMessage sm = md(
                chatId,
                "Введите упражнение №1 для " + TimeUtil.DATE_FMT.format(date) + ".\n" +
                        "Отправляйте каждое упражнение *отдельным сообщением*. Когда закончите — нажмите «Установить план»."
        );
        sm.setReplyMarkup(Keyboards.planFinalizeButton());
        return sm;
    }

    public static SendMessage onMessage(String adminId, long chatId, String text) throws Exception {
        var st = StateRepo.get(adminId);
        if (st == null || !"SET_PLAN".equals(st.type())) return null;

        String[] p = st.payload().split("\\|");
        String userId = p[0];
        LocalDate date = LocalDate.parse(p[1]);

        PlanRepo.addWorkoutLine(userId, date, text.trim(), adminId);
        return new SendMessage(String.valueOf(chatId), "Добавлено. Следующее упражнение или нажмите «Установить план».");
    }

    public static SendMessage onFinish(String adminId, long chatId) throws Exception {
        var st = StateRepo.get(adminId);
        if (st == null || !"SET_PLAN".equals(st.type())) return null;

        String[] p = st.payload().split("\\|");
        String userId = p[0];
        LocalDate date = LocalDate.parse(p[1]);

        StateRepo.clear(adminId);
        return new SendMessage(String.valueOf(chatId), "План тренировки на " + TimeUtil.DATE_FMT.format(date) + " установлен.");
    }
}