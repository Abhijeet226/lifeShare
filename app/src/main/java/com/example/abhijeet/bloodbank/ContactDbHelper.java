package com.example.abhijeet.bloodbank;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import static com.example.abhijeet.bloodbank.ContactContract.ContactEntry.EMAIL;
import static com.example.abhijeet.bloodbank.ContactContract.ContactEntry.MOBILE;
import static com.example.abhijeet.bloodbank.ContactContract.ContactEntry.NAME;
import static com.example.abhijeet.bloodbank.ContactContract.ContactEntry.PASSWORD;

public class ContactDbHelper extends SQLiteOpenHelper{
    public static  final String DATABASE_NAME= "contact";
    public static  final int DATABASE_VERSION=1;
    public  static final  String CREATE_TABLE = "create table "+ ContactContract.ContactEntry.TABLE_NAME+"(contact_id INTEGER PRIMARY KEY AUTOINCREMENT, NAME TEXT,EMAIL TEXT,PASSWORD TEXT,MOBILE TEXT,BLOODGROUP TEXT);";
    public  static final  String DROP_TABLE = "drop table if exists "+ContactContract.ContactEntry.TABLE_NAME;
    public ContactDbHelper(Context context)
    {
        super(context,DATABASE_NAME,null,DATABASE_VERSION);
        Log.d("Database Operations", "Database created...");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);

        Log.d("Database Operations", "Table is created");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_TABLE);
        onCreate(db);

    }
    public boolean addContact(int id,String name, String email, String password, String mobile){
        SQLiteDatabase db  = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
      //  contentValues.put(ContactContract.ContactEntry.CONTACT_ID,id);
        contentValues.put(NAME,name);
        contentValues.put(EMAIL,email);
        contentValues.put(PASSWORD,password);
        contentValues.put(MOBILE,mobile);

        long result = db.insert(ContactContract.ContactEntry.TABLE_NAME,null, contentValues);
        Log.d("Database Operations", "One row Inserted");
        if (result== -1 )
            return  false;
        else
            return  true;
    }

}
