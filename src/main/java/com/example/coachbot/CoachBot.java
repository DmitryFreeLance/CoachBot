package com.example.coachbot;

import com.example.coachbot.repo.*;
import com.example.coachbot.service.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;

import java.time.LocalDate;

public class CoachBot extends TelegramLongPollingBot {

    private final String username;
    public CoachBot(String username, String token) {
        super(token);
        this.username = username;
    }
    @Override public String getBotUsername() { return username; }

    // helpers
    private static SendMessage md(long chatId, String text) {
        SendMessage sm = new SendMessage(String.valueOf(chatId), text);
        sm.setParseMode(ParseMode.MARKDOWN);
        return sm;
    }

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

    public void safeExecute(SendMessage sm) {
        try { execute(sm); } catch (Exception ignored) {}
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!UpdatesRepo.markProcessed(update.getUpdateId())) return; // защита от дублей

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
            StateRepo.clear(tgId); // сброс любых текущих ожиданий
            if (text.startsWith("/start")) {
                SendMessage sm = md(m.getChatId(), Texts.start(m.getFrom().getFirstName()));
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

        // /help — памятка для пользователя
        if (text.startsWith("/help")) {
            SendMessage sm = md(m.getChatId(), helpText());
            sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
            safeExecute(sm);
            return;
        }

        // --- состояние пользователя: отчёт
        var stUser = StateRepo.get(tgId);
        if (stUser != null && "REPORT".equals(stUser.type())) {
            // блокируем команды во время отчёта, кроме нашей отмены через inline
            if (text.startsWith("/")) {
                safeExecute(md(m.getChatId(),"Сначала завершите отчёт или отмените его кнопкой «✖️ Отменить заполнение»."));
                return;
            }
            var sm = ReportWizard.onMessage(tgId, m.getChatId(), m);
            if (sm != null) safeExecute(sm);
            return;
        }

        // --- состояние админа: мини-визарды
        var stAdmin = StateRepo.get(tgId);
        if (stAdmin != null) {

            // Жёсткая блокировка во время ввода контактов тренера
            if ("CONTACT".equals(stAdmin.type())) {
                if (text.startsWith("/")) {
                    safeExecute(md(m.getChatId(),"Сейчас идёт ввод контактов. Введите текст контактов или нажмите «✖️ Отменить ввод»."));
                    return;
                }
                var sm = ContactWizard.onMessage(tgId, m.getChatId(), text);
                if (sm != null) {
                    sm.setReplyMarkup(Keyboards.contactCancelOnly());
                    safeExecute(sm);
                }
                return;
            }

            // Установка КБЖУ: tg_id -> дата
            if ("ASK_SET_CAL".equals(stAdmin.type())) {
                if (text.startsWith("/")) {
                    SendMessage warn = md(m.getChatId(),"Введите данные, пожалуйста. Для выхода нажмите «🔙 Вернуться в админ-панель».");
                    warn.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(warn);
                    return;
                }
                switch (stAdmin.step()) {
                    case 1 -> { // ждём tg_id
                        String uid = text;
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
                        safeExecute(CaloriesWizard.start(tgId, m.getChatId(), uid, date));
                        StateRepo.clear(tgId);
                        return;
                    }
                }
            }

            // Установка плана: tg_id -> дата
            if ("ASK_SET_PLAN".equals(stAdmin.type())) {
                if (text.startsWith("/")) {
                    SendMessage warn = md(m.getChatId(),"Введите данные, пожалуйста. Для выхода нажмите «🔙 Вернуться в админ-панель».");
                    warn.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(warn);
                    return;
                }
                switch (stAdmin.step()) {
                    case 1 -> {
                        String uid = text;
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
                        safeExecute(PlanWizard.start(tgId, m.getChatId(), uid, date));
                        StateRepo.clear(tgId);
                        return;
                    }
                }
            }

            // Установка норм: tg_id -> дата
            if ("ASK_SET_NORM".equals(stAdmin.type())) {
                if (text.startsWith("/")) {
                    SendMessage warn = md(m.getChatId(),"Введите данные, пожалуйста. Для выхода нажмите «🔙 Вернуться в админ-панель».");
                    warn.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(warn);
                    return;
                }
                switch (stAdmin.step()) {
                    case 1 -> {
                        String uid = text;
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
                        safeExecute(NormWizard.start(tgId, m.getChatId(), uid, date));
                        StateRepo.clear(tgId);
                        return;
                    }
                }
            }

            // Установка времени рассылки (только супер-админ)
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

            // Добавить админа: ждём tg_id
            if ("ASK_ADMIN_ADD".equals(stAdmin.type())) {
                if (UserRepo.role(tgId) != Roles.SUPERADMIN) { safeExecute(md(m.getChatId(),"Недостаточно прав.")); StateRepo.clear(tgId); return; }
                if (text.startsWith("/")) {
                    SendMessage sm = md(m.getChatId(),"Введите *tg_id* пользователя.");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    return;
                }
                String uid = text.replace("@","");
                if (uid.isEmpty()) {
                    SendMessage sm = md(m.getChatId(), "Введите *tg_id* пользователя:");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    return;
                }
                UserRepo.ensureAdmin(uid);
                SendMessage ok = md(m.getChatId(), "Админ добавлен: " + uid);
                ok.setReplyMarkup(Keyboards.backToAdmin());
                safeExecute(ok);
                StateRepo.clear(tgId);
                return;
            }

            // Удалить админа: ждём tg_id
            if ("ASK_ADMIN_DEL".equals(stAdmin.type())) {
                if (UserRepo.role(tgId) != Roles.SUPERADMIN) { safeExecute(md(m.getChatId(),"Недостаточно прав.")); StateRepo.clear(tgId); return; }
                if (text.startsWith("/")) {
                    SendMessage sm = md(m.getChatId(),"Введите *tg_id* администратора.");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    return;
                }
                String uid = text.replace("@","");
                if (uid.isEmpty()) {
                    SendMessage sm = md(m.getChatId(), "Введите *tg_id* администратора:");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    return;
                }
                UserRepo.setRole(uid, Roles.USER);
                SendMessage ok = md(m.getChatId(), "Админ удалён: " + uid);
                ok.setReplyMarkup(Keyboards.backToAdmin());
                safeExecute(ok);
                StateRepo.clear(tgId);
                return;
            }

            // Старые визарды (SET_CAL / SET_PLAN / SET_NORM)
            switch (stAdmin.type()) {
                case "SET_CAL" -> { safeExecute(CaloriesWizard.onMessage(tgId, m.getChatId(), text)); return; }
                case "SET_PLAN" -> { safeExecute(PlanWizard.onMessage(tgId, m.getChatId(), text)); return; }
                case "SET_NORM" -> { safeExecute(NormWizard.onMessage(tgId, m.getChatId(), text)); return; }
            }
        }

        // --- прочие команды верхнего уровня
        if (text.startsWith("/settime")) { // ручной ввод по желанию
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

        // по умолчанию — предлагаем пользоваться меню
        SendMessage sm = new SendMessage(String.valueOf(m.getChatId()), "Пожалуйста, используйте меню ниже.");
        sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
        safeExecute(sm);
    }

    private void handleCallback(CallbackQuery cq) throws Exception {
        String data = cq.getData();
        String tgId = String.valueOf(cq.getFrom().getId());
        long chatId = cq.getMessage().getChatId();

        // Если идёт ввод контактов — блокируем любые кнопки, кроме "contact:cancel"
        var st = StateRepo.get(tgId);
        if (st != null && "CONTACT".equals(st.type())) {
            if ("contact:cancel".equals(data)) {
                StateRepo.clear(tgId);
                // без сообщения — сразу показываем админ-панель
                SendMessage panel = md(chatId, Texts.adminTitle());
                panel.setReplyMarkup(Keyboards.adminPanel(isSuper(tgId)));
                safeExecute(panel);
                execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
                return;
            } else {
                execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).text("Сначала завершите ввод контактов или отмените.").showAlert(true).build());
                return;
            }
        }

