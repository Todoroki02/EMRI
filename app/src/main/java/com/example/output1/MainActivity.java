package com.example.output1;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private TextView transcriptTextView;
    private TextView booleanTextView; // TextView to display the Boolean result
    private static final String TAG = "ASR_APP";

    private static final String BASE_URL = "https://asr.iitm.ac.in/api";
    private OkHttpClient client;
    private MediaType mediaType;
    private String audioFileName;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        transcriptTextView = findViewById(R.id.transcriptTextView);
        booleanTextView = findViewById(R.id.booleanTextView); // Initialize the booleanTextView

        client = new OkHttpClient();
        mediaType = MediaType.parse("multipart/form-data");
        audioFileName = "audio.mp3";

        // Your email and password
        final String email = "shashank.nagumantri@research.iiit.ac.in";
        final String password = "Chandra_0831";

        obtainToken(email, password);
    }

    private void obtainToken(final String email, final String password) {
        RequestBody tokenRequestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("email", email)
                .addFormDataPart("password", password)
                .build();

        Request tokenRequest = new Request.Builder()
                .url(BASE_URL + "/accounts/login/")
                .post(tokenRequestBody)
                .build();

        client.newCall(tokenRequest).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseData = response.body().string();
                        JSONObject tokenData = new JSONObject(responseData);
                        token = tokenData.optString("token");

                        // Once you have the token, proceed to record and analyze audio
                        recordAndAnalyzeAudio();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error parsing token response: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Token request failed with code: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Token request failed: " + e.getMessage());
            }
        });
    }

    private void recordAndAnalyzeAudio() {
        // Replace this section with your logic to record and analyze audio.
        // Make sure to use the `token` variable for authentication.

        // Example:
        int audioFileResourceId = R.raw.no;

        BufferedInputStream bufferedInputStream = new BufferedInputStream(getResources().openRawResource(audioFileResourceId));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            int bytesRead;
            byte[] buffer = new byte[1024];
            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error reading audio file: " + e.getMessage());
        } finally {
            try {
                bufferedInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error closing input stream: " + e.getMessage());
            }
        }

        byte[] audioData = byteArrayOutputStream.toByteArray();

        if (audioData.length > 0) {
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", audioFileName, RequestBody.create(audioData, mediaType))
                    .addFormDataPart("language", "english")
                    .addFormDataPart("vtt", "true")
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/asr/")
                    .addHeader("Authorization", "Token " + token)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            String responseData = response.body().string();
                            Log.d(TAG, "ASR Response Data: " + responseData);

                            JSONObject jsonObject = new JSONObject(responseData);
                            final String extractedTranscript = jsonObject.optString("transcript");
                            Log.d(TAG, "Extracted Transcript: " + extractedTranscript);

                            boolean result = analyzeTranscript(extractedTranscript); // Analyze the transcript

                            runOnUiThread(() -> {
                                transcriptTextView.setText(getString(R.string.transcript_placeholder, extractedTranscript));
                                booleanTextView.setText("Result: " + result);
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "ASR Request failed with code: " + response.code());
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ASR Request failed: " + e.getMessage());
                }
            });
        }
    }

    // Function to analyze the transcript and return a Boolean value
    private boolean analyzeTranscript(String transcript) {
        if (transcript != null) {
            if (transcript.toLowerCase().contains("yes")) {
                return true;
            } else if (transcript.toLowerCase().contains("no")) {
                return false;
            }
        }
        return false;
    }
}
