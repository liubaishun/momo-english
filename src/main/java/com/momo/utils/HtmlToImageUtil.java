package com.momo.utils;

import com.microsoft.playwright.*;


import java.nio.file.Paths;

public class HtmlToImageUtil {

    public static void htmlToImage(String html, String outputFile) {

        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

            Page page = browser.newPage();

            page.setContent(html);

            page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(Paths.get(outputFile)));

            browser.close();
        }
    }
}