package hh.screenseek.app;

import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal REST client for sending selected screenshots to Gemini.
 *
 * The class uses Android/Java networking and JSON APIs directly to keep
 * ScreenSeek's dependency footprint small.
 */
public class GeminiApiHelper {

    public interface ApiCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    /**
     * Compresses the selected bitmap and sends it as an inline JPEG image.
     * Network work runs off the main thread so capture UI stays responsive.
     */
    public static void askGemini(
            final String apiKey,
            final String modelName,
            final Bitmap bitmap,
            final ApiCallback callback
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    bitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            80,
                            outputStream
                    );

                    String base64Image = Base64.encodeToString(
                            outputStream.toByteArray(),
                            Base64.NO_WRAP
                    );

                    JSONObject jsonPayload = new JSONObject();

                    JSONObject systemInstruction = new JSONObject();
                    JSONArray systemParts = new JSONArray();

                    JSONObject systemTextPart = new JSONObject();

                    systemTextPart.put(
                            "text",
                                "You are ScreenSeek, a visual assistant.\n\n" +
                                "Analyze the captured content and provide the most useful response based only on what is visible.\n\n" +
                                "If it contains a question, answer it directly.\n" +
                                "If it contains a problem, solve it.\n" +
                                "If it contains code or an error, explain the issue and provide a practical fix.\n" +
                                "If it contains a chart, table, diagram, or other visual information, interpret it.\n" +
                                "If it contains instructions, explain or help complete them.\n\n" +
                                "Do not ask what the user wants you to do.\n" +
                                "Do not unnecessarily describe what you see.\n" +
                                "Do not invent missing information, titles, categories, context, or details.\n" +
                                "Do not provide unnecessary background information.\n" +
                                "Do not mention the screenshot, image, snippet, or capture unless necessary.\n" +
                                "Keep responses concise, direct, accurate, and useful."
                    );

                    systemParts.put(systemTextPart);
                    systemInstruction.put("parts", systemParts);
                    jsonPayload.put("system_instruction", systemInstruction);

                    JSONObject generationConfig = new JSONObject();
                    generationConfig.put("maxOutputTokens", 2000);
                    jsonPayload.put("generationConfig", generationConfig);

                    JSONArray contents = new JSONArray();

                    JSONObject contentObj = new JSONObject();
                    JSONArray parts = new JSONArray();

                    JSONObject textPart = new JSONObject();
                    textPart.put(
                            "text",
                            "Analyze this and help me."
                    );

                    parts.put(textPart);

                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mime_type", "image/jpeg");
                    inlineData.put("data", base64Image);

                    JSONObject imagePart = new JSONObject();
                    imagePart.put("inline_data", inlineData);

                    parts.put(imagePart);

                    contentObj.put("parts", parts);
                    contents.put(contentObj);

                    jsonPayload.put("contents", contents);

                    String targetModel =
                            (modelName != null && !modelName.trim().isEmpty())
                                    ? modelName.trim()
                                    : "gemini-3.5-flash";

                    String endpoint =
                            "https://generativelanguage.googleapis.com/v1beta/models/"
                                    + targetModel
                                    + ":generateContent?key="
                                    + apiKey;

                    URL url = new URL(endpoint);
                    HttpURLConnection conn =
                            (HttpURLConnection) url.openConnection();

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty(
                            "Content-Type",
                            "application/json"
                    );
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(25000);

                    OutputStream os = conn.getOutputStream();

                    os.write(
                            jsonPayload
                                    .toString()
                                    .getBytes("UTF-8")
                    );

                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();

                    BufferedReader reader;

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(
                                new InputStreamReader(
                                        conn.getInputStream()
                                )
                        );
                    } else {
                        reader = new BufferedReader(
                                new InputStreamReader(
                                        conn.getErrorStream()
                                )
                        );
                    }

                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    reader.close();
                    conn.disconnect();

                    if (responseCode == HttpURLConnection.HTTP_OK) {

                        JSONObject root =
                                new JSONObject(response.toString());

                        JSONArray candidates =
                                root.getJSONArray("candidates");

                        JSONObject candidate =
                                candidates.getJSONObject(0);

                        JSONObject content =
                                candidate.getJSONObject("content");

                        JSONArray responseParts =
                                content.getJSONArray("parts");

                        final String resultText =
                                responseParts
                                        .getJSONObject(0)
                                        .getString("text");

                        callback.onSuccess(resultText);

                    } else {

                        callback.onError(
                                "API Error ("
                                        + responseCode
                                        + "): "
                                        + response.toString()
                        );
                    }

                } catch (Exception e) {

                    callback.onError(
                            "Request Failed: "
                                    + e.getMessage()
                    );
                }
            }
        }).start();
    }
}