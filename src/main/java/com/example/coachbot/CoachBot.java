package com.example.coachbot;

import com.example.coachbot.repo.*;
import com.example.coachbot.service.*;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
• 📏 *Мои параметры* — пошаговый ввод веса и замеров + фото.  
• 📞 *Контакты* — контакты твоего тренера.

*2) Ежедневные напоминания*  
• ⏰ Утро 08:00 — сценарий дня (питание, тренировка, нормы и мотивация).  
• 🌆 Вечер — напоминание с кнопкой «Заполнить отчёт» (время задаёт тренер).

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
                // Приветствие: одно сообщение с фото 3.png + текст
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
                sm.setReplyMarkup(Keyboards.adminPanel());
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
                SendMessage sm = md(m.getChatId(), "🛡 Супер-админ панель. Выберите действие:");
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

        // --- пользовательский визард «Мои параметры»
        if (stUser != null && "PARAMS".equals(stUser.type())) {
            if (text.startsWith("/")) {
                SendMessage warn = new SendMessage(String.valueOf(m.getChatId()),
                        "Сейчас идёт ввод параметров. Отправьте данные или нажмите «✖️ Отменить ввод».");
                warn.setReplyMarkup(Keyboards.paramsCancelOnly());
                safeExecute(warn);
                return;
            }
            Object obj = ParamsWizard.onAny(tgId, m.getChatId(), m);
            if (obj instanceof SendMessage sm) safeExecute(sm);
            else if (obj instanceof SendPhoto sp) safeExecute(sp);
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

            // ========== НОВОЕ: выбор клиента из «Мои клиенты» ==========
            if ("ASK_CLIENT_PICK".equals(stAdmin.type()) && stAdmin.step() == 1) {
                Integer idx = parseInt(text);
                String[] ids = stAdmin.payload().split(",");
                if (idx == null || idx < 1 || idx > ids.length) {
                    SendMessage err = new SendMessage(String.valueOf(m.getChatId()), "Введите номер из списка (1.." + ids.length + ").");
                    err.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(err);
                    return;
                }
                String uid = ids[idx - 1];
                // Показать меню действий для выбранного клиента
                SendMessage sm = new SendMessage(String.valueOf(m.getChatId()),
                        "Клиент выбран: " + uid + "\nВыберите действие:");
                sm.setReplyMarkup(Keyboards.adminClientActions(uid));
                safeExecute(sm);
                StateRepo.clear(tgId);
                return;
            }

            // Добавить в группу — выбор по номеру из общего списка
            if ("ASK_GROUP_ADD".equals(stAdmin.type())) {
                if (stAdmin.step() == 1) {
                    if (text.startsWith("/")) {
                        SendMessage warn = md(m.getChatId(),"Введите *номер* пользователя из списка.");
                        warn.setReplyMarkup(Keyboards.backToAdmin());
                        safeExecute(warn);
                        return;
                    }
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
                            ok ? ("Пользователь " + uid + " добавлен в ваши клиенты.") :
                                    "Не удалось добавить: пользователь уже привязан к другому тренеру (или к вам).");
                    done.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(done);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // Удалить из группы
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
                            ok ? ("Клиент " + uid + " удалён из списка.") :
                                    "Такой пары (пользователь — вы как тренер) нет.");
                    done.setReplyMarkup(Keyboards.backToAdmin());
                    safeExecute(done);
                    StateRepo.clear(tgId);
                    return;
                }
            }

            // Добавить админа — ввод tg_id (в /superadmin)
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

            // СТАРЫЕ визарды установки планов/норм/КБЖУ: рабочие шаги, не трогаем
            switch (stAdmin.type()) {
                case "SET_CAL" -> { var sm = CaloriesWizard.onMessage(tgId, m.getChatId(), text); if (sm != null) safeExecute(sm); return; }
                case "SET_PLAN" -> { var sm = PlanWizard.onMessage(tgId, m.getChatId(), text); if (sm != null) safeExecute(sm); return; }
                case "SET_NORM" -> { var sm = NormWizard.onMessage(tgId, m.getChatId(), text); if (sm != null) safeExecute(sm); return; }
                case "ASK_SET_CAL" -> {
                    if (stAdmin.step() == 2) {
                        // ожидание даты вручную
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
                case "ASK_SET_PLAN" -> {
                    if (stAdmin.step() == 2) {
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
                case "ASK_SET_NORM" -> {
                    if (stAdmin.step() == 2) {
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
        }

        // --- прочие команды
        if (text.startsWith("/settime")) { // быстрый способ для админов
            if (!isAdmin(tgId)) { safeExecute(md(m.getChatId(),"Команда доступна только администраторам.")); return; }
            String[] p = text.split("\\s+");
            if (p.length < 2 || !p[1].matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
                SendMessage err = md(m.getChatId(),"Укажите время в формате `HH:mm`, напр. `19:30`.");
                err.setReplyMarkup(Keyboards.backToMenu());
                safeExecute(err);
                return;
            }
            SettingsRepo.set("evening_time:"+tgId, p[1]);
            SendMessage ok = new SendMessage(String.valueOf(m.getChatId()), "Вечерняя рассылка для вашей группы установлена на " + p[1] + ".");
            ok.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(ok);
            return;
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
                 "ASK_REPORTS_VIEW",
                 "ASK_PARAMS_VIEW",
                 "ASK_CLIENT_PICK",
                 "SET_CAL","SET_PLAN","SET_NORM" -> true;
            default -> false;
        };
    }

    private boolean adminWizardAllows(String type, String data) {
        // Разрешаем выход в любое время
        if (data.equals("menu:main") || data.equals("menu:admin")) return true;

        return switch (type) {
            case "CONTACT" -> data.equals("contact:cancel") || data.equals("menu:admin");
            case "ASK_SET_CAL" -> data.startsWith("pick:setcal") || data.startsWith("date:setcal") || data.equals("menu:admin");
            case "ASK_SET_PLAN" -> data.startsWith("pick:setplan") || data.startsWith("date:setplan") || data.equals("menu:admin");
            case "ASK_SET_NORM" -> data.startsWith("pick:setnorm") || data.startsWith("date:setnorm") || data.equals("menu:admin");
            case "ASK_GROUP_DEL" -> data.startsWith("pick:groupdel") || data.equals("menu:admin");
            case "ASK_GROUP_ADD" -> data.startsWith("pick:groupadd") || data.equals("menu:admin");
            case "ASK_ADMIN_ADD" -> data.equals("menu:admin");
            case "ASK_ADMIN_DEL" -> data.startsWith("pick:admindel") || data.equals("menu:admin");
            case "ASK_SET_TIME" -> data.equals("menu:admin");
            case "ASK_REPORTS_VIEW" ->
                    data.startsWith("pick:reports") || data.equals("menu:admin");
            case "ASK_PARAMS_VIEW"  ->
                    data.startsWith("pick:params")  || data.equals("menu:admin");
            case "ASK_CLIENT_PICK" ->
                    data.startsWith("pick:client")  || data.equals("menu:admin");
            case "SET_PLAN" -> data.equals("plan:finish") || data.equals("menu:admin");
            case "SET_CAL", "SET_NORM" -> data.equals("menu:admin");
            default -> true;
        };
    }

    private void warnAdminBusy(long chatId, String type) {
        SendMessage sm = new SendMessage(String.valueOf(chatId),
                "Вы в процессе админ-действия. Завершите текущий шаг или вернитесь в админ-панель.");
        if ("CONTACT".equals(type)) sm.setReplyMarkup(Keyboards.contactCancelOnly());
        else sm.setReplyMarkup(Keyboards.backToAdmin());
        safeExecute(sm);
    }

    private InlineKeyboardButton btn(String text, String cb) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(cb);
        return b;
    }

    private InlineKeyboardMarkup pickerKeyboard(String base, int page, int pages, List<UserRepo.UserRow> rows) {
        // В этой версии мы используем ввод номера руками (без «Выбрать №…»),
        // поэтому эта клавиатура сейчас не используется. Оставлено как утилита.
        List<List<InlineKeyboardButton>> kb = new ArrayList<>();

        int i = 1;
        for (UserRepo.UserRow r : rows) {
            String label = "Выбрать №" + (i++);
            kb.add(List.of(btn(label, base.replace("pick:", "choose:") + ":" + r.id)));
        }

        List<InlineKeyboardButton> nav = new ArrayList<>();
        nav.add(btn("⬅️", base + ":" + Math.max(1, page - 1)));
        nav.add(btn("📄 " + page + "/" + pages, "noop"));
        nav.add(btn("➡️", base + ":" + Math.min(pages, page + 1)));
        kb.add(nav);

        kb.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));

        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(kb);
        return m;
    }

    // Универсальный safeExecute — принимает Object и сам разбирается с типом
    private void safeExecute(Object m) {
        if (m == null) return;
        try {
            if (m instanceof org.telegram.telegrambots.meta.api.methods.send.SendMessage) {
                execute((org.telegram.telegrambots.meta.api.methods.send.SendMessage) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.send.SendPhoto) {
                execute((org.telegram.telegrambots.meta.api.methods.send.SendPhoto) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup) {
                execute((org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery) {
                execute((org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText) {
                execute((org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption) {
                execute((org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup) {
                execute((org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.send.SendDocument) {
                execute((org.telegram.telegrambots.meta.api.methods.send.SendDocument) m);
            } else if (m instanceof org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage) {
                execute((org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage) m);
            } else {
                System.err.println("safeExecute: unsupported type: " + m.getClass().getName());
            }
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            e.printStackTrace();
        }
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
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            StateRepo.clear(tgId);
            SendMessage sm = md(chatId, "🛡 Супер-админ панель. Выберите действие:");
            sm.setReplyMarkup(Keyboards.superAdminPanel());
            safeExecute(sm);
            return;
        }

        // ===== защита во время отчёта =====
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

        // Отмена ввода контактов
        if ("contact:cancel".equals(data)) {
            StateRepo.clear(tgId);
            SendMessage sm = new SendMessage(String.valueOf(chatId), "Ввод контактов отменён.");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            return;
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

        if ("menu:params".equals(data)) {
            Object obj = ParamsWizard.start(tgId, chatId);
            if (obj instanceof SendMessage sm) safeExecute(sm);
            else if (obj instanceof SendPhoto sp) safeExecute(sp);
            return;
        }

        // Параметры — спец кнопки
        if ("params:cancel".equals(data)) {
            SendMessage sm = ParamsWizard.cancel(tgId, chatId);
            safeExecute(sm);
            return;
        }
        if ("params:skip".equals(data)) { // пропуск фото
            Object resp = ParamsWizard.skip(tgId, chatId);
            safeExecute(resp);
            return;
        }

        // =============== Админ: Мои клиенты (раньше «Моя группа») ===============
        if ("admin:my".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            // Рендерим список клиентов и ждём ввод номера -> потом покажем меню действий
            renderGroupPicker(chatId, tgId, "pick:client", 1, "ASK_CLIENT_PICK",
                    "Выберите клиента по номеру (введите номер сообщением):", false);
            return;
        }
        if (data.startsWith("pick:client:")) {
            int page = Integer.parseInt(data.substring("pick:client:".length()));
            renderGroupPicker(chatId, tgId, "pick:client", page, "ASK_CLIENT_PICK",
                    "Выберите клиента по номеру (введите номер сообщением):", false);
            return;
        }

        // Старт визардов добавления/удаления клиентов
        if ("admin:groupadd".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderAllUsersPicker(chatId, tgId, "pick:groupadd", 1, "ASK_GROUP_ADD", "Выберите пользователя по номеру для добавления в ваши клиенты:");
            return;
        }
        if ("admin:groupdel".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            renderGroupPicker(chatId, tgId, "pick:groupdel", 1, "ASK_GROUP_DEL", "Выберите клиента по номеру для удаления:", false);
            return;
        }

        if ("admin:contact".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            safeExecute(com.example.coachbot.service.ContactWizard.start(tgId, chatId));
            return;
        }

        if ("admin:settime".equals(data)) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            StateRepo.set(tgId, "ASK_SET_TIME", 1, "");
            SendMessage sm = md(chatId, "Введите время *вечерней рассылки* для вашей группы в формате `HH:mm` (напр.: `19:00`).");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            return;
        }

        // СУПЕР-АДМИН
        if ("super:add".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            StateRepo.set(tgId, "ASK_ADMIN_ADD", 1, "");
            SendMessage sm = md(chatId, "Введите *tg_id* пользователя для назначения администратором.");
            sm.setReplyMarkup(Keyboards.superAdminBack());
            safeExecute(sm);
            return;
        }
        if ("super:del".equals(data)) {
            if (!isSuper(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для главных админов.")); return; }
            renderAdminsPicker(tgId, chatId, "pick:admindel", 1, "ASK_ADMIN_DEL", "Выберите администратора по номеру для снятия прав:");
            return;
        }

        // Пагинация пиков
        if (data.startsWith("pick:setcal:")) {
            int page = Integer.parseInt(data.substring("pick:setcal:".length()));
            renderGroupPicker(chatId, tgId, "pick:setcal", page, "ASK_SET_CAL", "Выберите пользователя по номеру из списка:", false);
            return;
        }
        if (data.startsWith("pick:setplan:")) {
            int page = Integer.parseInt(data.substring("pick:setplan:".length()));
            renderGroupPicker(chatId, tgId, "pick:setplan", page, "ASK_SET_PLAN", "Выберите пользователя по номеру из списка:", false);
            return;
        }
        if (data.startsWith("pick:setnorm:")) {
            int page = Integer.parseInt(data.substring("pick:setnorm:".length()));
            renderGroupPicker(chatId, tgId, "pick:setnorm", page, "ASK_SET_NORM", "Выберите пользователя по номеру из списка:", false);
            return;
        }
        if (data.startsWith("pick:groupdel:")) {
            int page = Integer.parseInt(data.substring("pick:groupdel:".length()));
            renderGroupPicker(chatId, tgId, "pick:groupdel", page, "ASK_GROUP_DEL", "Выберите клиента по номеру для удаления:", false);
            return;
        }
        if (data.startsWith("pick:groupadd:")) {
            int page = Integer.parseInt(data.substring("pick:groupadd:".length()));
            renderAllUsersPicker(chatId, tgId, "pick:groupadd", page, "ASK_GROUP_ADD", "Выберите пользователя по номеру для добавления в ваши клиенты:");
            return;
        }
        if (data.startsWith("pick:admindel:")) {
            int page = Integer.parseInt(data.substring("pick:admindel:".length()));
            renderAdminsPicker(tgId, chatId, "pick:admindel", page, "ASK_ADMIN_DEL", "Выберите администратора по номеру для снятия прав:");
            return;
        }

        // Быстрые даты (1..7 дней вперёд) — работают, когда уже выбран конкретный клиент
        if (data.startsWith("date:setcal:") || data.startsWith("date:setplan:") || data.startsWith("date:setnorm:")) {
            String sDay = data.substring(data.lastIndexOf(':')+1);
            int day;
            try { day = Integer.parseInt(sDay); } catch (Exception e) { day = 1; }
            day = Math.max(1, Math.min(7, day));
            LocalDate base = TimeUtil.today();
            LocalDate date = base.plusDays(day - 1);

            var stAdmin2 = StateRepo.get(tgId);
            if (stAdmin2 == null || !(stAdmin2.type().equals("ASK_SET_CAL") || stAdmin2.type().equals("ASK_SET_PLAN") || stAdmin2.type().equals("ASK_SET_NORM")) || stAdmin2.step()!=2) {
                return;
            }
            String uid = stAdmin2.payload();
            if (data.startsWith("date:setcal:")) {
                safeExecute(CaloriesWizard.start(tgId, chatId, uid, date));
            } else if (data.startsWith("date:setplan:")) {
                safeExecute(PlanWizard.start(tgId, chatId, uid, date));
            } else {
                safeExecute(NormWizard.start(tgId, chatId, uid, date));
            }
            return;
        }

        // Напомнить пользователю обновить параметры
        if (data.startsWith("params:remind:")) {
            String uid = data.substring("params:remind:".length());
            String owner = GroupRepo.adminOf(uid);
            if (owner == null || (!owner.equals(tgId) && !isSuper(tgId))) {
                safeExecute(new SendMessage(String.valueOf(chatId), "Нет доступа."));
                return;
            }
            SendMessage toUser = new SendMessage(uid,
                    "🔔 Привет! Внеси, пожалуйста, *сегодня* свои параметры в боте: вес, талию, грудь и бицепсы + фото. " +
                            "Это займёт 2–3 минуты и поможет отслеживать прогресс. 💪");
            toUser.setReplyMarkup(Keyboards.inlineGoParams());
            safeExecute(toUser);

            SendMessage back = new SendMessage(String.valueOf(chatId), "Напоминание отправлено пользователю " + uid + ".");
            back.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(back);
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

        // ==== НОВЫЕ кнопки действий по выбранному клиенту ====

        if (data.startsWith("client:cal:")) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            String uid = data.substring("client:cal:".length());
            // проверим доступ
            String owner = GroupRepo.adminOf(uid);
            if (owner == null || (!owner.equals(tgId) && !isSuper(tgId))) {
                safeExecute(new SendMessage(String.valueOf(chatId), "Нет доступа."));
                return;
            }
            // спросим дату (быстрые кнопки + ввод вручную)
            StateRepo.set(tgId, "ASK_SET_CAL", 2, uid);
            SendMessage q = md(chatId, "Укажите дату вручную `dd.MM.yyyy` или выберите дни ниже.");
            q.setReplyMarkup(Keyboards.dateQuickPick("date:setcal", TimeUtil.today()));
            safeExecute(q);
            return;
        }

        if (data.startsWith("client:plan:")) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            String uid = data.substring("client:plan:".length());
            String owner = GroupRepo.adminOf(uid);
            if (owner == null || (!owner.equals(tgId) && !isSuper(tgId))) {
                safeExecute(new SendMessage(String.valueOf(chatId), "Нет доступа."));
                return;
            }
            StateRepo.set(tgId, "ASK_SET_PLAN", 2, uid);
            SendMessage q = md(chatId, "Укажите дату вручную `dd.MM.yyyy` или выберите дни ниже.");
            q.setReplyMarkup(Keyboards.dateQuickPick("date:setplan", TimeUtil.today()));
            safeExecute(q);
            return;
        }

        if (data.startsWith("client:norm:")) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            String uid = data.substring("client:norm:".length());
            String owner = GroupRepo.adminOf(uid);
            if (owner == null || (!owner.equals(tgId) && !isSuper(tgId))) {
                safeExecute(new SendMessage(String.valueOf(chatId), "Нет доступа."));
                return;
            }
            StateRepo.set(tgId, "ASK_SET_NORM", 2, uid);
            SendMessage q = md(chatId, "Укажите дату вручную `dd.MM.yyyy` или выберите дни ниже.");
            q.setReplyMarkup(Keyboards.dateQuickPick("date:setnorm", TimeUtil.today()));
            safeExecute(q);
            return;
        }

        if (data.startsWith("client:reports:")) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            String uid = data.substring("client:reports:".length());
            String owner = GroupRepo.adminOf(uid);
            if (owner == null || (!owner.equals(tgId) && !isSuper(tgId))) {
                safeExecute(new SendMessage(String.valueOf(chatId), "Нет доступа."));
                return;
            }
            sendReportsPage(tgId, chatId, uid, 1, true);
            return;
        }

        if (data.startsWith("client:params:")) {
            if (!isAdmin(tgId)) { safeExecute(new SendMessage(String.valueOf(chatId), "Только для админов.")); return; }
            String uid = data.substring("client:params:".length());
            showUserParamsForAdmin(tgId, chatId, uid);
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

    /* ==================== списки/пикеры ==================== */

    private String formatRow(UserRepo.UserRow r) {
        String name = (r.firstName != null && !r.firstName.isBlank()) ? r.firstName : "—";
        String tag  = (r.username  != null && !r.username.isBlank())  ? "@"+r.username : "—";
        return name + " | " + tag + " | " + r.id;
    }

    // Переиспользуемый рендер списка клиентов (без Markdown!)
    private void renderGroupPicker(long chatId, String adminId, String base, int page, String armStateType, String prompt, boolean withChooseButtons) throws Exception {
        int size = 10;
        int total = countGroupUsers(adminId);
        if (total <= 0) {
            SendMessage empty = new SendMessage(String.valueOf(chatId), "В ваших клиентах пока никого нет.");
            empty.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(empty);
            return;
        }
        int pages = Math.max(1, (int)Math.ceil(total / (double) size));
        page = Math.min(Math.max(1, page), pages);
        int offset = (page - 1) * size;

        var rows = fetchGroupUsersDetailed(adminId, size, offset);
        StringBuilder sb = new StringBuilder("Мои клиенты (стр. "+page+"/"+pages+"):\n");
        StringBuilder payload = new StringBuilder();
        int i=1;
        for (UserRepo.UserRow r : rows) {
            if (payload.length() > 0) payload.append(",");
            payload.append(r.id);
            sb.append(i++).append(". ").append(formatRow(r)).append("\n");
        }
        StateRepo.set(adminId, armStateType, 1, payload.toString());

        SendMessage msg = new SendMessage(String.valueOf(chatId), sb.toString() + "\n" + prompt + "\n\nВведите порядковый номер пользователя:");
        // ВАЖНО: здесь НЕТ setParseMode -> не будет падать из-за Markdown
        msg.setReplyMarkup(Keyboards.pager(base, page, pages));
        safeExecute(msg);
    }

    private void renderAdminsPicker(String adminId, long chatId, String base, int page, String armStateType, String prompt) throws Exception {
        int size = 10;
        int total = UserRepo.countAdmins();
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
        StringBuilder payload = new StringBuilder();
        StringBuilder sb = new StringBuilder("Действующие админы (стр. "+page+"/"+pages+"):\n");
        int i=1;
        for (UserRepo.UserRow r : rows) {
            if (payload.length() > 0) payload.append(",");
            payload.append(r.id);
            sb.append(i++).append(". ").append(formatRow(r)).append("\n");
        }
        StateRepo.set(adminId, armStateType, 1, payload.toString());

        SendMessage msg = new SendMessage(String.valueOf(chatId), sb.toString() + "\n" + prompt);
        // Без parseMode
        msg.setReplyMarkup(Keyboards.pager(base, page, pages));
        safeExecute(msg);
    }

    private void renderAllUsersPicker(long chatId, String adminId, String base, int page, String armStateType, String prompt) throws Exception {
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
        StringBuilder payload = new StringBuilder();
        StringBuilder sb = new StringBuilder("Активные пользователи (стр. "+page+"/"+pages+"):\n");
        int i = 1;
        for (UserRepo.UserRow r : rows) {
            if (payload.length() > 0) payload.append(",");
            payload.append(r.id);
            sb.append(i++).append(". ").append(formatRow(r)).append("\n");
        }

        StateRepo.set(adminId, armStateType, 1, payload.toString());
        SendMessage msg = new SendMessage(String.valueOf(chatId), sb.toString() + "\n" + prompt);
        // Без parseMode
        msg.setReplyMarkup(Keyboards.pager(base, page, pages));
        safeExecute(msg);
    }

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

    private void sendReportsPage(String adminId, long chatId, String userId, int page, boolean desc) throws Exception {
        String owner = GroupRepo.adminOf(userId);
        if (owner == null || (!owner.equals(adminId) && UserRepo.role(adminId) != Roles.SUPERADMIN)) {
            SendMessage sm = new SendMessage(String.valueOf(chatId), "Нет доступа.");
            sm.setReplyMarkup(Keyboards.backToMenu());
            safeExecute(sm);
            return;
        }
        int size = 1;
        int total = ReportRepo.countByUser(userId);
        int pages = Math.max(1, (int)Math.ceil(total/(double)size));
        page = Math.min(Math.max(1,page), pages);
        var rows = ReportRepo.listByUser(userId, page, size, desc);

        StringBuilder sb = new StringBuilder();
        sb.append("Отчёты клиента (tg_id: ").append(userId).append(")")
                .append(" — стр. ").append(page).append("/").append(pages).append("\n\n");

        if (!rows.isEmpty()) {
            String r = rows.get(0);

            String firstLine;
            int nl = r.indexOf('\n');
            if (nl >= 0) firstLine = r.substring(0, nl);
            else firstLine = r;

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\*(\\d{2}\\.\\d{2}\\.\\d{4})\\*")
                    .matcher(firstLine);
            java.time.LocalDate date = null;
            if (m.find()) {
                try { date = java.time.LocalDate.parse(m.group(1), TimeUtil.DATE_FMT); } catch (Exception ignore) {}
            }

            String clientBody = (nl >= 0 && nl + 1 < r.length()) ? r.substring(nl + 1) : "";

            String food = (date != null) ? PlanRepo.getNutritionText(userId, date) : "—";
            String wkt  = (date != null) ? PlanRepo.getWorkoutText(userId, date)   : "—";
            String norm = (date != null) ? NormRepo.getNormsText(userId, date)     : "—";

            sb.append("Дата: ").append((date != null) ? TimeUtil.DATE_FMT.format(date) : "—").append("\n\n");
            sb.append("Заданные тренером данные:\n");
            sb.append("🍽 План питания:\n").append(food).append("\n\n");
            sb.append("🏋️ Тренировка:\n").append(wkt).append("\n\n");
            sb.append("📊 Нормы активности:\n").append(norm).append("\n\n");

            sb.append("Отчёт клиента:\n").append(clientBody.trim());
        } else {
            sb.append("Нет отчётов.");
        }

        SendMessage sm = new SendMessage(String.valueOf(chatId), sb.toString());
        sm.setReplyMarkup(Keyboards.pager("reports:"+userId+":"+(desc?"desc":"asc"), page, pages));
        safeExecute(sm);
    }

    private void showUserParamsForAdmin(String adminId, long chatId, String userId) throws Exception {
        String owner = GroupRepo.adminOf(userId);
        if (owner == null || (!owner.equals(adminId) && UserRepo.role(adminId) != Roles.SUPERADMIN)) {
            SendMessage sm = new SendMessage(String.valueOf(chatId), "Нет доступа.");
            sm.setReplyMarkup(Keyboards.backToAdmin());
            safeExecute(sm);
            return;
        }
        String txt = com.example.coachbot.repo.ParamsRepo.getParamsText(userId);
        if (txt == null || txt.isBlank()) txt = "Параметры пользователя ещё не заполнены.";

        String photoId = com.example.coachbot.repo.ParamsRepo.getPhotoId(userId);
        if (photoId != null && !photoId.isBlank()) {
            SendPhoto sp = new SendPhoto();
            sp.setChatId(String.valueOf(chatId));
            sp.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(photoId));
            sp.setCaption("Фото пользователя tg_id: " + userId);
            safeExecute(sp);
        }

        SendMessage sm = new SendMessage(String.valueOf(chatId), "Параметры пользователя tg_id: " + userId + "\n\n" + txt);
        sm.setReplyMarkup(Keyboards.remindParamsAndBack(userId));
        safeExecute(sm);
    }
}