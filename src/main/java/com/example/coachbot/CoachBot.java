package com.example.coachbot;

import com.example.coachbot.repo.*;
import com.example.coachbot.service.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;

import java.io.File;
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

    @Override
    public String getBotUsername() { return username; }

    /* ===================== helpers ===================== */

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
    public void safeExecute(SendPhoto sp) {
        try { execute(sp); } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean isAdmin(String tgId) throws Exception {
        Roles r = UserRepo.role(tgId);
        return r == Roles.ADMIN || r == Roles.SUPERADMIN;
    }
    private boolean isSuper(String tgId) throws Exception {
        return UserRepo.role(tgId) == Roles.SUPERADMIN;
    }

    private boolean isInSuperAdmins(String id) {
        String prop = System.getProperty("super.admins", "");
        if (prop == null || prop.isBlank()) return false;
        String[] parts = prop.split("[,\\s]+");
        for (String p : parts) {
            if (!p.isBlank() && p.equals(id)) return true;
        }
        return false;
    }
    private void applyAutoSuper(String tgId) {
        try {
            if (isInSuperAdmins(tgId)) {
                UserRepo.setRole(tgId, Roles.SUPERADMIN);
            }
        } catch (Exception ignored) {}
    }

    private static String helpText() {
        return """
*Как пользоваться ботом* 🧭

*1) Главное меню*  
• 🍽 *План питания* — смотри запланированные калории и БЖУ на сегодня.  
• 🏋️ *Тренировка* — список упражнений с галочками.  
• 📊 *Нормы активности* — вода, шаги и сон на день.  
• 📝 *Отчёт* — заполни дневной отчёт: сон → шаги → вода → КБЖУ.  
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

    private void sendStartPhoto(long chatId, String firstName, boolean isAdminFlag, boolean isSuperFlag) {
        SendPhoto sp = new SendPhoto();
        sp.setChatId(String.valueOf(chatId));
        sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(new File("3.png")));
        sp.setCaption(Texts.start(firstName)); // текст приветствия в caption
        sp.setParseMode(ParseMode.MARKDOWN);
        sp.setReplyMarkup(Keyboards.inlineMainMenu(isAdminFlag, isSuperFlag));
        safeExecute(sp);
    }

    /* ===================== main ===================== */

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
        applyAutoSuper(tgId); // автоповышение SUPERADMIN по SUPERADMINS

        String text = m.hasText() ? m.getText().trim() : "";

        // === /start, /admin, /superadmin ===
        if (text.startsWith("/start") || text.startsWith("/admin") || text.startsWith("/superadmin")) {
            StateRepo.clear(tgId); // всегда выходим из любых визардов
            if (text.startsWith("/start")) {
                sendStartPhoto(m.getChatId(), m.getFrom().getFirstName(), isAdmin(tgId), isSuper(tgId));
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
                sm.setReplyMarkup(Keyboards.adminPanel()); // без супер-кнопок
                safeExecute(sm);
                return;
            }
            if (text.startsWith("/superadmin")) {
                if (!isSuper(tgId)) {
                    SendMessage sm = new SendMessage(String.valueOf(m.getChatId()), "Команда только для главных админов.");
                    sm.setReplyMarkup(Keyboards.backToMenu());
                    safeExecute(sm);
                    return;
                }
                SendMessage sm = md(m.getChatId(), "🛠 Панель супер-админа. Выберите действие:");
                sm.setReplyMarkup(Keyboards.superAdminPanel());
                safeExecute(sm);
                return;
            }
        }

        // /help
        if (text.startsWith("/help")) {
            SendMessage sm = md(m.getChatId(), helpText());
            sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
            safeExecute(sm);
            return;
        }

        // --- пользовательский визард отчёта
        var stUser = StateRepo.get(tgId);
        if (stUser != null && "REPORT".equals(stUser.type())) {
            if (text.startsWith("/")) {
                SendMessage warn = new SendMessage(String.valueOf(m.getChatId()),
                        "Вы в процессе записи отчёта. Для отмены нажмите кнопку ниже ✖️");
                warn.setReplyMarkup(Keyboards.reportCancel());
                safeExecute(warn);
                return;
            }
            var sm = ReportWizard.onMessage(tgId, m.getChatId(), m);
            if (sm != null) safeExecute(sm);
            return;
        }

        // --- состояния админа
        var stAdmin = StateRepo.get(tgId);
        if (stAdmin != null) {

            // Контакты — жёсткий визард
            if ("CONTACT".equals(stAdmin.type())) {
                if (text.startsWith("/")) {
                    safeExecute(md(m.getChatId(),"Сейчас идёт ввод контактов. Введите текст контактов или нажмите «✖️ Отменить ввод»."));
                    return;
                }
                var sm = com.example.coachbot.service.ContactWizard.onMessage(tgId, m.getChatId(), text);
                if (sm != null) {
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                }
                return;
            }

            // Добавить в группу: теперь выбор из списка, далее номер
            if ("ASK_GROUP_ADD".equals(stAdmin.type())) {
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

            // Добавить админа (через /superadmin панель)
            if ("ASK_ADMIN_ADD".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    if (text.startsWith("/")) {
                        SendMessage warn = md(m.getChatId(),"Введите *tg_id* пользователя для назначения администратором.");
                        warn.setReplyMarkup(Keyboards.superAdminBack());
                        safeExecute(warn);
                        return;
                    }
                    String uid = text.replace("@","").trim();
                    if (uid.isEmpty()) {
                        SendMessage err = md(m.getChatId(),"Укажите корректный *tg_id*.");
                        err.setReplyMarkup(Keyboards.superAdminBack());
                        safeExecute(err);
                        return;
                    }
                    UserRepo.ensureAdmin(uid);
                    SendMessage ok = md(m.getChatId(), "Админ добавлен: " + uid);
                    ok.setReplyMarkup(Keyboards.superAdminBack());
                    safeExecute(ok);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // Удалить админа (через /superadmin панель)
            if ("ASK_ADMIN_DEL".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    Integer idx = parseInt(text);
                    String[] ids = stAdmin.payload().split(",");
                    if (idx == null || idx < 1 || idx > ids.length) {
                        SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                        err.setReplyMarkup(Keyboards.superAdminBack());
                        safeExecute(err);
                        return;
                    }
                    String uid = ids[idx - 1];
                    UserRepo.setRole(uid, Roles.USER);
                    SendMessage ok = md(m.getChatId(), "Админ удалён: " + uid);
                    ok.setReplyMarkup(Keyboards.superAdminBack());
                    safeExecute(ok);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // Установить КБЖУ: выбор по номеру -> дата
            if ("ASK_SET_CAL".equals(stAdmin.type())) {
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
                        StateRepo.set(tgId, "ASK_SET_CAL", 2, uid);
                        SendMessage q = md(m.getChatId(),
                                "Укажите дату в формате `dd.MM.yyyy` или выберите кнопку ниже:\n" +
                                        "_Подсказка: «1 день» — сегодня, «2 день» — завтра, … «7 день» — через 6 дней._");
                        q.setReplyMarkup(Keyboards.dateQuickPick("date:setcal", TimeUtil.today()));
                        safeExecute(q);
                        return;
                    }
                    case 2 -> {
                        String uid = stAdmin.payload();
                        LocalDate date = TimeUtil.parseDate(text);
                        if (date == null) {
                            SendMessage err = md(m.getChatId(),"Неверная дата. Введите в формате `dd.MM.yyyy`, например `01.11.2025`.");
                            err.setReplyMarkup(Keyboards.dateQuickPick("date:setcal", TimeUtil.today()));
                            safeExecute(err);
                            return;
                        }
                        safeExecute(CaloriesWizard.start(tgId, m.getChatId(), uid, date));
                        return;
                    }
                }
            }

            // Установить план: выбор по номеру -> дата
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
                        SendMessage q = md(m.getChatId(),
                                "Укажите дату в формате `dd.MM.yyyy` или выберите кнопку ниже:\n" +
                                        "_Подсказка: «1 день» — сегодня, «2 день» — завтра, … «7 день» — через 6 дней._");
                        q.setReplyMarkup(Keyboards.dateQuickPick("date:setplan", TimeUtil.today()));
                        safeExecute(q);
                        return;
                    }
                    case 2 -> {
                        String uid = stAdmin.payload();
                        LocalDate date = TimeUtil.parseDate(text);
                        if (date == null) {
                            SendMessage err = md(m.getChatId(),"Неверная дата. Введите в формате `dd.MM.yyyy`, например `01.11.2025`.");
                            err.setReplyMarkup(Keyboards.dateQuickPick("date:setplan", TimeUtil.today()));
                            safeExecute(err);
                            return;
                        }
                        safeExecute(PlanWizard.start(tgId, m.getChatId(), uid, date));
                        return;
                    }
                }
            }

            // Установить нормы: выбор по номеру -> дата
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
                        SendMessage q = md(m.getChatId(),
                                "Укажите дату в формате `dd.MM.yyyy` или выберите кнопку ниже:\n" +
                                        "_Подсказка: «1 день» — сегодня, «2 день» — завтра, … «7 день» — через 6 дней._");
                        q.setReplyMarkup(Keyboards.dateQuickPick("date:setnorm", TimeUtil.today()));
                        safeExecute(q);
                        return;
                    }
                    case 2 -> {
                        String uid = stAdmin.payload();
                        LocalDate date = TimeUtil.parseDate(text);
                        if (date == null) {
                            SendMessage err = md(m.getChatId(),"Неверная дата. Введите в формате `dd.MM.yyyy`, например `01.11.2025`.");
                            err.setReplyMarkup(Keyboards.dateQuickPick("date:setnorm", TimeUtil.today()));
                            safeExecute(err);
                            return;
                        }
                        safeExecute(NormWizard.start(tgId, m.getChatId(), uid, date));
                        return;
                    }
                }
            }

            // Время рассылки (для своей группы — доступно всем админам)
            if ("ASK_SET_TIME".equals(stAdmin.type())) {
                if (!isAdmin(tgId)) {
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
                String t = text.trim();
                if (!t.matches("^\\d{2}:\\d{2}$")) {
                    SendMessage sm = md(m.getChatId(),"Неверный формат. Пример: `19:00`.");
                    sm.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(sm);
                    return;
                }
                SettingsRepo.set("evening_time:" + tgId, t);
                SendMessage ok = new SendMessage(String.valueOf(m.getChatId()), "Вечерняя рассылка для вашей группы установлена на " + t + ".");
                ok.setReplyMarkup(Keyboards.backToAdmin());
                safeExecute(ok);
                StateRepo.clear(tgId);
                return;
            }

            // Рабочие шаги визардов
            switch (stAdmin.type()) {
                case "SET_CAL" -> { var sm = CaloriesWizard.onMessage(tgId, m.getChatId(), text); if (sm != null) safeExecute(sm); return; }
                case "SET_PLAN" -> { var sm = PlanWizard.onMessage(tgId, m.getChatId(), text); if (sm != null) safeExecute(sm); return; }
                case "SET_NORM" -> { var sm = NormWizard.onMessage(tgId, m.getChatId(), text); if (sm != null) safeExecute(sm); return; }
            }
        }

        // по умолчанию
        SendMessage sm = new SendMessage(String.valueOf(m.getChatId()), "Пожалуйста, используйте меню ниже.");
        sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
        safeExecute(sm);
    }

    /* ======== БЛОКИРОВКА КНОПОК ДЛЯ АДМИН-ВИЗАРДОВ ======== */

    private boolean isAdminWizard(String t) {
        return switch (t) {
            case "CONTACT",
                 "ASK_SET_CAL","ASK_SET_PLAN","ASK_SET_NORM",
                 "ASK_GROUP_ADD","ASK_GROUP_DEL",
                 "ASK_ADMIN_ADD","ASK_ADMIN_DEL",
                 "ASK_SET_TIME",
                 "SET_CAL","SET_PLAN","SET_NORM" -> true;
            default -> false;
        };
    }

    private boolean adminWizardAllows(String type, String data) {
        // Разрешаем выход в любое время
        if (data.equals("menu:main") || data.equals("menu:admin") || data.equals("menu:super")) return true;

        return switch (type) {
            case "CONTACT" -> data.equals("contact:cancel") || data.equals("menu:admin");
            case "ASK_SET_CAL" -> data.startsWith("pick:setcal") || data.startsWith("date:setcal") || data.equals("menu:admin");
            case "ASK_SET_PLAN" -> data.startsWith("pick:setplan") || data.startsWith("date:setplan") || data.equals("menu:admin");
            case "ASK_SET_NORM" -> data.startsWith("pick:setnorm") || data.startsWith("date:setnorm") || data.equals("menu:admin");
            case "ASK_GROUP_DEL" -> data.startsWith("pick:groupdel") || data.equals("menu:admin");
            case "ASK_GROUP_ADD" -> data.startsWith("pick:addgroup") || data.equals("menu:admin");
            case "ASK_ADMIN_ADD" -> data.equals("menu:super");
            case "ASK_ADMIN_DEL" -> data.startsWith("pick:admindel") || data.equals("menu:super");
            case "ASK_SET_TIME" -> data.equals("menu:admin");
            case "SET_PLAN" -> data.equals("plan:finish") || data.equals("menu:admin");
            case "SET_CAL", "SET_NORM" -> data.equals("menu:admin");
            default -> true;
        };
    }

    private void warnAdminBusy(long chatId, String type) {
        SendMessage sm = new SendMessage(String.valueOf(chatId),
                "Вы в процессе админ-действия. Завершите текущий шаг или вернитесь в админ-панель.");
        if ("CONTACT".equals(type)) sm.setReplyMarkup(Keyboards.contactCancelOnly());
        else if ("ASK_ADMIN_ADD".equals(type) || "ASK_ADMIN_DEL".equals(type)) sm.setReplyMarkup(Keyboards.superAdminBack());
        else sm.setReplyMarkup(Keyboards.backToAdmin());
        safeExecute(sm);
    }

    private void handleCallback(CallbackQuery cq) throws Exception {
        String data = cq.getData();
        String tgId = String.valueOf(cq.getFrom().getId());
        long chatId = cq.getMessage().getChatId();

        applyAutoSuper(tgId); // автоповышение и по callback-ам

        try { execute(AnswerCallbackQuery.builder().callbackQueryId(cq.getId()).build()); } catch (Exception ignored) {}

        // ===== быстрый выход в меню =====
        if (data.equals("menu:main")) {
            StateRepo.clear(tgId); // чистим состояние
            // текст без фото (чтобы избежать «двойного /start»)
            SendMessage sm = md(chatId, Texts.start(cq.getFrom().getFirstName()));
            sm.setReplyMarkup(Keyboards.inlineMainMenu(isAdmin(tgId), isSuper(tgId)));
            safeExecute(sm);
            return;
        }
        if (data.equals("menu:admin")) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Команда только для админов.")); return; }
            StateRepo.clear(tgId); // при входе в админку чистим состояние
            SendMessage sm = md(chatId, Texts.adminTitle());
            sm.setReplyMarkup(Keyboards.adminPanel());
            safeExecute(sm);
            return;
        }
        if (data.equals("menu:super")) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Команда только для главных админов.")); return; }
            StateRepo.clear(tgId);
            SendMessage sm = md(chatId, "🛠 Панель супер-админа. Выберите действие:");
            sm.setReplyMarkup(Keyboards.superAdminPanel());
            safeExecute(sm);
            return;
        }

        // ===== блокировка во время отчёта =====
        var stUser = StateRepo.get(tgId);
        if (stUser != null && "REPORT".equals(stUser.type())) {
            if (!"report:cancel".equals(data)) {
                SendMessage warn = new SendMessage(String.valueOf(chatId),
                        "Вы в процессе записи отчёта. Для отмены нажмите кнопку ниже ✖️");
                warn.setReplyMarkup(Keyboards.reportCancel());
                safeExecute(warn);
                return;
            }
        }

        // ===== защита во время админ-визардов (с нашими послаблениями) =====
        var stAdmin = StateRepo.get(tgId);
        if (stAdmin != null && isAdminWizard(stAdmin.type())) {
            if (!adminWizardAllows(stAdmin.type(), data)) {
                warnAdminBusy(chatId, stAdmin.type());
                return;
            }
        }

        // меню пользователя
        if ("menu:food".equals(data)) {
            String msg = PlanRepo.getNutritionText(tgId, TimeUtil.today());
            SendMessage sm = new SendMessage(String.valueOf(chatId), msg);
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }

        if ("menu:workout".equals(data)) {
            String msg = PlanRepo.getWorkoutText(tgId, TimeUtil.today());
            if (msg == null || msg.isBlank()) {
                SendMessage sm = new SendMessage(String.valueOf(chatId), "План тренировки на сегодня не задан.");
                sm.setReplyMarkup(Keyboards.backToMenu());
                safeExecute(sm);
                return;
            }
            int max = 3800;
            for (int i=0; i<msg.length(); i+=max) {
                String part = msg.substring(i, Math.min(msg.length(), i+max));
                SendMessage sm = new SendMessage(String.valueOf(chatId), part);
                if (i==0) sm.setReplyMarkup(Keyboards.backToMenu());
                safeExecute(sm);
            }
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

        // Моя группа
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

        // Старт визардов
        if ("admin:groupadd".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderAllUsersPickerForAdd(chatId, tgId, 1);
            return;
        }
        if ("admin:groupdel".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:groupdel", 1, "ASK_GROUP_DEL", "Выберите пользователя по номеру для удаления из группы:");
            return;
        }
        if ("admin:setcal".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:setcal", 1, "ASK_SET_CAL", "Выберите пользователя по номеру из вашей группы:");
            return;
        }
        if ("admin:setplan".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:setplan", 1, "ASK_SET_PLAN", "Выберите пользователя по номеру из вашей группы:");
            return;
        }
        if ("admin:setnorma".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:setnorm", 1, "ASK_SET_NORM", "Выберите пользователя по номеру из вашей группы:");
            return;
        }

        // Контакты тренера
        if ("admin:contact".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            safeExecute(com.example.coachbot.service.ContactWizard.start(tgId, chatId));
            return;
        }

        // Время рассылки — доступно всем админам (персонифицировано)
        if ("admin:settime".equals(data)) {
            if (!isAdmin(tgId)) {
                safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов."));
                return;
            }
            StateRepo.set(tgId, "ASK_SET_TIME", 1, "");
            String current = SettingsRepo.get("evening_time:" + tgId, "19:00");
            SendMessage sm = md(chatId,
                    "Введите время для *вашей группы* в формате `HH:mm` (часовой пояс: " +
                            System.getProperty("bot.tz", "Asia/Yekaterinburg") + ").\n" +
                            "Текущее значение: `" + current + "`");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            return;
        }

        // Пагинация пиков
        if (data.startsWith("pick:setcal:")) {
            int page = Integer.parseInt(data.substring("pick:setcal:".length()));
            renderGroupPicker(chatId, tgId, "pick:setcal", page, "ASK_SET_CAL", "Выберите пользователя по номеру из вашей группы:");
            return;
        }
        if (data.startsWith("pick:setplan:")) {
            int page = Integer.parseInt(data.substring("pick:setplan:".length()));
            renderGroupPicker(chatId, tgId, "pick:setplan", page, "ASK_SET_PLAN", "Выберите пользователя по номеру из вашей группы:");
            return;
        }
        if (data.startsWith("pick:setnorm:")) {
            int page = Integer.parseInt(data.substring("pick:setnorm:".length()));
            renderGroupPicker(chatId, tgId, "pick:setnorm", page, "ASK_SET_NORM", "Выберите пользователя по номеру из вашей группы:");
            return;
        }
        if (data.startsWith("pick:addgroup:")) {
            int page = Integer.parseInt(data.substring("pick:addgroup:".length()));
            renderAllUsersPickerForAdd(chatId, tgId, page);
            return;
        }

        // Быстрые даты для админ-визардов (1..7 день; 1 = сегодня)
        if (data.startsWith("date:setcal:") || data.startsWith("date:setplan:") || data.startsWith("date:setnorm:")) {
            String tail = data.substring(data.lastIndexOf(':') + 1);
            int dayIdx = 1;
            try { dayIdx = Integer.parseInt(tail); } catch (Exception ignored) {}
            if (dayIdx < 1) dayIdx = 1;
            if (dayIdx > 7) dayIdx = 7;

            LocalDate base = TimeUtil.today();
            LocalDate date = base.plusDays(dayIdx - 1);

            var stAdmin2 = StateRepo.get(tgId);
            if (stAdmin2 == null ||
                    !(stAdmin2.type().equals("ASK_SET_CAL") || stAdmin2.type().equals("ASK_SET_PLAN") || stAdmin2.type().equals("ASK_SET_NORM")) ||
                    stAdmin2.step() != 2) {
                return;
            }
            String uid = stAdmin2.payload(); // шаг 2: payload = uid
            if (data.startsWith("date:setcal:")) {
                safeExecute(CaloriesWizard.start(tgId, chatId, uid, date));
            } else if (data.startsWith("date:setplan:")) {
                safeExecute(PlanWizard.start(tgId, chatId, uid, date));
            } else {
                safeExecute(NormWizard.start(tgId, chatId, uid, date));
            }
            return;
        }

        // ===== СУПЕР-АДМИНКА =====

        // Открыть панель супер-админа по кнопке (если решишь добавить в UI)
        if ("super:panel".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            StateRepo.clear(tgId);
            SendMessage sm = md(chatId, "🛠 Панель супер-админа. Выберите действие:");
            sm.setReplyMarkup(Keyboards.superAdminPanel());
            safeExecute(sm);
            return;
        }

        // Добавить админа
        if ("super:add".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_ADMIN_ADD", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя для назначения администратором.");
            sm.setReplyMarkup(Keyboards.superAdminBack());
            safeExecute(sm);
            return;
        }

        // Удалить админа: список с пагинацией
        if ("super:del".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            renderAdminsPicker(tgId, chatId, "pick:admindel", 1, "ASK_ADMIN_DEL", "Выберите администратора по номеру для снятия прав:");
            return;
        }
        if (data.startsWith("pick:admindel:")) {
            if (!isSuper(tgId)) { return; }
            int page = Integer.parseInt(data.substring("pick:admindel:".length()));
            renderAdminsPicker(tgId, chatId, "pick:admindel", page, "ASK_ADMIN_DEL", "Выберите администратора по номеру для снятия прав:");
            return;
        }

        // отчёт
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

        // Пагинация отчётов
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

    /* ==================== пикеры списков ==================== */

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
        msg.setReplyMarkup(Keyboards.pager(base, page, pages));
        safeExecute(msg);
    }

    private void renderAdminsPicker(String adminId, long chatId, String base, int page, String armStateType, String prompt) throws Exception {
        int size = 10;
        int total = UserRepo.countAdmins();
        if (total <= 0) {
            SendMessage empty = new SendMessage(String.valueOf(chatId), "Список админов пуст.");
            empty.setReplyMarkup(Keyboards.superAdminBack());
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
        StateRepo.set(adminId, armStateType, 1, payload.toString());
        SendMessage msg = new SendMessage(String.valueOf(chatId), sb.toString() + "\n" + prompt);
        msg.setReplyMarkup(Keyboards.pager(base, page, pages));
        safeExecute(msg);
    }

    // Пикер всех пользователей для добавления в группу
    private void renderAllUsersPickerForAdd(long chatId, String adminId, int page) {
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
            StringJoiner payload = new StringJoiner(",");
            StringBuilder sb = new StringBuilder("Активные пользователи (стр. "+page+"/"+pages+"):\n");
            int i = 1;
            for (UserRepo.UserRow r : rows) {
                payload.add(r.id);
                sb.append(i++).append(". ").append(formatRow(r)).append("\n");
            }

            StateRepo.set(adminId, "ASK_GROUP_ADD", 1, payload.toString());
            SendMessage sm = new SendMessage(String.valueOf(chatId), sb.toString() + "\nВыберите пользователя по номеру для добавления в вашу группу:");
            sm.setReplyMarkup(Keyboards.pager("pick:addgroup", page, pages));
            safeExecute(sm);
        } catch (Exception e) {
            SendMessage err = new SendMessage(String.valueOf(chatId), "Не удалось загрузить список пользователей: " + e.getMessage());
            err.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(err);
        }
    }

    // ===== данные для списков =====

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