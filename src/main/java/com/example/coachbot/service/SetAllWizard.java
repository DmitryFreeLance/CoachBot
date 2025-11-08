package com.example.coachbot.service;

import com.example.coachbot.repo.StateRepo;
import com.example.coachbot.repo.PlanRepo;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Keyboards;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;

/**
 * Единый визард "Установить параметры" для админа:
 * Шаги:
 *  1) ввод даты (текстом dd.MM.yyyy или быстрый выбор в CoachBot) -> перевод на шаг 2
 *  2) калории
 *  3) белки
 *  4) жиры
 *  5) углеводы  -> сохраняем КБЖУ
 *  6) ввод упражнений по одному сообщению (много раз), кнопка "✅ Завершить план"
 *  7) вода (л)
 *  8) шаги
 *  9) сон (ч)  -> сохраняем нормы, финал
 *
 * payload формата: userId|yyyy-MM-dd|accum_workout_lines (для шага 6; строки через \n)
 */
public class SetAllWizard {

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        return sm;
    }

    /** Старт после выбора даты. Переходим к шагу 2 (калории). */
    public static SendMessage start(String adminId, long chatId, String userId, LocalDate date) throws Exception {
        String payload = userId + "|" + date;
        StateRepo.set(adminId, "SET_ALL", 2, payload);
        return md(chatId,
                "Шаг 1/3: *КБЖУ*\n" +
                        "🔥 Введите калории на " + TimeUtil.DATE_FMT.format(date) + ":\n \n (Например: 1600)");
    }

    public static SendMessage onMessage(String adminId, long chatId, String text) throws Exception {
        var st = StateRepo.get(adminId);
        if (st == null || !"SET_ALL".equals(st.type())) return null;

        String[] p = st.payload().split("\\|", -1);
        String userId = p[0];
        // Для шага 1 payload содержит только userId; для остальных — userId|date...
        LocalDate date = (p.length >= 2 && p[1] != null && !p[1].isBlank())
                ? LocalDate.parse(p[1])
                : null;

        switch (st.step()) {
            // ===== НОВОЕ: ручной ввод даты =====
            case 1 -> {
                LocalDate d = TimeUtil.parseDate(text);
                if (d == null) {
                    SendMessage err = md(chatId,
                            "Укажите дату в формате `dd.MM.yyyy`, например: `12.11.2025`.\n" +
                                    "Или воспользуйтесь быстрыми кнопками выбора даты.");
                    err.setReplyMarkup(Keyboards.backToAdmin());
                    return err;
                }
                // Переходим к обычному сценарию (КБЖУ)
                return start(adminId, chatId, userId, d);
            }

            // ===== КБЖУ последовательно =====
            case 2 -> { // kcal
                Integer kcal = parseIntLimited(text, 5);
                if (kcal == null) return md(chatId, "🔥Введите целое число калорий:\n (Например: 1600)");
                StateRepo.set(adminId, "SET_ALL", 3, st.payload() + "|kcal=" + kcal);
                return md(chatId, "🥩Теперь введите белки (гр):\n (Например: 80)");
            }
            case 3 -> { // proteins
                Double prot = parseDLimited(text, 5);
                if (prot == null) return md(chatId, "🥩Теперь введите белки (гр):\n (Например: 80)");
                StateRepo.set(adminId, "SET_ALL", 4, st.payload() + "|p=" + prot);
                return md(chatId, "🥑Теперь введите жиры (гр):\n (Например: 50)");
            }
            case 4 -> { // fats
                Double fat = parseDLimited(text, 5);
                if (fat == null) return md(chatId, "🥑Теперь введите жиры (гр):\n(Например: 50)");
                StateRepo.set(adminId, "SET_ALL", 5, st.payload() + "|f=" + fat);
                return md(chatId, "🍞Теперь введите углеводы (гр):\n (Например: 120)");
            }
            case 5 -> { // carbs -> save nutrition
                Double carb = parseDLimited(text, 5);
                if (carb == null) return md(chatId, "🍞Теперь введите углеводы (гр):\n (Например: 120)");

                // извлечь kcal,p,f из payload
                Integer kcal = null; Double prot=null, fat=null;
                String[] parts = st.payload().split("\\|");
                for (String s : parts) {
                    if (s.startsWith("kcal=")) kcal = tryInt(s.substring(5));
                    if (s.startsWith("p="))    prot = tryD(s.substring(2));
                    if (s.startsWith("f="))    fat  = tryD(s.substring(2));
                }
                // на этом шаге дата уже обязана быть известна (через start() / date:setall)
                if (date == null) {
                    // Защита от рассинхрона состояния
                    return md(chatId, "Не определена дата. Вернитесь и выберите дату заново.");
                }
                PlanRepo.setNutrition(userId, date, kcal, prot, fat, carb, adminId);

                // Далее: план тренировки
                String payload = userId + "|" + date + "|";
                StateRepo.set(adminId, "SET_ALL", 6, payload);

                SendMessage sm = new SendMessage(String.valueOf(chatId),
                        "Шаг 2/3: План тренировки\n" +
                                "Отправляйте каждое упражнение отдельным сообщением.\n" +
                                "Когда закончите — нажмите «Завершить план». ");
                sm.setReplyMarkup(Keyboards.allPlanFinalizeButton());
                return sm;
            }

            case 6 -> { // накапливаем упражнения
                String line = text == null ? "" : text.trim();
                if (line.isBlank()) {
                    SendMessage hint = new SendMessage(String.valueOf(chatId),
                            "Сообщение пустое. Пришлите упражнение или нажмите «Завершить план».");
                    hint.setReplyMarkup(Keyboards.allPlanFinalizeButton());
                    return hint;
                }
                String acc = p.length >= 3 ? p[2] : "";
                acc = acc.isBlank() ? line : (acc + "\n" + line);
                StateRepo.set(adminId, "SET_ALL", 6, p[0] + "|" + p[1] + "|" + acc);

                SendMessage ok = new SendMessage(String.valueOf(chatId),
                        "✅Упражнение Добавлено.\nВведите следующее упражнение или нажмите «Завершить план».");
                ok.setReplyMarkup(Keyboards.allPlanFinalizeButton());
                return ok;
            }

            // ===== нормы последовательно =====
            case 7 -> { // вода
                Double water = parseD(text);
                if (water == null) return md(chatId, "💧Укажите нормы потребления воды в день в литрах: \n(например: 2.4)");
                StateRepo.set(adminId, "SET_ALL", 8, st.payload() + "|water=" + water);
                return new SendMessage(String.valueOf(chatId), "🏃Укажите суточную норму шагов:\n (например: 8500)");
            }
            case 8 -> { // шаги
                Integer steps = parseI(text);
                if (steps == null) return new SendMessage(String.valueOf(chatId), "🏃Укажите суточную норму шагов:\n (например: 8500)");
                StateRepo.set(adminId, "SET_ALL", 9, st.payload() + "|steps=" + steps);
                return new SendMessage(String.valueOf(chatId), "😴Укажите норму сна в часах:\n (например: 7.5)");
            }
            case 9 -> { // сон -> save norms, финал
                Double sleep = parseD(text);
                if (sleep == null) return new SendMessage(String.valueOf(chatId), "😴Укажите норму сна в часах:\n (например: 7.5)");
                Double water=null; Integer steps=null;

                for (String s : st.payload().split("\\|")) {
                    if (s.startsWith("water=")) water = tryD(s.substring(6));
                    if (s.startsWith("steps=")) steps = tryInt(s.substring(6));
                }

                // дата известна с шага 2
                if (date == null) {
                    return md(chatId, "Не определена дата. Вернитесь и выберите дату заново.");
                }

                com.example.coachbot.repo.NormRepo.setNorms(userId, date, water, steps, sleep, adminId);
                StateRepo.clear(adminId);

                SendMessage ok = new SendMessage(String.valueOf(chatId),
                        "Готово! Все параметры на " + TimeUtil.DATE_FMT.format(date) + " установлены.\n" +
                                "• КБЖУ сохранены\n• План тренировки сохранён\n• Нормы активности сохранены");
                ok.setReplyMarkup(Keyboards.backToAdmin());
                return ok;
            }
        }
        return null;
    }

    /** Завершение шага 6 (план) по колбэку: перенос в шаг 7 и сохранение упражнений. */
    public static SendMessage finishPlan(String adminId, long chatId) throws Exception {
        var st = StateRepo.get(adminId);
        if (st == null || !"SET_ALL".equals(st.type()) || st.step() != 6) return null;

        String[] p = st.payload().split("\\|", -1);
        String userId = p[0];
        LocalDate date = LocalDate.parse(p[1]);
        String acc = p.length >= 3 ? p[2] : "";

        // Сохраняем каждую непустую строку как упражнение
        if (!acc.isBlank()) {
            for (String line : acc.split("\\n")) {
                if (!line.isBlank()) {
                    PlanRepo.addWorkoutLine(userId, date, line.trim(), adminId);
                }
            }
        }

        // Нормы: вода
        StateRepo.set(adminId, "SET_ALL", 7, p[0] + "|" + p[1]);
        return md(chatId,
                "Шаг 3/3: Нормы активности\n" +
                        "💧Введите воду в литрах на " + TimeUtil.DATE_FMT.format(date) + ":\n (например: 3)");
    }

    /* ================= helpers ================= */

    private static Integer parseIntLimited(String s, int maxDigits) {
        try {
            String t = s.trim().replace(" ","");
            if (!t.matches("^\\d{1,"+maxDigits+"}$")) return null;
            return Integer.parseInt(t);
        } catch (Exception e) { return null; }
    }
    private static Double parseDLimited(String s, int maxDigits) {
        try {
            String t = s.replace(',','.').trim();
            String digits = t.replace(".","");
            if (!digits.matches("^\\d{1,"+maxDigits+"}$")) return null;
            return Double.parseDouble(t);
        } catch (Exception e) { return null; }
    }
    private static Double parseD(String s) {
        try { return Double.parseDouble(s.replace(',', '.').trim()); }
        catch (Exception e) { return null; }
    }
    private static Integer parseI(String s) {
        try { return Integer.parseInt(s.trim().replace(" ", "")); }
        catch (Exception e) { return null; }
    }
    private static Integer tryInt(String s){ try { return Integer.parseInt(s); } catch(Exception e){ return null; } }
    private static Double  tryD(String s){ try { return Double.parseDouble(s); } catch(Exception e){ return null; } }
}