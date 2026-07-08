package com.example.reminderapp;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;

public class MainActivity extends Activity {
    private EditText reminderText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        createNotificationChannel();
        
        reminderText = findViewById(R.id.reminderText);
        Button setReminderButton = findViewById(R.id.setReminderButton);
        Button saveFileButton = findViewById(R.id.saveFileButton);
        
        setReminderButton.setOnClickListener(v -> setReminder());
        saveFileButton.setOnClickListener(v -> saveToFile());
    }
    
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            "reminder_channel",
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
    
    private void setReminder() {
        String text = reminderText.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter reminder text", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("reminder_text", text);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        long triggerTime = System.currentTimeMillis() + 10000;
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        
        Toast.makeText(this, "Reminder set for 10 seconds", Toast.LENGTH_SHORT).show();
    }
    
    private void saveToFile() {
        String text = reminderText.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter text to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "ReminderApp");
            dir.mkdirs();
            File file = new File(dir, "reminder.txt");
            
            FileWriter writer = new FileWriter(file, true);
            writer.append(text).append("\n");
            writer.close();
            
            Toast.makeText(this, "Saved to " + file.getPath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
