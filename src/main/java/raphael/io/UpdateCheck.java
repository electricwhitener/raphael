package raphael.io;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Whether a newer Raphael has been released. It asks GitHub's public API for the newest release
// of the project and compares its version with this one. The request carries nothing about the
// user or their code: GitHub sees only that someone asked (see PRIVACY.md), and it can be
// switched off in settings. Any problem (offline, GitHub down, an odd answer) just means
// "nothing to report".
// OOP: a utility class; a record (Release); exception handling: the network's checked
// exceptions are declared, so the caller decides what a failure means.
public final class UpdateCheck {

    private static final URI LATEST = URI.create("https://api.github.com/repos/electricwhitener/raphael/releases/latest");
    public static final String RELEASES_PAGE = "https://github.com/electricwhitener/raphael/releases/latest";

    // A release: its version ("1.1.0") and the web page to download it from.
    public record Release(String version, String page) {
    }

    private UpdateCheck() {
    }

    // This Raphael's version, from its jar's manifest; null when it runs from the source code,
    // where there's nothing to compare.
    public static String currentVersion() {
        return UpdateCheck.class.getPackage().getImplementationVersion();
    }

    // The newest release, if it's newer than current; otherwise null.
    public static Release newer(String current) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(LATEST)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Raphael/" + current)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null; // 404: no release yet
        }
        String tag = field(response.body(), "tag_name");
        String page = field(response.body(), "html_url");
        if (tag == null) {
            return null;
        }
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        return isNewer(version, current) ? new Release(version, page != null ? page : RELEASES_PAGE) : null;
    }

    // "1.10.0" is newer than "1.9.2": the numbers are compared one by one, not as text.
    public static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[.-]");
        String[] b = current.split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? number(a[i]) : 0;
            int y = i < b.length ? number(b[i]) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0; // "SNAPSHOT" and the like
        }
    }

    // One text field of GitHub's JSON answer. A full JSON parser isn't needed for two fields.
    static String field(String json, String name) {
        Matcher match = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return match.find() ? match.group(1) : null;
    }
}
