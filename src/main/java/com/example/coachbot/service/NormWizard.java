package com.example.coachbot.service;

import com.example.coachbot.repo.StateRepo;
import com.example.coachbot.repo.NormRepo;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Keyboards;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;

public class NormWizard {

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        return sm;
    }

    public static SendMessage start(String adminId, long chatId, String userId, LocalDate date) throws Exception {
        String payload = userId + "|" + date.toString();
        StateRepo.set(adminId, "SET_NORM", 1, payload);
        return md(chatId, "*Укажите нормы потребления воды в день в литрах:* (например: 2.4) на" + TimeUtil.DATE_FMT.format(date) + ":");
    }

    public static SendMessage onMessage(String adminId, long chatId, String text) throws Exception {
        var st = StateRepo.get(adminId);
        if (st == null || !"SET_NORM".equals(st.type())) return null;

        String[] p = st.payload().split("\\|");
        String userId = p[0];
        LocalDate date = LocalDate.parse(p[1]);

        switch (st.step()) {
            case 1 -> {
                Double water = parseD(text);
                if (water == null) return md(chatId, "*Укажите нормы потребления воды в день в литрах:* (например: 2.4)");
                StateRepo.set(adminId, "SET_NORM", 2, st.payload() + "|" + water);
                return new SendMessage(String.valueOf(chatId), "Шаги (шт):");
            }
            case 2 -> {
                Integer steps = parseI(text);
                if (steps == null) return new SendMessage(String.valueOf(chatId), "*Укажите суточную норму шагов:* (например: 8500)");
                StateRepo.set(adminId, "SET_NORM", 3, st.payload() + "|" + steps);
                return new SendMessage(String.valueOf(chatId), "*Укажите сон в часах:* (например: 7.5)");
            }
            case 3 -> {
                Double sleep = parseD(text);
                if (sleep == null) return new SendMessage(String.valueOf(chatId), "*Укажите сон в часах:* (например: 7.5)");
                String[] arr = st.payload().split("\\|");
                Double water = Double.parseDouble(arr[2]);
                Integer steps = Integer.parseInt(arr[3]);
                NormRepo.setNorms(userId, date, water, steps, sleep, adminId);
                StateRepo.clear(adminId);
                SendMessage ok = new SendMessage(String.valueOf(chatId), "Нормы активности сохранены.");
                ok.setReplyMarkup(Keyboards.backToAdmin());
                return ok;
            }
        }
        return null;
    }

    private static Double parseD(String s) {
        try { return Double.parseDouble(s.replace(',', '.').trim()); }
        catch (Exception e) { return null; }
    }
    private static Integer parseI(String s) {
        try { return Integer.parseInt(s.trim().replace(" ", "")); }
        catch (Exception e) { return null; }
    }
}