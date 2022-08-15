package com.wolftech.videoreader.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.material.snackbar.Snackbar;
import com.wolftech.videoreader.R;
import com.wolftech.videoreader.services.CustomOnScaleGestureListener;
import com.wolftech.videoreader.services.PinchListener;
import com.wolftech.videoreader.util.Widget;

import java.util.ArrayList;

public class PrincipalActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_GALLERY_VIDEO = 1;
    private final int PETICION_PERMISOS = 0;
    private Context mContext;

    // Player variables
    private ExoPlayer player;
    private PlayerView playerView;
    private ActionBar ac;
    private static boolean floating = false;
    private TextView tv;
    private ImageView mExoLock;
    private LinearLayout mExoControls;
    private boolean locked = false;
    private ScaleGestureDetector scaleGestureDetector;

    // Source data
    private String url, title;

    //PIP Mode
    private BroadcastReceiver mReceiver;
    private static final int REQUEST_CODE=101;
    public static int permissionCheck;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = PrincipalActivity.this;
        /* Establecemos los flags de la Pantalla para que la aplicación se ejecute en pantalla completa
			de modo que se oculte la barra de notificaciones. */
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        zoomVideo();

        Toolbar toolbar = findViewById(R.id.toolbar);
        ImageView mExoPiP = findViewById(R.id.exo_pip);
        ImageView mExoRotate = findViewById(R.id.exo_rotate);

        playerView = findViewById(R.id.playerView);
        mExoLock = findViewById(R.id.exo_lock);
        mExoControls = findViewById(R.id.exo_controls);
		
		/* Creamos la función detectora de gestos para poder acercar o alejar la pantalla pellizcandola.*/
        scaleGestureDetector = new ScaleGestureDetector(mContext, new CustomOnScaleGestureListener(
                new PinchListener() {
                    @Override
                    public void onZoomOut() {
                        if(!locked && playerView != null){
                            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);//MODE_FILL
                            showSnack(getString(R.string.zoom_out));
                        }
                    }

                    @Override
                    public void onZoomIn() {
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                        showSnack(getString(R.string.zoom_in));
                    }
                }
        ));


		// Creamos el hilo de escucha del modo picture in picture
        mExoPiP.setOnClickListener(view -> {
            try{
                enterPIPMode();
            }catch(Exception ignored){}
        });

		// Creamos el hilo de escucha para rotar la pantalla de portrait a landscape y a la inversa.
        mExoRotate.setOnClickListener(view -> {
            int orientation = getResources().getConfiguration().orientation;
            if(orientation == Configuration.ORIENTATION_PORTRAIT)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            else
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        });

        // Set toolbar
        setSupportActionBar(toolbar);
        startPlaying();
    }

	/**
		Mostramos la barra/UI de manejo del vídeo y la barra de tiempo.
		@variable msg String
	*/
    private void showSnack(String msg){
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinator);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_LONG);
        View view = snackbar.getView();
        view.setBackgroundColor(Color.parseColor("#65888888"));

        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        view.setLayoutParams(params);

        snackbar.show();
    }

	/**
		Inicializamos la reproducción del vídeo
		@variable url String: Url local donde se encuentra el vídeo que vamos a reproducir.
	*/
    private void startPlaying(){
        if (url ==null)
            seleccionarVideo();
        else {
            retrieveExtras();
            openPlayer();
        }
    }

    private void retrieveExtras(){
        try{
            Uri data = getIntent().getData();
            url = data.toString();
            Cursor returnCursor = getContentResolver().query(data, null, null, null,null);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();

            title = returnCursor.getString(nameIndex);
        }catch (Exception e){
            Toast.makeText(mContext, getString(R.string.error_ocurr), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openPlayer() {
        ExoPlayer.Builder builder = new ExoPlayer.Builder(this);
        builder.setSeekBackIncrementMs(10000);
        builder.setSeekForwardIncrementMs(10000);
        //playerView.setFasForwardIncrementMs();
        // playerView.setFasRewindIncrementMs();

        player= builder.build();

        mExoLock.setOnClickListener(view -> {
            locked = !locked;
            mExoLock.setImageDrawable(ContextCompat.getDrawable(mContext,
                    locked ? R.drawable.ic_action_lock : R.drawable.ic_action_lockopen));
            lockPlayerControls(locked);
        });
        setActionBar();

        try{
            changeBarSize();
        }catch (Exception ignored){}

        init();
    }

    private void setActionBar() {
        ac = getSupportActionBar();
        if(ac != null)
            ac.setDisplayHomeAsUpEnabled(false);
    }

    private void changeBarSize(){
        tv = new TextView(mContext);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );

        tv.setLayoutParams(lp);
        tv.setText(getString(R.string.m_load));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);

        ac.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        ac.setCustomView(tv);
        ac.setDisplayHomeAsUpEnabled(true);
    }

    private void lockPlayerControls(boolean lock){
        mExoControls.setVisibility(lock ? View.INVISIBLE : View.VISIBLE);
        showActionBar(!lock);
    }

    private void showActionBar(boolean show){
        if (ac != null){
            if (show){
                if(!ac.isShowing())
                    ac.show();
                else if(ac.isShowing())
                    ac.hide();
            }
        }
    }

    private void zoomVideo(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            Window window = getWindow();
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void init(){
        DefaultLoadControl.Builder bl = new DefaultLoadControl.Builder();
        bl.setBufferDurationsMs(3500, 150000, 2500,3000);

        SimpleExoPlayer.Builder builder = new SimpleExoPlayer.Builder(mContext,
                new DefaultRenderersFactory(mContext))
                .setLoadControl(bl.createDefaultLoadControl());
				
		player=builder.build();
		player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING); //C.
		playerView.setPlayer(player);
        playerView.setOnTouchListener((view, notionEvent) -> {
            scaleGestureDetector.onTouchEvent(notionEvent);
            return false;
        });
		
		playerView.setKeepScreenOn(true); //Mantenemos la pantalla encendida
		playerView.requestFocus();
		playerView.setControllerVisibilityListener(visibility -> {
            if (visibility==View.VISIBLE){
				if(!locked)
					showActionBar(true);
			}else showActionBar(false);
        });
		
		DefaultDataSourceFactory defaultDataSource = new DefaultDataSourceFactory(mContext,"Android");
		MediaSource mediaSource = new ProgressiveMediaSource.Factory(defaultDataSource)
				.createMediaSource(MediaItem.fromUri(Uri.parse(url)));
		
		player.prepare(mediaSource);
		player.setPlayWhenReady(true);
		
		MediaSessionCompat mediaSession = new MediaSessionCompat(mContext, getPackageName());
		MediaSessionConnector mediaSessionConnector = new MediaSessionConnector(mediaSession);
		mediaSessionConnector.setPlayer(player);
		mediaSession.setActive(true);
		
		tv.setText(title);
		if (!playerView.getUseController())
			playerView.setUseController(true);
	}

    private void releasePlayer(boolean finish) {
        if (player != null){
            if (finish){
                player.release();
                playerView.setPlayer(null);
                finish();
            }else pausePlayer();
        }else finish();
    }

    private void enterPIPMode(){
        if(Widget.canPip(mContext)){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                Rational aspectRatio = new Rational(playerView.getWidth(), playerView.getHeight());
                PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                params.setAspectRatio(aspectRatio);
                enterPictureInPictureMode(params.build());
            }else enterPictureInPictureMode();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus)
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public void createPipAction(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            final ArrayList<RemoteAction> actions = new ArrayList<>();
            Intent actionItent = new Intent("com.wolftech.videoreader.PLAY_PAUSE");

            final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext,
                    REQUEST_CODE, actionItent, PendingIntent.FLAG_IMMUTABLE);

            Icon icon = Icon.createWithResource(mContext,
                    player != null && player.getPlayWhenReady() ? R.drawable.ic_action_pause : R.drawable.ic_action_play);

            RemoteAction remoteAction = new RemoteAction(icon, "Player", "Play", pendingIntent);

            actions.add(remoteAction);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setActions(actions)
                    .build();
            setPictureInPictureParams(params);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if ((keyCode ==  KeyEvent.KEYCODE_BACK ||
                keyCode ==  KeyEvent.KEYCODE_MENU ||
                keyCode ==  KeyEvent.KEYCODE_HOME) && locked){
            return false;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
                                              Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode){
            startPlayer();
            playerView.setUseController(false);
            floating = true;
            showActionBar(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.wolftech.videoreader.PLAY_PAUSE");
                mReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (player !=  null){
                            boolean state = !player.getPlayWhenReady();
                            player.setPlayWhenReady(state);
                            createPipAction();
                        }
                    }
                };

                registerReceiver(mReceiver, filter);
                createPipAction();
            }
        }else {
            playerView.setUseController(true);
            floating = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mReceiver != null)
                unregisterReceiver(mReceiver);
        }
    }


    private void pausePlayer() {
        if (player != null){
            try{
                if(player.getPlaybackState() == Player.STATE_READY && player.getPlayWhenReady())
                    player.setPlayWhenReady(false);
            }catch (Exception ignore){}
        }
    }

    private void startPlayer() {
        if (player != null){
            try{
                if(player.getPlaybackState() == Player.STATE_READY && player.getPlayWhenReady())
                    player.setPlayWhenReady(true);
            }catch (Exception ignore){}
        }
    }

    @Override
    protected void onPause() {
        showActionBar(true);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode() && floating)
            releasePlayer(false);
        else pausePlayer();

        super.onPause();
    }

    @Override
    protected void onStop() {
        if (floating)
            releasePlayer(true);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode() && floating)
            releasePlayer(false);
        else pausePlayer();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        releasePlayer(true);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPlayer();
    }

    @Override
    public void onBackPressed() {
        releasePlayer(true);
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home){
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            //if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                assert data != null;
                Uri selectedImageUri = data.getData();
                url = selectedImageUri.toString();
                openPlayer();
            //}
        }
    }

    private void seleccionarVideo() {
        permissionCheck= ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                //intent.setType("video/");
                startActivityForResult(intent, REQUEST_TAKE_GALLERY_VIDEO);
            }
        } else
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,}, PETICION_PERMISOS);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PETICION_PERMISOS) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
                onRestart();
            }
        }
    }
}