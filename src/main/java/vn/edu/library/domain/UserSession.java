package vn.edu.library.domain;

import java.util.Set;

public record UserSession(long accountId, String username, String displayName, Set<String> roles) {
    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean canManage() { return hasRole("ADMIN") || hasRole("LIBRARIAN") || hasRole("WAREHOUSE"); }
}
