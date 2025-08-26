package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    // Product Details Page
    @GetMapping("/detail")
    public String detail() {
        return "detail";
    }

    // Cart Page
    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    // Checkout Page
    @GetMapping("/checkout")
    public String checkout() {
        return "checkout";
    }

    // Shop Page
    @GetMapping("/shop")
    public String shop() {
        return "shop";
    }

    // Checkout Page
    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }
}
