package com.example.coachbot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    // Главное меню
    public static InlineKeyboardMarkup inlineMainMenu(boolean isAdmin, boolean isSuper) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🍽 План питания", "menu:food")));
        rows.add(List.of(btn("🏋️‍♀️ Тренировка", "menu:workout")));
        rows.add(List.of(btn("📊 Нормы активности", "menu:norms")));
        rows.add(List.of(btn("📝 Отчёт", "menu:report")));
        rows.add(List.of(btn("📞 Контакты", "menu:contact")));
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

    // Контакты: только отмена
    public static InlineKeyboardMarkup contactCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить ввод", "contact:cancel"))));
        return m;
    }

    // Отчёт: только «Отменить заполнение»
    public static InlineKeyboardMarkup reportCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить заполнение", "report:cancel"))));
        return m;
    }

    // Завершить план + назад в меню
    public static InlineKeyboardMarkup planFinalizeButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✅ Установить план", "plan:finish")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    // Админ-панель
    public static InlineKeyboardMarkup adminPanel(boolean isSuper) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("👥 Моя группа", "admin:my")));
        rows.add(List.of(btn("👤 Все пользователи", "admin:all")));
        rows.add(List.of(btn("➕ Добавить в группу", "admin:groupadd")));
        rows.add(List.of(btn("➖ Удалить из группы", "admin:groupdel")));
        rows.add(List.of(btn("🍽 Установить КБЖУ", "admin:setcal")));
        rows.add(List.of(btn("🏋️ Установить план", "admin:setplan")));
        rows.add(List.of(btn("📊 Установить нормы", "admin:setnorma")));
        rows.add(List.of(btn("📞 Контакты тренера", "admin:contact")));
        if (isSuper) {
            rows.add(List.of(btn("⏰ Время рассылки", "admin:settime")));
            rows.add(List.of(btn("➕ Добавить админа", "admin:add")));
            rows.add(List.of(btn("➖ Удалить админа", "admin:del")));
        }
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    public static InlineKeyboardMarkup reportButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("📝 Заполнить отчёт", "report:start")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    /**
     * Универсальная пагинация — ВСЕГДА 3 кнопки:
     * ⬅️ (или noop), 🔢 page/pages, ➡️ (или noop)
     */
    public static InlineKeyboardMarkup pager(String base, int page, int pages) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int prev = Math.max(1, page - 1);
        int next = Math.min(pages, page + 1);

        List<InlineKeyboardButton> nav = new ArrayList<>();
        nav.add(btn("⬅️", (page > 1) ? (base + ":" + prev) : "noop"));
        nav.add(btn("🔢 " + page + "/" + pages, "noop"));
        nav.add(btn("➡️", (page < pages) ? (base + ":" + next) : "noop"));
        rows.add(nav);

        rows.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));

        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    private static InlineKeyboardButton btn(String text, String cb) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(cb);
        return b;
    }
}