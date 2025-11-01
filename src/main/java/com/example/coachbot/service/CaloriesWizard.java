package com.example.coachbot.service;

import com.example.coachbot.repo.StateRepo;
import com.example.coachbot.repo.PlanRepo;
import com.example.coachbot.TimeUtil;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;

public class CaloriesWizard {

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN); // или "MarkdownV2"
        return sm;
    }

    public static SendMessage start(String adminId, long chatId, String userId, LocalDate date) throws Exception {
        String payload = userId + "|" + date.toString();
        StateRepo.set(adminId, "SET_CAL", 1, payload);
        return md(chatId, "Введите *калории* на " + TimeUtil.DATE_FMT.format(date) + ":");
    }

    public static SendMessage onMessage(String adminId, long chatId, String text) throws Exception {
        var st = StateRepo.get(adminId);
        if (st==null || !"SET_CAL".equals(st.type())) return null;
        String[] p = st.payload().split("\\|");
        String userId = p[0]; LocalDate date = LocalDate.parse(p[1]);

        switch (st.step()) {
            case 1 -> {
                Integer kcal = parseInt(text);
                if (kcal==null) return md(chatId, "Введите целое число калорий.");
                StateRepo.set(adminId, "SET_CAL", 2, st.payload()+"|"+kcal);
                return md(chatId, "Теперь введите *белки (г)*:");
            }
            case 2 -> {
                Double prot = parseD(text);
                if (prot==null) return md(chatId, "Введите число (г).");
                StateRepo.set(adminId, "SET_CAL", 3, st.payload()+"|"+prot);
                return md(chatId, "Теперь *жиры (г)*:");
            }
            case 3 -> {
                Double fat = parseD(text);
                if (fat==null) return md(chatId, "Введите число (г).");
                StateRepo.set(adminId, "SET_CAL", 4, st.payload()+"|"+fat);
                return md(chatId, "Теперь *углеводы (г)*:");
            }
            case 4 -> {
                Double carb = parseD(text);
                if (carb==null) return md(chatId, "Введите число (г).");
                String[] arr = st.payload().split("\\|");
                Integer kcal = Integer.parseInt(arr[2]);
                Double prot = Double.parseDouble(arr[3]);
                Double fat = Double.parseDouble(arr[4]);
                PlanRepo.setNutrition(userId, date, kcal, prot, fat, carb, adminId);
                StateRepo.clear(adminId);
                return new SendMessage(String.valueOf(chatId),
                        "План питания установлен.\n" +
                                "🔥 Калории: " + kcal + "\n🥩 Белки: " + prot + "\n🥑 Жиры: " + fat + "\n🍞 Углеводы: " + carb);
            }
        }
        return null;
    }

    private static Integer parseInt(String s){ try { return Integer.parseInt(s.trim().replace(" ","")); } catch(Exception e){ return null; } }
    private static Double parseD(String s){ try { return Double.parseDouble(s.replace(',','.').trim()); } catch(Exception e){ return null; } }
}