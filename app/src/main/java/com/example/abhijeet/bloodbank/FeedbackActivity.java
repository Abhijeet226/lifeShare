package com.example.abhijeet.bloodbank;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class FeedbackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowHelper.applyEdgeToEdge(this);
        setContentView(R.layout.activity_feedback);

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        android.widget.TextView tvTitle = findViewById(R.id.tv_header_title);
        if (tvTitle != null) {
            tvTitle.setText("Feedback & Support");
        }

        final EditText to = findViewById(R.id.sendTo);
        if (to != null) {
            to.setEnabled(false);
        }
        final EditText subject = findViewById(R.id.subject);
        final EditText message = findViewById(R.id.EmailText);
        final Button send = findViewById(R.id.sendEmail);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String subS = subject.getText().toString().trim();
                String msgS = message.getText().toString().trim();

                if (msgS.isEmpty()) {
                    Toast.makeText(FeedbackActivity.this, "Please enter your message", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:"));
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"Abhijeet.pradhan@gmail.com"});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subS.isEmpty() ? "Life Share Feedback" : subS);
                emailIntent.putExtra(Intent.EXTRA_TEXT, msgS);

                try {
                    startActivity(Intent.createChooser(emailIntent, "Choose Email App"));
                } catch (Exception e) {
                    Toast.makeText(FeedbackActivity.this, "No email client found", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
