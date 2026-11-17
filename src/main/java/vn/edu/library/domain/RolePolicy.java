package vn.edu.library.domain;

import java.util.EnumSet;
import java.util.Set;

/** Central, explicit role matrix for the staff application. */
public final class RolePolicy {
    private static final Set<StaffPermission> ADMIN = EnumSet.of(
            StaffPermission.DASHBOARD,
            StaffPermission.MANAGE_STAFF,
            StaffPermission.MANAGE_ACCOUNTS,
            StaffPermission.MANAGE_ASSIGNMENTS
    );

    private static final Set<StaffPermission> LIBRARIAN = EnumSet.of(
            StaffPermission.DASHBOARD,
            StaffPermission.VIEW_BOOKS,
            StaffPermission.MANAGE_BOOKS,
            StaffPermission.MANAGE_CATALOG_LOOKUPS,
            StaffPermission.MANAGE_SCHOOL_MEMBERS,
            StaffPermission.MANAGE_LIBRARY_CARDS,
            StaffPermission.MANAGE_RESERVATIONS,
            StaffPermission.MANAGE_LOANS,
            StaffPermission.MANAGE_VIOLATIONS,
            StaffPermission.MANAGE_DOCUMENTS,
            StaffPermission.VIEW_REPORTS
    );

    private static final Set<StaffPermission> WAREHOUSE = EnumSet.of(
            StaffPermission.DASHBOARD,
            StaffPermission.VIEW_BOOKS,
            StaffPermission.MANAGE_STOCK
    );

    private RolePolicy() { }

    public static boolean isStaff(UserSession session) {
        return session != null && (session.hasRole("ADMIN") || session.hasRole("LIBRARIAN") || session.hasRole("WAREHOUSE"));
    }

    public static boolean allows(UserSession session, StaffPermission permission) {
        if (session == null || permission == null) return false;
        if (session.hasRole("ADMIN") && ADMIN.contains(permission)) return true;
        if (session.hasRole("LIBRARIAN") && LIBRARIAN.contains(permission)) return true;
        return session.hasRole("WAREHOUSE") && WAREHOUSE.contains(permission);
    }
}
