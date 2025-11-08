package com.example.coachbot.service;

import com.example.coachbot.repo.StateRepo;
import com.example.coachbot.repo.ReportRepo;
import com.example.coachbot.TimeUtil;
import com.example.coachbot.Texts;
import com.example.coachbot.Emojis;
import com.example.coachbot.Keyboards;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;

public class ReportWizard {

    /** Максимальное число фото еды в отчёте за день */
    private static final int MAX_PHOTOS = 10;

    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        sm.setReplyMarkup(Keyboards.reportCancel()); // по умолчанию кнопки отмены
        return sm;
    }

    public static SendMessage start(String userId, long chatId) throws Exception {
        LocalDate today = TimeUtil.today();
        if (ReportRepo.existsFor(userId, today)) {
            SendMessage sm = new SendMessage(String.valueOf(chatId), Texts.reportAlready());
            sm.setReplyMarkup(Keyboards.backToMenu());
            return sm;
        }
        StateRepo.set(userId, "REPORT", 1, "");
        return md(chatId,
                "Начнём дневной отчёт за *" + TimeUtil.DATE_FMT.format(today) + "*.\n\n" +
                        "1/6. Введите *часы сна* за сутки (например: `7.5`).");
    }

    public static SendMessage onMessage(String userId, long chatId, Message msg) throws Exception {
        var st = StateRepo.get(userId);
        if (st==null || !"REPORT".equals(st.type())) return null;

        switch (st.step()) {
            case 1 -> {
                Double sleep = parseDouble(msg.getText());
                if (sleep==null) return md(chatId, "Пожалуйста, введите число, пример: `7.5`");
                ReportRepo.insertOrUpdateForToday(userId, sleep, null, null, null, null, null, null, null, null);
                StateRepo.set(userId, "REPORT", 2, "");
                return md(chatId, "2/6. Введите *количество шагов* (например: `8000`).");
            }
            case 2 -> {
                Integer steps = parseInt(msg.getText());
                if (steps==null) return md(chatId, "Введите целое число, пример: `8000`.");
                ReportRepo.insertOrUpdateForToday(userId, null, steps, null, null, null, null, null, null, null);
                StateRepo.set(userId, "REPORT", 3, "");
                return md(chatId, "3/6. Введите *литры воды* (например: `2.5`).");
            }
            case 3 -> {
                Double water = parseDouble(msg.getText());
                if (water==null) return md(chatId, "Введите число, пример: `2.5`.");
                ReportRepo.insertOrUpdateForToday(userId, null, null, water, null, null, null, null, null, null);
                StateRepo.set(userId, "REPORT", 4, "");
                SendMessage askKbju = md(chatId,
                        "4/6. Отправьте *КБЖУ одним сообщением через запятую* (например: `1778,133,59,178`) " +
                                "или пришлите *скриншот* с этими данными.\n\n" +
                                Emojis.FIRE+" Калории, "+Emojis.MEAT+" Белки, "+Emojis.AVOCADO+" Жиры, "+Emojis.BREAD+" Углеводы");
                askKbju.setReplyMarkup(Keyboards.reportCancel()); // здесь «пропуска» нет
                return askKbju;
            }
            case 4 -> {
                Integer kcal=null; Double p=null,f=null,c=null; String photo=null;
                if (msg.hasPhoto()) {
                    // legacy-скрин с КБЖУ — сохраним в reports.photo_id для обратной совместимости
                    photo = msg.getPhoto().get(msg.getPhoto().size()-1).getFileId();
                } else {
                    String t = msg.hasText() ? msg.getText() : "";
                    String[] parts = t.split("[,; ]+");
                    if (parts.length>=4) {
                        try {
                            kcal = Integer.parseInt(parts[0].trim());
                            p = Double.parseDouble(parts[1].trim());
                            f = Double.parseDouble(parts[2].trim());
                            c = Double.parseDouble(parts[3].trim());
                        } catch (Exception e) {
                            return md(chatId, "Не удалось разобрать КБЖУ. Пример: `1778,133,59,178`");
                        }
                    } else {
                        SendMessage sm = new SendMessage(String.valueOf(chatId), "Пожалуйста, укажите 4 числа через запятую или отправьте скриншот.");
                        sm.setReplyMarkup(Keyboards.reportCancel());
                        return sm;
                    }
                }
                ReportRepo.insertOrUpdateForToday(userId, null,null,null, kcal,p,f,c, null, photo);

                // Переходим к шагу «фото еды»
                StateRepo.set(userId, "REPORT", 5, "");
                SendMessage askPhotos = md(chatId,
                        "5/6. Пришлите *фото еды*. Можно отправить *альбом* (до 10 фото за раз) " +
                                "или несколько сообщений с фото. Максимум — *10 фото за день*.\n" +
                                "Когда закончите — нажмите «Пропустить».");
                askPhotos.setReplyMarkup(Keyboards.reportSkipOrCancel());
                return askPhotos;
            }
            case 5 -> {
                // Шаг фото еды: теперь есть лимит MAX_PHOTOS
                LocalDate today = TimeUtil.today();
                int current = ReportRepo.countFoodPhotos(userId, today);

                if (!msg.hasPhoto()) {
                    SendMessage hint = md(chatId,
                            "5/6. Пришлите фото. Можно альбом (до 10 фото) или несколько сообщений.\n" +
                                    "Максимум — *10 фото за день*. Когда закончите — нажмите «Пропустить», чтобы перейти к комментарию.");
                    hint.setReplyMarkup(Keyboards.reportSkipOrCancel());
                    return hint;
                }

                if (current >= MAX_PHOTOS) {
                    // Уже исчерпан лимит — сразу вперёд к комментарию
                    StateRepo.set(userId, "REPORT", 6, "");
                    SendMessage full = md(chatId, "Вы загрузили *максимум фото (10)*. Переходим к комментарию.\n\n6/6. Пришлите *текстовый комментарий* или нажмите «Пропустить».");
                    full.setReplyMarkup(Keyboards.reportSkipOrCancel());
                    return full;
                }

                String fileId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
                ReportRepo.addFoodPhoto(userId, today, fileId);
                int after = current + 1;

                if (after >= MAX_PHOTOS) {
                    // Достигли лимита — автоматически переносим на комментарий
                    StateRepo.set(userId, "REPORT", 6, "");
                    SendMessage cap = md(chatId,
                            "Фото добавлено (" + after + "/" + MAX_PHOTOS + ").\n" +
                                    "Вы загрузили *максимум фото*. Переходим к комментарию.\n\n6/6. Пришлите *текстовый комментарий* или нажмите «Пропустить».");
                    cap.setReplyMarkup(Keyboards.reportSkipOrCancel());
                    return cap;
                }

                SendMessage ack = md(chatId,
                        "Фото добавлено (" + after + "/" + MAX_PHOTOS + "). " +
                                "Пришлите ещё или нажмите «Пропустить», чтобы перейти к комментарию.");
                ack.setReplyMarkup(Keyboards.reportSkipOrCancel());
                return ack;
            }
            case 6 -> {
                // Последний шаг — текстовый комментарий (или skip по кнопке в колбэке)
                if (!msg.hasText() || msg.getText().isBlank()) {
                    SendMessage ask = md(chatId, "6/6. Пришлите *текстовый комментарий* или нажмите «Пропустить».");
                    ask.setReplyMarkup(Keyboards.reportSkipOrCancel());
                    return ask;
                }
                String note = msg.getText().trim();
                ReportRepo.insertOrUpdateForToday(userId, null,null,null, null,null,null,null, note, null);
                StateRepo.clear(userId);
                SendMessage done = new SendMessage(String.valueOf(chatId),
                        Emojis.CHECK + " Отчёт принят! Отличная работа — ещё один шаг к цели " + Emojis.MUSCLE);
                done.setReplyMarkup(Keyboards.backToMenu());
                return done;
            }
        }
        return null;
    }

    /** Обработка «⏭ Пропустить» из колбэка. Работает на шагах 5 и 6. */
    public static SendMessage onSkip(String userId, long chatId) throws Exception {
        var st = StateRepo.get(userId);
        if (st==null || !"REPORT".equals(st.type())) return null;

        if (st.step() <= 4) {
            // На ранних шагах пропуск не предусмотрен — подскажем
            SendMessage warn = md(chatId, "Сейчас пропуск недоступен. Пожалуйста, заполните текущий шаг или нажмите «Отменить заполнение».");
            warn.setReplyMarkup(Keyboards.reportCancel());
            return warn;
        }

        if (st.step() == 5) {
            // Пропуск фото -> комментарий
            StateRepo.set(userId, "REPORT", 6, "");
            SendMessage ask = md(chatId, "6/6. Пришлите *текстовый комментарий* или снова нажмите «Пропустить», чтобы завершить без комментария.");
            ask.setReplyMarkup(Keyboards.reportSkipOrCancel());
            return ask;
        }

        if (st.step() == 6) {
            // Пропуск комментария -> завершение без заметки
            ReportRepo.insertOrUpdateForToday(userId, null,null,null, null,null,null,null, null, null);
            StateRepo.clear(userId);
            SendMessage done = new SendMessage(String.valueOf(chatId),
                    Emojis.CHECK + " Отчёт принят! Отличная работа — ещё один шаг к цели " + Emojis.MUSCLE);
            done.setReplyMarkup(Keyboards.backToMenu());
            return done;
        }

        // На всякий случай
        SendMessage hint = md(chatId, "Сейчас нечего пропускать.");
        hint.setReplyMarkup(Keyboards.reportCancel());
        return hint;
    }

    public static SendMessage cancel(String userId, long chatId) throws Exception {
        StateRepo.clear(userId);
        SendMessage sm = new SendMessage(String.valueOf(chatId), "Заполнение отчёта отменено.");
        sm.setReplyMarkup(Keyboards.backToMenu());
        return sm;
    }

    private static Double parseDouble(String s){ try { return Double.parseDouble(s.replace(',','.').trim()); } catch(Exception e){ return null; } }
    private static Integer parseInt(String s){ try { return Integer.parseInt(s.trim().replace(" ","")); } catch(Exception e){ return null; } }
}