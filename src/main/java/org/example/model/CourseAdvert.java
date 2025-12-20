package org.example.model;

import java.time.Instant;

public class CourseAdvert {
    public String id;       // identifiant distant (ex: "c1")
    public String title;    // titre lisible
    public int version;     // version du cours
    public long expiresAt;  // epoch seconds (TTL)

    public CourseAdvert() {}

    public CourseAdvert(String id, String title, int version, long expiresAt) {
        this.id = id; this.title = title; this.version = version; this.expiresAt = expiresAt;
    }

    public CourseAdvert withNewExpiry(long ms) {
        long s = Instant.now().plusMillis(ms).getEpochSecond();
        return new CourseAdvert(id, title, version, s);
    }
}

