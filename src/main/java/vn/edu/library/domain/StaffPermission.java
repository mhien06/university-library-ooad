package vn.edu.library.domain;

/**
 * Các thao tác dành cho ứng dụng Swing nội bộ, phân tách theo vai trò
 * ADMIN (quản trị tài khoản, nhân sự, phân công), LIBRARIAN (nghiệp vụ thư viện)
 * và WAREHOUSE (quản lý kho sách).
 */
public enum StaffPermission {
    DASHBOARD,
    VIEW_BOOKS,
    MANAGE_BOOKS,
    MANAGE_CATALOG_LOOKUPS,
    MANAGE_SCHOOL_MEMBERS,
    MANAGE_LIBRARY_CARDS,
    MANAGE_RESERVATIONS,
    MANAGE_LOANS,
    MANAGE_VIOLATIONS,
    MANAGE_DOCUMENTS,
    VIEW_REPORTS,
    MANAGE_STOCK,
    MANAGE_STAFF,
    MANAGE_ACCOUNTS,
    MANAGE_ASSIGNMENTS
}
