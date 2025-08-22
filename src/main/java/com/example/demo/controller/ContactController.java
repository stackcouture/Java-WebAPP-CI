package com.example.demo.controller;

import com.example.demo.model.ContactMessage;
import com.example.demo.repository.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ContactController {

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("contactMessage", new ContactMessage());
        return "contact";
    }

    @PostMapping("/contact")
    public String submitContactForm(@RequestParam("name") String name,
                                    @RequestParam("email") String email,
                                    @RequestParam("subject") String subject,
                                    @RequestParam("message") String message,
                                    Model model) {

        ContactMessage contactMessage = new ContactMessage();
        contactMessage.setName(name);
        contactMessage.setEmail(email);
        contactMessage.setSubject(subject);
        contactMessage.setMessage(message);

        contactMessageRepository.save(contactMessage);

        model.addAttribute("success", "Your message has been sent!");
        return "contact";
    }
}
