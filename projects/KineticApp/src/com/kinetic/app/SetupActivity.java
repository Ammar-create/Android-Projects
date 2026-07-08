package com.kinetic.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * First-run setup screen. Collects name, avatar, and API key.
 * Also handles initial storage permission request.
 */
public class SetupActivity extends Activity {
    private static final int PICK_IMAGE = 100;
    private static final int PERM_STORAGE = 200;

    private EditText nameInput;
    private EditText apiKeyInput;
    private ImageView avatarPreview;
    private View avatarPlaceholder;
    private TextView saveBtn;
    private ImageView eyeToggle;
    private boolean keyVisible = false;
    private String avatarBase64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Skip setup if already done
        Prefs prefs = new Prefs(this);
        if (prefs.isSetupComplete()) {
            startMain();
            return;
        }

        setContentView(R.layout.activity_setup);
        requestStoragePermission();

        nameInput = findViewById(R.id.setupName);
        apiKeyInput = findViewById(R.id.setupApiKey);
        avatarPreview = findViewById(R.id.setupAvatarPreview);
        avatarPlaceholder = findViewById(R.id.setupAvatarPlaceholder);
        saveBtn = findViewById(R.id.setupSaveBtn);
        eyeToggle = findViewById(R.id.setupEyeToggle);

        // Avatar click
        findViewById(R.id.setupAvatarWrap).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        });

        // Eye toggle
        eyeToggle.setOnClickListener(v -> {
            keyVisible = !keyVisible;
            if (keyVisible) {
                apiKeyInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                eyeToggle.setImageResource(R.drawable.ic_eye_off);
            } else {
                apiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyeToggle.setImageResource(R.drawable.ic_eye);
            }
            apiKeyInput.setSelection(apiKeyInput.getText().length());
        });

        // Watch inputs to enable/disable button
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSaveBtn(); }
        };
        nameInput.addTextChangedListener(watcher);
        apiKeyInput.addTextChangedListener(watcher);

        // Save button
        saveBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String apiKey = apiKeyInput.getText().toString().trim();

            if (name.isEmpty()) {
                nameInput.setError("Enter your name");
                return;
            }
            if (apiKey.isEmpty()) {
                apiKeyInput.setError("Enter your API key");
                return;
            }

            prefs.setName(name);
            prefs.setApiKey(apiKey);
            prefs.setSetupComplete(true);
            if (avatarBase64 != null) {
                prefs.setAvatar(avatarBase64);
            }

            Toast.makeText(this, getString(R.string.setup_complete), Toast.LENGTH_SHORT).show();
            startMain();
        });
    }

    private void updateSaveBtn() {
        boolean valid = !nameInput.getText().toString().trim().isEmpty()
            && !apiKeyInput.getText().toString().trim().isEmpty();
        saveBtn.setAlpha(valid ? 1.0f : 0.5f);
        saveBtn.setClickable(valid);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                if (uri == null) return;
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();

                if (bmp != null) {
                    // Resize to 256x256
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, 256, 256, true);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    avatarBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                    avatarPreview.setImageBitmap(scaled);
                    avatarPreview.setVisibility(View.VISIBLE);
                    avatarPlaceholder.setVisibility(View.GONE);

                    if (!bmp.isRecycled()) bmp.recycle();
                    if (!scaled.isRecycled()) scaled.recycle();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERM_STORAGE);
            }
        } else if (Build.VERSION.SDK_INT <= 28) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, PERM_STORAGE);
            }
        }
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
