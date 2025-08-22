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
        return "contact"; // This is the name of your Thymeleaf template (contact.html)
    }

    // ✅ This is the method you're referring to:
    @PostMapping("/contact")
    public String submitContactForm(@ModelAttribute ContactMessage contactMessage, Model model) {
        contactMessageRepository.save(contactMessage);
        model.addAttribute("success", "Your message has been sent!");
        return "contact"; // Re-render the same page with success message
    }
}
