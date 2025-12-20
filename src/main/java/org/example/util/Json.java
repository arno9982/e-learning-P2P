package org.example.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Json {
    private static final Gson G = new GsonBuilder().create();
    public static String encode(Object o) { return G.toJson(o); }
    public static <T> T decode(String s, Class<T> c) { return G.fromJson(s, c); }

    // Overload pratique pour types génériques (ex: Set<String>)
    public static <T> T decode(String s, java.lang.reflect.Type t) { return G.fromJson(s, t); }
}
