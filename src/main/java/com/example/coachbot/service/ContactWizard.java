package com.example.coachbot.service;

import com.example.coachbot.repo.ContactRepo;
import com.example.coachbot.repo.StateRepo;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class ContactWizard {
    public static SendMessage start(String adminId, long chatId) throws Exception {
        StateRepo.set(adminId, "CONTACT", 1, "");
        return new SendMessage(String.valueOf(chatId),
                "Отправьте ваши контактные данные одним сообщением (телефон, @тег и т.п.).");
    }

    public static SendMessage onMessage(String adminId, long chatId, String text) throws Exception {
        var st = StateRepo.get(adminId);
        if (st==null || !"CONTACT".equals(st.type())) return null;
        ContactRepo.set(adminId, text.trim());
        StateRepo.clear(adminId);
        return new SendMessage(String.valueOf(chatId), "Контакты сохранены.");
    }
}