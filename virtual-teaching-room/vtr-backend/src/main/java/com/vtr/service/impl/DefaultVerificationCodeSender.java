package com.vtr.service.impl;

import com.vtr.common.exception.BusinessException;
import com.vtr.service.VerificationCodeSender;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultVerificationCodeSender implements VerificationCodeSender {

    private final JavaMailSender mailSender;

    @Value("${verification.provider:console}")
    private String provider;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:465}")
    private int mailPort;

    @PostConstruct
    void logConfiguration() {
        if ("smtp".equalsIgnoreCase(provider)) {
            log.info("邮箱验证码 SMTP 模式已启用，host={}, port={}, from={}",
                    mailHost, mailPort, mask(mailFrom));
        } else {
            log.info("邮箱验证码当前使用 {} 模式", provider);
        }
    }

    @Override
    public void sendSms(String phone, String code) {
        if ("console".equalsIgnoreCase(provider)) {
            log.info("[开发环境] 手机验证码 phone={}, code={}", mask(phone), code);
            return;
        }
        throw new BusinessException("短信服务尚未配置，请先配置短信服务商");
    }

    @Override
    public void sendEmail(String email, String code) {
        if ("console".equalsIgnoreCase(provider)) {
            log.info("[开发环境] 邮箱验证码 email={}, code={}", mask(email), code);
            return;
        }
        if (!"smtp".equalsIgnoreCase(provider)) {
            throw new BusinessException("邮箱验证码未启用，请将 VERIFICATION_PROVIDER 设置为 smtp");
        }
        if (!StringUtils.hasText(mailHost)) {
            throw new BusinessException("邮件服务尚未配置 SMTP 服务器地址");
        }
        if (!StringUtils.hasText(mailFrom)) {
            throw new BusinessException("邮件服务尚未配置发件邮箱");
        }
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("收件邮箱不能为空");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        // mailFrom 是系统固定发件邮箱，email 是用户输入的收件邮箱，二者可以不同。
        message.setTo(email);
        message.setSubject("虚拟教研室身份验证码");
        message.setText("您的验证码是：" + code + "，5分钟内有效。如非本人操作，请忽略此邮件。");
        try {
            log.info("开始发送验证码邮件，host={}, port={}, from={}, recipient={}",
                    mailHost, mailPort, mask(mailFrom), mask(email));
            mailSender.send(message);
            log.info("验证码邮件发送成功，from={}, recipient={}", mask(mailFrom), mask(email));
        } catch (MailException e) {
            log.error("验证码邮件发送失败，recipient={}", mask(email), e);
            throw new BusinessException("验证码邮件发送失败，请检查邮箱配置或稍后重试", e);
        }
    }

    private String mask(String value) {
        if (value == null || value.length() < 5) return "***";
        int visible = Math.min(3, value.length() / 3);
        return value.substring(0, visible) + "***" + value.substring(value.length() - visible);
    }
}
