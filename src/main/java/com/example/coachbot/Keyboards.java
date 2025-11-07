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

    // Главное меню: добавлена кнопка «Мои параметры». Кнопки админки отсутствуют здесь.
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

    // Обычная админ-панель (без супер-админских пунктов)
    public static InlineKeyboardMarkup adminPanel() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("👥 Моя группа", "admin:my")));
        rows.add(List.of(btn("➕ Добавить в группу", "admin:groupadd")));
        rows.add(List.of(btn("➖ Удалить из группы", "admin:groupdel")));
        rows.add(List.of(btn("🍽 Установить КБЖУ", "admin:setcal")));
        rows.add(List.of(btn("🏋️ Установить план", "admin:setplan")));
        rows.add(List.of(btn("📊 Установить нормы", "admin:setnorma")));
        rows.add(List.of(btn("📞 Контакты тренера", "admin:contact")));
        rows.add(List.of(btn("📝 Отчёты клиента", "admin:reports")));
        rows.add(List.of(btn("📏 Параметры группы", "admin:params")));
        rows.add(List.of(btn("⏰ Время рассылки", "admin:settime")));
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }

    // Панель супер-админа (вынесенные пункты)
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

    /* ========================= Кнопки отмены/спец в визардах ========================= */

    // Для ввода контактов: только отмена
    public static InlineKeyboardMarkup contactCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить ввод", "contact:cancel"))));
        return m;
    }

    // Отчёт: только отмена (по требованию)
    public static InlineKeyboardMarkup reportCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить заполнение", "report:cancel"))));
        return m;
    }

    // Мои параметры: только отмена (для промежуточных шагов)
    public static InlineKeyboardMarkup paramsCancelOnly() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(List.of(btn("✖️ Отменить ввод", "params:cancel"))));
        return m;
    }

    // Мои параметры: последний шаг — предложить «Пропустить фото» или «Отменить»
    public static InlineKeyboardMarkup paramsSkipOrCancel() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("⏭ Пропустить фото", "params:skip")),
                List.of(btn("✖️ Отменить ввод", "params:cancel"))
        ));
        return m;
    }

    // Кнопка, которую отправляем пользователю в напоминании про параметры
    public static InlineKeyboardMarkup inlineGoParams() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("📏 Заполнить параметры", "menu:params")),
                List.of(btn("🔙 В меню", "menu:main"))
        ));
        return m;
    }

    // Клавиатура для карточки «Параметры пользователя» у админа
    public static InlineKeyboardMarkup remindParamsAndBack(String userId) {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("🔔 Напомнить о параметрах", "params:remind:" + userId)),
                List.of(btn("🔙 Вернуться в админ-панель", "menu:admin"))
        ));
        return m;
    }

    /* ========================= План тренировок: завершение ========================= */

    // План: завершить + назад в меню — должны быть в каждом сообщении визарда
    public static InlineKeyboardMarkup planFinalizeButton() {
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(List.of(
                List.of(btn("✅ Установить план", "plan:finish")),
                List.of(btn("🔙 Вернуться в меню", "menu:main"))
        ));
        return m;
    }

    /* ========================= Пейджер и быстрые даты ========================= */

    // Универсальный пейджер: ⬅️ 📄 ➡️ + "Назад в админ-панель"
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

    // Быстрый выбор даты для админ-визардов: 7 кнопок "1 день".."7 день"
    // base = "date:setcal" | "date:setplan" | "date:setnorm"
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
}