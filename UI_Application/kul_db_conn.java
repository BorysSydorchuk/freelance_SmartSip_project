package org.myprojects.project_APP;

// Source code is decompiled from a .class file using FernFlower decompiler (from IntelliJ IDEA).

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

public class kul_db_conn {
    public kul_db_conn() {
    }

    public HttpResponse<String> makeGETRequest(HttpClient client, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void parseJSON(String jsoString) {
        try {
            JSONArray array = new JSONArray(jsoString);

            for(int i = 0; i < array.length(); ++i) {
                JSONObject o = array.getJSONObject(i);
                int id = o.getInt("id");
                String name = o.getString("Name");
                System.out.println("ID: " + id + " | name: " + name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void main(String[] args) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = "https://studev.groept.be/api/a25ee2team203/selectAllTest";
            HttpResponse<String> response = this.makeGETRequest(client, url);
            System.out.println("Status Code: " + response.statusCode());
            this.parseJSON(response.body());

            client.close();
        } catch (Exception var5) {
            var5.printStackTrace();
        }

    }
}
