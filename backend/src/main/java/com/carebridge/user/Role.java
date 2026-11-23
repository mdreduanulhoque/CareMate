package com.carebridge.user;

public enum Role {
    CUSTOMER,
    CARETAKER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
