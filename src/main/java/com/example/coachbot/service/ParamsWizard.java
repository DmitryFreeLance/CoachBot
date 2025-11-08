package com.example.coachbot.service;

import com.example.coachbot.Emojis;
import com.example.coachbot.Keyboards;
import com.example.coachbot.repo.ParamsRepo;
import com.example.coachbot.repo.StateRepo;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.File;

/**
 * Визард «Мои параметры» — расширенная версия.
 *
 * Обновлено:
 *  - На каждом шаге есть «⏭ Пропустить замер».
 *  - Порядок шагов: Галифе (шаг 4) идёт *до* Ягодиц (шаг 5).
 */
public class ParamsWizard {

    private static final String TYPE = "PARAMS";

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        sm.setReplyMarkup(Keyboards.paramsSkipOrCancel()); // везде разрешаем «пропустить»
        return sm;
    }

    public static Object start(String userId, long chatId) throws Exception {
        // 14 числовых слотов без фото
        StateRepo.set(userId, TYPE, 1, "||||||||||||||");

        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("8.png"))); // вес
        sp.setCaption("Шаг 1/11. Введите *вес* в кг (например: `82.5`).");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    public static SendMessage cancel(String userId, long chatId) throws Exception {
        StateRepo.clear(userId);
        SendMessage sm = new SendMessage(String.valueOf(chatId), "Ввод параметров отменён.");
        sm.setReplyMarkup(com.example.coachbot.Keyboards.backToMenu());
        return sm;
    }

    /** Пропустить текущий шаг (универсально для всех шагов 1..11). */
    public static Object skip(String userId, long chatId) throws Exception {
        var st = StateRepo.get(userId);
        if (st == null || !TYPE.equals(st.type())) {
            return md(chatId, "Сейчас нечего пропускать.");
        }

        int step = st.step();
        String[] p = slots(st.payload());

        switch (step) {
            case 1 -> { p[0]  = ""; StateRepo.set(userId, TYPE, 2,  join(p)); return askThighLeft(chatId); }
            case 2 -> { p[1]  = ""; StateRepo.set(userId, TYPE, 3,  join(p)); return askThighRight(chatId); }
            // шаг 4 теперь ГАЛИФЕ -> сохраняем в p[4]
            case 3 -> { p[2]  = ""; StateRepo.set(userId, TYPE, 4,  join(p)); return askGalife(chatId); }
            case 4 -> { p[4]  = ""; StateRepo.set(userId, TYPE, 5,  join(p)); return askHips(chatId); }
            // шаг 5 ЯГОДИЦЫ -> сохраняем в p[3]
            case 5 -> { p[3]  = ""; StateRepo.set(userId, TYPE, 6,  join(p)); return askWaistNavel(chatId); }
            case 6 -> { p[5]  = ""; StateRepo.set(userId, TYPE, 7,  join(p)); return askWaistMax(chatId); }
            case 7 -> { p[6]  = ""; StateRepo.set(userId, TYPE, 8,  join(p)); return askChest(chatId); }
            case 8 -> { p[7]=p[8]=p[9] = ""; StateRepo.set(userId, TYPE, 9,  join(p)); return askBicepsLeft(chatId); }
            case 9 -> { p[10]=p[11]   = ""; StateRepo.set(userId, TYPE, 10, join(p)); return askBicepsRight(chatId); }
            case 10-> { p[12]=p[13]   = ""; StateRepo.set(userId, TYPE, 11, join(p)); return askPhoto(chatId); }
            case 11-> { // пропуск фото = сохранение чисел без фото
                persistNumbers(userId, st.payload());
                StateRepo.clear(userId);
                SendMessage ok = new SendMessage(String.valueOf(chatId),
                        "Параметры сохранены без фото. " + Emojis.CHECK);
                ok.setReplyMarkup(com.example.coachbot.Keyboards.backToMenu());
                return ok;
            }
            default -> { return md(chatId, "Сейчас нечего пропускать."); }
        }
    }

    public static Object onAny(String userId, long chatId, Message msg) throws Exception {
        var st = StateRepo.get(userId);
        if (st == null || !TYPE.equals(st.type())) return null;

        switch (st.step()) {
            case 1 -> { // вес
                Double weight = parseD(msg.getText());
                if (weight == null) return md(chatId, "Введите число, например: `82.5`");
                String[] p = slots(st.payload());
                p[0] = String.valueOf(weight);
                StateRepo.set(userId, TYPE, 2, join(p));
                return askThighLeft(chatId);
            }
            case 2 -> { // левое бедро
                Double v = parseD(msg.getText());
                if (v == null) return md(chatId, "Введите число, например: `58.5`");
                String[] p = slots(st.payload()); p[1] = String.valueOf(v);
                StateRepo.set(userId, TYPE, 3, join(p));
                return askThighRight(chatId);
            }
            case 3 -> { // правое бедро
                Double v = parseD(msg.getText());
                if (v == null) return md(chatId, "Введите число, например: `58.0`");
                String[] p = slots(st.payload()); p[2] = String.valueOf(v);
                StateRepo.set(userId, TYPE, 4, join(p));
                return askGalife(chatId); // <<< теперь галифе
            }
            case 4 -> { // галифе (можно "нет") -> p[4]
                String txt = msg.hasText() ? msg.getText().trim().toLowerCase() : "";
                Double v = ("нет".equals(txt) || "no".equals(txt) || "-".equals(txt)) ? null : parseD(txt);
                if (v == null && !( "нет".equals(txt) || "no".equals(txt) || "-".equals(txt))) {
                    return md(chatId, "Введите число (см) или напишите `нет`.");
                }
                String[] p = slots(st.payload()); p[4] = v == null ? "" : String.valueOf(v);
                StateRepo.set(userId, TYPE, 5, join(p));
                return askHips(chatId); // далее ягодицы
            }
            case 5 -> { // ягодицы -> p[3]
                Double v = parseD(msg.getText());
                if (v == null) return md(chatId, "Введите число, например: `96`");
                String[] p = slots(st.payload()); p[3] = String.valueOf(v);
                StateRepo.set(userId, TYPE, 6, join(p));
                return askWaistNavel(chatId);
            }
            case 6 -> { // талия на уровне пупка
                Double v = parseD(msg.getText());
                if (v == null) return md(chatId, "Введите число, например: `84`");
                String[] p = slots(st.payload()); p[5] = String.valueOf(v);
                StateRepo.set(userId, TYPE, 7, join(p));
                return askWaistMax(chatId);
            }
            case 7 -> { // талия максимальный обхват
                Double v = parseD(msg.getText());
                if (v == null) return md(chatId, "Введите число, например: `88`");
                String[] p = slots(st.payload()); p[6] = String.valueOf(v);
                StateRepo.set(userId, TYPE, 8, join(p));
                return askChest(chatId);
            }
            case 8 -> { // грудь x3
                String[] parts = splitNums(msg.getText(), 3);
                if (parts == null) return md(chatId, "Нужно три числа через запятую, пример: `96,98,102`");
                Double ex = parseD(parts[0]), rl = parseD(parts[1]), in = parseD(parts[2]);
                if (ex == null || rl == null || in == null) return md(chatId, "Нужно три числа через запятую, пример: `96,98,102`");

                String[] p = slots(st.payload());
                p[7] = String.valueOf(ex);
                p[8] = String.valueOf(rl);
                p[9] = String.valueOf(in);
                StateRepo.set(userId, TYPE, 9, join(p));

                return askBicepsLeft(chatId);
            }
            case 9 -> { // левый бицепс x2
                String[] parts = splitNums(msg.getText(), 2);
                if (parts == null) return md(chatId, "Нужно два числа через запятую, пример: `31,35`");
                Double br = parseD(parts[0]), bf = parseD(parts[1]);
                if (br == null || bf == null) return md(chatId, "Нужно два числа через запятую, пример: `31,35`");

                String[] p = slots(st.payload());
                p[10] = String.valueOf(br);
                p[11] = String.valueOf(bf);
                StateRepo.set(userId, TYPE, 10, join(p));

                return askBicepsRight(chatId);
            }
            case 10 -> { // правый бицепс x2
                String[] parts = splitNums(msg.getText(), 2);
                if (parts == null) return md(chatId, "Нужно два числа через запятую, пример: `31,35`");
                Double br = parseD(parts[0]), bf = parseD(parts[1]);
                if (br == null || bf == null) return md(chatId, "Нужно два числа через запятую, пример: `31,35`");

                String[] p = slots(st.payload());
                p[12] = String.valueOf(br);
                p[13] = String.valueOf(bf);
                StateRepo.set(userId, TYPE, 11, join(p));

                return askPhoto(chatId);
            }
            case 11 -> { // фото (можно пропустить)
                if (!msg.hasPhoto()) {
                    return md(chatId, "Пожалуйста, отправьте *фото* или нажмите «Пропустить фото».");
                }
                String fileId = msg.getPhoto().get(msg.getPhoto().size()-1).getFileId();
                // Сохраняем числа + фото
                persistNumbers(userId, st.payload());
                ParamsRepo.setPhoto(userId, fileId);
                StateRepo.clear(userId);
                SendMessage ok = new SendMessage(String.valueOf(chatId),
                        "Параметры и фото сохранены. " + Emojis.CHECK);
                ok.setReplyMarkup(com.example.coachbot.Keyboards.backToMenu());
                return ok;
            }
        }
        return null;
    }

    /* ==================== helpers ==================== */

    private static Double parseD(String s) {
        try { return Double.parseDouble(s.replace(',', '.').trim()); }
        catch (Exception e) { return null; }
    }

    private static String[] splitNums(String s, int cnt) {
        if (s == null) return null;
        String[] raw = s.split("[,; ]+");
        if (raw.length < cnt) return null;
        String[] out = new String[cnt];
        for (int i=0;i<cnt;i++) out[i] = raw[i];
        return out;
    }

    private static String[] slots(String payload) {
        // гарантируем 14 слотов
        String[] p = payload.split("\\|", -1);
        if (p.length < 14) {
            String[] n = new String[14];
            System.arraycopy(p, 0, n, 0, p.length);
            for (int i=p.length;i<14;i++) n[i] = "";
            return n;
        }
        return p;
    }

    private static String join(String[] p) { return String.join("|", p); }

    private static Double d(String s) {
        try { return (s==null || s.isBlank()) ? null : Double.parseDouble(s); }
        catch (Exception e) { return null; }
    }

    private static void persistNumbers(String userId, String payload) throws Exception {
        String[] p = slots(payload);
        Double weight       = d(p[0]);
        Double thighL       = d(p[1]);
        Double thighR       = d(p[2]);
        Double hips         = d(p[3]); // Ягодицы остаются в p[3]
        Double galife       = d(p[4]); // Галифе — p[4]
        Double waistNavel   = d(p[5]);
        Double waistMax     = d(p[6]);
        Double chEx         = d(p[7]);
        Double chRl         = d(p[8]);
        Double chIn         = d(p[9]);
        Double biL_Rl       = d(p[10]);
        Double biL_Fx       = d(p[11]);
        Double biR_Rl       = d(p[12]);
        Double biR_Fx       = d(p[13]);

        ParamsRepo.upsertNumbers(
                userId,
                weight,
                waistNavel, waistMax,
                chEx, chRl, chIn,
                biL_Rl, biL_Fx,
                biR_Rl, biR_Fx,
                thighL, thighR,
                hips, galife
        );
    }

    // ==== запросы следующего шага (с картинками/текстом) ====

    private static Object askThighLeft(long chatId) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("16.png"))); // левое бедро
        sp.setCaption("Шаг 2/11. *Левое бедро* (верхняя треть / максимальный обхват), см. Пример: `58.5`");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    private static Object askThighRight(long chatId) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("17.png"))); // правое бедро
        sp.setCaption("Шаг 3/11. *Правое бедро* (верхняя треть / максимальный обхват), пример: `58.0`");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    private static Object askGalife(long chatId) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("19.png"))); // галифе
        sp.setCaption("Шаг 4/11. *Галифе* (при наличии). Введите число в см или напишите `нет`.");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    private static Object askHips(long chatId) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("18.png"))); // ягодицы
        sp.setCaption("Шаг 5/11. *Ягодицы* (ноги вместе, по пику ягодиц), пример: `96`");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    private static Object askWaistNavel(long chatId) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("7.png"))); // талия на уровне пупка
        sp.setCaption("Шаг 6/11. *Талия (уровень пупка)*, пример: `84`");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    private static Object askWaistMax(long chatId) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("11.png"))); // талия макс. обхват
        sp.setCaption("Шаг 7/11. *Талия (максимальный обхват)*, пример: `88`");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sp;
    }

    private static Object askChest(long chatId) {
        SendMessage sm = md(chatId, """
Шаг 8/11. Введите *замеры груди* через запятую:
*на выдохе, расслабленно, на вдохе* — пример: `96,98,102`""");
        sm.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sm;
    }

    private static Object askBicepsLeft(long chatId) {
        SendMessage sm = md(chatId, """
Шаг 9/11. *Левый бицепс*: введите два числа через запятую:
*расслабленный, напряжённый* — пример: `31,35`""");
        sm.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sm;
    }

    private static Object askBicepsRight(long chatId) {
        SendMessage sm = md(chatId, """
Шаг 10/11. *Правый бицепс*: введите два числа через запятую:
*расслабленный, напряжённый* — пример: `31,35`""");
        sm.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return sm;
    }

    private static Object askPhoto(long chatId) {
        SendMessage askPhoto = new SendMessage(String.valueOf(chatId),
                "Шаг 11/11. *Загрузите ваше фото* (или нажмите «Пропустить фото»).");
        askPhoto.setParseMode(ParseMode.MARKDOWN);
        askPhoto.setReplyMarkup(Keyboards.paramsSkipOrCancel());
        return askPhoto;
    }
}