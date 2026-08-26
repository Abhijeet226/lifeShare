package com.example.abhijeet.bloodbank;

public final class ContactContract {
    private ContactContract(){

    }
    public static class ContactEntry{
        public static  final String TABLE_NAME= "info";
        public static  final String CONTACT_ID= "contact_id";
        public static  final String NAME= "name";
        public static  final String EMAIL= "email";
        public static  final String PASSWORD= "password";
        public static  final String MOBILE= "mobile";
        public static  final String BLOODGROUP= "bloodgroup";

    }
}
