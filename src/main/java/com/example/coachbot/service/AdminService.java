package com.example.coachbot.service;

import com.example.coachbot.Roles;
import com.example.coachbot.repo.UserRepo;
import com.example.coachbot.repo.GroupRepo;

public class AdminService {

    public static boolean isAdmin(String id) throws Exception {
        Roles r = UserRepo.role(id);
        return r==Roles.ADMIN || r==Roles.SUPERADMIN;
    }
    public static boolean isSuper(String id) throws Exception {
        return UserRepo.role(id)==Roles.SUPERADMIN;
    }

    public static String groupList(String adminId, int page, int size) throws Exception {
        int total = GroupRepo.countUsersOfAdmin(adminId);
        int pages = Math.max(1, (int)Math.ceil(total/(double)size));
        page = Math.min(Math.max(1,page), pages);
        var ids = GroupRepo.usersOfAdmin(adminId, size, (page-1)*size);
        StringBuilder sb = new StringBuilder("Моя группа:\n");
        int i=1+(page-1)*size;
        for (String uid : ids) {
            sb.append(i++).append(". {")
                    .append("username?").append("} ")
                    .append("tg_id: ").append(uid).append("\n");
        }
        if (ids.isEmpty()) sb.append("Пока пусто.");
        return sb.toString();
    }

    public static boolean addAdmin(String superId, String targetId) throws Exception {
        if (!isSuper(superId)) return false;
        UserRepo.ensureAdmin(targetId);
        return true;
    }
}