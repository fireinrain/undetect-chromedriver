package com.fireinrain.undetect;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChromeDriverBuilder {

    // false: use temp user data dir
    private boolean keepUserDataDir = false;

    // user data dir path
    private String userDataDir = null;

    // chrome exec path
    private String binaryLocation = null;

    // chromeOptions args
    private List<String> args = new ArrayList<>();

    /**
     * step1: Patcher build
     *
     * @param driverExecutablePath driver path
     * @throws RuntimeException
     */
    private void buildPatcher(String driverExecutablePath) throws RuntimeException {
        // patcher
        Patcher patcher = new Patcher(driverExecutablePath);

        try {
            patcher.Auto();
        } catch (Exception e) {
            throw new RuntimeException("patcher cdc replace fail");
        }
    }

    /**
     * step2：find a free port and set host in options
     *
     * @param chromeOptions
     * @throws RuntimeException
     */
    private ChromeOptions setHostAndPort(ChromeOptions chromeOptions) throws RuntimeException {
        // debug host and port
        String debugHost = null;
        int debugPort = -1;
        if (args != null && args.size() > 0) {
            for (String arg : args) {
                if (arg.contains("--remote-debugging-host")) {
                    try {
                        debugHost = arg.split("=")[1];
                    } catch (Exception ignored) {

                    }
                }
                if (arg.contains("--remote-debugging-port")) {
                    try {
                        debugPort = Integer.parseInt(arg.split("=")[1]);
                    } catch (Exception ignored) {

                    }
                }
            }
        }
        if (debugHost == null) {
            debugHost = "127.0.0.1";
            chromeOptions.addArguments("--remote-debugging-host=" + debugHost);
        }
        if (debugPort == -1) {
            debugPort = findFreePort();
        }
        if (debugPort == -1) {
            throw new RuntimeException("free port not find");
        } else {
            chromeOptions.addArguments("--remote-debugging-port=" + String.valueOf(debugPort));
        }

        try {
            Field experimentalOptions = chromeOptions.getClass().getSuperclass().getDeclaredField("experimentalOptions");
            experimentalOptions.setAccessible(true);
            Map<String, Object> experimentals = (Map<String, Object>) experimentalOptions.get(chromeOptions);
            if (experimentals != null && experimentals.get("debuggerAddress") != null) {
                return chromeOptions;
            }
        } catch (Exception ignored) {

        }
        chromeOptions.setExperimentalOption("debuggerAddress", debugHost + ":" + String.valueOf(debugPort));
        return chromeOptions;
    }

    /**
     * step3: set user data dir arg for chromeOptions
     *
     * @param chromeOptions
     * @return
     * @throws RuntimeException
     */
    private ChromeOptions setUserDataDir(ChromeOptions chromeOptions) throws RuntimeException {
        // find user data dir in chromeOptions
        if (args != null) {
            for (String arg : args) {
                if (arg.contains("--user-data-dir")) {
                    try {
                        userDataDir = arg.split("=")[1];
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }
        if (userDataDir == null || userDataDir.equals("")) {
            // no user data dir in it
            keepUserDataDir = false;
            try {
                // create temp dir
                userDataDir = Files.createTempDirectory("undetected_chrome_driver").toString();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("temp user data dir create fail");
            }
            // add into options
            chromeOptions.addArguments("--user-data-dir=" + userDataDir);
        } else {
            keepUserDataDir = true;
        }
        return chromeOptions;
    }

    /**
     * step4: set browser language
     *
     * @param chromeOptions
     * @return
     * @throws RuntimeException
     */
    private ChromeOptions setLanguage(ChromeOptions chromeOptions) {
        if (args != null) {
            for (String arg : args) {
                if (arg.contains("--lang=")) {
                    return chromeOptions;
                }
            }
        }
        // no argument lang
        String language = Locale.getDefault().getLanguage().replace("_", "-");
        chromeOptions.addArguments("--lang=" + language);
        return chromeOptions;
    }

    /**
     * step5: find and set chrome BinaryLocation
     *
     * @param chromeOptions
     * @return
     */
    private ChromeOptions setBinaryLocation(ChromeOptions chromeOptions, String binaryLocation) {
        if (binaryLocation == null) {
            try {
                binaryLocation = getChromePath();
            } catch (Exception e) {
                throw new RuntimeException("chrome not find");
            }
            if (binaryLocation.equals("")) {
                throw new RuntimeException("chrome not find");
            }
            chromeOptions.setBinary(binaryLocation);
        } else {
            chromeOptions.setBinary(binaryLocation);
        }
        this.binaryLocation = binaryLocation;
        return chromeOptions;
    }

    /**
     * step 6: suppressWelcome
     *
     * @param chromeOptions
     * @param suppressWelcome
     * @return
     */
    private ChromeOptions suppressWelcome(ChromeOptions chromeOptions, boolean suppressWelcome) {
        if (suppressWelcome) {
            if (args != null) {
                if (!args.contains("--no-default-browser-check")) {
                    chromeOptions.addArguments("--no-default-browser-check");
                }
                if (!args.contains("--no-first-run")) {
                    chromeOptions.addArguments("--no-first-run");
                }
            } else {
                chromeOptions.addArguments("--no-default-browser-check", "--no-first-run");
            }
        }
        return chromeOptions;
    }

    /**
     * step7, set headless arg
     *
     * @param chromeOptions
     * @param headless
     * @return
     */
    private ChromeOptions setHeadless(ChromeOptions chromeOptions, boolean headless) {
        if (headless) {
            if (args != null) {
                if (!args.contains("--headless")) {
                    chromeOptions.addArguments("--headless");
                }
                boolean hasWindowSize = false;
                for (String arg : args) {
                    if (arg.contains("--window-size=")) {
                        hasWindowSize = true;
                        break;
                    }
                }
                if (!hasWindowSize) {
                    chromeOptions.addArguments("--window-size=1920,1080");
                }
                if (!args.contains("--start-maximized")) {
                    chromeOptions.addArguments("--start-maximized");
                }
                if (!args.contains("--no-sandbox")) {
                    chromeOptions.addArguments("--no-sandbox");
                }
            } else {
                chromeOptions.addArguments("--headless");
                chromeOptions.addArguments("--window-size=1920,1080");
                chromeOptions.addArguments("--start-maximized");
                chromeOptions.addArguments("--no-sandbox");
            }
        }
        return chromeOptions;
    }

    /**
     * step8, set log level
     *
     * @param chromeOptions
     * @return
     */
    private ChromeOptions setLogLevel(ChromeOptions chromeOptions) {
        if (args != null) {
            for (String arg : args) {
                if (arg.contains("--log-level=")) {
                    return chromeOptions;
                }
            }
        }
        chromeOptions.addArguments("--log-level=0");
        return chromeOptions;
    }

    /**
     * step9, add prefs into user dir
     *
     * @param userDataDir
     * @param prefs
     */
    private void handlePrefs(String userDataDir, Map<String, Object> prefs) throws RuntimeException {
        String defaultPath = userDataDir + File.separator + "Default";
        Path path = Paths.get(defaultPath);
        if (!Files.exists(path)){
            try {
                Files.createDirectories(path);
            }catch (IOException e){
                throw new RuntimeException(e);
            }
        }

        Map<String, Object> newPrefs = new HashMap<String, Object>(prefs);

        String prefsFile = defaultPath + File.separator + "Preferences";
        if (new File(prefsFile).exists()) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(prefsFile, StandardCharsets.ISO_8859_1));
                String line = null;
                StringBuilder stringBuilder = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    stringBuilder.append(line);
                    stringBuilder.append("\n");
                }
                ObjectMapper objectMapper = new ObjectMapper();
                newPrefs = objectMapper.readValue(stringBuilder.toString(), new TypeReference<Map<String, Object>>() {
                });

            } catch (Exception e) {
                throw new RuntimeException("Default Preferences dir not find");
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            try {
                for (Map.Entry<String, Object> pref : prefs.entrySet()) {
                    undotMerge(pref.getKey(), pref.getValue(), newPrefs);
                }
            } catch (Exception e) {
                throw new RuntimeException("Prefs merge fail");
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(prefsFile, StandardCharsets.ISO_8859_1))) {
                ObjectMapper objectMapper = new ObjectMapper();
                String jsonString = objectMapper.writeValueAsString(newPrefs);
                bw.write(jsonString);
                bw.flush();
            } catch (Exception e) {
                throw new RuntimeException("prefs write to file fail");
            }
        }

    }

    /**
     * step10, fix exit type
     *
     * @param chromeOptions
     * @return
     */
    private ChromeOptions fixExitType(ChromeOptions chromeOptions) {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            String filePath = userDataDir + File.separator + "Default" + File.separator + "Preferences";

            reader = new BufferedReader(new FileReader(filePath, StandardCharsets.ISO_8859_1));

            String line = null;
            StringBuilder jsonStr = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                jsonStr.append(line);
                jsonStr.append("\n");
            }
            reader.close();

            String json = jsonStr.toString();
            Pattern pattern = Pattern.compile("(?<=exit_type\"\":)(.*?)(?=,)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                writer = new BufferedWriter(new FileWriter(filePath, StandardCharsets.ISO_8859_1));
                json = json.replace(matcher.group(), "null");
                writer.write(json);
                writer.close();
            }
        } catch (Exception ignored) {

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
        return chromeOptions;
    }

    /**
     * step11: open chrome by args on new process
     *
     * @param chromeOptions
     * @return
     */
    private Process createBrowserProcess(ChromeOptions chromeOptions, boolean needPrintChromeInfo) throws RuntimeException {
        LoadChromeOptionsArgs(chromeOptions);
        if (args == null) {
            throw new RuntimeException("can't open browser, args not found");
        }
        Process p = null;
        try {
            args.add(0, binaryLocation);
            p = new ProcessBuilder(args).start();
        } catch (Exception e) {
            throw new RuntimeException("chrome open fail");
        }

        Process browser = p;

        Thread outputThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(browser.getInputStream()));
                    String buff = null;
                    while ((buff = br.readLine()) != null) {
                        System.out.println(buff);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Thread errorPutThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader er = new BufferedReader(new InputStreamReader(browser.getErrorStream()));
                    String errors = null;
                    while ((errors = er.readLine()) != null) {
                        System.out.println(errors);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        if (needPrintChromeInfo) {
            outputThread.start();
            errorPutThread.start();
        }

        return browser;
    }

    /**
     * build a undetected chrome driver
     *
     * @param options              chromeOptions           required no prefs args in experiment map, if has prefs, use param prefs
     * @param driverExecutablePath chrome driver path      not null
     * @param binaryLocation       chrome path
     * @param headless             is headless
     * @param suppressWelcome      suppress welcome
     * @param needPrintChromeInfo  need print chrome process information and errors
     * @param prefs                add prefs into user-data-dir
     * @return
     * @throws RuntimeException
     */
    public ChromeDriver build(ChromeOptions options,
                              String driverExecutablePath,
                              String binaryLocation,
                              boolean headless,
                              boolean suppressWelcome,
                              boolean needPrintChromeInfo,
                              Map<String, Object> prefs
    ) throws RuntimeException {
        // Create ChromeDriver
        if (driverExecutablePath == null) {
            throw new RuntimeException("driverExecutablePath is required.");
        }
        // step 0, load origin args for options
        LoadChromeOptionsArgs(options);

        // step 1，patcher replace cdc mark
        buildPatcher(driverExecutablePath);

        // chrome options assert not null
        ChromeOptions chromeOptions = options;
        if (chromeOptions == null) {
            chromeOptions = new ChromeOptions();
        }

        // step 2, set host and port
        chromeOptions = setHostAndPort(chromeOptions);

        // step3, set user data dir
        chromeOptions = setUserDataDir(chromeOptions);

        // step4, set language
        chromeOptions = setLanguage(chromeOptions);

        // step5, set binaryLocation
        chromeOptions = setBinaryLocation(chromeOptions, binaryLocation);

        // step6, suppressWelcome
        chromeOptions = suppressWelcome(chromeOptions, suppressWelcome);

        // step7, set headless arguments
        chromeOptions = setHeadless(chromeOptions, headless);

        // step8, set logLevel
        chromeOptions = setLogLevel(chromeOptions);

        // step9 ,merge prefs
        if (prefs != null) {
            handlePrefs(userDataDir, prefs);
        }

        // step10, fix exit type
        chromeOptions = fixExitType(chromeOptions);

        // step11, start process
        Process browser = createBrowserProcess(chromeOptions, needPrintChromeInfo);

        // step12, make undetectedChrome chrome driver


        UndetectedChromeDriver undetectedChromeDriver =
                new UndetectedChromeDriver(chromeOptions, headless, keepUserDataDir, userDataDir, browser);

        return undetectedChromeDriver;
    }

    /**
     * recommend use it
     *
     * @param options
     * @param driverExecutablePath
     * @param binaryLocation
     * @param suppressWelcome
     * @param needPrintChromeInfo
     * @return
     */
    public ChromeDriver build(ChromeOptions options,
                              String driverExecutablePath,
                              String binaryLocation,
                              boolean suppressWelcome,
                              boolean needPrintChromeInfo
    ) {
        // operator headless
        boolean headless = false;
        try {
            Field argsField = options.getClass().getSuperclass().getDeclaredField("args");
            argsField.setAccessible(true);
            List<String> args = (List<String>) argsField.get(options);
            if (args.contains("--headless")) {
                headless = true;
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> prefs = null;
        try {
            Field argsField = options.getClass().getSuperclass().getDeclaredField("experimentalOptions");
            argsField.setAccessible(true);
            Map<String, Object> args = (Map<String, Object>) argsField.get(options);
            if (args.containsKey("prefs")) {
                prefs = new HashMap<>((Map<String, Object>) args.get("prefs"));
                args.remove("prefs");
            }
        } catch (Exception ignored) {
        }

        return this.build(options,
                driverExecutablePath,
                binaryLocation,
                headless,
                suppressWelcome,
                needPrintChromeInfo,
                prefs);
    }

    /**
     * recommend use it
     *
     * @param options
     * @param driverExecutablePath
     * @param suppressWelcome
     * @param needPrintChromeInfo
     * @return
     */
    public ChromeDriver build(ChromeOptions options,
                              String driverExecutablePath,
                              boolean suppressWelcome,
                              boolean needPrintChromeInfo
    ) {
        return this.build(options, driverExecutablePath, null, suppressWelcome, needPrintChromeInfo);
    }

    /**
     * recommend use it
     *
     * @param options
     * @param driverExecutablePath
     * @return
     */
    public ChromeDriver build(ChromeOptions options,
                              String driverExecutablePath) {
        return this.build(options, driverExecutablePath, true, false);
    }

    /**
     * recommend use it
     *
     * @param driverExecutablePath
     * @return
     */
    public ChromeDriver build(String driverExecutablePath) {
        return this.build(null, driverExecutablePath);
    }

    /**
     * find free port
     *
     * @return
     */
    private int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {

            }
        }
    }

    /**
     * find args in chromeOptions
     *
     * @param chromeOptions
     */
    private void LoadChromeOptionsArgs(ChromeOptions chromeOptions) {
        try {
            Field argsField = chromeOptions.getClass().getSuperclass().getDeclaredField("args");
            argsField.setAccessible(true);
            args = new ArrayList<>((List<String>) argsField.get(chromeOptions));
        } catch (Exception ignored) {
        }
    }

    /**
     * find chrome.exe or chrome
     *
     * @return
     */
    private String getChromePath() throws RuntimeException {
        String os = System.getProperties().getProperty("os.name");
        String chromeDataPath = null;
        boolean IS_POSIX = SysUtil.isMacOs() || SysUtil.isLinux();
        Set<String> possibles = new HashSet<>();
        if (IS_POSIX) {
            List<String> names = Arrays.asList("google-chrome",
                    "chromium",
                    "chromium-browser",
                    "chrome",
                    "google-chrome-stable");
            for (String path : SysUtil.getPath()) {
                for (String name : names) {
                    possibles.add(path + File.separator + name);
                }
            }
            if (SysUtil.isMacOs()) {
                possibles.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
                possibles.add("/Applications/Chromium.app/Contents/MacOS/Chromium");
            }
        } else {
            List<String> paths = new ArrayList<>();
            paths.add(SysUtil.getString("PROGRAMFILES"));
            paths.add(SysUtil.getString("PROGRAMFILES(X86)"));
            paths.add(SysUtil.getString("LOCALAPPDATA"));

            List<String> middles = Arrays.asList("Google" + File.separator + "Chrome" + File.separator + "Application",
                    "Google" + File.separator + "Chrome Beta" + File.separator + "Application",
                    "Google" + File.separator + "Chrome Canary" + File.separator + "Application");

            for (String path : paths) {
                for (String middle : middles) {
                    possibles.add(path + File.separator + middle + File.separator + "chrome.exe");
                }
            }

        }

        for (String possible : possibles) {
            File file = new File(possible);
            if (file.exists() && file.canExecute()) {
                chromeDataPath = file.getAbsolutePath();
                break;
            }
        }

        if (chromeDataPath == null) {
            throw new RuntimeException("chrome not find in your pc, please use arg binaryLocation");
        }

        return chromeDataPath;
    }

    /**
     * merge param for user-data
     *
     * @param key
     * @param value
     * @param dict
     */
    private void undotMerge(String key, Object value, Map<String, Object> dict) {
        if (key.contains(".")) {
            String[] splits = key.split("\\.", 2);
            String k1 = splits[0];
            String k2 = splits[1];

            if (!dict.containsKey(k1)) {
                dict.put(k1, new HashMap<String, Object>());
            }
            try {
                undotMerge(k2, value, (Map<String, Object>) dict.get(k1));
            } catch (Exception ignored) {
            }
            return;
        }
        dict.put(key, value);
    }
}
