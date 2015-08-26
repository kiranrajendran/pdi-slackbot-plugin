package com.findthebest.slack;

import com.google.gson.*;
import com.google.gson.internal.LinkedTreeMap;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static com.findthebest.slack.BotIcons.botIcons;

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
    private String configFile = "config.properties";
    private Boolean authStatus;
    private String token;
    private final static Logger LOGGER = Logger.getLogger(SlackConnection.class.getName());
    public final static int CHANNEL = 1, GROUP = 2, DM = 3;
    private final String propertiesFile = "com/findthebest/slack/resources/config.properties";
    Properties properties;



     
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
//            properties = loadProperties(propertiesFile);
            token = passedToken; //== null ? properties.getProperty("defaultToken") : passedToken;
            configLogger(Level.CONFIG);
            String authUrlString = baseAuthUrl + token;
            LOGGER.config("Attempting to Auth");
            HttpsURLConnection con = sendGetRequest(new URL(authUrlString));
            LOGGER.config("Checking response code");
            int response = con.getResponseCode();
            if (response == OK) {
                LOGGER.config("Setting auth status");
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

    private void configLogger(Level logLevel) {

        LOGGER.setLevel(logLevel);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(logLevel);
        LOGGER.addHandler(handler);
    }

    private static Properties loadProperties(String propFile) throws FileNotFoundException {
        InputStream in = SlackConnection.class.getClassLoader().getResourceAsStream(propFile);
        Properties properties = new Properties();
        try {
            if (in != null) {
                properties.load(in);
            } else {
                throw new FileNotFoundException(String.format("property file %s not found in classpath", propFile));
            }

        } catch (IOException e) {
            throw new FileNotFoundException();
        }
        return properties;
    }

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
            LOGGER.config("Building GET request");
            LinkedHashMap<String,String> params = new LinkedHashMap<String, String>();
            params.put("token", token);
            params.put("channel", channel.startsWith("#") ? channel : "#" + channel);
            params.put("text", message);
            params.put("username", username);
            params.put("icon_emoji", icon);
            params.put("link_names", "1");
            for (Map.Entry entry : params.entrySet()) {
                baseMessageUrl.append(entry.getKey());
                baseMessageUrl.append("=");
                baseMessageUrl.append(URLEncoder.encode((String) entry.getValue(), "UTF-8"));
                baseMessageUrl.append("&");
            }
            baseMessageUrl.deleteCharAt(baseMessageUrl.length() - 1);  // delete trailing &
            LOGGER.config("Sending GET request");
            LOGGER.config(baseMessageUrl.toString());
            HttpsURLConnection con = sendGetRequest(new URL(baseMessageUrl.toString()));
            String response = extractResponse(con);
            LOGGER.config(response);
            return determineStatus(response);
        } else {
            LOGGER.severe("Client not authed");
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
        LOGGER.config("Getting room information");
        HttpsURLConnection con = sendGetRequest(new URL(url));
        String response = extractResponse(con);
        LOGGER.info(response);
        return response;

    }

    public Boolean getAuthStatus() {
        return authStatus;
    }

    public static void main(String[] args) throws IOException, InputMismatchException {
        SlackConnection slack = new SlackConnection("xoxp-2225323168-4194257109-6101248807-defb57");
//        slack.postToSlack("integration-testing", "Test");
//        String result = slack.getRoomList(GROUP);
//        JsonElement parsed = new JsonParser().parse(result);
//        JsonObject jObject = parsed.getAsJsonObject();
//        String status = jObject.get("ok").toString();
//        JsonArray jarray = jObject.getAsJsonArray("groups");
//        List<String> options = new LinkedList<String>();
//        Iterator<JsonElement> jelement = jarray.iterator();
//        while (jelement.hasNext()) {
//            options.add(jelement.next().getAsJsonObject().get("name").toString());
//        }
//        LOGGER.info("Test");
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
