package com.example.coachbot.service;

import com.example.coachbot.repo.StateRepo;
import com.example.coachbot.repo.PlanRepo;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Keyboards;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;

public class CaloriesWizard {

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        return sm;
    }

    public static SendMessage start(String adminId, long chatId, String userId, LocalDate date) throws Exception {
        String payload = userId + "|" + date.toString();
        StateRepo.set(adminId, "SET_CAL", 1, payload);
        return md(chatId,
                "Введите *калории* на " + TimeUtil.DATE_FMT.format(date) + " " +
                        "или сразу *КБЖУ через запятую* (например: `1778,133,59,178`).\n" +
                        "_Числа до 5 цифр._");
    }

    public static SendMessage onMessage(String adminId, long chatId, String text) throws Exception {
        var st = StateRepo.get(adminId);
        if (st==null || !"SET_CAL".equals(st.type())) return null;
        String[] p = st.payload().split("\\|");
        String userId = p[0]; LocalDate date = LocalDate.parse(p[1]);

        switch (st.step()) {
            case 1 -> {
                // Вариант 1: вся строка "kcal,p,f,c"
                String[] parts = text.split("[,; ]+");
                if (parts.length >= 4) {
                    Integer kcal = parseIntLimited(parts[0], 5);
                    Double prot  = parseDLimited(parts[1], 5);
                    Double fat   = parseDLimited(parts[2], 5);
                    Double carb  = parseDLimited(parts[3], 5);
                    if (kcal==null || prot==null || fat==null || carb==null) {
                        return md(chatId, "Формат: `1778,133,59,178`. Каждый параметр — до 5 цифр.");
                    }
                    PlanRepo.setNutrition(userId, date, kcal, prot, fat, carb, adminId);
                    StateRepo.clear(adminId);
                    SendMessage done = new SendMessage(String.valueOf(chatId),
                            "План питания установлен.\n" +
                                    "🔥 Калории: " + kcal + "\n🥩 Белки: " + prot + "\n🥑 Жиры: " + fat + "\n🍞 Углеводы: " + carb);
                    done.setReplyMarkup(Keyboards.backToAdmin());
                    return done;
                }

                // Вариант 2: по шагам
                Integer kcal = parseIntLimited(text, 5);
                if (kcal==null) return md(chatId, "Введите целое число калорий или строку `kcal,б,ж,у`.");
                StateRepo.set(adminId, "SET_CAL", 2, st.payload()+"|"+kcal);
                return md(chatId, "Введите число белков: (например: 80)");
            }
            case 2 -> {
                Double prot = parseDLimited(text, 5);
                if (prot==null) return md(chatId, "Введите число белков: (например: 80)");
                StateRepo.set(adminId, "SET_CAL", 3, st.payload()+"|"+prot);
                return md(chatId, "Введите число жиров: (например: 50):");
            }
            case 3 -> {
                Double fat = parseDLimited(text, 5);
                if (fat==null) return md(chatId, "Введите число жиров (например: 50).");
                StateRepo.set(adminId, "SET_CAL", 4, st.payload()+"|"+fat);
                return md(chatId, "Введите число углеводов: (например: 120):");
            }
            case 4 -> {
                Double carb = parseDLimited(text, 5);
                if (carb==null) return md(chatId, "Введите число углеводов (например: 120).");
                String[] arr = st.payload().split("\\|");
                Integer kcal = Integer.parseInt(arr[2]);
                Double prot  = Double.parseDouble(arr[3]);
                Double fat   = Double.parseDouble(arr[4]);
                PlanRepo.setNutrition(userId, date, kcal, prot, fat, carb, adminId);
                StateRepo.clear(adminId);
                SendMessage sm = new SendMessage(String.valueOf(chatId),
                        "План питания установлен.\n" +
                                "🔥 Калории: " + kcal + "\n🥩 Белки: " + prot + "\n🥑 Жиры: " + fat + "\n🍞 Углеводы: " + carb);
                sm.setReplyMarkup(Keyboards.backToAdmin());
                return sm;
            }
        }
        return null;
    }

    private static Integer parseIntLimited(String s, int maxDigits){
        try {
            String t = s.trim().replace(" ","");
            if (!t.matches("^\\d{1,"+maxDigits+"}$")) return null;
            return Integer.parseInt(t);
        } catch(Exception e){ return null; }
    }
    private static Double parseDLimited(String s, int maxDigits){
        try {
            String t = s.replace(',','.').trim();
            String digits = t.replace(".","");
            if (!digits.matches("^\\d{1,"+maxDigits+"}$")) return null;
            return Double.parseDouble(t);
        } catch(Exception e){ return null; }
    }
}