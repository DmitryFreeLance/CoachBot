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
 * Визард «Мои параметры».
 * Шаги:
 * 1 — вес (фото 8.png, ввод числом)
 * 2 — талия (фото 7.png, ввод числом)
 * 3 — грудь (на выдохе, расслабленно, на вдохе) (фото 10.png, ввод "x,y,z")
 * 4 — бицепс (расслабл., напряж.) (фото 11.png, ввод "x,y")
 * 5 — фото себя (можно пропустить кнопкой)
 *
 * Только на шаге 5 запрашиваем/принимаем фото. На остальных — только текст.
 * Данные сохраняются при завершении (шаг 5) или по skip фото.
 */
public class ParamsWizard {

    private static final String TYPE = "PARAMS";

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        sm.setReplyMarkup(Keyboards.paramsCancelOnly());
        return sm;
    }

    public static Object start(String userId, long chatId) throws Exception {
        // payload: weight|waist|chEx|chRl|chIn|biRl|biFx (пусто на старте)
        StateRepo.set(userId, TYPE, 1, "||||||");
        // Фото + подсказка
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("8.png"))); // вес
        sp.setCaption("Шаг 1/5. Введите *вес* в кг (например: `82.5`).");
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.paramsCancelOnly());
        return sp;
    }

    public static SendMessage cancel(String userId, long chatId) throws Exception {
        StateRepo.clear(userId);
        SendMessage sm = new SendMessage(String.valueOf(chatId), "Ввод параметров отменён.");
        sm.setReplyMarkup(com.example.coachbot.Keyboards.backToMenu());
        return sm;
    }

    public static SendMessage skipPhoto(String userId, long chatId) throws Exception {
        var st = StateRepo.get(userId);
        if (st == null || !TYPE.equals(st.type()) || st.step() != 5) {
            return md(chatId, "Пропуск доступен только на *последнем шаге (фото)*.");
        }
        // сохраняем без фото
        persistNumbers(userId, st.payload());
        StateRepo.clear(userId);
        SendMessage ok = new SendMessage(String.valueOf(chatId),
                "Параметры сохранены без фото. " + Emojis.CHECK);
        ok.setReplyMarkup(com.example.coachbot.Keyboards.backToMenu());
        return ok;
    }

    public static Object onAny(String userId, long chatId, Message msg) throws Exception {
        var st = StateRepo.get(userId);
        if (st == null || !TYPE.equals(st.type())) return null;

        switch (st.step()) {
            case 1 -> { // вес
                Double weight = parseD(msg.getText());
                if (weight == null) return md(chatId, "Введите число, например: `82.5`");
                String[] p = st.payload().split("\\|", -1);
                p[0] = String.valueOf(weight);
                StateRepo.set(userId, TYPE, 2, String.join("|", p));

                SendPhoto sp = new SendPhoto();
                sp.setChatId(String.valueOf(chatId));
                sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("7.png"))); // талия
                sp.setCaption("Шаг 2/5. Введите *обхват талии* в см (уровень пупка), например: `84`");
                sp.setParseMode(ParseMode.MARKDOWN);
                sp.setReplyMarkup(Keyboards.paramsCancelOnly());
                return sp;
            }
            case 2 -> { // талия
                Double waist = parseD(msg.getText());
                if (waist == null) return md(chatId, "Введите число, например: `84`");
                String[] p = st.payload().split("\\|", -1);
                p[1] = String.valueOf(waist);
                StateRepo.set(userId, TYPE, 3, String.join("|", p));

                SendPhoto sp = new SendPhoto();
                sp.setChatId(String.valueOf(chatId));
                sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("10.png"))); // грудь
                sp.setCaption("""
Шаг 3/5. Введите *замеры груди* через запятую:
*на выдохе, расслабленно, на вдохе* — пример: `96,98,102`""");
                sp.setParseMode(ParseMode.MARKDOWN);
                sp.setReplyMarkup(Keyboards.paramsCancelOnly());
                return sp;
            }
            case 3 -> { // грудь x3
                String[] parts = splitNums(msg.getText(), 3);
                if (parts == null) {
                    return md(chatId, "Нужно три числа через запятую, пример: `96,98,102`");
                }
                Double ex = parseD(parts[0]), rl = parseD(parts[1]), in = parseD(parts[2]);
                if (ex == null || rl == null || in == null) {
                    return md(chatId, "Нужно три числа через запятую, пример: `96,98,102`");
                }
                String[] p = st.payload().split("\\|", -1);
                p[2] = String.valueOf(ex);
                p[3] = String.valueOf(rl);
                p[4] = String.valueOf(in);
                StateRepo.set(userId, TYPE, 4, String.join("|", p));

                SendPhoto sp = new SendPhoto();
                sp.setChatId(String.valueOf(chatId));
                sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("11.png"))); // бицепс
                sp.setCaption("""
Шаг 4/5. Введите *замеры бицепса* через запятую:
*в расслабленном, в напряжённом* — пример: `31,35`""");
                sp.setParseMode(ParseMode.MARKDOWN);
                sp.setReplyMarkup(Keyboards.paramsCancelOnly());
                return sp;
            }
            case 4 -> { // бицепс x2
                String[] parts = splitNums(msg.getText(), 2);
                if (parts == null) return md(chatId, "Нужно два числа через запятую, пример: `31,35`");
                Double br = parseD(parts[0]), bf = parseD(parts[1]);
                if (br == null || bf == null) return md(chatId, "Нужно два числа через запятую, пример: `31,35`");

                String[] p = st.payload().split("\\|", -1);
                p[5] = String.valueOf(br);
                p[6] = String.valueOf(bf);
                StateRepo.set(userId, TYPE, 5, String.join("|", p));

                SendMessage askPhoto = new SendMessage(String.valueOf(chatId),
                        "Шаг 5/5. *Загрузите ваше фото* (или нажмите «Пропустить фото»).");
                askPhoto.setParseMode(ParseMode.MARKDOWN);
                askPhoto.setReplyMarkup(Keyboards.paramsPhotoStep());
                return askPhoto;
            }
            case 5 -> { // фото (единственный шаг, где можно фото/скип)
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

    private static void persistNumbers(String userId, String payload) throws Exception {
        String[] p = payload.split("\\|", -1);
        Double weight = d(p[0]);
        Double waist  = d(p[1]);
        Double chEx   = d(p[2]);
        Double chRl   = d(p[3]);
        Double chIn   = d(p[4]);
        Double biRl   = d(p[5]);
        Double biFx   = d(p[6]);
        ParamsRepo.upsertNumbers(userId, weight, waist, chEx, chRl, chIn, biRl, biFx);
    }

    private static Double parseD(String s) { try { return Double.parseDouble(s.replace(',','.').trim()); } catch (Exception e){ return null; } }
    private static Double d(String s) { try { return (s==null||s.isBlank())?null:Double.parseDouble(s); } catch(Exception e){ return null; } }
    private static String[] splitNums(String s, int cnt) {
        if (s == null) return null;
        String[] raw = s.split("[,; ]+");
        if (raw.length < cnt) return null;
        String[] out = new String[cnt];
        for (int i=0;i<cnt;i++) out[i] = raw[i];
        return out;
    }
}