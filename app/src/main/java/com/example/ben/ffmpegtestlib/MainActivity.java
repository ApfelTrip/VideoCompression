package com.example.ben.ffmpegtestlib;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_TAKE_GALLERY_VIDEO_EXT = 1;
    private static final int REQUEST_READ_STORAGE_PERMISSION = 2;
    private static final int REQUEST_TAKE_GALLERY_VIDEO_INT = 3;
    private String selectedImagePath;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btn = (Button) findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStoragePerm();
            }
        });
    }

    private void requestStoragePerm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_STORAGE_PERMISSION);
        } else {
            loadBinary();
            openPicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_READ_STORAGE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openPicker();
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private void openPicker() {
        Intent intent;
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, REQUEST_TAKE_GALLERY_VIDEO_EXT);
        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI);
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, REQUEST_TAKE_GALLERY_VIDEO_INT);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO_EXT) {
                loadBinary();
                execute(getpath(data.getData(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI));
            } else if (requestCode == REQUEST_TAKE_GALLERY_VIDEO_INT) {
                loadBinary();
                execute(getpath(data.getData(), MediaStore.Video.Media.INTERNAL_CONTENT_URI));
            }

        }
    }

    public String getPath(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
        String filePath = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                filePath = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return filePath == null ? uri.getPath() : filePath;
    }

    public String getpath(Uri uri, Uri ContentUri) {
        Cursor cursor;
        int columnIndex;
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String wholeID = DocumentsContract.getDocumentId(uri);

            //get Id
            String id = wholeID.split(":")[1];
            String[] column = {MediaStore.Video.Media.DATA};
            String sel = MediaStore.Video.Media._ID + "=?";
            cursor = getContentResolver().query(ContentUri, column, sel, new String[]{id}, null);
            columnIndex = cursor.getColumnIndex(column[0]);
        } else {
            cursor = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
            columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
        }
        String filePath = "";
        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex);
        }
        cursor.close();
        return filePath;
    }

    private void execute(String path) {
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        final ProgressDialog dialog = new ProgressDialog(this);
        setProgressDialog(dialog, MediaPlayer.create(this, Uri.fromFile(new File(path))).getDuration());
        try {//see http://androidwarzone.blogspot.de/2011/12/ffmpeg4android.html for detailed Information
            String outputFile = "/sdcard/DCIM/Camera/out.mp4";
            String resolution = "720x720";
            String[] cmd = new String[]{"-y", "-i", path, "-strict", "experimental", "-s", resolution, "-r", "25", "-vcodec",
                    "mpeg4", "-b", "150k", "-ab", "48000", "-ac", "2", "-ar", "22050", "-preset", "ultrafast", outputFile};
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override
                public void onStart() {
                    dialog.show();
                }

                @Override
                public void onProgress(String message) {
                    dialog.setProgress(getProgress(message));
                }

                @Override
                public void onFailure(String message) {
                    Log.e("onFailure: ", message);
                }

                @Override
                public void onSuccess(String message) {
                    Log.e("onSuccess: ", message);
                }

                @Override
                public void onFinish() {
                    String s = System.currentTimeMillis() - MainActivity.this.startTime + "";
                    dialog.dismiss();
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
        }
    }

    private void setProgressDialog(ProgressDialog dialog, int duration) {
        dialog.setMax(duration);
        dialog.setMessage("Progress");
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setProgress(0);
        dialog.setCanceledOnTouchOutside(false);
    }

    private int getProgress(String message) {
        int start = message.indexOf("time=");
        int end = message.indexOf(" bitrate");
        if (start != -1 && end != -1) {
            String duration = message.substring(start + 5, end);//crop HH:mm:ss.SSS String
            if (duration != "") {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return (int) sdf.parse("1970-01-01 " + duration).getTime();

                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }

    private void loadBinary() {
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {

                @Override
                public void onStart() {
                }

                @Override
                public void onFailure() {
                    Log.e("onFailure: ", "");
                }

                @Override
                public void onSuccess() {
                    Log.e("onSuccess: ", "");
                }

                @Override
                public void onFinish() {
                }
            });
        } catch (FFmpegNotSupportedException e) {
            e.printStackTrace();
        }

    }
}
