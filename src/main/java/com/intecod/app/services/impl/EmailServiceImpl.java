package com.intecod.app.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.intecod.app.dto.EmailRequest;
import com.intecod.app.services.EmailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Override
    public void sendEmail(EmailRequest request) {
        try {

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.getTo());
            message.setSubject(request.getSubject());
            message.setText(request.getText());

            // Actualizado con el identificador de Brevo
            message.setFrom("aescritura95@gmail.com");

            // Agregar CC si no es nulo o vacío
            if (request.getCc() != null && !request.getCc().isEmpty()) {
                message.setCc(request.getCc());
            }

            // Agregar BCC si no es nulo o vacío
            if (request.getBcc() != null && !request.getBcc().isEmpty()) {
                message.setBcc(request.getBcc());
            }

            // Enviar el correo
            javaMailSender.send(message);
            logger.info("Correo enviado a: {}", request.getTo());

        } catch (Exception e) {
            logger.error("Error al enviar el correo: ", e);
            throw new RuntimeException("No se pudo enviar el correo. Por favor, inténtalo de nuevo.", e);
        }
    }
}
