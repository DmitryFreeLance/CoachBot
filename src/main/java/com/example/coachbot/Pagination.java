package com.example.coachbot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import java.util.*;

public class Pagination {
    public static InlineKeyboardMarkup pages(String base, int page, int pages) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> r = new ArrayList<>();
        InlineKeyboardButton prev = new InlineKeyboardButton();
        prev.setText("⬅️");
        prev.setCallbackData(base + ":" + Math.max(1, page-1));
        InlineKeyboardButton cur = new InlineKeyboardButton();
        cur.setText("Стр. " + page + "/" + pages);
        cur.setCallbackData("noop");
        InlineKeyboardButton next = new InlineKeyboardButton();
        next.setText("➡️");
        next.setCallbackData(base + ":" + Math.min(pages, page+1));
        r.add(prev); r.add(cur); r.add(next);
        rows.add(r);
        InlineKeyboardMarkup m = new InlineKeyboardMarkup();
        m.setKeyboard(rows);
        return m;
    }
}