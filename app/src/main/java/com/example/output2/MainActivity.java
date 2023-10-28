package com.example.output2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private TextView transcriptTextView;
    private TextView booleanTextView;
    private static final String TAG = "ASR_APP";

    private static final String BASE_URL = "https://asr.iitm.ac.in/api";
    private OkHttpClient client;
    private MediaType mediaType;
    private String audioFileName;
    private String token;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private String audioFilePath = null;
    private MediaRecorder mediaRecorder = null;
    private Button startRecord = null;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        transcriptTextView = findViewById(R.id.transcriptTextView);
        booleanTextView = findViewById(R.id.booleanTextView);
        startRecord = findViewById(R.id.startRecord);

        client = new OkHttpClient();
        mediaType = MediaType.parse("multipart/form-data");
        audioFileName = "audio.mp3";

        final String email = "shashank.nagumantri@research.iiit.ac.in";
        final String password = "Chandra_0831";

        obtainToken(email, password);

        startRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopRecording();
                } else {
                    if (checkPermissions()) {
                        startRecording();
                    }
                }
            }
        });
    }

    private void startRecording() {
        audioFilePath = getExternalCacheDir().getAbsolutePath() + "/recorded_audio.mp3";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(audioFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            startRecord.setText("Stop Recording");
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
    }

    private void stopRecording() {
        if (isRecording) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            startRecord.setText("Start Recording");
            recordAndAnalyzeAudio();
        }
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return false;
        }
        return true;
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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                recordAndAnalyzeAudio();
                            }
                        });
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
        // Make sure you have already recorded audio and stored it in the 'audioFilePath'
        if (audioFilePath == null) {
            Log.e(TAG, "Audio file path is null.");
            return;
        }

        File audioFile = new File(audioFilePath);

        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file does not exist.");
            return;
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFileName, RequestBody.create(audioFile, mediaType))
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

                        boolean result = analyzeTranscript(extractedTranscript);

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
