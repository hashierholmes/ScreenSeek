package hh.screenseek.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * ScreenSeek settings and application entry screen.
 *
 * This Activity handles local configuration and permission setup. Screen
 * capture itself is delegated to the foreground service so it is not tied
 * to this Activity's lifecycle.
 */
public class MainActivity extends Activity {

    public static final String PREFS_NAME = "ScreenSeekPrefs";
    public static final String KEY_API_KEY = "gemini_api_key";
    public static final String KEY_MODEL_NAME = "gemini_model_name";
    public static final String DEFAULT_MODEL = "gemini-3.5-flash";

    private EditText etApiKey;
    private EditText etModelName;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        etApiKey = findViewById(R.id.etApiKey);
        etModelName = findViewById(R.id.etModelName);

        Button btnSaveApiKey = findViewById(R.id.btnSaveApiKey);
        Button btnPermission = findViewById(R.id.btnPermission);
        Button btnStartSnip = findViewById(R.id.btnStartSnip);

        LinearLayout layoutApiHelpHeader =
                findViewById(R.id.layoutApiHelpHeader);

        LinearLayout layoutApiHelpContent =
                findViewById(R.id.layoutApiHelpContent);

        ImageView ivApiHelpArrow =
                findViewById(R.id.ivApiHelpArrow);

        TextView tvAiStudioLink =
                findViewById(R.id.tvAiStudioLink);

        TextView tvDeveloperCredit =
                findViewById(R.id.tvDeveloperCredit);

        // Restore the user's local configuration when the settings screen opens.
        etApiKey.setText(
                prefs.getString(KEY_API_KEY, "")
        );

        etModelName.setText(
                prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL)
        );

        layoutApiHelpHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (layoutApiHelpContent.getVisibility() == View.GONE) {
                    layoutApiHelpContent.setVisibility(View.VISIBLE);
                    ivApiHelpArrow.setRotation(180f);
                } else {
                    layoutApiHelpContent.setVisibility(View.GONE);
                    ivApiHelpArrow.setRotation(0f);
                }
            }
        });

        tvAiStudioLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://aistudio.google.com/apikey")
                );
                startActivity(intent);
            }
        });

        String credit = "Made with 💙 by @hashierholmes";
        SpannableString creditText = new SpannableString(credit);

        int start = credit.indexOf("@hashierholmes");
        int end = start + "@hashierholmes".length();

        creditText.setSpan(
                new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://hashierholmes.vercel.app")
                        );
                        startActivity(intent);
                    }

                    @Override
                    public void updateDrawState(TextPaint ds) {
                        ds.setColor(Color.rgb(96, 165, 250));
                        ds.setUnderlineText(false);
                    }
                },
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tvDeveloperCredit.setText(creditText);
        tvDeveloperCredit.setMovementMethod(LinkMovementMethod.getInstance());
        tvDeveloperCredit.setHighlightColor(Color.TRANSPARENT);

        btnSaveApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String key = etApiKey.getText().toString().trim();
                String model = etModelName.getText().toString().trim();

                if (model.isEmpty()) {
                    model = DEFAULT_MODEL;
                }

                // SharedPreferences is sufficient for these small local settings.
                prefs.edit()
                        .putString(KEY_API_KEY, key)
                        .putString(KEY_MODEL_NAME, model)
                        .apply();

                Toast.makeText(
                        MainActivity.this,
                        "Settings Saved!",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        btnPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // The selection UI is drawn above other apps, so overlay access is required.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(MainActivity.this)) {

                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );

                    startActivity(intent);

                } else {

                    Toast.makeText(
                            MainActivity.this,
                            "Overlay permission already granted",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        });

        btnStartSnip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(MainActivity.this)) {

                    Toast.makeText(
                            MainActivity.this,
                            "Please grant overlay permission first!",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                if (prefs.getString(KEY_API_KEY, "").isEmpty()) {

                    Toast.makeText(
                            MainActivity.this,
                            "Please configure your Gemini API Key first!",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                Intent intent = new Intent(
                        MainActivity.this,
                        CapturePromptActivity.class
                );

                startActivity(intent);
            }
        });
    }
}