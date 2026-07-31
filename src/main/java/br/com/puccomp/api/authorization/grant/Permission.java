package br.com.puccomp.api.authorization.grant;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Permission {

    MEMBERS_READ("members:read"),
    MEMBERS_WRITE("members:write"),
    MEMBERS_INVITE("members:invite"),

    ROLES_READ("roles:read"),
    ROLES_WRITE("roles:write"),

    DEPARTMENTS_READ("departments:read"),
    DEPARTMENTS_WRITE("departments:write"),

    COURSES_WRITE("courses:write"),

    FINANCIAL_READ("financial:read"),
    FINANCIAL_WRITE("financial:write"),

    PERMISSIONS_MANAGE("permissions:manage");

    private static final Map<String, Permission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::code, Function.identity()));

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Permission> fromCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
