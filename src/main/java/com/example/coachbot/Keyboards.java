package com.example.coachbot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    /* ========================= Общие утилиты ========================= */

    private static InlineKeyboardButton btn(String text, String cb) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(cb);
        return b;
    }

    private static InlineKeyboardButton urlBtn(String text, String url) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setUrl(url);
        return b;
    }

    /* ========================= Главное меню ========================= */

    public static InlineKeyboardMarkup inlineMainMenu(boolean isAdmin, boolean isSuper) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🍽 План питания", "menu:food")));
        rows.add(List.of(btn("🏋️‍♀️ Тренировка", "menu:workout")));
        rows.add(List.of(btn("📊 Нормы активности", "menu:norms")));
        rows.add(List.of(btn("📝 Отчёт", "menu:report")));
        rows.add(List.of(btn("📏 Мои параметры", "menu:params")));
        rows.add(List.of(btn("📞 Контакты", "menu:contact")));
        if (isAdmin) rows.add(List.of(btn("🔧 Админ-панель", "menu:admin")));
        if (isSuper) rows.add(List.of(btn("🛡 Супер-админ", "menu:super")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup backToMenu() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("🔙 Вернуться в меню", "menu:main"))));
        return m;
    }

    public static InlineKeyboardMarkup backToAdmin() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin"))));
        return m;
    }

    /* ========================= Панели админов ========================= */

    public static InlineKeyboardMarkup adminPanel() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("👥 Мои клиенты", "admin:my")));
        rows.add(List.of(btn("➕ Добавить клиента", "admin:groupadd")));
        rows.add(List.of(btn("➖ Удалить клиента", "admin:groupdel")));
        rows.add(List.of(btn("📞 Мои контакты", "admin:contact")));
        rows.add(List.of(btn("⏰ Время рассылки", "admin:settime")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup superAdminPanel() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("➕ Добавить админа", "super:add")));
        rows.add(List.of(btn("➖ Удалить админа", "super:del")));
        rows.add(List.of(btn("🔙 В админ-панель", "menu:admin")));
        rows.add(List.of(btn("🔙 В главное меню", "menu:main")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup superAdminBack() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("🔙 Супер-админ панель", "menu:super")),
                List.of(btn("🔙 Админ-панель", "menu:admin"))
        ));
        return m;
    }

    /* ========================= Кнопки отмен/спец ========================= */

    public static InlineKeyboardMarkup contactCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить ввод", "contact:cancel"))));
        return m;
    }

    public static InlineKeyboardMarkup reportCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить заполнение", "report:cancel"))));
        return m;
    }

    public static InlineKeyboardMarkup reportSkipOrCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("⏭ Пропустить", "report:skip")));
        rows.add(List.of(btn("✖️ Отменить заполнение", "report:cancel")));
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup paramsCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить ввод", "params:cancel"))));
        return m;
    }

    public static InlineKeyboardMarkup paramsSkipOrCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("⏭ Пропустить замер", "params:skip")),
                List.of(btn("✖️ Отменить ввод", "params:cancel"))
        ));
        return m;
    }

    public static InlineKeyboardMarkup inlineGoParams() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("📏 Заполнить параметры", "menu:params")),
                List.of(btn("🔙 В меню", "menu:main"))
        ));
        return m;
    }

    public static InlineKeyboardMarkup remindParamsAndBack(String userId) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("🔔 Напомнить о параметрах", "params:remind:" + userId)),
                List.of(btn("🔙 Вернуться в админ-панель", "menu:admin"))
        ));
        return m;
    }

    /* ========================= План тренировок: завершение ========================= */

    /** Старая кнопка — всё ещё используется в обычном PlanWizard */
    public static InlineKeyboardMarkup planFinalizeButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✅ Установить план", "plan:finish")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    /** Новая кнопка — завершение шага плана внутри единого визарда */
    public static InlineKeyboardMarkup allPlanFinalizeButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✅ Завершить план", "all:plan_finish")),
                List.of(btn("🔙 Вернуться в админ-панель", "menu:admin"))
        ));
        return m;
    }

    /* ========================= Пейджер и быстрые даты ========================= */

    public static InlineKeyboardMarkup pager(String base, int page, int pages) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> nav = new ArrayList<>();
        nav.add(btn("⬅️", base + ":" + Math.max(1, page - 1)));
        nav.add(btn("📄 " + page + "/" + pages, "noop"));
        nav.add(btn("➡️", base + ":" + Math.min(pages, page + 1)));
        rows.add(nav);
        rows.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup dateQuickPick(String base, LocalDate today) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> r1 = new ArrayList<>();
        List<InlineKeyboardButton> r2 = new ArrayList<>();
        List<InlineKeyboardButton> r3 = new ArrayList<>();

        r1.add(btn("1 день", base + ":1"));
        r1.add(btn("2 день", base + ":2"));
        r1.add(btn("3 день", base + ":3"));

        r2.add(btn("4 день", base + ":4"));
        r2.add(btn("5 день", base + ":5"));
        r2.add(btn("6 день", base + ":6"));

        r3.add(btn("7 день", base + ":7"));

        rows.add(r1);
        rows.add(r2);
        rows.add(r3);
        rows.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));

        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    /* ========================= Вечерняя рассылка (кнопка отчёта) ========================= */

    public static InlineKeyboardMarkup reportButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("📝 Заполнить отчёт", "report:start")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    public static InlineKeyboardMarkup paramsPhotoStep() {
        return paramsSkipOrCancel();
    }

    /* ========================= Меню действий по выбранному клиенту ========================= */

    public static InlineKeyboardMarkup adminClientActions(String userId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        // ⬇⬇⬇ БЫЛО 3 кнопки -> теперь одна
        rows.add(List.of(btn("🧩 Установить параметры", "client:set:" + userId)));
        rows.add(List.of(btn("📝 Отчёты клиента", "client:reports:" + userId)));
        rows.add(List.of(btn("📏 Параметры клиента", "client:params:" + userId)));
        rows.add(List.of(btn("🔙 В админ-панель", "menu:admin")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }
}