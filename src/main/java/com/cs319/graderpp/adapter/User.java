package com.cs319.graderpp.adapter;

/**
 * Created by burak on 07.11.2015.
 */
public class User {
    private String username;
    private String password;
    private int userType;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getUserType() {
        return userType;
    }

    public void setUserType(int userType) {
        this.userType = userType;
    }
}
