package com.intecod.app.dto;

import lombok.Data;

@Data
public class EmailRequest {
    private String to;
    private String cc;
    private String bcc;
    private String subject;
    private String text;

     // Getter y setter para 'to'
     public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }

    // Getter y setter para 'subject'
    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }

    // Getter y setter para 'text'
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }
}