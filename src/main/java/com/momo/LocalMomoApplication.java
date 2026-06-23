package com.momo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class LocalMomoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalMomoApplication.class, args);
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        System.out.println("=================================================");
        System.out.println("🚀 网页仿墨墨背单词系统（Spring Boot 工业版）全线拉起成功！");
        System.out.println("👉 📱 墨墨仿真背词前台: http://localhost:8080/index.html");
        System.out.println("👉 ⚙️ 词库数据后台管理: http://localhost:8080/admin.html");
        System.out.println("👉 📥 外部词汇集批量导入: http://localhost:8080/import.html");
        System.out.println("=================================================");
    }
}