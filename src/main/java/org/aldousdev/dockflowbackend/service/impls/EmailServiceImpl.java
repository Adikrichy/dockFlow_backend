package org.aldousdev.dockflowbackend.service.impls;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl {

    private final JavaMailSender mailSender;


    public void sendVerificationEmail(String to, String code){
         SimpleMailMessage message = new SimpleMailMessage();
         message.setTo(to);
         message.setSubject("Verification Email");
         message.setText(code);
         mailSender.send(message);
    }
}
