package com.graphiq.kettle.jobentries.slack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.InputMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by aoverton on 6/11/15.
 */
public class SlackConnection {
    /*
     * Instance Variables
     */

    private Gson gson;
    private int OK = 200;
    private String baseAuthUrl = "https://slack.com/api/auth.test?token=";
    private StringBuilder baseMessageUrl = new StringBuilder("https://slack.com/api/chat.postMessage?");
    private Boolean authStatus;
    private String token;
    public final static int CHANNEL = 1, GROUP = 2, DM = 3;



     
    /*
     * Constructors
     */

    public SlackConnection() throws IOException {
        this(null);
    }

    public SlackConnection(String token) {
        this(token, false);
    }

    public SlackConnection(String passedToken, Boolean debug) {
        try {
            gson = new Gson();
            token = passedToken;
            String authUrlString = baseAuthUrl + token;
            HttpsURLConnection con = sendGetRequest(new URL(authUrlString));
            int response = con.getResponseCode();
            if (response == OK) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                LinkedTreeMap result = gson.fromJson(in, LinkedTreeMap.class);
                String status = result.get("ok").toString();
                authStatus = status.equals("true");
                authStatus = true;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /*
     * Methods
     */

    private HttpsURLConnection sendGetRequest(URL url) throws IOException {
        return (HttpsURLConnection) url.openConnection();
    }

    private String extractResponse(HttpsURLConnection con) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }


    public boolean postToSlack(String channel, String message, String username, String icon) throws IOException {
        if (authStatus) {
            LinkedHashMap<String,String> params = new LinkedHashMap<String, String>();
            params.put("token", token);
            params.put("channel", channel.startsWith("#") ? channel : "#" + channel);
            params.put("text", message);
            params.put("username", username);
            params.put("icon_emoji", icon);
            params.put("link_names", "1");
            params.put("unfurl_links", "true");
            for (Map.Entry entry : params.entrySet()) {
                baseMessageUrl.append(entry.getKey());
                baseMessageUrl.append("=");
                baseMessageUrl.append(URLEncoder.encode((String) entry.getValue(), "UTF-8"));
                baseMessageUrl.append("&");
            }
            baseMessageUrl.deleteCharAt(baseMessageUrl.length() - 1);  // delete trailing &
            HttpsURLConnection con = sendGetRequest(new URL(baseMessageUrl.toString()));
            String response = extractResponse(con);
            return determineStatus(response);
        }
        return false;
    }

    public String getRoomList(int type) throws InputMismatchException, IOException {
        String url;
        switch (type) {
            case CHANNEL:
                url = "https://slack.com/api/channels.list?token=" + token;
                break;
            case GROUP:
                url = "https://slack.com/api/groups.list?token=" + token;
                break;
            case DM:
                url = "https://slack.com/api/im.list?token=" + token;
                break;
            default:
                throw new InputMismatchException("Not a valid option for a room type");
        }
        HttpsURLConnection con = sendGetRequest(new URL(url));
        String response = extractResponse(con);
        return response;

    }

    public Boolean getAuthStatus() {
        return authStatus;
    }

    private static boolean determineStatus(String apiResult) {
        JsonElement parsed = new JsonParser().parse(apiResult);
        JsonObject jObject = parsed.getAsJsonObject();
        String status = jObject.get("ok").toString();
        return status.equals("true");
    }


    public String toString() {
        return String.format("token: %s, auth_status: %b", token, authStatus);
    }

}