        // ---- навигация главного меню
        if ("menu:main".equals(data)) {
            SendMessage sm = md(chatId, Texts.start(cq.getFrom().getFirstName()));
            sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        if ("menu:food".equals(data)) {
            String msg = PlanRepo.getNutritionText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        if ("menu:workout".equals(data)) {
            String msg = PlanRepo.getWorkoutText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        if ("menu:norms".equals(data)) {
            String msg = NormRepo.getNormsText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
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
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        if ("menu:report".equals(data)) {
            safeExecute(ReportWizard.start(tgId, chatId));
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // ---- админ-панель (вход)
        if ("menu:admin".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Команда только для админов.")); return; }
            SendMessage sm = md(chatId, Texts.adminTitle());
            sm.setReplyMarkup(Keyboards.adminPanel(isSuper(tgId)));
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Моя группа
        if ("admin:my".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Только для админов.")); return; }
            int total = GroupRepo.countUsersOfAdmin(tgId);
            var ids = GroupRepo.usersOfAdmin(tgId, 50, 0);
            StringBuilder sb = new StringBuilder("Моя группа ("+total+"):\n");
            int i=1;
            for (String id : ids) sb.append(i++).append(". ").append(mention(id)).append("  tg_id: ").append(id).append("\n");
            SendMessage sm = md(chatId, ids.isEmpty() ? "В вашей группе пока нет пользователей." : sb.toString());
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Все пользователи — последняя страница (новые сверху) + в том же сообщении "назад в админ-панель"
        if ("admin:all".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Только для админов.")); return; }
            renderAllUsers(chatId, /*page*/-1); // -1 = последняя
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Пагинация "Все пользователи"
        if (data.startsWith("allusers:")) {
            String pageStr = data.substring("allusers:".length());
            int page = Integer.parseInt(pageStr);
            renderAllUsers(chatId, page);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Установить КБЖУ — мини-визард (tg_id -> дата)
        if ("admin:setcal".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Только для админов.")); return; }
            StateRepo.set(tgId, "ASK_SET_CAL", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Установить план — мини-визард
        if ("admin:setplan".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Только для админов.")); return; }
            StateRepo.set(tgId, "ASK_SET_PLAN", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Установить нормы — мини-визард
        if ("admin:setnorma".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Только для админов.")); return; }
            StateRepo.set(tgId, "ASK_SET_NORM", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Контакты — жёсткий визард: только ввод или отмена
        if ("admin:contact".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(md(chatId, "Только для админов.")); return; }
            SendMessage sm = ContactWizard.start(tgId, chatId);
            sm.setReplyMarkup(Keyboards.contactCancelOnly());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Время рассылки — мини-визард ввода времени (только супер-админ)
        if ("admin:settime".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(md(chatId, "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_SET_TIME", 1, "");
            SendMessage sm = md(chatId, "Введите время в формате `HH:mm` (Екатеринбург):");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Добавить админа — мини-визард: просим только tg_id
        if ("admin:add".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(md(chatId, "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_ADMIN_ADD", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя для назначения администратором:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // Удалить админа — мини-визард: просим только tg_id
        if ("admin:del".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(md(chatId, "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_ADMIN_DEL", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* администратора для снятия прав:");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // отчёт: отмена и старт
        if ("report:cancel".equals(data)) {
            safeExecute(ReportWizard.cancel(String.valueOf(cq.getFrom().getId()), chatId));
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).text("Отчёт отменён").build());
            return;
        }
        if ("report:start".equals(data)) {
            safeExecute(ReportWizard.start(String.valueOf(cq.getFrom().getId()), chatId));
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // пагинация отчётов (если используется inline-пагинация)
        if (data.startsWith("reports:")) {
            String[] p = data.split(":");
            String uid = p[1];
            int page = Integer.parseInt(p[2]);
            boolean desc = "desc".equals(p[3]);
            sendReportsPage(tgId, chatId, uid, page, desc);
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // "заглушка" кнопки
        if ("noop".equals(data)) {
            execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
            return;
        }

        // default
        execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build());
    }

    // Рендер списка пользователей с нашей клавиатурой пагинации + "назад в админ-панель"
    private void renderAllUsers(long chatId, int pageRequested) throws Exception {
        int size = 20;
        int total = UserRepo.countUsers();
        int pages = Math.max(1,(int)Math.ceil(total/(double)size));
        int page = pageRequested == -1 ? pages : Math.min(Math.max(1,pageRequested), pages);
        int offset = (page-1)*size;
        var ids = UserRepo.allUsersPaged(size, offset);

        StringBuilder sb = new StringBuilder("Активные пользователи (стр. "+page+"/"+pages+"):\n");
        int i = offset+1;
        for (String id : ids) sb.append(i++).append(". ").append(mention(id)).append("  tg_id: ").append(id).append("\n");

        SendMessage sm = md(chatId, sb.toString());
        sm.setReplyMarkup(Keyboards.allUsersPager(page, pages));
        safeExecute(sm);
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
        StringBuilder sb = new StringBuilder("Отчёты пользователя ").append(mention(userId))
                .append(" (стр. ").append(page).append("/").append(pages).append("):\n\n");
        for (String r : rows) sb.append(r).append("\n\n");
        SendMessage sm = md(chatId, sb.toString());
        sm.setReplyMarkup(Pagination.pages("reports:"+userId+":"+(page)+":"+(desc?"desc":"asc"), page, pages));
        safeExecute(sm);
    }

    private boolean isAdmin(String tgId) throws Exception {
        Roles r = UserRepo.role(tgId);
        return r == Roles.ADMIN || r == Roles.SUPERADMIN;
    }
    private boolean isSuper(String tgId) throws Exception { return UserRepo.role(tgId) == Roles.SUPERADMIN; }

    private String mention(String tgId) {
        return "[профиль](tg://user?id=" + tgId + ")";
    }
}