package org.example.aiwear.util;


import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

//邮件服务类
@Slf4j
@Service

public class EmailService {

    //邮件发送器 spring 提供的邮件发送工具。
    @Autowired
    private JavaMailSender javaMailSender;

    //发件人邮箱
    @Value("${spring.mail.username}")
    private String fromEmail;

    //发送验证码邮件
    //toEmail收件人邮箱。
    public boolean sendVerificationCodeEmail(String toEmail, String verificationCode) {
       try{
           //邮件内容
           String content = "尊敬的用户：\n\n您正在使用衣览无余进行邮箱验证，本次验证码为："
                   + verificationCode + "\n\n验证码有效期为5分钟，请勿将验证码泄露给他人。如非本人操作，请忽略此邮件。";
           //创建邮件
           SimpleMailMessage message = new SimpleMailMessage();
           message.setFrom(fromEmail);
           message.setTo(toEmail);
           message.setSubject("[衣览无余]邮箱验证码");
           message.setText(content);
           //发送邮件
           javaMailSender.send(message);
           return true;
       }catch (Exception e){
           log.error("发送邮件失败：收件人是:{}", toEmail);
           return false;
       }
    }
}
