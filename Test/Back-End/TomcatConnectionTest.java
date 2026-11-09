
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TomcatConnectionTest {

    private static final String APP_NAME = "/3940-Group-Assignment";

    private static String SERVER;
    static String APP;
    private static HttpClient client;

    @BeforeAll
    static void ensureTomcatIsUp() throws Exception {
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        File home = tomcatHome();
        SERVER = "http://localhost:" + httpPort(home);
        APP = SERVER + APP_NAME;

        if (isUp()) {
            return;
        }

        if (home == null) {
            fail("Tomcat is not running on " + SERVER + " and could not be found to "
                + "auto-start it. Start Tomcat (bin/startup.bat) or set CATALINA_HOME.");
        }

        System.out.println("      \u001B[33m> Tomcat was not running - starting it from " + home + " ...\u001B[0m");
        startTomcat(home);

        for (int i = 0; i < 60; i++) {
            if (isUp()) {
                return;
            }
            Thread.sleep(1000);
        }
        fail("Tomcat is not running on " + SERVER + " and auto-start failed "
            + "(see " + new File(home, "logs/catalina.out") + "). "
            + "Start it manually: " + new File(home, "bin/startup.bat"));
    }

    private static boolean isUp() {
        try {
            send(HttpRequest.newBuilder(URI.create(SERVER + "/")).GET().build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static File tomcatHome() {
        String env = System.getenv("CATALINA_HOME");
        File[] candidates = {
            env == null ? null : new File(env),
            new File(System.getProperty("user.dir"), "../..")
        };
        for (File dir : candidates) {
            if (dir != null && new File(dir, "bin/bootstrap.jar").isFile()) {
                return dir.getAbsoluteFile().toPath().normalize().toFile();
            }
        }
        return null;
    }

    private static int httpPort(File home) {
        if (home != null) {
            try {
                String xml = Files.readString(new File(home, "conf/server.xml").toPath())
                    .replaceAll("(?s)<!--.*?-->", "");
                Matcher m = Pattern.compile(
                    "<Connector\\s+port=\"(\\d+)\"\\s+protocol=\"HTTP/1\\.1\"").matcher(xml);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            } catch (Exception e) {
            }
        }
        return 8080;
    }

    private static void startTomcat(File home) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String cp = new File(home, "bin/bootstrap.jar") + File.pathSeparator
                  + new File(home, "bin/tomcat-juli.jar");

        ProcessBuilder pb = new ProcessBuilder(
            java,
            "-Dcatalina.home=" + home,
            "-Dcatalina.base=" + home,
            "-Djava.io.tmpdir=" + new File(home, "temp"),
            "-Djava.util.logging.config.file=" + new File(home, "conf/logging.properties"),
            "-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager",
            "-cp", cp,
            "org.apache.catalina.startup.Bootstrap", "start");
        pb.directory(home);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(home, "logs/catalina.out")));
        pb.start();
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(APP + path))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build());
    }

    private static HttpResponse<String> post(String path, String form, boolean ajax)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(APP + path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form));
        if (ajax) {
            b.header("X-Requested-With", "XMLHttpRequest");
        }
        return send(b.build());
    }

    private static void assertJson(HttpResponse<String> res) {
        String type = res.headers().firstValue("Content-Type").orElse("");
        assertTrue(type.startsWith("application/json"),
            "expected JSON but got Content-Type: " + type);
    }

    @Test
    void tomcatServerIsUp() throws Exception {
        HttpResponse<String> res =
            send(HttpRequest.newBuilder(URI.create(SERVER + "/")).GET().build());
        assertEquals(200, res.statusCode());
    }

    @Test
    void appIsDeployed() throws Exception {
        assertEquals(200, get("/").statusCode());
    }

    @Test
    void staticPagesLoad() throws Exception {
        String[] pages = {
            "/index.html", "/login.html", "/adminPage.html", "/lobby.html",
            "/triviaAdmin.html", "/triviaCreate.html", "/triviaSolo.html",
            "/triviaUser.html"
        };
        for (String page : pages) {
            assertEquals(200, get(page).statusCode(), page);
        }
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertEquals(404, get("/doesNotExist").statusCode());
    }

    @Test
    void tomcatCanReachDatabase() throws Exception {
        HttpResponse<String> res = get("/testdb");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("Connected to Supabase!"), res.body());
    }

    @Test
    void sessionWhileLoggedOut() throws Exception {
        HttpResponse<String> res = get("/session");
        assertEquals(200, res.statusCode());
        assertJson(res);
        assertTrue(res.body().contains("\"loggedIn\":false"), res.body());
    }

    @Test
    void categoriesLoad() throws Exception {
        HttpResponse<String> res = get("/categories");
        assertEquals(200, res.statusCode());
        assertJson(res);
        assertTrue(res.body().startsWith("["), res.body());
    }

    @Test
    void questionsWithoutCategoryReturns400() throws Exception {
        assertEquals(400, get("/questions").statusCode());
    }

    @Test
    void questionsWithBadCategoryReturns400() throws Exception {
        assertEquals(400, get("/questions?category_id=abc").statusCode());
    }

    @Test
    void questionsWithRealCategoryLoad() throws Exception {
        Matcher m = Pattern.compile("\"category_id\":(\\d+)")
            .matcher(get("/categories").body());
        assumeTrue(m.find(), "no categories in the database - skipping");

        HttpResponse<String> res = get("/questions?category_id=" + m.group(1));
        assertEquals(200, res.statusCode());
        assertJson(res);
    }

    @Test
    void answerWithoutIdReturns400() throws Exception {
        assertEquals(400, post("/answer", "", false).statusCode());
    }

    @Test
    void answerWithUnknownIdReturns404() throws Exception {
        assertEquals(404, post("/answer", "answer_id=999999999", false).statusCode());
    }

    @Test
    void loginWithoutAjaxHeaderReturns403() throws Exception {
        assertEquals(403, post("/login", "name=x&password=x", false).statusCode());
    }

    @Test
    void loginWithEmptyFieldsReturns400() throws Exception {
        assertEquals(400, post("/login", "name=&password=", true).statusCode());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        HttpResponse<String> res =
            post("/login", "name=noSuchUser_qa&password=wrongPassword", true);
        assertEquals(401, res.statusCode());
    }

    @Test
    void logoutWithoutAjaxHeaderReturns403() throws Exception {
        assertEquals(403, post("/logout", "", false).statusCode());
    }

    @Test
    void logoutWhileLoggedOutReturns200() throws Exception {
        assertEquals(200, post("/logout", "", true).statusCode());
    }

    @Test
    void adminWhileLoggedOutReturns401() throws Exception {
        assertEquals(401, get("/admin/categories").statusCode());
    }

    @Test
    void adminPostWhileLoggedOutReturns401() throws Exception {
        assertEquals(401, post("/admin/categories", "", true).statusCode());
    }

    @Test
    void errorsComeBackAsJson() throws Exception {
        HttpResponse<String> res = get("/admin/categories");
        assertJson(res);
        assertTrue(res.body().contains("\"error\""), res.body());
    }
}
