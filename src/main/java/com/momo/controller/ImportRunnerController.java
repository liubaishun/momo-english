package com.momo.controller;

import com.momo.service.dict.PhoneticImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
public class ImportRunnerController {

    @Autowired
    private PhoneticImportService service;

    @GetMapping("/test")
    public void run(String... args) throws Exception {

        service.importPhonetic("/Users/liubaishun/Downloads/csv/en_UK.csv");
    }
}