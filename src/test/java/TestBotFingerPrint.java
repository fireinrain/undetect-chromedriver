import com.fireinrain.undetect.ChromeDriverBuilder;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.concurrent.TimeUnit;

public class TestBotFingerPrint {

    public static void main(String[] args) throws InterruptedException {
        String dirverHome = "/Users/sunrise/.cache/selenium/chromedriver/mac64/111.0.5563.64/chromedriver";
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--window-size=1920,1080");
        chromeOptions.addArguments("--remote-allow-origins=*");
        // chromeOptions.addArguments("--user-data-dir=/Users/sunrise/Library/Application Support/Google/Chrome/Default");
        // chromeOptions.addArguments("--headless");

        // ChromeDriverBuilder could throw RuntimeError, you can catch it, *catch it is unnecessary
        ChromeDriver chromeDriver1 = new ChromeDriverBuilder()
                .build(chromeOptions, dirverHome, true, true);

        chromeDriver1.get("https://bot.sannysoft.com");

        TimeUnit.SECONDS.sleep(100);
    }

}
