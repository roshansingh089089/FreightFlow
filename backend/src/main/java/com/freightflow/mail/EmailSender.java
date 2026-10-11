package com.freightflow.mail;

public interface EmailSender {
  boolean configured();

  void send(String to, String cc, String subject, String body, String filename, byte[] pdf);
}
