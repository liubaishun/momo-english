package com.momo.controller;

import com.momo.service.dict.PhoneticImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
@CrossOrigin(origins = "*") // 允许前端本地跨域调用
public class ImportRunnerController {

    @Autowired
    private PhoneticImportService service;

    @GetMapping("/test")
    public void run(String... args) throws Exception {

        service.importPhonetic("/Users/liubaishun/Documents/yintech/momo-english/src/main/resources/static/books/csv/en_US.csv");
    }
}