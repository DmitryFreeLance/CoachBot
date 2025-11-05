package com.example.coachbot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
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
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));
        m.setKeyboard(rows);
        return m;
    }

    // Кнопка назад в /superadmin
    public static InlineKeyboardMarkup superAdminBack() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("🔙 В супер-панель", "menu:super"))));
        return m;
    }

    // Для ввода контактов: только отмена
    public static InlineKeyboardMarkup contactCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить ввод", "contact:cancel"))));
        return m;
    }

    // Отчёт: только отмена
    public static InlineKeyboardMarkup reportCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить заполнение", "report:cancel"))));
        return m;
    }

    // План: завершить + назад в меню — в каждом сообщении визарда
    public static InlineKeyboardMarkup planFinalizeButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✅ Установить план", "plan:finish")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    // Админ-панель (без супер-кнопок и без «Все пользователи»)
    public static InlineKeyboardMarkup adminPanel() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("👥 Моя группа", "admin:my")));
        rows.add(List.of(btn("➕ Добавить в группу", "admin:groupadd")));
        rows.add(List.of(btn("➖ Удалить из группы", "admin:groupdel")));
        rows.add(List.of(btn("🍽 Установить КБЖУ", "admin:setcal")));
        rows.add(List.of(btn("🏋️ Установить план", "admin:setplan")));
        rows.add(List.of(btn("📊 Установить нормы", "admin:setnorma")));
        rows.add(List.of(btn("📞 Контакты тренера", "admin:contact")));
        rows.add(List.of(btn("⏰ Время рассылки (моя группа)", "admin:settime")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    // Супер-админ панель (/superadmin)
    public static InlineKeyboardMarkup superAdminPanel() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("➕ Добавить админа", "super:add")));
        rows.add(List.of(btn("➖ Удалить админа", "super:del")));
        rows.add(List.of(btn("🔙 В админ-панель", "menu:admin")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    // Универсальный пейджер: ⬅️ 📄 ➡️ + "Назад"
    public static InlineKeyboardMarkup pager(String base, int page, int pages) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> nav = new ArrayList<>();
        nav.add(btn("⬅️", base + ":" + Math.max(1, page - 1)));
        nav.add(btn("📄 " + page + "/" + pages, "noop"));
        nav.add(btn("➡️", base + ":" + Math.min(pages, page + 1)));
        rows.add(nav);
        // Назад куда релевантно: если это супер-операции — пусть возвращают в super, иначе — в admin
        String backTarget = base.startsWith("pick:admin") || base.startsWith("pick:admindel") ? "menu:super" : "menu:admin";
        rows.add(List.of(btn("🔙 Назад", backTarget)));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    // Быстрый выбор даты для админ-визардов
    // base = "date:setcal" | "date:setplan" | "date:setnorm"
    // Рендерим 7 кнопок: 1 день..7 день (1 = сегодня)
    public static InlineKeyboardMarkup dateQuickPick(String base, LocalDate today) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> r1 = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            r1.add(btn(i + " день", base + ":" + i));
        }
        List<InlineKeyboardButton> r2 = new ArrayList<>();
        for (int i = 5; i <= 7; i++) {
            r2.add(btn(i + " день", base + ":" + i));
        }

        rows.add(r1);
        rows.add(r2);
        rows.add(List.of(btn("🔙 Вернуться в админ-панель", "menu:admin")));

        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    // Вечерняя рассылка
    public static InlineKeyboardMarkup reportButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("📝 Заполнить отчёт", "report:start")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
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