package com.amy.jpapp;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.TimedText;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;


/**
 * A simple {@link Fragment} subclass.
 */
public class AudioPlayerFragment extends Fragment implements MediaPlayer.OnTimedTextListener {

    private static final String TAG = "AudioPlayerFragment";
    private static Handler timedTextHandler = new Handler();
    private static Handler audioProgressHandler = new Handler();

    @BindView(R.id.item_image) ImageView mCurrentImage;
    @BindView(R.id.item_text) TextView mCurrentWord;
    @BindView(R.id.play_pause_button) ToggleButton mPlayPauseBtn;
    @BindView(R.id.audio_seek) SeekBar mAudioProgress;
//    @BindView(R.id.time_left_text) TextView mTimeLeftText;
//    @BindView(R.id.time_total_text) TextView mTimeTotalText;

    private MediaPlayer mMediaPlayer;

    public AudioPlayerFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_audio_player, container, false);
        ButterKnife.bind(this, rootView);
        mPlayPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playPauseBtnClicked();
            }
        });

        int itemIndex = Integer.valueOf(getActivity().getIntent().getStringExtra(Intent.EXTRA_TEXT));
        int imgId = getActivity().getResources().obtainTypedArray(R.array.image_ids).getResourceId(itemIndex, -1);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), imgId);
        mCurrentImage.setImageBitmap(bitmap);
        String audioFilename = getActivity().getResources().obtainTypedArray(R.array.audio_filename).getString(itemIndex);
        resetMediaPlayer(audioFilename);
        String timedtextFilename = getActivity().getResources().obtainTypedArray(R.array.srt_filename).getString(itemIndex);
        addTimedText(timedtextFilename);

        int totalruntime = mMediaPlayer.getDuration();
//        mTimeTotalText.setText(Integer.toString(totalruntime));
        mAudioProgress.setMax(totalruntime);
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if(mMediaPlayer != null){

                    mAudioProgress.post(new Runnable() {
                        @Override
                        public void run() {
                            int mCurrentPosition = mMediaPlayer.getCurrentPosition();
//                            mTimeLeftText.setText(Integer.toString(mCurrentPosition));
                            mAudioProgress.setProgress(mCurrentPosition);
                        }
                    });
                }
                audioProgressHandler.postDelayed(this, 300);
            }
        });


        return rootView;
    }


    private int findTrackIndexFor(int mediaTrackType, MediaPlayer.TrackInfo[] trackInfo) {
        int index = -1;
        for (int i = 0; i < trackInfo.length; i++) {
            if (trackInfo[i].getTrackType() == mediaTrackType) {
                return i;
            }
        }
        return index;
    }

    private String getSubtitleFile(int resId) {
        String fileName = getResources().getResourceEntryName(resId);
        File subtitleFile = getActivity().getFileStreamPath(fileName);
        if (subtitleFile.exists()) {
            Log.d(TAG, "Subtitle already exists");
            return subtitleFile.getAbsolutePath();
        }
        Log.d(TAG, "Subtitle does not exists, copy it from res/raw");

        // Copy the file from the res/raw folder to your app folder on the
        // device
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = getResources().openRawResource(resId);
            outputStream = new FileOutputStream(subtitleFile, false);
            copyFile(inputStream, outputStream);
            return subtitleFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeStreams(inputStream, outputStream);
        }
        return "";
    }

    private void copyFile(InputStream inputStream, OutputStream outputStream)
            throws IOException {
        final int BUFFER_SIZE = 1024;
        byte[] buffer = new byte[BUFFER_SIZE];
        int length = -1;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
    }

    // A handy method I use to close all the streams
    private void closeStreams(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable stream : closeables) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public void onTimedText(final MediaPlayer mp, final TimedText text) {
        if (text != null) {
            timedTextHandler.post(new Runnable() {
                @Override
                public void run() {
                    int seconds = mp.getCurrentPosition() / 1000;
                    Log.v(TAG,"[" + secondsToDuration(seconds) + "] "
                            + text.getText());
                    mCurrentWord.setText(text.getText());
                }
            });
        }
    }

    // To display the seconds in the duration format 00:00:00
    public String secondsToDuration(int seconds) {
        return String.format("%02d:%02d:%02d", seconds / 3600,
                (seconds % 3600) / 60, (seconds % 60), Locale.US);
    }

    private void resetMediaPlayer(String filename)
    {
        if(mMediaPlayer != null)
            mMediaPlayer.reset();

        int resid = getActivity().getResources().getIdentifier(filename, "raw", getActivity().getPackageName());

        mMediaPlayer = MediaPlayer.create(getActivity(), resid);
    }

    private void addTimedText(String filename)
    {
        int resid = getActivity().getResources().getIdentifier(filename, "raw", getActivity().getPackageName());
        try {
            mMediaPlayer.addTimedTextSource(getSubtitleFile(resid),
                    MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP);
            int textTrackIndex = findTrackIndexFor(
                    MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT, mMediaPlayer.getTrackInfo());
            if (textTrackIndex >= 0) {
                mMediaPlayer.selectTrack(textTrackIndex);
            } else {
                Log.w(TAG, "Cannot find text track!");
            }
            mMediaPlayer.setOnTimedTextListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void playPauseBtnClicked()
    {
        if(mPlayPauseBtn.isChecked())
        {
            if(mMediaPlayer != null)
                mMediaPlayer.start();
        }
        else
        {
            if(mMediaPlayer!=null)
                mMediaPlayer.pause();
        }
    }



}
