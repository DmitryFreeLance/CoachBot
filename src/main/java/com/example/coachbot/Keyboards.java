package com.example.coachbot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    // Главное меню: каждая кнопка в ОТДЕЛЬНОМ ряду; без "Админ-панель"
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

    // Назад в главное меню
    public static InlineKeyboardMarkup backToMenu() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    // Назад в админ-панель (одна кнопка, один ряд)
    public static InlineKeyboardMarkup backToAdmin() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("🔙 Вернуться в админ-панель", "menu:admin"))
        ));
        return m;
    }

    // Для ввода контактов: только отмена (жёсткий режим)
    public static InlineKeyboardMarkup contactCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✖️ Отменить ввод", "contact:cancel"))
        ));
        return m;
    }

    // Отчёт: отменить + назад в меню (каждая кнопка в своём ряду)
    public static InlineKeyboardMarkup reportCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✖️ Отменить заполнение", "report:cancel")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    // Завершить план + назад в меню (каждая кнопка в своём ряду)
    public static InlineKeyboardMarkup planFinalizeButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✅ Установить план", "plan:finish")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    // Админ-панель: каждая кнопка в ОТДЕЛЬНОМ ряду (без "Вернуться в меню")
    public static InlineKeyboardMarkup adminPanel(boolean isSuper) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("👥 Моя группа", "admin:my")));
        rows.add(List.of(btn("👤 Все пользователи", "admin:all")));
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

    // Вечерняя рассылка: начать отчёт + назад в меню (каждая кнопка в своём ряду)
    public static InlineKeyboardMarkup reportButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("📝 Заполнить отчёт", "report:start")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    // Пагинация для "Все пользователи" + "Назад в админ-панель"
    public static InlineKeyboardMarkup allUsersPager(int page, int pages) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 1) {
            nav.add(btn("⬅️ Назад", "allusers:" + (page - 1)));
        }
        nav.add(btn("📄 " + page + "/" + pages, "noop"));
        if (page < pages) {
            nav.add(btn("Вперёд ➡️", "allusers:" + (page + 1)));
        }
        rows.add(nav);
        rows.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));

        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    // утилита
    private static InlineKeyboardButton btn(String text, String cb) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(cb);
        return b;
    }
}