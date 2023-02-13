package com.epicshaggy.biometric;

import static com.epicshaggy.biometric.NativeBiometric.PASSWORD_KEY;
import static com.epicshaggy.biometric.NativeBiometric.SERVER_KEY;
import static com.epicshaggy.biometric.NativeBiometric.USERNAME_KEY;

import com.getcapacitor.JSObject;

import org.json.JSONException;
import org.json.JSONObject;

public class Credentials {
    public final String username;
    public final String password;
    public final String server;

    public Credentials(String username, String password, String server) {
        this.username = username;
        this.password = password;
        this.server = server;
    }

    public Credentials(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        this.username = jsonObject.getString(USERNAME_KEY);
        this.password = jsonObject.getString(PASSWORD_KEY);
        this.server = jsonObject.getString(SERVER_KEY);
    }

    public String toJSON() {
        JSObject json = new JSObject();
        json.put(USERNAME_KEY, username);
        json.put(PASSWORD_KEY, password);
        json.put(SERVER_KEY, server);
        return json.toString();
    }
}
