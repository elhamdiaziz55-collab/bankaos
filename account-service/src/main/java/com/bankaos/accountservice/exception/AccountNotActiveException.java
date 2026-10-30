package com.bankaos.accountservice.exception;

public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException(String s) {
        super(s);
    }
}
