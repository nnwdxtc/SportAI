package com.example.myapplication;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.example.poselandmarker.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.AssetDataSource;

public class activity_action_detail extends AppCompatActivity {

    private PlayerView playerView;
    private ProgressBar progressBar;
    private TextView tvError;
    private ExoPlayer player;

    private ActionStrategy actionStrategy;
    private String specificActionName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_action_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        actionStrategy = (ActionStrategy) getIntent().getSerializableExtra("action_strategy");
        specificActionName = getIntent().getStringExtra("specific_action_name");

        if (actionStrategy == null) {
            Toast.makeText(this, "错误：未接收到动作类型", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (specificActionName == null || specificActionName.isEmpty()) {
            specificActionName = actionStrategy.getActionName();
        }

        initViews();
        updateUI();
        setupClickListeners();
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);

        ((TextView) findViewById(R.id.tv_action_name)).setText(specificActionName);
        ((TextView) findViewById(R.id.tv_subtitle)).setText("基础" + actionStrategy.getActionName() + "动作");
    }

    private void updateUI() {
        ActionStrategy.OfficialVideo video = actionStrategy.getOfficialVideoByName(specificActionName);
        if (video != null) {
            initializePlayer(video.videoResName);
        } else {
            showError("未找到视频");
        }
    }

    private void initializePlayer(String videoFileName) {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    progressBar.setVisibility(View.GONE);
                } else if (state == Player.STATE_ENDED) {
                    player.seekTo(0);
                    player.play();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                progressBar.setVisibility(View.GONE);
                showError("播放失败");
            }
        });


        int resId = getResources().getIdentifier(videoFileName, "raw", getPackageName());

        if (resId != 0) {
            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + resId);
            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            showError("视频资源不存在: " + videoFileName);
        }
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_realtime_compare).setOnClickListener(v -> {
            Intent intent = new Intent(this, RealtimeActivity.class);
            intent.putExtra("action_strategy", actionStrategy);
            intent.putExtra("specific_action_name", specificActionName);
            startActivity(intent);
        });

        findViewById(R.id.btn_non_realtime_compare).setOnClickListener(v -> {
            Intent intent = new Intent(this, NonRealtimeActivity.class);
            intent.putExtra("action_strategy", actionStrategy);
            intent.putExtra("specific_action_name", specificActionName);
            startActivity(intent);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.play();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}