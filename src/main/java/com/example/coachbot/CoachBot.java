package com.example.coachbot;

import com.example.coachbot.repo.*;
import com.example.coachbot.service.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CoachBot extends TelegramLongPollingBot {

    private final String username;
    public CoachBot(String username, String token) {
        super(token);
        this.username = username;
    }
    @Override public String getBotUsername() { return username; }

    // ===== helpers =====
    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        return sm;
    }
    private static Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim().replace(" ", "")); }
        catch (Exception e) { return null; }
    }
    public void safeExecute(SendMessage sm) {
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
    private boolean isAdmin(String tgId) throws Exception {
        Roles r = UserRepo.role(tgId);
        return r == Roles.ADMIN || r == Roles.SUPERADMIN;
    }
    private boolean isSuper(String tgId) throws Exception { return UserRepo.role(tgId) == Roles.SUPERADMIN; }

    private static String helpText() {
        return """
*Как пользоваться ботом* 🧭

*1) Главное меню*  
• 🍽 *План питания* — смотри запланированные калории и БЖУ на сегодня.  
• 🏋️ *Тренировка* — список упражнений с галочками.  
• 📊 *Нормы активности* — вода, шаги и сон на день.  
• 📝 *Отчёт* — заполни дневной отчёт: сон → шаги → вода → КБЖУ (одним сообщением или скриншотом).  
• 📞 *Контакты* — контакты твоего тренера.

*2) Ежедневные напоминания*  
• ⏰ Утро 08:00 — сценарий дня (питание, тренировка, нормы и мотивация).  
• 🌆 Вечер — напоминание с кнопкой «Заполнить отчёт».

*3) Отчёт*  
• В день можно отправить *только 1 отчёт*.  
• КБЖУ можно ввести так: `1778,133,59,178` или отправить *скрин*.  
• Если ошибся — нажми «✖️ Отменить заполнение» и начни заново.

*4) Подсказки*  
• Если что-то пошло не так — набери */start* для перезапуска меню.  
• Тренер/админ видит прогресс и может корректировать план.  
*Дисциплина сегодня — результат завтра!* 🔥💪
""";
    }

    // ===== main =====
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!UpdatesRepo.markProcessed(update.getUpdateId())) return; // анти-дубль

            if (update.hasMessage()) handleMessage(update.getMessage());
            else if (update.hasCallbackQuery()) handleCallback(update.getCallbackQuery());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(Message m) throws Exception {
        String tgId = String.valueOf(m.getFrom().getId());
        UserRepo.upsertUser(tgId, m.getFrom().getUserName(), m.getFrom().getFirstName());

        String text = m.hasText() ? m.getText().trim() : "";

        // === ГЛОБАЛЬНЫЙ СБРОС ПО /start И /admin ===
        if (text.startsWith("/start") || text.startsWith("/admin")) {
            StateRepo.clear(tgId);
            if (text.startsWith("/start")) {
                SendMessage sm = md(m.getChatId(), Texts.start(m.getFrom().getFirstName()));
                // ВАЖНО: здесь меню БЕЗ "Админ-панель"
                sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
                safeExecute(sm);
                return;
            }
            if (text.startsWith("/admin")) {
                if (!isAdmin(tgId)) {
                    SendMessage sm = new SendMessage(String.valueOf(m.getChatId()), "Команда только для админов.");
                    sm.setReplyMarkup(Keyboards.backToMenu());
                    safeExecute(sm);
                    return;
                }
                SendMessage sm = md(m.getChatId(), Texts.adminTitle());
                sm.setReplyMarkup(Keyboards.adminPanel(isSuper(tgId)));
                safeExecute(sm);
                return;
            }
        }

        // /help — памятка
        if (text.startsWith("/help")) {
            SendMessage sm = md(m.getChatId(), helpText());
            sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
            safeExecute(sm);
            return;
        }

        // --- состояние пользователя: отчёт (жёсткий режим, только cancel)
        var stUser = StateRepo.get(tgId);
        if (stUser != null && "REPORT".equals(stUser.type())) {
            if (text.startsWith("/")) {
                safeExecute(md(m.getChatId(),"Сначала завершите отчёт или отмените его кнопкой «✖️ Отменить заполнение»."));
                return;
            }
            var sm = ReportWizard.onMessage(tgId, m.getChatId(), m);
            if (sm != null) safeExecute(sm);
            return;
        }

        // --- состояния админа
        var stAdmin = StateRepo.get(tgId);
        if (stAdmin != null) {

            // Жёсткий визард контактов
            if ("CONTACT".equals(stAdmin.type())) {
                if (text.startsWith("/")) {
                    safeExecute(md(m.getChatId(),"Сейчас идёт ввод контактов. Введите текст контактов или нажмите «✖️ Отменить ввод»."));
                    return;
                }
                var sm = ContactWizard.onMessage(tgId, m.getChatId(), text);
                if (sm != null) {
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                }
                return;
            }

            // ======== ДОБАВИТЬ В ГРУППУ (по tg_id) ========
            if ("ASK_GROUP_ADD".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    if (text.startsWith("/")) {
                        SendMessage warn = md(m.getChatId(),"Введите *tg_id* пользователя для добавления в вашу группу.");
                        warn.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(warn);
                        return;
                    }
                    String uid = text.replace("@","").trim();
                    if (uid.isEmpty()) {
                        SendMessage err = md(m.getChatId(),"Укажите корректный *tg_id*.");
                        err.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(err);
                        return;
                    }
                    boolean ok = GroupRepo.addToAdmin(tgId, uid);
                    SendMessage done = new SendMessage(String.valueOf(m.getChatId()),
                            ok ? ("Пользователь " + uid + " добавлен в вашу группу.") :
                                    "Не удалось добавить: пользователь уже привязан к другому тренеру (или к вам).");
                    done.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(done);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // ======== ДОБАВИТЬ АДМИНА (по tg_id) ========
            if ("ASK_ADMIN_ADD".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    if (text.startsWith("/")) {
                        SendMessage warn = md(m.getChatId(),"Введите *tg_id* пользователя для назначения администратором.");
                        warn.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(warn);
                        return;
                    }
                    String uid = text.replace("@","").trim();
                    if (uid.isEmpty()) {
                        SendMessage err = md(m.getChatId(),"Укажите корректный *tg_id*.");
                        err.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(err);
                        return;
                    }
                    UserRepo.ensureAdmin(uid);
                    SendMessage ok = md(m.getChatId(), "Админ добавлен: " + uid);
                    ok.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(ok);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // ======== УСТАНОВИТЬ КБЖУ: выбор по номеру ========
            if ("ASK_SET_CAL".equals(stAdmin.type())) {
                switch (stAdmin.step()) {
                    case 1 -> { // ждём номер
                        Integer idx = parseInt(text);
                        String[] ids = stAdmin.payload().split(",");
                        if (idx == null || idx < 1 || idx > ids.length) {
                            SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                            err.setReplyMarkup(Keyboards.backToAdmin());
                            safeExecute(err);
                            return;
                        }
                        String uid = ids[idx - 1];
                        StateRepo.set(tgId, "ASK_SET_CAL", 2, uid);
                        SendMessage q = md(m.getChatId(), "Укажите дату в формате `dd.MM.yyyy`:");
                        q.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(q);
                        return;
                    }
                    case 2 -> { // ждём дату
                        String uid = stAdmin.payload();
                        LocalDate date = TimeUtil.parseDate(text);
                        if (date == null) {
                            SendMessage err = md(m.getChatId(),"Неверная дата. Введите в формате `dd.MM.yyyy`, например `01.11.2025`.");
                            err.setReplyMarkup(Keyboards.backToAdmin());
                            safeExecute(err);
                            return;
                        }
                        // НЕ чистим состояние — визард сам поставит SET_CAL
                        safeExecute(CaloriesWizard.start(tgId, m.getChatId(), uid, date));
                        return;
                    }
                }
            }

            // ======== УСТАНОВИТЬ ПЛАН: выбор по номеру ========
            if ("ASK_SET_PLAN".equals(stAdmin.type())) {
                switch (stAdmin.step()) {
                    case 1 -> {
                        Integer idx = parseInt(text);
                        String[] ids = stAdmin.payload().split(",");
                        if (idx == null || idx < 1 || idx > ids.length) {
                            SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                            err.setReplyMarkup(Keyboards.backToAdmin());
                            safeExecute(err);
                            return;
                        }
                        String uid = ids[idx - 1];
                        StateRepo.set(tgId, "ASK_SET_PLAN", 2, uid);
                        SendMessage q = md(m.getChatId(), "Укажите дату в формате `dd.MM.yyyy`:");
                        q.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(q);
                        return;
                    }
                    case 2 -> {
                        String uid = stAdmin.payload();
                        LocalDate date = TimeUtil.parseDate(text);
                        if (date == null) {
                            SendMessage err = md(m.getChatId(),"Неверная дата. Введите в формате `dd.MM.yyyy`, например `01.11.2025`.");
                            err.setReplyMarkup(Keyboards.backToAdmin());
                            safeExecute(err);
                            return;
                        }
                        // НЕ чистим — визард сам поставит SET_PLAN
                        safeExecute(PlanWizard.start(tgId, m.getChatId(), uid, date));
                        return;
                    }
                }
            }

            // ======== УСТАНОВИТЬ НОРМЫ: выбор по номеру ========
            if ("ASK_SET_NORM".equals(stAdmin.type())) {
                switch (stAdmin.step()) {
                    case 1 -> {
                        Integer idx = parseInt(text);
                        String[] ids = stAdmin.payload().split(",");
                        if (idx == null || idx < 1 || idx > ids.length) {
                            SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                            err.setReplyMarkup(Keyboards.backToAdmin());
                            safeExecute(err);
                            return;
                        }
                        String uid = ids[idx - 1];
                        StateRepo.set(tgId, "ASK_SET_NORM", 2, uid);
                        SendMessage q = md(m.getChatId(), "Укажите дату в формате `dd.MM.yyyy`:");
                        q.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(q);
                        return;
                    }
                    case 2 -> {
                        String uid = stAdmin.payload();
                        LocalDate date = TimeUtil.parseDate(text);
                        if (date == null) {
                            SendMessage err = md(m.getChatId(),"Неверная дата. Введите в формате `dd.MM.yyyy`, например `01.11.2025`.");
                            err.setReplyMarkup(Keyboards.backToAdmin());
                            safeExecute(err);
                            return;
                        }
                        // НЕ чистим — визард сам поставит SET_NORM
                        safeExecute(NormWizard.start(tgId, m.getChatId(), uid, date));
                        return;
                    }
                }
            }

            // ======== УДАЛИТЬ ИЗ ГРУППЫ: выбор по номеру ========
            if ("ASK_GROUP_DEL".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    Integer idx = parseInt(text);
                    String[] ids = stAdmin.payload().split(",");
                    if (idx == null || idx < 1 || idx > ids.length) {
                        SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                        err.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(err);
                        return;
                    }
                    String uid = ids[idx - 1];
                    boolean ok = GroupRepo.removeFromAdmin(tgId, uid);
                    SendMessage done = new SendMessage(String.valueOf(m.getChatId()),
                            ok ? ("Пользователь " + uid + " удалён из вашей группы.") :
                                    "Такой пары (пользователь — вы как тренер) нет.");
                    done.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(done);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // ======== УДАЛИТЬ АДМИНА: выбор по номеру ========
            if ("ASK_ADMIN_DEL".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    Integer idx = parseInt(text);
                    String[] ids = stAdmin.payload().split(",");
                    if (idx == null || idx < 1 || idx > ids.length) {
                        SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                        err.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(err);
                        return;
                    }
                    String uid = ids[idx - 1];
                    UserRepo.setRole(uid, Roles.USER);
                    SendMessage ok = md(m.getChatId(), "Админ удалён: " + uid);
                    ok.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(ok);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // Время рассылки (супер-админ)
            if ("ASK_SET_TIME".equals(stAdmin.type())) {
                if (UserRepo.role(tgId) != Roles.SUPERADMIN) {
                    SendMessage sm = md(m.getChatId(), "Недостаточно прав.");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    StateRepo.clear(tgId);
                    return;
                }
                if (text.startsWith("/")) {
                    SendMessage sm = md(m.getChatId(),"Введите время в формате `HH:mm`, например `19:00`.");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    return;
                }
                String t = text;
                SettingsRepo.set("evening_time", t);
                SendMessage ok = new SendMessage(String.valueOf(m.getChatId()), "Вечерняя рассылка установлена на " + t + " (Екатеринбург).");
                ok.setReplyMarkup(Keyboards.backToAdmin());
                safeExecute(ok);
                StateRepo.clear(tgId);
                return;
            }

            /* ===== ОБРАБОТКА самих визардов ===== */
            switch (stAdmin.type()) {
                case "SET_CAL" -> { // калории → белки → жиры → углеводы
                    var sm = CaloriesWizard.onMessage(tgId, m.getChatId(), text);
                    if (sm != null) safeExecute(sm);
                    return;
                }
                case "SET_PLAN" -> { // пошаговый ввод упражнений
                    var sm = PlanWizard.onMessage(tgId, m.getChatId(), text);
                    if (sm != null) safeExecute(sm);
                    return;
                }
                case "SET_NORM" -> { // вода → шаги → сон
                    var sm = NormWizard.onMessage(tgId, m.getChatId(), text);
                    if (sm != null) safeExecute(sm);
                    return;
                }
            }
        }

        // --- прочие команды
        if (text.startsWith("/settime")) {
            if (!isSuper(tgId)) { safeExecute(md(m.getChatId(),"Команда доступна только главным администраторам.")); return; }
            String[] p = text.split("\\s+");
            if (p.length < 2) {
                SendMessage err = md(m.getChatId(),"Укажите время в формате `HH:mm`, напр. `19:30`.");
                err.setReplyMarkup(Keyboards.backToMenu());
                safeExecute(err);
                return;
            }
            SettingsRepo.set("evening_time", p[1]);
            SendMessage ok = new SendMessage(String.valueOf(m.getChatId()), "Вечерняя рассылка установлена на " + p[1] + " (Екатеринбург).");
            ok.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(ok);
            return;
        }

        // по умолчанию — предлагаем меню
        SendMessage sm = new SendMessage(String.valueOf(m.getChatId()), "Пожалуйста, используйте меню ниже.");
        sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
        safeExecute(sm);
    }

    private void handleCallback(CallbackQuery cq) throws Exception {
        String data = cq.getData();
        String tgId = String.valueOf(cq.getFrom().getId());
        long chatId = cq.getMessage().getChatId();

        // убрать "часики"
        try { execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build()); } catch (Exception ignored) {}

        // блок контактов (жёсткий)
        var st = StateRepo.get(tgId);
        if (st != null && "CONTACT".equals(st.type())) {
            if ("contact:cancel".equals(data)) {
                StateRepo.clear(tgId);
                SendMessage panel = md(chatId, Texts.adminTitle());
                panel.setReplyMarkup(Keyboards.adminPanel(isSuper(tgId)));
                safeExecute(panel);
                return;
            } else {
                execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).text("Сначала завершите ввод контактов или отмените.").showAlert(true).build());
                return;
            }
        }

        // ---- главное меню
        if ("menu:main".equals(data)) {
            SendMessage sm = md(chatId, Texts.start(cq.getFrom().getFirstName()));
            sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
            safeExecute(sm);
            return;
        }

        if ("menu:food".equals(data)) {
            String msg = PlanRepo.getNutritionText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }

        if ("menu:workout".equals(data)) {
            String msg = PlanRepo.getWorkoutText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }

        if ("menu:norms".equals(data)) {
            String msg = NormRepo.getNormsText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }

        if ("menu:contact".equals(data)) {
            String admin = GroupRepo.adminOf(tgId);
            SendMessage sm;
            if (admin == null) sm = new SendMessage(String.valueOf(chatId), Texts.noGroup());
            else {
                String ct = ContactRepo.get(admin);
                sm = new SendMessage(String.valueOf(chatId), ct == null ? "Тренер пока не указал контакты." : ("Контакты вашего тренера:\n" + ct));
            }
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }

        if ("menu:report".equals(data)) {
            safeExecute(ReportWizard.start(tgId, chatId));
            return;
        }

        // ---- админ-панель (вход)
        if ("menu:admin".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Команда только для админов.")); return; }
            SendMessage sm = md(chatId, Texts.adminTitle());
            sm.setReplyMarkup(Keyboards.adminPanel(isSuper(tgId)));
            safeExecute(sm);
            return;
        }

        // Моя группа — вывод + пагинация
        if ("admin:my".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderMyGroup(chatId, tgId, 1);
            return;
        }
        if (data.startsWith("mygroup:")) {
            int page = Integer.parseInt(data.substring("mygroup:".length()));
            renderMyGroup(chatId, tgId, page);
            return;
        }

        // Все пользователи — вывод
        if ("admin:all".equals(data) || "admin:allusers".equals(data) || "admin:users".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderAllUsers(chatId, 1);
            return;
        }
        if (data.startsWith("allusers:")) {
            int page = Integer.parseInt(data.substring("allusers:".length()));
            renderAllUsers(chatId, page);
            return;
        }

        // ====== визарды — старт ======
        if ("admin:groupadd".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            StateRepo.set(tgId, "ASK_GROUP_ADD", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя для добавления в вашу группу:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm); return;
        }
        if ("admin:groupdel".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:groupdel", 1, "ASK_GROUP_DEL", "Выберите пользователя по номеру для удаления из группы:");
            return;
        }
        if ("admin:setcal".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:setcal", 1, "ASK_SET_CAL", "Выберите пользователя по номеру из списка:");
            return;
        }
        if ("admin:setplan".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:setplan", 1, "ASK_SET_PLAN", "Выберите пользователя по номеру из списка:");
            return;
        }
        if ("admin:setnorma".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:setnorm", 1, "ASK_SET_NORM", "Выберите пользователя по номеру из списка:");
            return;
        }
        if ("admin:del".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            renderAdminsPicker(chatId, "pick:admindel", 1, "ASK_ADMIN_DEL", "Выберите администратора по номеру для снятия прав:");
            return;
        }

        // Пагинация пиков
        if (data.startsWith("pick:setcal:")) {
            int page = Integer.parseInt(data.substring("pick:setcal:".length()));
            renderGroupPicker(chatId, tgId, "pick:setcal", page, "ASK_SET_CAL", "Выберите пользователя по номеру из списка:");
            return;
        }
        if (data.startsWith("pick:setplan:")) {
            int page = Integer.parseInt(data.substring("pick:setplan:".length()));
            renderGroupPicker(chatId, tgId, "pick:setplan", page, "ASK_SET_PLAN", "Выберите пользователя по номеру из списка:");
            return;
        }
        if (data.startsWith("pick:setnorm:")) {
            int page = Integer.parseInt(data.substring("pick:setnorm:".length()));
            renderGroupPicker(chatId, tgId, "pick:setnorm", page, "ASK_SET_NORM", "Выберите пользователя по номеру из списка:");
            return;
        }
        if (data.startsWith("pick:groupdel:")) {
            int page = Integer.parseInt(data.substring("pick:groupdel:".length()));
            renderGroupPicker(chatId, tgId, "pick:groupdel", page, "ASK_GROUP_DEL", "Выберите пользователя по номеру для удаления из группы:");
            return;
        }
        if (data.startsWith("pick:admindel:")) {
            int page = Integer.parseInt(data.substring("pick:admindel:".length()));
            renderAdminsPicker(chatId, "pick:admindel", page, "ASK_ADMIN_DEL", "Выберите администратора по номеру для снятия прав:");
            return;
        }

        // Контакты — жёсткий визард
        if ("admin:contact".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            SendMessage sm = ContactWizard.start(tgId, chatId);
            sm.setReplyMarkup(Keyboards.contactCancelOnly());
            safeExecute(sm);
            return;
        }

        // Время рассылки — мини-визард (супер-админ)
        if ("admin:settime".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_SET_TIME", 1, "");
            SendMessage sm = md(chatId, "Введите время в формате `HH:mm` (Екатеринбург):");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            return;
        }

        // Добавить админа — по tg_id (запрос текста)
        if ("admin:add".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_ADMIN_ADD", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя для назначения администратором:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            return;
        }

        // отчёт: отмена и старт
        if ("report:cancel".equals(data)) {
            safeExecute(ReportWizard.cancel(String.valueOf(cq.getFrom().getId()), chatId));
            try { execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).text("Отчёт отменён").build()); } catch (Exception ignored) {}
            return;
        }
        if ("report:start".equals(data)) {
            safeExecute(ReportWizard.start(String.valueOf(cq.getFrom().getId()), chatId));
            return;
        }

        // Завершить план
        if ("plan:finish".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            SendMessage sm = PlanWizard.onFinish(tgId, chatId);
            if (sm != null) safeExecute(sm);
            return;
        }

        // пагинация отчётов
        if (data.startsWith("reports:")) {
            String[] p = data.split(":");
            String uid = p[1];
            boolean desc = "desc".equals(p[2]);
            if (p.length >= 4) {
                int page = Integer.parseInt(p[3]);
                sendReportsPage(tgId, chatId, uid, page, desc);
            }
            return;
        }

        if ("noop".equals(data)) { return; }
    }

    /* ==================== ПИКЕРЫ (пагинированные списки для выбора по номеру) ==================== */

    private String formatRow(UserRepo.UserRow r) {
        String name = (r.firstName != null && !r.firstName.isBlank()) ? r.firstName : "—";
        String tag  = (r.username  != null && !r.username.isBlank())  ? "@"+r.username : "—";
        return name + " | " + tag + " | " + r.id;
    }

    private void renderGroupPicker(long chatId, String adminId, String base, int page, String armStateType, String prompt) throws Exception {
        int size = 10;
        int total = countGroupUsers(adminId);
        if (total <= 0) {
            SendMessage empty = new SendMessage(String.valueOf(chatId), "В вашей группе пока нет пользователей.");
            empty.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(empty);
            return;
        }
        int pages = Math.max(1, (int)Math.ceil(total / (double) size));
        page = Math.min(Math.max(1, page), pages);
        int offset = (page - 1) * size;

        var rows = fetchGroupUsersDetailed(adminId, size, offset);
        StringJoiner payload = new StringJoiner(",");
        StringBuilder sb = new StringBuilder("Ваша группа (стр. "+page+"/"+pages+"):\n");
        int i=1;
        for (UserRepo.UserRow r : rows) {
            payload.add(r.id);
            sb.append(i++).append(". ").append(formatRow(r)).append("\n");
        }
        StateRepo.set(adminId, armStateType, 1, payload.toString());
        SendMessage msg = new SendMessage(String.valueOf(chatId), sb.toString() + "\n" + prompt);
        msg.setReplyMarkup(Keyboards.pager(base, page, pages)); // ⬅️ 📄 ➡️
        safeExecute(msg);
    }

    private void renderAdminsPicker(long chatId, String base, int page, String armStateType, String prompt) throws Exception {
        int size = 10;
        int total = UserRepo.countAdmins(); // ADMIN
        if (total <= 0) {
            SendMessage empty = new SendMessage(String.valueOf(chatId), "Список админов пуст.");
            empty.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(empty);
            return;
        }
        int pages = Math.max(1, (int)Math.ceil(total / (double) size));
        page = Math.min(Math.max(1, page), pages);
        int offset = (page - 1) * size;

        var rows = UserRepo.adminsPagedDetailed(size, offset);
        StringJoiner payload = new StringJoiner(",");
        StringBuilder sb = new StringBuilder("Действующие админы (стр. "+page+"/"+pages+"):\n");
        int i=1;
        for (UserRepo.UserRow r : rows) {
            payload.add(r.id);
            sb.append(i++).append(". ").append(formatRow(r)).append("\n");
        }
        StateRepo.set(String.valueOf(chatId), armStateType, 1, payload.toString());
        SendMessage msg = new SendMessage(String.valueOf(chatId), sb.toString() + "\n" + prompt);
        msg.setReplyMarkup(Keyboards.pager(base, page, pages));
        safeExecute(msg);
    }

    // ===== запросы для списков =====

    private List<UserRepo.UserRow> fetchGroupUsersDetailed(String adminId, int limit, int offset) throws Exception {
        List<UserRepo.UserRow> out = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT u.id, u.username, u.first_name " +
                             "FROM users u JOIN groups g ON g.user_id = u.id " +
                             "WHERE g.admin_id=? AND u.active=1 " +
                             "ORDER BY u.rowid DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, adminId);
            ps.setInt(2, Math.max(1, limit));
            ps.setInt(3, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UserRepo.UserRow(
                            rs.getString("id"),
                            rs.getString("username"),
                            rs.getString("first_name")
                    ));
                }
            }
        }
        return out;
    }

    private int countGroupUsers(String adminId) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM users u JOIN groups g ON g.user_id=u.id " +
                             "WHERE g.admin_id=? AND u.active=1")) {
            ps.setString(1, adminId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private void renderMyGroup(long chatId, String adminId, int page) {
        try {
            int size = 10;
            int total = countGroupUsers(adminId);
            if (total <= 0) {
                SendMessage empty = new SendMessage(String.valueOf(chatId), "В вашей группе пока нет пользователей.");
                empty.setReplyMarkup(Keyboards.backToAdmin());
                safeExecute(empty);
                return;
            }
            int pages = Math.max(1, (int)Math.ceil(total / (double) size));
            page = Math.min(Math.max(1, page), pages);
            int offset = (page - 1) * size;

            var rows = fetchGroupUsersDetailed(adminId, size, offset);
            StringBuilder sb = new StringBuilder("Моя группа (стр. " + page + "/" + pages + "):\n");
            int i = offset + 1;
            for (UserRepo.UserRow r : rows) {
                sb.append(i++).append(". ").append(formatRow(r)).append("\n");
            }

            SendMessage sm = new SendMessage(String.valueOf(chatId), sb.toString());
            sm.setReplyMarkup(Keyboards.pager("mygroup", page, pages));
            safeExecute(sm);
        } catch (Exception e) {
            SendMessage err = new SendMessage(String.valueOf(chatId), "Не удалось загрузить группу: " + e.getMessage());
            err.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(err);
        }
    }

    private void renderAllUsers(long chatId, int page) {
        try {
            int size = 20;
            int total = UserRepo.countUsers();
            if (total <= 0) {
                SendMessage empty = new SendMessage(String.valueOf(chatId), "Активных пользователей пока нет.");
                empty.setReplyMarkup(Keyboards.backToAdmin());
                safeExecute(empty);
                return;
            }
            int pages = Math.max(1,(int)Math.ceil(total/(double)size));
            page = Math.min(Math.max(1,page), pages);
            int offset = (page-1)*size;

            var rows = UserRepo.allUsersPagedDetailed(size, offset); // DESC по rowid
            StringBuilder sb = new StringBuilder("Активные пользователи (стр. "+page+"/"+pages+"):\n");
            int i = offset + 1;
            for (UserRepo.UserRow r : rows) {
                sb.append(i++).append(". ").append(formatRow(r)).append("\n");
            }

            SendMessage sm = new SendMessage(String.valueOf(chatId), sb.toString());
            sm.setReplyMarkup(Keyboards.pager("allusers", page, pages));
            safeExecute(sm);
        } catch (Exception e) {
            SendMessage err = new SendMessage(String.valueOf(chatId), "Не удалось загрузить список пользователей: " + e.getMessage());
            err.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(err);
        }
    }

    private void sendReportsPage(String adminId, long chatId, String userId, int page, boolean desc) throws Exception {
        String owner = GroupRepo.adminOf(userId);
        if (owner == null || (!owner.equals(adminId) && UserRepo.role(adminId) != Roles.SUPERADMIN)) {
            SendMessage sm = new SendMessage(String.valueOf(chatId), "Нет доступа.");
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }
        int size = 5;
        int total = ReportRepo.countByUser(userId);
        int pages = Math.max(1, (int)Math.ceil(total/(double)size));
        page = Math.min(Math.max(1,page), pages);
        var rows = ReportRepo.listByUser(userId, page, size, desc);

        StringBuilder sb = new StringBuilder("Отчёты пользователя tg_id: ")
                .append(userId)
                .append(" (стр. ").append(page).append("/").append(pages).append("):\n\n");
        for (String r : rows) sb.append(r).append("\n\n");

        SendMessage sm = new SendMessage(String.valueOf(chatId), sb.toString());
        sm.setReplyMarkup(Keyboards.pager("reports:"+userId+":"+(desc?"desc":"asc"), page, pages));
        safeExecute(sm);
    }
}