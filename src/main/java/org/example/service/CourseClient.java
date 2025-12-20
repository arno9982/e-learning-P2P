package org.example.service;

import java.io.*;
import java.net.Socket;

public class CourseClient {
    public static byte[] download(String host, int port, String courseId) throws IOException {
        try (Socket s = new Socket(host, port)) {
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
            pw.println("GET /course/" + courseId);

            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String status = br.readLine(); // "OK N"
            if (status == null || !status.startsWith("OK")) throw new IOException("Bad status");
            int size = Integer.parseInt(status.split(" ")[1]);
            return s.getInputStream().readNBytes(size);
        }
    }
}
