package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WdgObservationUploader extends ObservationUploader {

    private static final String UPLOAD_URL =
            "https://wdgwars.pl/api/v2/upload-csv";

    public WdgObservationUploader(
            final FragmentActivity context,
            final DatabaseHelper dbHelper,
            final ApiListener listener,
            boolean justWriteFile,
            boolean writeEntireDb,
            boolean writeRun
    ) {
        super(context, dbHelper, listener, justWriteFile, writeEntireDb, writeRun);
    }

    @Override
    protected void doUpload(final Bundle bundle) {

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.MINUTES)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        try {

            ObservationUploader.CountStats countStats =
                    new ObservationUploader.CountStats();

            /*
             * 1. Generate CSV in memory
             */
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            long maxId = writeFile(baos, bundle, countStats);

            byte[] csvData = baos.toByteArray();
            baos.close();

            Logging.info("CSV size bytes: " + csvData.length);

            if (countStats.lineCount == 0) {
                status = Status.EMPTY_FILE;
                sendBundledMessage(status.ordinal(), bundle);
                return;
            }

            sendBundledMessage(Status.UPLOADING.ordinal(), bundle);

            /*
             * 2. API key
             */
            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PreferenceKeys.SHARED_PREFS,
                            0
                    );

            String apiKey =
                    prefs.getString(
                            PreferenceKeys.PREF_WDG_API_KEY,
                            ""
                    );

            if (apiKey.isEmpty()) {
                status = Status.BAD_LOGIN;
                bundle.putString(
                        BackgroundGuiHandler.ERROR,
                        "Missing WDG Wars API key"
                );
                sendBundledMessage(status.ordinal(), bundle);
                return;
            }

            /*
             * 3. Upload (async job request)
             */
            RequestBody fileBody =
                    RequestBody.create(
                            csvData,
                            MediaType.get("text/csv")
                    );

            MultipartBody requestBody =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                    "file",
                                    "wigle-upload.csv",
                                    fileBody
                            )
                            .build();

            Request request =
                    new Request.Builder()
                            .url(UPLOAD_URL)
                            .addHeader("X-API-Key", apiKey)
                            .post(requestBody)
                            .build();

            Logging.info("Submitting upload job...");

            Response response =
                    client.newCall(request).execute();

            String responseBody =
                    response.body() != null
                            ? response.body().string()
                            : "";

            Logging.info("Upload response: " + responseBody);

            if (response.code() != 202) {
                status = Status.FAIL;
                bundle.putString(
                        BackgroundGuiHandler.ERROR,
                        "Upload failed: " + response.code() + " " + responseBody
                );
                sendBundledMessage(status.ordinal(), bundle);
                return;
            }

            JSONObject json = new JSONObject(responseBody);

            int jobId = json.optInt("job_id", -1);

            if (jobId == -1) {
                status = Status.FAIL;
                bundle.putString(
                        BackgroundGuiHandler.ERROR,
                        "Missing job_id: " + responseBody
                );
                sendBundledMessage(status.ordinal(), bundle);
                return;
            }

            Logging.info("WDG job created: " + jobId);

            /*
             * 4. Poll job status
             */
            boolean done = false;
            JSONObject finalResult = null;

            for (int i = 0; i < 120; i++) { // ~4 minutes max
                Thread.sleep(2000);

                Request pollRequest =
                        new Request.Builder()
                                .url("https://wdgwars.pl/api/v2/upload-job/" + jobId)
                                .addHeader("X-API-Key", apiKey)
                                .build();

                Response pollResponse =
                        client.newCall(pollRequest).execute();

                String pollBody =
                        pollResponse.body() != null
                                ? pollResponse.body().string()
                                : "";

                JSONObject pollJson = new JSONObject(pollBody);

                String state = pollJson.optString("status");

                Logging.info("WDG job status: " + state);

                if ("done".equals(state)) {
                    done = true;
                    finalResult = pollJson.optJSONObject("result");
                    break;
                }

                if ("failed".equals(state)) {
                    status = Status.FAIL;
                    bundle.putString(
                            BackgroundGuiHandler.ERROR,
                            "WDG processing failed: " + pollBody
                    );
                    sendBundledMessage(status.ordinal(), bundle);
                    return;
                }
            }

            if (!done) {
                status = Status.FAIL;
                bundle.putString(
                        BackgroundGuiHandler.ERROR,
                        "WDG job timeout (processing still running)"
                );
                sendBundledMessage(status.ordinal(), bundle);
                return;
            }

            /*
             * 5. Success
             */
            Logging.info("WDG upload complete");

            status = Status.SUCCESS;

            SharedPreferences.Editor editor = prefs.edit();

            editor.putLong(
                    PreferenceKeys.PREF_DB_MARKER,
                    maxId
            );

            editor.putLong(
                    PreferenceKeys.PREF_MAX_DB,
                    maxId
            );

            editor.apply();

            bundle.putString(
                    BackgroundGuiHandler.ERROR,
                    finalResult != null ? finalResult.toString() : "ok"
            );

            sendBundledMessage(status.ordinal(), bundle);

        } catch (IOException ex) {

            Logging.error("WDG upload IO error", ex);

            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, ex.toString());
            sendBundledMessage(status.ordinal(), bundle);

        } catch (Exception ex) {

            Logging.error("WDG upload exception", ex);

            MainActivity.writeError(this, ex, context);

            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, ex.toString());
            sendBundledMessage(status.ordinal(), bundle);
        }
    }
}