package com.vtr.service;

public interface VerificationCodeSender {

    void sendSms(String phone, String code);

    void sendEmail(String email, String code);
}
