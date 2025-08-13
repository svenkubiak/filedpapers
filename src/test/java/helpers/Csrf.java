package helpers;

import java.net.HttpCookie;

public record Csrf (HttpCookie cookie, String token) {}