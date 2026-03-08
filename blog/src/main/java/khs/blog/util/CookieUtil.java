package khs.blog.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.SerializationUtils;

import java.util.Base64;

public class CookieUtil {

    public static void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }

        for (Cookie cookie : cookies) {
            if (!cookie.getName().equals(name)) continue;

            cookie.setMaxAge(0);
            cookie.setValue("");
            cookie.setPath("/");
            response.addCookie(cookie);
        }
    }

    public static String serialize(Object object) {
        byte[] bytes = SerializationUtils.serialize(object);
        if (bytes == null) {
            throw new IllegalArgumentException("Serialization failed: object is not Serializable");
        }
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static <T> T deserialize(Cookie cookie, Class<T> cls) {
        byte[] decoded = Base64.getUrlDecoder().decode(cookie.getValue());
        Object obj = SerializationUtils.deserialize(decoded);
        return cls.cast(obj);
    }
}