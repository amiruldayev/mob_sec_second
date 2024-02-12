package com.example.lab2;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class MainActivity extends AppCompatActivity {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/contacts";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onExtractContactsClick(View view) {
        // Проверяем, есть ли разрешение на чтение контактов
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Разрешение не предоставлено, поэтому запрашиваем его у пользователя
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS);
        } else {
            // Разрешение уже предоставлено, начинаем извлечение контактов
            new ExtractContactsTask().execute();
        }
    }

    private class ExtractContactsTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            StringBuilder stringBuilder = new StringBuilder();
            ContentResolver contentResolver = getContentResolver();
            String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
            try (Cursor cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    do {
                        String name = cursor.getString(nameIndex);
                        String number = cursor.getString(numberIndex);
                        stringBuilder.append(name).append(": ").append(number).append("\n");
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return stringBuilder.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            // Отправляем результат на ваш сервер
            sendContactsToServer(result);
        }
    }

    private void sendContactsToServer(final String contacts) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Проверка доступности сервера
                    InetAddress address = InetAddress.getByName("localhost");
                    if (address.isReachable(5000)) { // Проверка доступности сервера в течение 5 секунд
                        Log.d("ConnectionCheck", "Сервер доступен, попытка подключения к базе данных...");

                        // Подключение к базе данных
                        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

                        // Подготовка запроса для вставки контактов в таблицу
                        String insertQuery = "INSERT INTO contacts (name, phone_number) VALUES (?, ?)";
                        PreparedStatement preparedStatement = conn.prepareStatement(insertQuery);

                        // Разбиваем строку контактов на отдельные записи и вставляем их в базу данных
                        String[] contactLines = contacts.split("\n");
                        for (String contactLine : contactLines) {
                            String[] parts = contactLine.split(":");
                            String name = parts[0].trim();
                            String phoneNumber = parts[1].trim();
                            preparedStatement.setString(1, name);
                            preparedStatement.setString(2, phoneNumber);
                            preparedStatement.executeUpdate();
                        }

                        // Закрываем соединение с базой данных
                        preparedStatement.close();
                        conn.close();

                        // Выводим сообщение об успешном сохранении контактов
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Контакты успешно сохранены в базе данных", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        Log.e("ConnectionCheck", "Сервер недоступен");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Сервер недоступен" , Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    // Выводим сообщение об ошибке при сохранении контактов
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Ошибка при сохранении контактов в базе данных" , Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Добавленный вызов
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_CONTACTS) {
            // Если запрос отменен, массив результатов будет пустым
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение предоставлено, начинаем извлечение контактов
                new ExtractContactsTask().execute();
            } else {
                // Разрешение не предоставлено, сообщаем пользователю
                Toast.makeText(this, "Для доступа к контактам необходимо предоставить разрешение", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
