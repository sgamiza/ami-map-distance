package com.example.mapdistance;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements TrackEngine.Listener {
    public static final String EXTRA_CONFIRM_STOP = "confirm_stop";
    private TrackEngine engine;
    private WebView web;
    private View paneMeasure;
    private View paneHistory;
    private View paneStats;
    private View paneSettings;
    private LinearLayout statsContainer;
    private Spinner spinnerStats;
    private TextView txtStatsVersion;
    private TextView emptyStats;
    private WebView webCharts;
    private MaterialButtonToggleGroup statsKind;
    private View statsFilterRow;
    private boolean chartsReady;
    private boolean statsHasData;
    private boolean chartFullscreen;
    private int savedSysUi = Integer.MIN_VALUE;
    private final Runnable resizeChartOverlay = () -> {
        if (webCharts != null && chartFullscreen && chartsReady) {
            webCharts.evaluateJavascript(
                    "if(window.paintOverlay)paintOverlay()", null);
        }
    };
    private View historyBanner;
    private View cardStats;
    private View statsDetail;
    private View rowStatsFold;
    private TextView txtStatsFold;
    private boolean measureStatsOpen = true;
    private Toolbar toolbar;
    private TextView txtGps;
    private TextView txtNet;
    private TextView txtPlace;
    private TextView txtDistance;
    private TextView txtTime;
    private TextView txtPace;
    private TextView txtSpeed;
    private TextView txtAvg;
    private TextView txtStride;
    private TextView txtAuto;
    private TextView txtExtra;
    private TextView txtHistoryBanner;
    private TextView txtHistoryEmpty;
    private Button btnPrimary;
    private Button btnStop;
    private Button btnMark;
    private Button btnSatellite;
    private MaterialButton btnSpan;
    private Button btnSpanHist;
    private boolean spanPicking;
    private int spanMarkA = -1;
    private int spanPointA = -1;
    private TextView txtMarks;
    private MaterialButtonToggleGroup tabs;
    private MaterialButton btnMode;
    private String selectedMode = TrackEngine.MODE_WALK;
    private ListView listHistory;
    private EditText editHistoryQ;
    private TextView txtHistoryCount;
    private MaterialButton sortTime;
    private MaterialButton sortKm;
    private MaterialButton sortStepsBtn;
    private MaterialButton sortSpeedBtn;
    private SwitchCompat swAuto;
    private SwitchCompat swAutoMark;
    private SwitchCompat swAutoStill;
    private SwitchCompat swWaitGps;
    private SwitchCompat swRecordGpsLost;
    private SwitchCompat swHistClean;
    private SwitchCompat swHistCleanSettings;
    private MaterialButtonToggleGroup speedUnitGroup;
    private EditText editWeight;
    private EditText editAccuracy;
    private EditText editKey;
    private EditText editAutoMarkMin;
    private EditText editGoalKm;
    private EditText editGoalSteps;
    private EditText editEveryKm;
    private EditText editEverySteps;
    private EditText editTodayGoal;
    private SwitchCompat swGoalKm;
    private SwitchCompat swGoalSteps;
    private SwitchCompat swEveryKm;
    private SwitchCompat swEverySteps;
    private SwitchCompat swTodayGoal;
    private AlertKind pickingKind;
    private Ringtone tonePreview;
    private final Runnable stopTonePreview = this::stopTonePreview;
    private final java.util.EnumMap<AlertKind, SoundRow> soundRows =
            new java.util.EnumMap<>(AlertKind.class);
    private TextView txtCache;
    private TextView txtKeepAlive;
    private TextView txtKeepAliveBanner;
    private Button btnAutoStartSys;
    private Button btnOem;
    private Button btnKeepAliveDone;
    private TextView txtSync;
    private NearbySession nearbySession;
    private AlertDialog nearbyDialog;
    private AlertDialog backupDialog;
    private Runnable backupPanelRefresh;
    private boolean pickTreeForBackup;
    private final List<NearbySession.Peer> nearbyPeers = new ArrayList<>();

    private boolean mapReady;
    private boolean follow = true;
    private boolean satellite;
    private boolean previewingHistory;
    private TrackSession previewRaw;
    private boolean editingPoints;
    private Button btnHopLimit;
    private Button btnEditPts;
    private Button btnSuspectPts;
    private Button btnRangePts;
    private Button btnRestorePts;
    private int focusHopIndex = -1;
    private int rangeAnchor = -1;
    private boolean rangeMode;
    private int drawnPoints;
    private int drawnMarks;
    private String lastPlace = "";
    private final List<TrackSession> historyAll = new ArrayList<>();
    private final List<TrackSession> history = new ArrayList<>();
    private HistoryAdapter historyAdapter;
    private String historySort = HistoryQuery.SORT_TIME;
    private boolean historySortDesc = true;
    private LocationManager previewLocations;
    private boolean wantAutoStart;
    private boolean pendingStart;
    private ConnectivityManager connectivity;
    private boolean netCbRegistered;
    private AlertDialog downloadDlg;
    private AlertDialog keepAliveDlg;
    private boolean keepAliveReady;
    private boolean keepAlivePromptedThisProcess;
    private boolean awaitingKeepAliveReturn;
    private final java.util.concurrent.atomic.AtomicBoolean downloadCancel =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final ConnectivityManager.NetworkCallback netCb =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> applyNetwork(true));
                }

                @Override
                public void onLost(Network network) {
                    runOnUiThread(() -> applyNetwork(Net.isOnline(MainActivity.this)));
                }
            };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissions);

    private final ActivityResultLauncher<String[]> nearbyPerms =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> openNearbyDialog());

    private final ActivityResultLauncher<Intent> pickTree =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) {
                    return;
                }
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                try {
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (SecurityException e) {
                    Toast.makeText(this, "无法保持文件夹权限", Toast.LENGTH_LONG).show();
                    return;
                }
                boolean forBackup = pickTreeForBackup;
                pickTreeForBackup = false;
                Backups.setFolder(this, uri);
                if (forBackup) {
                    refreshBackupPanel();
                    loadSettingsUi();
                    Toast.makeText(this, "备份和文件夹同步都用这个目录：\n"
                            + Backups.locationLabel(this), Toast.LENGTH_LONG).show();
                    return;
                }
                String sync = SyncPack.syncFolder(this, engine.store(), true);
                loadSettingsUi();
                refreshHistory();
                Toast.makeText(this, "已记住文件夹。各手机选同一个目录即可。\n" + sync,
                        Toast.LENGTH_LONG).show();
            });

    private final ActivityResultLauncher<Intent> pickRingtone =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null
                        || pickingKind == null) {
                    return;
                }
                Intent data = result.getData();
                Uri uri = data.getData();
                if (uri == null) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
                    } else {
                        uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    }
                }
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                    applyPickedTone(pickingKind, uri, ringtoneTitle(uri));
                }
            });

    private final ActivityResultLauncher<Intent> btDiscoverable =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        engine = TrackEngine.get(this);
        bindViews();
        setupMap();
        setupTabs();
        setupButtons();
        loadSettingsUi();
        refreshHistory();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (spanPicking) {
                    setSpanPicking(false);
                    return;
                }
                if (previewingHistory && editingPoints) {
                    setEditingPoints(false);
                    return;
                }
                if (previewingHistory) {
                    exitPreview(true);
                    return;
                }
                if (webCharts != null && webCharts.getVisibility() == View.VISIBLE && chartsReady) {
                    webCharts.evaluateJavascript(
                            "(function(){return (window.closeOverlay && window.closeOverlay()) ? true : false;})()",
                            value -> {
                                if (value != null && value.contains("true")) {
                                    return;
                                }
                                tabs.check(R.id.tab_measure);
                            });
                    return;
                }
                if (tabs.getCheckedButtonId() != R.id.tab_measure) {
                    tabs.check(R.id.tab_measure);
                    return;
                }
                if (engine.isActive()) {
                    moveTaskToBack(true);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        engine.addListener(this);
        registerNet();
        maybePrivacyThenStart();
        applyNetwork(Net.isOnline(this));
        txtExtra.setOnClickListener(v -> onStepsClicked());
        if (txtStride != null) {
            txtStride.setOnClickListener(v -> onStepsClicked());
        }
        if (txtAuto != null) {
            txtAuto.setOnClickListener(v -> showAutoHelp());
        }
        handleNotificationIntent(getIntent());
        backupIfDue();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onDestroy() {
        engine.removeListener(this);
        stopPreviewLocation();
        unregisterNet();
        downloadCancel.set(true);
        if (keepAliveDlg != null && keepAliveDlg.isShowing()) {
            keepAliveDlg.dismiss();
        }
        if (nearbySession != null) {
            nearbySession.stop();
            nearbySession = null;
        }
        if (webCharts != null) {
            webCharts.removeCallbacks(resizeChartOverlay);
            leaveChartFullscreen();
            android.view.ViewParent parent = webCharts.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webCharts);
            }
            webCharts.destroy();
            webCharts = null;
        }
        stopTonePreview();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!engine.isActive()) {
            startPreviewLocation();
        }
        applyKeepScreen();
        applyNetwork(Net.isOnline(this));
        StepSensor.get(this).start();
        AlertNotify.stop(this);
        refreshStride(engine.session());
        if (keepAliveReady) {
            paneMeasure.post(this::onKeepAliveResume);
        }
        if (pendingStart && !engine.isActive() && hasLocationPermission() && systemLocationOn()) {
            pendingStart = false;
            beginMeasure(wantAutoStart);
            Toast.makeText(this, "已打开定位，开始测量", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreviewLocation();
        if (paneSettings != null && paneSettings.getVisibility() == View.VISIBLE) {
            persistGoals();
            persistAutoMark();
        }
        backupIfDue();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        web = findViewById(R.id.web_map);
        paneMeasure = findViewById(R.id.pane_measure);
        paneHistory = findViewById(R.id.pane_history);
        paneStats = findViewById(R.id.pane_stats);
        paneSettings = findViewById(R.id.pane_settings);
        statsContainer = findViewById(R.id.stats_container);
        spinnerStats = findViewById(R.id.spinner_stats);
        txtStatsVersion = findViewById(R.id.text_stats_version);
        emptyStats = findViewById(R.id.empty_stats);
        webCharts = findViewById(R.id.web_charts);
        statsKind = findViewById(R.id.stats_kind);
        statsFilterRow = findViewById(R.id.stats_filter_row);
        historyBanner = findViewById(R.id.history_banner);
        if (historyBanner != null) {
            final int padL = historyBanner.getPaddingLeft();
            final int padT = historyBanner.getPaddingTop();
            final int padR = historyBanner.getPaddingRight();
            final int padB = historyBanner.getPaddingBottom();
            int minNav = Math.round(48 * historyBanner.getResources().getDisplayMetrics().density);
            historyBanner.setPadding(padL, padT, padR, padB + minNav);
            ViewCompat.setOnApplyWindowInsetsListener(historyBanner, (v, insets) -> {
                int nav = Math.max(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom, minNav);
                v.setPadding(padL, padT, padR, padB + nav);
                return insets;
            });
            ViewCompat.requestApplyInsets(historyBanner);
        }
        cardStats = findViewById(R.id.card_stats);
        statsDetail = findViewById(R.id.stats_detail);
        rowStatsFold = findViewById(R.id.row_stats_fold);
        txtStatsFold = findViewById(R.id.txt_stats_fold);
        measureStatsOpen = Prefs.measureStatsOpen(this);
        txtGps = findViewById(R.id.txt_gps);
        txtNet = findViewById(R.id.txt_net);
        txtPlace = findViewById(R.id.txt_place);
        txtDistance = findViewById(R.id.txt_distance);
        txtTime = findViewById(R.id.txt_time);
        txtPace = findViewById(R.id.txt_pace);
        txtSpeed = findViewById(R.id.txt_speed);
        txtAvg = findViewById(R.id.txt_avg);
        txtStride = findViewById(R.id.txt_stride);
        txtAuto = findViewById(R.id.txt_auto);
        txtExtra = findViewById(R.id.txt_extra);
        txtMarks = findViewById(R.id.txt_marks);
        txtHistoryBanner = findViewById(R.id.txt_history_banner);
        btnHopLimit = findViewById(R.id.btn_hop_limit);
        btnEditPts = findViewById(R.id.btn_edit_pts);
        btnSpan = findViewById(R.id.btn_span);
        btnSpanHist = findViewById(R.id.btn_span_hist);
        btnSuspectPts = findViewById(R.id.btn_suspect_pts);
        btnRangePts = findViewById(R.id.btn_range_pts);
        btnRestorePts = findViewById(R.id.btn_restore_pts);
        txtHistoryEmpty = findViewById(R.id.txt_history_empty);
        btnPrimary = findViewById(R.id.btn_primary);
        btnStop = findViewById(R.id.btn_stop);
        btnMark = findViewById(R.id.btn_mark);
        btnSatellite = findViewById(R.id.btn_satellite);
        tabs = findViewById(R.id.tabs);
        btnMode = findViewById(R.id.btn_mode);
        listHistory = findViewById(R.id.list_history);
        editHistoryQ = findViewById(R.id.edit_history_q);
        txtHistoryCount = findViewById(R.id.txt_history_count);
        sortTime = findViewById(R.id.sort_time);
        sortKm = findViewById(R.id.sort_km);
        sortStepsBtn = findViewById(R.id.sort_steps);
        sortSpeedBtn = findViewById(R.id.sort_speed);
        swAuto = findViewById(R.id.sw_autostart);
        swAutoMark = findViewById(R.id.sw_automark);
        swAutoStill = findViewById(R.id.sw_auto_still);
        swWaitGps = findViewById(R.id.sw_wait_gps);
        swRecordGpsLost = findViewById(R.id.sw_record_gps_lost);
        swHistClean = findViewById(R.id.sw_hist_clean);
        swHistCleanSettings = findViewById(R.id.sw_hist_clean_settings);
        speedUnitGroup = findViewById(R.id.speed_unit_group);
        editWeight = findViewById(R.id.edit_weight);
        editAccuracy = findViewById(R.id.edit_accuracy);
        editKey = findViewById(R.id.edit_key);
        editAutoMarkMin = findViewById(R.id.edit_automark_min);
        editGoalKm = findViewById(R.id.edit_goal_km);
        editGoalSteps = findViewById(R.id.edit_goal_steps);
        editEveryKm = findViewById(R.id.edit_every_km);
        editEverySteps = findViewById(R.id.edit_every_steps);
        editTodayGoal = findViewById(R.id.edit_today_goal);
        swGoalKm = findViewById(R.id.sw_goal_km);
        swGoalSteps = findViewById(R.id.sw_goal_steps);
        swEveryKm = findViewById(R.id.sw_every_km);
        swEverySteps = findViewById(R.id.sw_every_steps);
        swTodayGoal = findViewById(R.id.sw_today_goal);
        bindSoundRow(AlertKind.TRIP_KM, R.id.sw_ring_goal_km, R.id.sw_voice_goal_km,
                R.id.txt_tone_goal_km, R.id.btn_tone_goal_km);
        bindSoundRow(AlertKind.TRIP_STEPS, R.id.sw_ring_goal_steps, R.id.sw_voice_goal_steps,
                R.id.txt_tone_goal_steps, R.id.btn_tone_goal_steps);
        bindSoundRow(AlertKind.EVERY_KM, R.id.sw_ring_every_km, R.id.sw_voice_every_km,
                R.id.txt_tone_every_km, R.id.btn_tone_every_km);
        bindSoundRow(AlertKind.EVERY_STEPS, R.id.sw_ring_every_steps, R.id.sw_voice_every_steps,
                R.id.txt_tone_every_steps, R.id.btn_tone_every_steps);
        bindSoundRow(AlertKind.TODAY, R.id.sw_ring_today, R.id.sw_voice_today,
                R.id.txt_tone_today, R.id.btn_tone_today);
        txtCache = findViewById(R.id.txt_cache);
        txtKeepAlive = findViewById(R.id.txt_keepalive);
        txtKeepAliveBanner = findViewById(R.id.txt_keepalive_banner);
        btnAutoStartSys = findViewById(R.id.btn_autostart_sys);
        btnOem = findViewById(R.id.btn_oem);
        btnKeepAliveDone = findViewById(R.id.btn_keepalive_done);
        txtSync = findViewById(R.id.txt_sync);
        historyAdapter = new HistoryAdapter();
        listHistory.setAdapter(historyAdapter);
        historySort = Prefs.historySort(this);
        historySortDesc = Prefs.historySortDesc(this);
        setupHistoryTools();
        applyLastMode();
        setupStatsUi();
    }

    private void setupMap() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= 16) {
            s.setAllowFileAccessFromFileURLs(true);
        }
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return super.shouldInterceptRequest(view, request);
                }
                String url = request.getUrl().toString();
                if (TileCache.isTile(url)) {
                    return TileCache.serve(getApplicationContext(), url);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        web.addJavascriptInterface(new JsBridge(), "Android");
        web.loadUrl("file:///android_asset/map.html");
    }

    private void setupTabs() {
        tabs.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) {
                return;
            }
            if (id != R.id.tab_settings && paneSettings.getVisibility() == View.VISIBLE) {
                persistGoals();
                persistAutoMark();
                persistTrackClean();
            }
            paneMeasure.setVisibility(id == R.id.tab_measure ? View.VISIBLE : View.GONE);
            paneHistory.setVisibility(id == R.id.tab_history ? View.VISIBLE : View.GONE);
            if (paneStats != null) {
                paneStats.setVisibility(id == R.id.tab_stats ? View.VISIBLE : View.GONE);
            }
            paneSettings.setVisibility(id == R.id.tab_settings ? View.VISIBLE : View.GONE);
            if (id == R.id.tab_history) {
                bindHistCleanSwitches();
                refreshHistory();
            } else {
                hideHistoryKeyboard();
            }
            if (id != R.id.tab_stats) {
                leaveChartFullscreen();
            }
            if (id == R.id.tab_stats) {
                refreshStats();
            }
            if (id == R.id.tab_settings) {
                loadSettingsUi();
            }
        });
    }

    private void setupButtons() {
        if (rowStatsFold != null) {
            rowStatsFold.setOnClickListener(v -> toggleMeasureStats());
        }
        applyMeasureStatsFold();
        paintAutoPanel(engine.session(), 0);
        btnPrimary.setOnClickListener(v -> onPrimary());
        btnStop.setOnClickListener(v -> confirmStop());
        btnMark.setOnClickListener(v -> onMark());
        txtMarks.setOnClickListener(v -> showMarksDialog(engine.session(), "这次打点"));
        findViewById(R.id.btn_locate).setOnClickListener(v -> {
            follow = true;
            js("setFollow(true)");
            TrackSession s = engine.session();
            TrackPoint p = lastPoint(s);
            if (p != null) {
                js("setCenter(" + p.latGcj + "," + p.lngGcj + ",true)");
            }
        });
        if (btnMode != null) {
            btnMode.setOnClickListener(v -> showModeMenu());
        }
        if (btnSpan != null) {
            btnSpan.setOnClickListener(v -> toggleSpanPick());
        }
        if (btnSpanHist != null) {
            btnSpanHist.setOnClickListener(v -> toggleSpanPick());
        }
        btnSatellite.setOnClickListener(v -> {
            satellite = !satellite;
            btnSatellite.setText(satellite ? "地图" : "卫星");
            js("setSatellite(" + satellite + ")");
        });
        findViewById(R.id.btn_exit_preview).setOnClickListener(v -> {
            if (spanPicking) {
                setSpanPicking(false);
                return;
            }
            if (editingPoints) {
                setEditingPoints(false);
                return;
            }
            exitPreview(true);
        });
        if (btnHopLimit != null) {
            btnHopLimit.setOnClickListener(v -> editHopLimit());
        }
        if (btnEditPts != null) {
            btnEditPts.setOnClickListener(v -> {
                if (!previewingHistory) {
                    return;
                }
                setEditingPoints(!editingPoints);
            });
        }
        if (btnSuspectPts != null) {
            btnSuspectPts.setOnClickListener(v -> showSuspectHops());
        }
        if (btnRangePts != null) {
            btnRangePts.setOnClickListener(v -> {
                rangeMode = !rangeMode;
                rangeAnchor = -1;
                if (btnRangePts != null) {
                    btnRangePts.setText(rangeMode ? "取消连段" : "连段去掉");
                }
                Toast.makeText(this, rangeMode
                        ? "先点起点再点终点，中间一段都会去掉（点还在，可恢复）"
                        : "已退出连段", Toast.LENGTH_LONG).show();
            });
        }
        if (btnRestorePts != null) {
            btnRestorePts.setOnClickListener(v -> restoreAllHidden());
        }
        findViewById(R.id.btn_save_settings).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btn_battery).setOnClickListener(v -> jumpKeepAlive(KeepAlive.openBattery(this)));
        btnAutoStartSys.setOnClickListener(v -> jumpKeepAlive(KeepAlive.openAutoStart(this)));
        btnOem.setOnClickListener(v -> jumpKeepAlive(KeepAlive.openOemExtra(this)));
        btnKeepAliveDone.setOnClickListener(v -> confirmOemKeepAlive());
        txtKeepAliveBanner.setOnClickListener(v -> showKeepAliveDialog(KeepAlive.inspect(this), true));
        findViewById(R.id.btn_download_map).setOnClickListener(v -> chooseDownloadArea());
        findViewById(R.id.btn_clear_tiles).setOnClickListener(v -> confirmClearTiles());
        findViewById(R.id.btn_nearby_sync).setOnClickListener(v -> showNearbySync());
        findViewById(R.id.btn_backup).setOnClickListener(v -> showBackupPanel());
        findViewById(R.id.btn_sync_folder).setOnClickListener(v -> pickSyncTree());
        findViewById(R.id.btn_folder_sync_now).setOnClickListener(v -> runFolderSync(true));
        findViewById(R.id.btn_sync_help).setOnClickListener(v -> showSyncHelp());
        findViewById(R.id.cell_pace).setOnClickListener(v ->
                Toast.makeText(this,
                        "配速是走完 1 公里花的时间。例如 11分17秒/公里，大约等于时速 5.3 km/h。",
                        Toast.LENGTH_LONG).show());
        View.OnClickListener cycleSpeed = v -> cycleSpeedUnit();
        findViewById(R.id.cell_speed).setOnClickListener(cycleSpeed);
        findViewById(R.id.cell_avg).setOnClickListener(cycleSpeed);
        speedUnitGroup.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) {
                return;
            }
            String unit = Formats.speedUnitFromButton(id);
            if (unit.equals(Prefs.speedUnit(this))) {
                return;
            }
            Prefs.setSpeedUnit(this, unit);
            engine.refresh();
        });
        swAuto.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAutoStart(this, checked);
            engine.refresh();
        });
        if (swAutoStill != null) {
            swAutoStill.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setAutoStill(this, checked);
                engine.refresh();
            });
        }
    }

    private void maybePrivacyThenStart() {
        if (!Prefs.agreedPrivacy(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("定位与地图")
                    .setMessage("阿米测距用系统 GPS 记走路、跑步、骑车，也可以选「自动」：有步数算走路，没步数但在动算车程。算距离、用时、速度、配速；走路还记步数和步频。没网也能测。地图底图和附近地址用高德，要联网后才补上；走过的地图会缓存在本机。步数来自手机计步芯片（需要「身体活动」权限），轨迹只存在这台手机，没有账号、也不上传。不同意将退出。")
                    .setCancelable(false)
                    .setNegativeButton("不同意", (d, w) -> finish())
                    .setPositiveButton("同意", (d, w) -> {
                        Prefs.setAgreedPrivacy(this, true);
                        requestPerms();
                    })
                    .show();
            return;
        }
        requestPerms();
    }

    private void requestPerms() {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 29
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (need.isEmpty()) {
            onLocationReady();
            StepSensor.get(this).start();
            return;
        }
        permissionLauncher.launch(need.toArray(new String[0]));
    }

    private void onPermissions(Map<String, Boolean> result) {
        boolean loc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
        if (!loc) {
            pendingStart = false;
            Toast.makeText(this, "没有定位权限就没法测距离，请到系统设置里打开", Toast.LENGTH_LONG).show();
            return;
        }
        onLocationReady();
        StepSensor.get(this).start();
        engine.refresh();
    }

    private void onLocationReady() {
        keepAliveReady = true;
        if (engine.isActive()) {
            TrackService.start(this);
            stopPreviewLocation();
            redrawPath(engine.session());
            paneMeasure.post(this::maybePromptKeepAlive);
            return;
        }
        startPreviewLocation();
        boolean auto = engine.consumeAutoStart();
        if (auto) {
            wantAutoStart = true;
        }
        if ((auto || pendingStart) && !engine.isActive()) {
            pendingStart = false;
            startMeasureOrPrompt(auto);
        }
        paneMeasure.post(this::maybePromptKeepAlive);
    }

    private void onPrimary() {
        if (previewingHistory) {
            exitPreview(false);
        }
        String state = engine.session().state;
        if (TrackEngine.IDLE.equals(state)) {
            startMeasureOrPrompt(false);
        } else if (TrackEngine.RUNNING.equals(state)) {
            engine.pause();
        } else if (TrackEngine.PAUSED.equals(state)) {
            engine.resume();
        }
        applyKeepScreen();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean systemLocationOn() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            return true;
        }
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return lm.isLocationEnabled();
            }
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return true;
        }
    }

    private void startMeasureOrPrompt(boolean autoToast) {
        if (engine.isActive()) {
            return;
        }
        if (!hasLocationPermission()) {
            pendingStart = true;
            Toast.makeText(this, "没有定位权限就没法测距离，请允许定位", Toast.LENGTH_LONG).show();
            requestPerms();
            return;
        }
        if (!systemLocationOn()) {
            promptEnableLocation(autoToast);
            return;
        }
        beginMeasure(autoToast);
    }

    private void promptEnableLocation(boolean fromAuto) {
        if (fromAuto) {
            pendingStart = true;
        }
        new AlertDialog.Builder(this)
                .setTitle("请打开定位")
                .setMessage("系统定位关着，测距会一直停在 0。先去打开定位，回来后会自动开始。")
                .setNegativeButton("仍然开始", (d, w) -> {
                    pendingStart = false;
                    beginMeasure(fromAuto);
                })
                .setPositiveButton("去打开", (d, w) -> {
                    pendingStart = true;
                    try {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    } catch (Exception e) {
                        Toast.makeText(this, "打不开系统定位页，请到设置里打开定位",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setOnCancelListener(d -> {
                    if (!fromAuto) {
                        pendingStart = false;
                    }
                })
                .show();
    }

    private void beginMeasure(boolean autoToast) {
        if (engine.isActive()) {
            return;
        }
        if (previewingHistory) {
            exitPreview(false);
        }
        stopPreviewLocation();
        js("clearPath();clearMarks()");
        drawnPoints = 0;
        drawnMarks = 0;
        engine.start(currentMode());
        applyKeepScreen();
        if (autoToast) {
            Toast.makeText(this, "已开始测量。没网也照记距离，联网后再补地图和地址",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmStop() {
        TrackSession s = engine.session();
        if (!engine.isActive()) {
            return;
        }
        String msg = s.distanceM < 15
                ? "几乎还没动，结束的话这条就不保存了。"
                : "结束并保存这次 " + Formats.modeLabel(s.mode) + "？\n"
                + Formats.distance(s.distanceM) + "，用时 " + Formats.duration(s.movingMs)
                + (s.marks.isEmpty() ? "" : "\n" + Formats.marksCountLine(s.marks));
        new AlertDialog.Builder(this)
                .setTitle("结束测量")
                .setMessage(msg)
                .setNegativeButton("继续", null)
                .setPositiveButton("结束", (d, w) -> engine.stop(s.distanceM >= 15))
                .show();
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra(AlertNotify.EXTRA_STOP, false)) {
            intent.removeExtra(AlertNotify.EXTRA_STOP);
            AlertNotify.stop(this);
        }
        if (!intent.getBooleanExtra(EXTRA_CONFIRM_STOP, false)) {
            return;
        }
        intent.removeExtra(EXTRA_CONFIRM_STOP);
        if (previewingHistory) {
            exitPreview(false);
        }
        if (tabs != null) {
            tabs.check(R.id.tab_measure);
        }
        if (!engine.isActive()) {
            return;
        }
        paneMeasure.post(this::confirmStop);
    }

    private void onMark() {
        if (previewingHistory) {
            Toast.makeText(this, "正在看历史，先返回再打点", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!engine.isActive()) {
            Toast.makeText(this, "先开始测量再打点", Toast.LENGTH_SHORT).show();
            return;
        }
        int before = engine.session().marks.size();
        Checkpoint m = engine.markNow();
        if (m == null) {
            Toast.makeText(this, "还没有定位，到室外等 GPS 再打点", Toast.LENGTH_LONG).show();
            return;
        }
        if (engine.session().marks.size() == before) {
            Toast.makeText(this, "刚打过，稍等再点", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, Formats.markTitle(m) + "：到这里 "
                        + Formats.distance(m.distanceM) + "，用时 " + Formats.duration(m.movingMs),
                Toast.LENGTH_LONG).show();
    }

    private void showMarksDialog(TrackSession session, String title) {
        if (session == null) {
            return;
        }
        List<Checkpoint> marks = session.marks;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(Formats.marksDialog(marks))
                .setPositiveButton("好", null);
        if (marks.size() >= 2) {
            b.setNeutralButton("选两个打点", (d, w) -> pickTwoMarks(session));
        }
        b.show();
    }

    private void pickTwoMarks(final TrackSession session) {
        if (session == null || session.marks.size() < 2) {
            return;
        }
        final CharSequence[] labels = new CharSequence[session.marks.size()];
        for (int i = 0; i < session.marks.size(); i++) {
            Checkpoint m = session.marks.get(i);
            labels[i] = Formats.markTitle(m) + "  " + Formats.distance(m.distanceM)
                    + "  " + Formats.duration(m.movingMs);
        }
        new AlertDialog.Builder(this)
                .setTitle("这一段从哪个打点开始")
                .setItems(labels, (d, ia) -> new AlertDialog.Builder(this)
                        .setTitle("到哪个打点结束")
                        .setItems(labels, (d2, ib) -> {
                            if (ia == ib) {
                                Toast.makeText(this, "请选两个不同的打点", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            showSpanResult(SpanStats.betweenMarks(session, ia, ib), session);
                        })
                        .setNegativeButton("取消", null)
                        .show())
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleSpanPick() {
        setSpanPicking(!spanPicking);
    }

    private void setSpanPicking(boolean on) {
        if (on && editingPoints) {
            setEditingPoints(false);
        }
        spanPicking = on;
        spanMarkA = -1;
        spanPointA = -1;
        if (btnSpan != null) {
            btnSpan.setText(on ? "取消量" : "量两点");
        }
        if (btnSpanHist != null) {
            btnSpanHist.setText(on ? "取消量" : "量两点");
        }
        js("setSpanPick(" + on + ")");
        if (on) {
            Toast.makeText(this, "先点起点，再点终点。可点打点编号，也可点轨迹上任意位置。",
                    Toast.LENGTH_LONG).show();
        }
    }

    private TrackSession spanSession() {
        if (previewingHistory) {
            return previewRaw;
        }
        return engine.session();
    }

    private boolean canPaintSpan(TrackSession s) {
        if (s == null) {
            return false;
        }
        if (previewingHistory && previewRaw != null && s.id > 0 && s.id == previewRaw.id) {
            return true;
        }
        return !previewingHistory && s == engine.session();
    }

    private void onSpanTap(double latGcj, double lngGcj, double zoom) {
        TrackSession s = spanSession();
        if (s == null || (s.points.isEmpty() && s.marks.isEmpty())) {
            Toast.makeText(this, "还没有轨迹，先走一段或打开历史", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrackPoint> vis = SpanStats.visible(s);
        double maxM = 60;
        if (zoom >= 15) {
            double mpp = 156543.03392 * Math.cos(Math.toRadians(latGcj))
                    / Math.pow(2.0, zoom);
            maxM = Math.max(8, mpp * 48);
        }
        int mi = SpanStats.nearestMark(s.marks, latGcj, lngGcj, Math.max(40, maxM));
        int pi = SpanStats.nearest(vis, latGcj, lngGcj, maxM);
        boolean useMark = false;
        if (mi >= 0) {
            Checkpoint m = s.marks.get(mi);
            double md = CoordTransform.haversineM(m.latGcj, m.lngGcj, latGcj, lngGcj);
            double pd = pi >= 0
                    ? CoordTransform.haversineM(vis.get(pi).latGcj, vis.get(pi).lngGcj, latGcj, lngGcj)
                    : 1e9;
            useMark = md <= pd + 10;
        }
        if (!useMark && pi < 0) {
            Toast.makeText(this, "再靠近打点或轨迹线试试", Toast.LENGTH_SHORT).show();
            return;
        }
        int markI = useMark ? mi : -1;
        int pointI = useMark
                ? SpanStats.nearest(vis, s.marks.get(mi).latGcj, s.marks.get(mi).lngGcj, 20_000)
                : pi;
        if (spanMarkA < 0 && spanPointA < 0) {
            spanMarkA = markI;
            spanPointA = pointI;
            paintSpanStart(s, markI, pointI, vis);
            Toast.makeText(this, "起点已定，再点终点", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean same = (markI >= 0 && markI == spanMarkA)
                || (markI < 0 && pointI == spanPointA);
        if (same) {
            Toast.makeText(this, "请点另一个点", Toast.LENGTH_SHORT).show();
            return;
        }
        SpanStats st;
        if (spanMarkA >= 0 && markI >= 0) {
            st = SpanStats.betweenMarks(s, spanMarkA, markI);
        } else {
            st = SpanStats.betweenPoints(s, vis, spanPointA, pointI);
        }
        spanMarkA = -1;
        spanPointA = -1;
        showSpanResult(st, s);
    }

    private void paintSpanStart(TrackSession s, int markI, int pointI, List<TrackPoint> vis) {
        try {
            JSONObject o = new JSONObject();
            JSONObject pin = new JSONObject();
            if (markI >= 0) {
                pin.put("a", s.marks.get(markI).latGcj);
                pin.put("n", s.marks.get(markI).lngGcj);
            } else if (pointI >= 0 && pointI < vis.size()) {
                pin.put("a", vis.get(pointI).latGcj);
                pin.put("n", vis.get(pointI).lngGcj);
            } else {
                return;
            }
            o.put("a", pin);
            js("setSpan(" + JSONObject.quote(o.toString()) + ")");
        } catch (JSONException ignored) {
        }
    }

    private void showSpanResult(SpanStats st, TrackSession session) {
        if (st == null) {
            Toast.makeText(this, "这两点太近，换两个再试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (canPaintSpan(session)) {
            js("setSpan(" + JSONObject.quote(st.mapJson().toString()) + ")");
        }
        new AlertDialog.Builder(this)
                .setTitle("这一段")
                .setMessage(st.text(this))
                .setPositiveButton("好", null)
                .setNeutralButton("再选", (d, w) -> {
                    spanMarkA = -1;
                    spanPointA = -1;
                    if (spanPicking) {
                        Toast.makeText(this, "再点起点和终点", Toast.LENGTH_SHORT).show();
                    } else if (session != null && session.marks.size() >= 2) {
                        pickTwoMarks(session);
                    }
                })
                .show();
    }

    private void applyLastMode() {
        applyMode(Prefs.lastMode(this), false);
    }

    private void applyMode(String mode, boolean persist) {
        if (TrackEngine.MODE_RUN.equals(mode)
                || TrackEngine.MODE_RIDE.equals(mode)
                || TrackEngine.MODE_AUTO.equals(mode)
                || TrackEngine.MODE_WALK.equals(mode)) {
            selectedMode = mode;
        } else {
            selectedMode = TrackEngine.MODE_WALK;
        }
        if (btnMode != null) {
            btnMode.setText(Formats.modeLabel(selectedMode) + " ▾");
        }
        if (persist) {
            Prefs.setLastMode(this, selectedMode);
            refreshStride(engine.session());
            paintAutoPanel(engine.session(), 0);
        }
    }

    private void showModeMenu() {
        if (engine.isActive() || btnMode == null) {
            return;
        }
        PopupMenu pop = new PopupMenu(this, btnMode);
        Menu menu = pop.getMenu();
        menu.add(1, 1, 0, "走路");
        menu.add(1, 2, 1, "跑步");
        menu.add(1, 3, 2, "骑车");
        menu.add(1, 4, 3, "自动");
        menu.setGroupCheckable(1, true, true);
        int checked = 1;
        if (TrackEngine.MODE_RUN.equals(selectedMode)) {
            checked = 2;
        } else if (TrackEngine.MODE_RIDE.equals(selectedMode)) {
            checked = 3;
        } else if (TrackEngine.MODE_AUTO.equals(selectedMode)) {
            checked = 4;
        }
        MenuItem current = menu.findItem(checked);
        if (current != null) {
            current.setChecked(true);
        }
        pop.setOnMenuItemClickListener(item -> {
            String mode = TrackEngine.MODE_WALK;
            int id = item.getItemId();
            if (id == 2) {
                mode = TrackEngine.MODE_RUN;
            } else if (id == 3) {
                mode = TrackEngine.MODE_RIDE;
            } else if (id == 4) {
                mode = TrackEngine.MODE_AUTO;
            }
            applyMode(mode, true);
            return true;
        });
        pop.show();
    }

    private String currentMode() {
        return selectedMode;
    }

    @Override
    public void onUpdate(TrackSession session, float currentSpeedMps, String gpsLabel,
                         boolean hasFix, TrackPoint lastFix) {
        if (previewingHistory) {
            return;
        }
        txtGps.setText(gpsLabel);
        txtDistance.setText(Formats.distance(session.distanceM));
        txtTime.setText(Formats.duration(session.movingMs));
        txtPace.setText(Formats.pace(session.distanceM, session.movingMs));
        txtSpeed.setText(Formats.speed(this, currentSpeedMps));
        txtAvg.setText(Formats.speed(this, session.avgSpeedMps()));
        refreshStride(session);
        paintAutoPanel(session, currentSpeedMps);
        txtExtra.setText(extraLine(session));

        String place = placeLine(session, lastFix);
        if (!place.equals(lastPlace)) {
            lastPlace = place;
            txtPlace.setText(place);
        }

        boolean active = TrackEngine.RUNNING.equals(session.state)
                || TrackEngine.PAUSED.equals(session.state);
        btnStop.setVisibility(active ? View.VISIBLE : View.GONE);
        btnMark.setVisibility(active && !previewingHistory ? View.VISIBLE : View.GONE);
        if (btnMode != null) {
            btnMode.setEnabled(!active);
        }
        if (TrackEngine.RUNNING.equals(session.state)) {
            btnPrimary.setText("暂停");
        } else if (TrackEngine.PAUSED.equals(session.state)) {
            btnPrimary.setText("继续");
        } else {
            btnPrimary.setText("开始");
        }
        if (txtMarks != null) {
            if (!active || previewingHistory) {
                txtMarks.setVisibility(View.GONE);
            } else {
                txtMarks.setVisibility(View.VISIBLE);
                txtMarks.setText(liveMarksHint(session));
            }
        }
        if (active && !previewingHistory) {
            syncMarksOnMap(session);
        }

        if (lastFix != null && (follow || !active) && !previewingHistory) {
            js("setCenter(" + lastFix.latGcj + "," + lastFix.lngGcj + "," + (!active) + ")");
        }
        applyKeepScreen();
        if (!active && paneHistory.getVisibility() == View.VISIBLE) {
            refreshHistory();
        }
        updateFoldHandle();
    }

    @Override
    public void onNewPoint(TrackPoint point) {
        if (previewingHistory) {
            return;
        }
        js("addPoint(" + point.latGcj + "," + point.lngGcj + "," + point.kind + ")");
        drawnPoints++;
    }

    @Override
    public void onStopped(TrackSession saved) {
        drawnPoints = 0;
        drawnMarks = 0;
        js("clearPath();clearMarks()");
        refreshHistory();
        refreshStats();
        startPreviewLocation();
        editSessionLabels(saved, true);
    }

    private String savedStats(TrackSession saved) {
        if (TrackEngine.isAuto(saved.mode)) {
            return "自动\n"
                    + "走路 " + Formats.distance(saved.walkDistanceM)
                    + "  ·  " + Formats.duration(saved.walkMovingMs)
                    + "  ·  " + Formats.steps(saved.walkSteps)
                    + "  ·  步频 " + Formats.cadence(saved.walkCadenceSpm()) + "\n"
                    + "车程 " + Formats.distance(saved.vehicleDistanceM)
                    + "  ·  " + Formats.duration(saved.vehicleMovingMs)
                    + "  ·  均速 " + Formats.speed(this, saved.vehicleAvgMps()) + "\n"
                    + battSavedLine(saved)
                    + "热量 " + Formats.kcal(saved.calories) + "（按走路段）\n"
                    + addrLine(saved)
                    + marksSavedLine(saved);
        }
        return Formats.modeLabel(saved.mode) + " "
                + Formats.distance(saved.distanceM) + "\n"
                + "用时 " + Formats.duration(saved.movingMs)
                + "  配速 " + Formats.pace(saved.distanceM, saved.movingMs) + "\n"
                + "这次 " + Formats.tripSteps(saved.distanceM, saved.steps) + "\n"
                + battSavedLine(saved)
                + "均速 " + Formats.speed(this, saved.avgSpeedMps())
                + "  最高 " + Formats.speed(this, saved.maxSpeedMps) + "\n"
                + "热量 " + Formats.kcal(saved.calories) + "\n"
                + addrLine(saved)
                + marksSavedLine(saved)
                + (Formats.needsGeocode(saved.startAddr) && Formats.needsGeocode(saved.endAddr)
                ? "\n联网后会补上出发和到达地点" : "");
    }

    private void editSessionLabels(final TrackSession s, boolean justSaved) {
        if (s == null || s.id <= 0) {
            return;
        }
        View box = LayoutInflater.from(this).inflate(R.layout.dialog_session_label, null);
        TextView stats = box.findViewById(R.id.txt_label_stats);
        final EditText editTitle = box.findViewById(R.id.edit_title);
        final EditText editFrom = box.findViewById(R.id.edit_from);
        final EditText editTo = box.findViewById(R.id.edit_to);
        stats.setText(justSaved ? savedStats(s) : Formats.headline(s) + "\n" + Formats.when(s.startMs));
        editTitle.setText(Formats.nz(s.title));
        editFrom.setText(Formats.suggestPlace(s.fromPlace, s.startAddr));
        editTo.setText(Formats.suggestPlace(s.toPlace, s.endAddr));
        Runnable save = () -> {
            String title = Formats.nz(editTitle.getText().toString());
            String from = Formats.nz(editFrom.getText().toString());
            String to = Formats.nz(editTo.getText().toString());
            engine.store().updateLabels(s.id, title, from, to);
            s.title = title;
            s.fromPlace = from;
            s.toPlace = to;
            refreshHistory();
            if (previewingHistory && txtHistoryBanner != null) {
                txtHistoryBanner.setText(previewBanner(s));
            }
        };
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(justSaved ? "给这次起个名" : "改名称")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    save.run();
                    Toast.makeText(this, "已记下", Toast.LENGTH_SHORT).show();
                });
        if (justSaved) {
            b.setNeutralButton("去历史", (d, w) -> {
                save.run();
                tabs.check(R.id.tab_history);
            });
            b.setNegativeButton("先这样", (d, w) -> save.run());
        } else {
            b.setNegativeButton("取消", null);
        }
        AlertDialog dlg = b.create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private static String marksSavedLine(TrackSession s) {
        if (s.marks.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder("\n").append(Formats.marksCountLine(s.marks));
        for (Checkpoint m : s.marks) {
            b.append('\n').append(Formats.markTitle(m)).append("  到这里 ")
                    .append(Formats.distance(m.distanceM)).append(" · ")
                    .append(Formats.duration(m.movingMs));
        }
        return b.toString();
    }

    private String placeLine(TrackSession session, TrackPoint lastFix) {
        boolean online = Net.isOnline(this);
        String autoTag = "";
        boolean autoOn = TrackEngine.isAuto(session.mode)
                || (!engine.isActive() && TrackEngine.isAuto(currentMode()));
        if (autoOn && TrackEngine.RUNNING.equals(session.state)) {
            int k = engine.autoKind();
            if (k == TrackPoint.KIND_WALK) {
                autoTag = "自动·走路  ";
            } else if (k == TrackPoint.KIND_VEHICLE) {
                autoTag = "自动·车程  ";
            } else {
                autoTag = "自动识别  ";
            }
        }
        if (previewingHistory) {
            return online ? "正在看历史轨迹" : "正在看历史轨迹（离线，底图可能不完整）";
        }
        if (TrackEngine.RUNNING.equals(session.state)) {
            if (engine.waitingForGps()) {
                return autoTag + "等 GPS 合格再开始记（精度须 ≤ "
                        + Prefs.maxAccuracyM(this) + " 米）";
            }
            if (engine.gpsLostHold()) {
                return autoTag + "丢星，暂不计时计步；有合格 GPS 再接着";
            }
            if (engine.autoStillActive()) {
                return autoTag + "已停下，不计时；人一走又接着记";
            }
            if (!Formats.needsGeocode(session.endAddr)) {
                return autoTag + session.endAddr;
            }
            if (!Formats.needsGeocode(session.startAddr)) {
                return autoTag + "从 " + session.startAddr + " 出发";
            }
            return autoTag + (online ? "测量中，走到目的地再点结束"
                    : "离线记录中，距离和用时照常，联网后补地图");
        }
        if (TrackEngine.PAUSED.equals(session.state)) {
            return online ? "已暂停，继续走之前先点继续"
                    : "已暂停（离线仍会记 GPS），点继续接着走";
        }
        if (wantAutoStart) {
            return online
                    ? "已自动开始。没有 GPS 时距离会停在 0，到室外会跟上"
                    : "已自动开始 · 离线记录，联网后看地图和地点";
        }
        boolean auto = Prefs.autoStart(this);
        if (lastFix != null) {
            if (online) {
                return auto ? "已定位，点开始或打开后会自动开始" : "已定位，点开始即可测量";
            }
            return auto ? "已定位 · 没网也能开始，联网后再看地图" : "已定位 · 没网也能测，点开始即可";
        }
        return auto
                ? "打开后会自动开始测量，先允许定位。没网也能记。"
                : "点开始即可测量。没网也能记。";
    }

    private void refreshStride(TrackSession session) {
        if (txtStride == null) {
            return;
        }
        if (session == null) {
            session = engine.session();
        }
        double meters = session == null ? 0 : session.distanceM;
        int steps = session == null ? 0 : session.steps;
        boolean active = session != null && (TrackEngine.RUNNING.equals(session.state)
                || TrackEngine.PAUSED.equals(session.state));
        String mode = active ? session.mode : currentMode();
        if (TrackEngine.isAuto(mode) && session != null) {
            meters = session.walkDistanceM;
            steps = session.walkSteps;
        }
        txtStride.setText(Formats.strideAlways(this, meters, steps, mode));
    }

    private String extraLine(TrackSession session) {
        StringBuilder b = new StringBuilder();
        b.append(StepSensor.get(this).todayText(this));
        boolean active = TrackEngine.RUNNING.equals(session.state)
                || TrackEngine.PAUSED.equals(session.state);
        if (active || session.steps > 0 || session.walkSteps > 0) {
            if (TrackEngine.isAuto(session.mode)) {
                b.append('\n').append("这次走路 ").append(
                        Formats.tripSteps(session.walkDistanceM, session.walkSteps));
            } else {
                b.append('\n').append("这次 ").append(Formats.tripSteps(session.distanceM, session.steps));
            }
        }
        String goal = GoalAlerts.tripHint(this, session);
        if (!goal.isEmpty()) {
            b.append('\n').append(goal);
        }
        String openBatt = BatterySnap.sinceOpenLine(this);
        if (!openBatt.isEmpty()) {
            b.append('\n').append(openBatt);
        }
        String tripBatt = BatterySnap.sessionLine(this, session);
        if (!tripBatt.isEmpty()) {
            b.append('\n').append(tripBatt);
        }
        b.append('\n').append("热量 ").append(Formats.kcal(session.calories))
                .append(" · 最高 ").append(Formats.speed(this, session.maxSpeedMps))
                .append(" · ").append(session.points.size()).append(" 个点");
        return b.toString();
    }

    private void paintAutoPanel(TrackSession session, float nowMps) {
        if (txtAuto == null) {
            return;
        }
        boolean active = session != null && (TrackEngine.RUNNING.equals(session.state)
                || TrackEngine.PAUSED.equals(session.state));
        String mode = active && session != null ? session.mode : currentMode();
        if (!TrackEngine.isAuto(mode)) {
            txtAuto.setVisibility(View.GONE);
            return;
        }
        txtAuto.setVisibility(View.VISIBLE);
        if (session == null || (!active && session.walkDistanceM < 1 && session.vehicleDistanceM < 1)) {
            txtAuto.setText("自动：芯片有步数就算走路（步频/步数/公里/用时/速度）；没步数但在动就算车程。点开始后不用手动切换。");
            return;
        }
        int kind = active ? engine.autoKind() : session.autoKind;
        txtAuto.setText(Formats.autoPanel(this, session, nowMps, kind));
    }

    private void showAutoHelp() {
        new AlertDialog.Builder(this)
                .setTitle("自动识别")
                .setMessage("选「自动」后不用在走路/骑车之间切换。\n\n"
                        + "检测到步数：按走路统计——时间、速度、距离、步数、步频。\n\n"
                        + "没检测到步数但 GPS 在动：按车程统计——速度、距离、时间（开车、坐车、骑车都算这类）。\n\n"
                        + "地图上绿线是走路，橙线是车程。到达提醒的公里和步数只算走路段。需要「身体活动」权限读步数；没有步数时，移动都会算进车程。")
                .setPositiveButton("好", null)
                .show();
    }

    private void onStepsClicked() {
        if (Build.VERSION.SDK_INT >= 29
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.ACTIVITY_RECOGNITION});
            return;
        }
        StepSensor ss = StepSensor.get(this);
        ss.start();
        String stride = String.format(Locale.CHINA, "%.0f 厘米", Prefs.lastStrideM(this) * 100);
        new AlertDialog.Builder(this)
                .setTitle("步数、公里和电量")
                .setMessage("步幅：测量页大字一直显示。走够几步、有 GPS 距离后按这次实测更新；刚开始或还没走完用上次记下的。\n\n"
                        + "这次步数：点「开始」才从 0 计，暂停冻住（和距离、用时一样），点「继续」再加。跟这次 GPS 公里对照，算出一步多长。\n\n"
                        + "今日步数：同一天内关掉 App 再打开，会用芯片里的累计值把中间那段补上。这版会尽量在零点拍一次累计值，少记的那截会小很多。小米仍可能拦住零点闹钟，那零点到第一次打开之前还是记不到。\n\n"
                        + "和运动健康对不上是正常的。那边有系统服务 24 小时听着，还可以接手环；这边只读手机芯片，第三方读不到它的全天数字。差一千多步，常见就是零点到打开前、或者手环多记了。\n\n"
                        + "电量：打开 App 起记「打开后」；点「开始」再记「本段」。锁屏通知上能看到本段掉了百分之几。这是整机掉电，不是系统里「仅本 App」那一栏——走路锁屏时多半就是测距在耗。充电中不算。\n\n"
                        + "当前今日 " + Formats.steps(ss.today())
                        + "，用来估公里的步幅 " + stride + "（走完一段会按这次实测更新）。")
                .setPositiveButton("好", null)
                .show();
    }

    private static String addrLine(TrackSession s) {
        String a = Formats.needsGeocode(s.startAddr) ? "" : s.startAddr;
        String b = Formats.needsGeocode(s.endAddr) ? "" : s.endAddr;
        if (a.isEmpty() && b.isEmpty()) {
            return "联网后显示地点";
        }
        if (a.equals(b) || b.isEmpty()) {
            return a;
        }
        if (a.isEmpty()) {
            return b;
        }
        return a + " → " + b;
    }

    private static String battSavedLine(TrackSession s) {
        String line = BatterySnap.sessionLine(null, s);
        if (line.isEmpty()) {
            return "";
        }
        return line + "\n";
    }

    private static String battListBit(TrackSession s) {
        String shortTxt = BatterySnap.savedShort(s);
        return shortTxt.isEmpty() ? "" : "  " + shortTxt;
    }

    private void redrawPath(TrackSession session) {
        if (session.points.isEmpty()) {
            js("clearPath()");
            drawnPoints = 0;
        } else {
            try {
                JSONArray arr = new JSONArray();
                for (TrackPoint p : session.points) {
                    arr.put(p.toJson());
                }
                js("setPath(" + JSONObject.quote(arr.toString()) + ")");
                drawnPoints = session.points.size();
            } catch (JSONException e) {
                js("clearPath()");
                drawnPoints = 0;
            }
        }
        pushMarks(session);
        drawnMarks = session.marks.size();
    }

    private String liveMarksHint(TrackSession session) {
        int autoMin = Prefs.autoMarkMin(this);
        String autoBit = autoMin > 0 ? "自动每 " + autoMin + " 分钟" : "";
        if (session.marks.isEmpty()) {
            if (!autoBit.isEmpty()) {
                return autoBit + " · 走到地方也可手动打点";
            }
            return "走到地方点「打点」，记下到这里的距离和用时";
        }
        Checkpoint last = session.marks.get(session.marks.size() - 1);
        StringBuilder b = new StringBuilder(Formats.marksCountLine(session.marks));
        b.append(" · 最近").append(last.auto ? "自动" : "手动").append("到这里 ")
                .append(Formats.distance(last.distanceM)).append(" / ")
                .append(Formats.duration(last.movingMs));
        if (!autoBit.isEmpty()) {
            b.append(" · ").append(autoBit);
        }
        b.append("（点这里看全部，也可选两个打点）");
        return b.toString();
    }

    private void syncMarksOnMap(TrackSession session) {
        int n = session.marks.size();
        if (n == drawnMarks) {
            return;
        }
        if (n == drawnMarks + 1) {
            pushOneMark(session.marks.get(n - 1));
        } else {
            pushMarks(session);
        }
        drawnMarks = n;
    }

    private void pushOneMark(Checkpoint m) {
        js("addMark(" + m.latGcj + "," + m.lngGcj + "," + m.n + ","
                + JSONObject.quote(Formats.markTitle(m) + "\n" + Formats.markBody(m)) + ","
                + JSONObject.quote(m.auto ? "auto" : "hand") + ")");
    }

    private void pushMarks(TrackSession s) {
        JSONArray arr = new JSONArray();
        for (Checkpoint m : s.marks) {
            try {
                JSONObject o = new JSONObject();
                o.put("a", m.latGcj);
                o.put("g", m.lngGcj);
                o.put("i", m.n);
                o.put("p", Formats.markTitle(m) + "\n" + Formats.markBody(m));
                o.put("k", m.auto ? "auto" : "hand");
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        js("setMarks(" + JSONObject.quote(arr.toString()) + ")");
        drawnMarks = s.marks.size();
    }

    private void previewSession(TrackSession s) {
        if (engine.isActive()) {
            Toast.makeText(this, "请先结束当前测量再看历史", Toast.LENGTH_SHORT).show();
            return;
        }
        if (s == null || s.id <= 0) {
            return;
        }
        TrackSession raw = engine.store().get(s.id);
        if (raw == null) {
            Toast.makeText(this, "这条记录找不到了", Toast.LENGTH_SHORT).show();
            return;
        }
        hideHistoryKeyboard();
        setSpanPicking(false);
        previewRaw = raw;
        previewingHistory = true;
        editingPoints = false;
        rangeMode = false;
        rangeAnchor = -1;
        focusHopIndex = -1;
        engine.setPreviewing(true);
        follow = false;
        js("setFollow(false)");
        setHistoryFullscreen(true);
        tabs.check(R.id.tab_measure);
        redrawPreview();
    }

    private void redrawPreview() {
        if (previewRaw == null) {
            return;
        }
        TrackSession shown = TrackClean.view(this, previewRaw);
        if (txtHistoryBanner != null) {
            txtHistoryBanner.setText(previewBanner(shown));
        }
        redrawPreviewPath();
        if (btnEditPts != null) {
            btnEditPts.setText(editingPoints ? "完成改点" : "去掉点");
        }
        if (btnSuspectPts != null) {
            btnSuspectPts.setVisibility(editingPoints ? View.VISIBLE : View.GONE);
        }
        if (btnRangePts != null) {
            btnRangePts.setVisibility(editingPoints ? View.VISIBLE : View.GONE);
            btnRangePts.setText(rangeMode ? "取消连段" : "连段去掉");
        }
        if (btnRestorePts != null) {
            boolean show = editingPoints && TrackClean.hasHidden(previewRaw);
            btnRestorePts.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnHopLimit != null) {
            btnHopLimit.setVisibility(View.VISIBLE);
        }
        pushEditDots();
    }

    private void redrawPreviewPath() {
        if (previewRaw == null) {
            return;
        }
        List<List<TrackPoint>> segs = TrackClean.pathSegments(previewRaw);
        if (segs.isEmpty()) {
            js("clearPath()");
            drawnPoints = 0;
        } else {
            try {
                JSONArray all = new JSONArray();
                int n = 0;
                for (List<TrackPoint> seg : segs) {
                    JSONArray arr = new JSONArray();
                    for (TrackPoint p : seg) {
                        arr.put(p.toJson());
                        n++;
                    }
                    all.put(arr);
                }
                js("setPath(" + JSONObject.quote(all.toString()) + ")");
                drawnPoints = n;
            } catch (JSONException e) {
                js("clearPath()");
                drawnPoints = 0;
            }
        }
        pushMarks(previewRaw);
        drawnMarks = previewRaw.marks.size();
    }

    private void setEditingPoints(boolean on) {
        if (on) {
            setSpanPicking(false);
        }
        editingPoints = on;
        rangeMode = false;
        rangeAnchor = -1;
        if (!on) {
            focusHopIndex = -1;
        }
        js("setEditPts(" + on + ")");
        if (on) {
            Toast.makeText(this, "点还在库里，随时可恢复。点编号红点立刻去掉/恢复；连段可一次去掉一截。",
                    Toast.LENGTH_LONG).show();
        }
        redrawPreview();
    }

    private void pushEditDots() {
        if (!editingPoints || previewRaw == null) {
            js("setDots(" + JSONObject.quote("[]") + ")");
            return;
        }
        JSONArray arr = new JSONArray();
        List<TrackClean.Hop> hops = TrackClean.suspectHops(previewRaw);
        for (int n = 0; n < hops.size(); n++) {
            TrackClean.Hop h = hops.get(n);
            if (h.index < 0 || h.index >= previewRaw.points.size()) {
                continue;
            }
            TrackPoint p = previewRaw.points.get(h.index);
            try {
                JSONObject o = new JSONObject();
                o.put("a", p.latGcj);
                o.put("n", p.lngGcj);
                o.put("k", h.hidden ? "hid" : "fly");
                o.put("lab", n + 1);
                o.put("sel", h.index == focusHopIndex || h.index == rangeAnchor);
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        js("setDots(" + JSONObject.quote(arr.toString()) + ")");
    }

    private void persistPreview(String toast) {
        if (previewRaw == null || previewRaw.id <= 0) {
            return;
        }
        TrackClean.applyStats(this, previewRaw, false);
        engine.store().updateTrack(previewRaw);
        refreshHistory();
        refreshStats();
        redrawPreview();
        if (toast != null && !toast.isEmpty()) {
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void editHopLimit() {
        if (previewRaw == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(18 * density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView hint = new TextView(this);
        hint.setText("超过这个速度的位移当 GPS 跳动，不算进距离、用时和轨迹。下面是这条现在用的阈值，可改数字、可切单位。");
        hint.setTextColor(0xFF334155);
        hint.setTextSize(14);
        final boolean[] asMs = { Formats.UNIT_MS.equals(Prefs.speedUnit(this)) };
        double curMps = TrackClean.hopLimitMps(previewRaw);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(asMs[0] ? trimNum((float) curMps) : trimNum((float) (curMps * 3.6)));
        input.setSelectAllOnFocus(true);
        RadioGroup units = new RadioGroup(this);
        units.setOrientation(RadioGroup.HORIZONTAL);
        units.setPadding(0, Math.round(8 * density), 0, Math.round(4 * density));
        final RadioButton rbKmh = new RadioButton(this);
        rbKmh.setId(View.generateViewId());
        rbKmh.setText("km/h");
        final RadioButton rbMs = new RadioButton(this);
        rbMs.setId(View.generateViewId());
        rbMs.setText("m/s");
        units.addView(rbKmh);
        units.addView(rbMs);
        units.check(asMs[0] ? rbMs.getId() : rbKmh.getId());
        units.setOnCheckedChangeListener((g, id) -> {
            boolean nowMs = id == rbMs.getId();
            if (nowMs == asMs[0]) {
                return;
            }
            double v;
            try {
                String t = input.getText().toString().trim();
                v = t.isEmpty() ? Double.NaN : Double.parseDouble(t);
            } catch (Exception e) {
                v = Double.NaN;
            }
            if (!Double.isNaN(v) && v > 0) {
                double mps = asMs[0] ? v : v / 3.6;
                input.setText(nowMs ? trimNum((float) mps) : trimNum((float) (mps * 3.6)));
                input.selectAll();
            }
            asMs[0] = nowMs;
        });
        TextView def = new TextView(this);
        def.setText(Formats.modeLabel(previewRaw.mode) + "默认 "
                + TrackClean.hopLimitLabel(this, newLimitSession(previewRaw.mode, 0)));
        def.setTextColor(0xFF64748B);
        def.setTextSize(13);
        box.addView(hint);
        box.addView(input);
        box.addView(units);
        box.addView(def);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("这条的速度阈值")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    double mps = TrackClean.parseHopNumber(input.getText().toString(), asMs[0]);
                    if (mps < 0) {
                        Toast.makeText(this, "数字看不懂，改一下再保存", Toast.LENGTH_LONG).show();
                        return;
                    }
                    previewRaw.hopMaxMps = mps;
                    persistPreview(mps < 0.3
                            ? "已用" + Formats.modeLabel(previewRaw.mode) + "默认阈值"
                            : "已设 " + Formats.speed(this, mps) + "，超过的点不计入");
                })
                .setNeutralButton("恢复默认", (d, w) -> {
                    previewRaw.hopMaxMps = 0;
                    persistPreview("已恢复默认阈值");
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        input.requestFocus();
    }

    private static TrackSession newLimitSession(String mode, double mps) {
        TrackSession s = new TrackSession();
        s.mode = mode;
        s.hopMaxMps = mps;
        return s;
    }

    private String hopRowLabel(int n, TrackClean.Hop h) {
        StringBuilder b = new StringBuilder();
        b.append('#').append(n).append("  ");
        b.append(h.hidden ? "已去掉  " : "可疑  ");
        b.append(Formats.clock(h.t));
        if (h.meters > 0.5) {
            b.append("  ").append(Formats.distance(h.meters));
            b.append("  ").append(Formats.speed(this, (float) h.mps));
        }
        return b.toString();
    }

    private void showSuspectHops() {
        if (previewRaw == null) {
            return;
        }
        final List<TrackClean.Hop> hops = TrackClean.suspectHops(previewRaw);
        if (hops.isEmpty()) {
            Toast.makeText(this, "没有明显可疑的点。可在地图上点轨迹附近挑选。", Toast.LENGTH_LONG).show();
            return;
        }
        final AlertDialog[] dlgRef = new AlertDialog[1];
        float density = getResources().getDisplayMetrics().density;
        final boolean[] checked = new boolean[hops.size()];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * density);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView hint = new TextView(this);
        hint.setText("编号和地图红点一致。点一行看地图；勾选后可批量去掉或恢复。点还在库里，不会删掉。");
        hint.setTextColor(0xFF334155);
        hint.setTextSize(13);
        box.addView(hint);
        ListView lv = new ListView(this);
        lv.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return hops.size();
            }

            @Override
            public TrackClean.Hop getItem(int position) {
                return hops.get(position);
            }

            @Override
            public long getItemId(int position) {
                return hops.get(position).index;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                CheckBox cb;
                TextView txt;
                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                    cb = (CheckBox) row.getChildAt(0);
                    txt = (TextView) row.getChildAt(1);
                } else {
                    row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    int vpad = Math.round(6 * density);
                    row.setPadding(0, vpad, 0, vpad);
                    cb = new CheckBox(MainActivity.this);
                    txt = new TextView(MainActivity.this);
                    txt.setTextColor(0xFF0F172A);
                    txt.setTextSize(14);
                    txt.setPadding(Math.round(6 * density), 0, 0, 0);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    row.addView(cb);
                    row.addView(txt, lp);
                }
                TrackClean.Hop h = hops.get(position);
                cb.setOnCheckedChangeListener(null);
                cb.setChecked(checked[position]);
                txt.setText(hopRowLabel(position + 1, h));
                cb.setFocusable(false);
                cb.setOnCheckedChangeListener((button, isChecked) -> checked[position] = isChecked);
                txt.setOnClickListener(v -> {
                    if (dlgRef[0] != null) {
                        dlgRef[0].dismiss();
                    }
                    focusHopOnMap(h.index, position + 1);
                });
                return row;
            }
        });
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(320 * density));
        box.addView(lv, listLp);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button allFly = new Button(this, null, android.R.attr.borderlessButtonStyle);
        allFly.setText("勾选可疑");
        allFly.setOnClickListener(v -> {
            for (int i = 0; i < hops.size(); i++) {
                checked[i] = !hops.get(i).hidden;
            }
            ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
        });
        Button hideSel = new Button(this, null, android.R.attr.borderlessButtonStyle);
        hideSel.setText("去掉已选");
        hideSel.setOnClickListener(v -> {
            if (dlgRef[0] != null) {
                dlgRef[0].dismiss();
            }
            applyCheckedHops(hops, checked, true);
        });
        Button showSel = new Button(this, null, android.R.attr.borderlessButtonStyle);
        showSel.setText("恢复已选");
        showSel.setOnClickListener(v -> {
            if (dlgRef[0] != null) {
                dlgRef[0].dismiss();
            }
            applyCheckedHops(hops, checked, false);
        });
        actions.addView(allFly);
        actions.addView(hideSel);
        actions.addView(showSel);
        box.addView(actions);
        dlgRef[0] = new AlertDialog.Builder(this)
                .setTitle("可疑 / 已去掉 · 共 " + hops.size() + " 个")
                .setView(box)
                .setNegativeButton("关闭", null)
                .create();
        dlgRef[0].show();
    }

    private void focusHopOnMap(int pointIndex, int n) {
        if (previewRaw == null || pointIndex < 0 || pointIndex >= previewRaw.points.size()) {
            return;
        }
        TrackPoint p = previewRaw.points.get(pointIndex);
        focusHopIndex = pointIndex;
        js("setCenter(" + p.latGcj + "," + p.lngGcj + ",true)");
        pushEditDots();
        Toast.makeText(this, "地图上的 #" + n, Toast.LENGTH_SHORT).show();
    }

    private void applyCheckedHops(List<TrackClean.Hop> hops, boolean[] checked, boolean hide) {
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < hops.size(); i++) {
            if (checked[i]) {
                idxs.add(hops.get(i).index);
            }
        }
        if (idxs.isEmpty()) {
            Toast.makeText(this, "先勾几个点", Toast.LENGTH_SHORT).show();
            return;
        }
        setHiddenMany(idxs, hide);
    }

    private void onPreviewMapTap(double latGcj, double lngGcj) {
        onPreviewMapTap(latGcj, lngGcj, 17);
    }

    private void onPreviewMapTap(double latGcj, double lngGcj, double zoom) {
        if (spanPicking) {
            onSpanTap(latGcj, lngGcj, zoom);
            return;
        }
        if (!editingPoints || previewRaw == null) {
            return;
        }
        double maxM = 45;
        if (zoom >= 15) {
            double mpp = 156543.03392 * Math.cos(Math.toRadians(latGcj))
                    / Math.pow(2.0, zoom);
            maxM = Math.max(1.2, mpp * 36);
        }
        int i = TrackClean.nearestIndex(previewRaw.points, latGcj, lngGcj, maxM);
        if (i < 0) {
            Toast.makeText(this, "附近没有轨迹点，再靠近编号红点或再放大试试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rangeMode) {
            onRangeTap(i);
            return;
        }
        toggleHiddenPoint(i);
    }

    private void onRangeTap(int index) {
        if (rangeAnchor < 0) {
            rangeAnchor = index;
            focusHopIndex = index;
            pushEditDots();
            Toast.makeText(this, "起点已定，再点终点（中间都会去掉，可恢复）", Toast.LENGTH_SHORT).show();
            return;
        }
        int a = Math.min(rangeAnchor, index);
        int b = Math.max(rangeAnchor, index);
        List<Integer> idxs = new ArrayList<>();
        for (int i = a; i <= b; i++) {
            idxs.add(i);
        }
        rangeAnchor = -1;
        setHiddenMany(idxs, true);
    }

    private int visibleCount() {
        int n = 0;
        if (previewRaw == null) {
            return 0;
        }
        for (TrackPoint x : previewRaw.points) {
            if (!x.hidden) {
                n++;
            }
        }
        return n;
    }

    private void setHiddenMany(List<Integer> idxs, boolean hide) {
        if (previewRaw == null || idxs == null || idxs.isEmpty()) {
            return;
        }
        int wouldHide = 0;
        int vis = visibleCount();
        for (int index : idxs) {
            if (index < 0 || index >= previewRaw.points.size()) {
                continue;
            }
            if (hide && !previewRaw.points.get(index).hidden) {
                wouldHide++;
            }
        }
        if (hide && vis - wouldHide < 1) {
            Toast.makeText(this, "至少留一个点", Toast.LENGTH_SHORT).show();
            return;
        }
        int n = 0;
        for (int index : idxs) {
            if (index < 0 || index >= previewRaw.points.size()) {
                continue;
            }
            TrackPoint p = previewRaw.points.get(index);
            if (p.hidden == hide) {
                continue;
            }
            p.hidden = hide;
            n++;
        }
        if (n == 0) {
            Toast.makeText(this, hide ? "这些点已经去掉了" : "这些点本来就在", Toast.LENGTH_SHORT).show();
            return;
        }
        persistPreview(hide
                ? "已去掉 " + n + " 个点（还在库里，可恢复）"
                : "已恢复 " + n + " 个点");
    }

    private void restoreAllHidden() {
        if (previewRaw == null) {
            return;
        }
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < previewRaw.points.size(); i++) {
            if (previewRaw.points.get(i).hidden) {
                idxs.add(i);
            }
        }
        if (idxs.isEmpty()) {
            Toast.makeText(this, "没有去掉的点", Toast.LENGTH_SHORT).show();
            return;
        }
        setHiddenMany(idxs, false);
    }

    private void toggleHiddenPoint(int index) {
        if (previewRaw == null || index < 0 || index >= previewRaw.points.size()) {
            return;
        }
        focusHopIndex = index;
        TrackPoint p = previewRaw.points.get(index);
        List<Integer> one = new ArrayList<>();
        one.add(index);
        setHiddenMany(one, !p.hidden);
    }

    private String previewBanner(TrackSession s) {
        StringBuilder b = new StringBuilder();
        String origin = Formats.originTag(s);
        if (!origin.isEmpty()) {
            b.append('[').append(origin).append("]  ");
        }
        if (TrackEngine.isAuto(s.mode)) {
            b.append("走路 ").append(Formats.distance(s.walkDistanceM));
            if (s.walkSteps > 0) {
                b.append("  ·  ").append(Formats.steps(s.walkSteps));
                String stride = Formats.strideCm(s.walkDistanceM, s.walkSteps);
                if (!"--".equals(stride)) {
                    b.append("  ·  ").append(stride);
                }
            }
            b.append("  ·  ").append(Formats.duration(s.walkMovingMs));
            if (s.vehicleDistanceM >= 20) {
                b.append("\n车程 ").append(Formats.distance(s.vehicleDistanceM))
                        .append("  ·  ").append(Formats.duration(s.vehicleMovingMs))
                        .append("  ·  ").append(Formats.speed(this, s.vehicleAvgMps()));
            }
        } else {
            b.append(Formats.distance(s.distanceM));
            if (s.steps > 0) {
                b.append("  ·  ").append(Formats.steps(s.steps));
                String stride = Formats.strideCm(s.distanceM, s.steps);
                if (!"--".equals(stride)) {
                    b.append("  ·  ").append(stride);
                } else if (s.steps >= 5 && s.distanceM >= 5) {
                    b.append("  ·  步幅 ")
                            .append(Math.round(s.distanceM / s.steps * 100.0))
                            .append(" 厘米");
                }
            } else {
                b.append("  ·  步数 --");
            }
            b.append("  ·  ").append(Formats.duration(s.movingMs));
        }
        String clean = TrackClean.hint(s);
        if (!clean.isEmpty()) {
            b.append("  ·  ").append(clean);
        }
        String batt = BatterySnap.savedShort(s);
        if (!batt.isEmpty()) {
            b.append("  ·  ").append(batt);
        }
        b.append('\n');
        String name = Formats.nz(s.title);
        b.append(name.isEmpty() ? Formats.modeLabel(s.mode) : name);
        String r = Formats.routeLine(s);
        if (!r.isEmpty()) {
            b.append("  ").append(r);
        }
        b.append("  ").append(Formats.when(s.startMs));
        if (!s.marks.isEmpty()) {
            b.append("  ").append(Formats.marksCountLine(s.marks));
        }
        b.append("\n飞点阈值 ").append(TrackClean.hopLimitLabel(this, s));
        if (editingPoints) {
            b.append("  ·  点地图改点");
        }
        return b.toString();
    }

    private void toggleMeasureStats() {
        if (previewingHistory) {
            return;
        }
        setMeasureStatsOpen(!measureStatsOpen);
    }

    private void setMeasureStatsOpen(boolean open) {
        measureStatsOpen = open;
        Prefs.setMeasureStatsOpen(this, open);
        applyMeasureStatsFold();
    }

    private void applyMeasureStatsFold() {
        if (statsDetail == null || previewingHistory) {
            return;
        }
        statsDetail.setVisibility(measureStatsOpen ? View.VISIBLE : View.GONE);
        if (cardStats != null) {
            float d = getResources().getDisplayMetrics().density;
            int pad = Math.round(14 * d);
            int padV = measureStatsOpen ? pad : Math.round(8 * d);
            cardStats.setPadding(pad, padV, pad, padV);
        }
        updateFoldHandle();
        if (web != null) {
            web.post(() -> js("if(map)map.invalidateSize()"));
        }
    }

    private void updateFoldHandle() {
        if (txtStatsFold == null) {
            return;
        }
        if (measureStatsOpen) {
            txtStatsFold.setText("收起，多看地图  ▾");
            return;
        }
        String dist;
        if (engine != null && TrackEngine.isAuto(engine.session().mode)) {
            dist = "走路 " + Formats.distance(engine.session().walkDistanceM);
        } else {
            dist = txtDistance != null ? String.valueOf(txtDistance.getText()) : "";
        }
        String time = txtTime != null ? String.valueOf(txtTime.getText()) : "";
        StringBuilder b = new StringBuilder("▴  ");
        if (!dist.isEmpty()) {
            b.append(dist);
        }
        if (!time.isEmpty()) {
            if (b.length() > 3) {
                b.append("  ·  ");
            }
            b.append(time);
        }
        if (btnPrimary != null) {
            CharSequence st = btnPrimary.getText();
            if ("暂停".contentEquals(st)) {
                b.append("  ·  测量中");
            } else if ("继续".contentEquals(st)) {
                b.append("  ·  已暂停");
            }
        }
        b.append("  ·  点这里展开");
        txtStatsFold.setText(b.toString());
    }

    private void setHistoryFullscreen(boolean on) {
        int chrome = on ? View.GONE : View.VISIBLE;
        if (toolbar != null) {
            toolbar.setVisibility(chrome);
        }
        tabs.setVisibility(chrome);
        if (cardStats != null) {
            cardStats.setVisibility(chrome);
        }
        if (!on) {
            applyMeasureStatsFold();
        }
        if (btnMode != null) {
            btnMode.setVisibility(chrome);
        }
        js("setHist(" + (on ? "true" : "false") + ")");
        txtGps.setVisibility(chrome);
        if (txtKeepAliveBanner != null && on) {
            txtKeepAliveBanner.setVisibility(View.GONE);
        }
        historyBanner.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void exitPreview() {
        exitPreview(true);
    }

    private void exitPreview(boolean backToHistory) {
        setSpanPicking(false);
        editingPoints = false;
        rangeMode = false;
        rangeAnchor = -1;
        focusHopIndex = -1;
        js("setEditPts(false)");
        js("setDots(" + JSONObject.quote("[]") + ")");
        previewingHistory = false;
        previewRaw = null;
        engine.setPreviewing(false);
        setHistoryFullscreen(false);
        follow = true;
        js("setFollow(true)");
        js("clearPath();clearMarks()");
        drawnPoints = 0;
        drawnMarks = 0;
        TrackSession cur = engine.session();
        if (engine.isActive()) {
            redrawPath(cur);
        }
        engine.refresh();
        if (backToHistory && tabs != null) {
            tabs.check(R.id.tab_history);
        }
    }

    private void refreshHistory() {
        historyAll.clear();
        historyAll.addAll(TrackClean.views(this, engine.store().list()));
        applyHistoryFilter();
    }

    private void setupStatsUi() {
        if (spinnerStats == null) {
            return;
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TrackStats.FILTER_LABELS);
        spinnerStats.setAdapter(a);
        spinnerStats.setSelection(TrackStats.indexOf(Prefs.statsFilter(this)));
        spinnerStats.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < TrackStats.FILTER_KEYS.length) {
                    Prefs.setStatsFilter(MainActivity.this, TrackStats.FILTER_KEYS[position]);
                }
                refreshStats();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        View share = findViewById(R.id.btn_share_stats);
        if (share != null) {
            share.setOnClickListener(v -> shareStats());
        }
        if (statsKind != null) {
            statsKind.check(Prefs.statsCharts(this) ? R.id.stats_kind_charts : R.id.stats_kind_cards);
            statsKind.addOnButtonCheckedListener((group, id, checked) -> {
                if (!checked) {
                    return;
                }
                Prefs.setStatsCharts(this, id == R.id.stats_kind_charts);
                applyStatsPane();
                refreshCharts();
            });
        }
        setupChartsWeb();
    }

    private void setupChartsWeb() {
        if (webCharts == null) {
            return;
        }
        WebSettings s = webCharts.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webCharts.setBackgroundColor(0xFFF1F5F9);
        webCharts.addJavascriptInterface(new ChartBridge(this, engine.store()), "TrackApp");
        webCharts.setWebChromeClient(new WebChromeClient());
        webCharts.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                chartsReady = true;
                refreshCharts();
            }
        });
        webCharts.loadUrl("file:///android_asset/charts.html");
    }

    private void refreshStats() {
        if (statsContainer == null) {
            return;
        }
        if (txtStatsVersion != null) {
            txtStatsVersion.setText("应用版本 " + BuildConfig.VERSION_NAME
                    + "（versionCode " + BuildConfig.VERSION_CODE
                    + "）  ·  卡片看数字，图表看走势");
        }
        statsContainer.removeAllViews();
        List<TrackSession> all = engine.store().list();
        if (all.isEmpty()) {
            statsHasData = false;
            if (emptyStats != null) {
                emptyStats.setVisibility(View.VISIBLE);
                emptyStats.setText("还没有运动记录。到「测量」点开始，走一段再结束，就会出现在这里。");
            }
            applyStatsPane();
            return;
        }
        String filter = Prefs.statsFilter(this);
        if (spinnerStats != null && spinnerStats.getSelectedItemPosition() >= 0
                && spinnerStats.getSelectedItemPosition() < TrackStats.FILTER_KEYS.length) {
            filter = TrackStats.FILTER_KEYS[spinnerStats.getSelectedItemPosition()];
        }
        List<TrackStats.Card> cards = TrackStats.compute(this, all, filter);
        if (cards.isEmpty()) {
            statsHasData = false;
            if (emptyStats != null) {
                emptyStats.setVisibility(View.VISIBLE);
                emptyStats.setText("这一筛选下没有记录，换「全部」或别的时间看看。");
            }
            applyStatsPane();
            return;
        }
        statsHasData = true;
        if (emptyStats != null) {
            emptyStats.setVisibility(View.GONE);
        }
        LayoutInflater inf = getLayoutInflater();
        for (TrackStats.Card card : cards) {
            View row = inf.inflate(R.layout.item_stat_card, statsContainer, false);
            ((TextView) row.findViewById(R.id.stat_title)).setText(card.title);
            ((TextView) row.findViewById(R.id.stat_value)).setText(card.value);
            TextView note = row.findViewById(R.id.stat_note);
            if (card.note.isEmpty()) {
                note.setVisibility(View.GONE);
            } else {
                note.setVisibility(View.VISIBLE);
                note.setText(card.note);
            }
            statsContainer.addView(row);
        }
        applyStatsPane();
        refreshCharts();
    }

    private boolean wantCharts() {
        return statsKind != null && statsKind.getCheckedButtonId() == R.id.stats_kind_charts;
    }

    private void applyStatsPane() {
        View scroll = findViewById(R.id.scroll_stats);
        boolean charts = wantCharts();
        if (!statsHasData || !charts) {
            leaveChartFullscreen();
        }
        if (!statsHasData) {
            if (scroll != null) {
                scroll.setVisibility(View.GONE);
            }
            if (webCharts != null) {
                webCharts.setVisibility(View.GONE);
            }
            return;
        }
        if (scroll != null) {
            scroll.setVisibility(charts ? View.GONE : View.VISIBLE);
        }
        if (webCharts != null) {
            webCharts.setVisibility(charts ? View.VISIBLE : View.GONE);
        }
    }

    void setChartFullscreen(boolean on) {
        if (isFinishing()) {
            return;
        }
        if (chartFullscreen == on) {
            if (on) {
                scheduleChartResize();
            }
            return;
        }
        chartFullscreen = on;
        int chrome = on ? View.GONE : View.VISIBLE;
        if (toolbar != null) {
            toolbar.setVisibility(chrome);
        }
        if (tabs != null) {
            tabs.setVisibility(chrome);
        }
        if (statsFilterRow != null) {
            statsFilterRow.setVisibility(chrome);
        }
        if (statsKind != null) {
            statsKind.setVisibility(chrome);
        }
        if (txtStatsVersion != null) {
            txtStatsVersion.setVisibility(chrome);
        }
        if (paneStats != null) {
            int p = on ? 0 : Math.round(10 * getResources().getDisplayMetrics().density);
            paneStats.setPadding(p, p, p, p);
        }
        setChartSystemBars(on);
        if (on) {
            scheduleChartResize();
        } else if (webCharts != null) {
            webCharts.removeCallbacks(resizeChartOverlay);
        }
    }

    private void leaveChartFullscreen() {
        if (webCharts != null && chartsReady && chartFullscreen) {
            webCharts.evaluateJavascript(
                    "(function(){if(window.closeOverlay)closeOverlay();})()", null);
        }
        setChartFullscreen(false);
    }

    private void scheduleChartResize() {
        if (webCharts == null) {
            return;
        }
        webCharts.removeCallbacks(resizeChartOverlay);
        webCharts.requestLayout();
        webCharts.post(resizeChartOverlay);
        webCharts.postDelayed(resizeChartOverlay, 80);
        webCharts.postDelayed(resizeChartOverlay, 250);
        webCharts.postDelayed(resizeChartOverlay, 480);
    }

    private void setChartSystemBars(boolean hide) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) {
                return;
            }
            int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
            if (hide) {
                c.hide(types);
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(types);
            }
            return;
        }
        View decor = getWindow().getDecorView();
        if (hide) {
            savedSysUi = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else if (savedSysUi != Integer.MIN_VALUE) {
            decor.setSystemUiVisibility(savedSysUi);
        }
    }

    private void refreshCharts() {
        if (webCharts == null || !chartsReady || webCharts.getVisibility() != View.VISIBLE) {
            return;
        }
        webCharts.evaluateJavascript("boot()", null);
    }

    private void shareStats() {
        List<TrackSession> all = engine.store().list();
        if (all.isEmpty()) {
            Toast.makeText(this, "还没有记录", Toast.LENGTH_SHORT).show();
            return;
        }
        String filter = Prefs.statsFilter(this);
        String text = TrackStats.shareText(this, all, filter);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "阿米测距统计");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "分享统计"));
    }

    private void setupHistoryTools() {
        if (editHistoryQ != null) {
            editHistoryQ.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    applyHistoryFilter();
                }
            });
            editHistoryQ.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideHistoryKeyboard();
                    return true;
                }
                return false;
            });
        }
        if (sortTime != null) {
            sortTime.setOnClickListener(v -> onHistorySort(HistoryQuery.SORT_TIME));
        }
        if (sortKm != null) {
            sortKm.setOnClickListener(v -> onHistorySort(HistoryQuery.SORT_KM));
        }
        if (sortStepsBtn != null) {
            sortStepsBtn.setOnClickListener(v -> onHistorySort(HistoryQuery.SORT_STEPS));
        }
        if (sortSpeedBtn != null) {
            sortSpeedBtn.setOnClickListener(v -> onHistorySort(HistoryQuery.SORT_SPEED));
        }
        paintHistorySortButtons();
    }

    private void onHistorySort(String key) {
        if (key.equals(historySort)) {
            historySortDesc = !historySortDesc;
        } else {
            historySort = key;
            historySortDesc = true;
        }
        Prefs.setHistorySort(this, historySort, historySortDesc);
        paintHistorySortButtons();
        applyHistoryFilter();
    }

    private void paintHistorySortButtons() {
        paintHistorySort(sortTime, HistoryQuery.SORT_TIME, "时间");
        paintHistorySort(sortKm, HistoryQuery.SORT_KM, "公里");
        paintHistorySort(sortStepsBtn, HistoryQuery.SORT_STEPS, "步数");
        paintHistorySort(sortSpeedBtn, HistoryQuery.SORT_SPEED, "速度");
    }

    private void paintHistorySort(MaterialButton b, String key, String label) {
        if (b == null) {
            return;
        }
        boolean on = key.equals(historySort);
        b.setText(on ? label + (historySortDesc ? " ↓" : " ↑") : label);
        b.setBackgroundTintList(ColorStateList.valueOf(on ? 0xFF0F766E : 0xFFF8FAFC));
        b.setTextColor(on ? 0xFFFFFFFF : 0xFF0F766E);
    }

    private void applyHistoryFilter() {
        String q = editHistoryQ == null || editHistoryQ.getText() == null
                ? "" : editHistoryQ.getText().toString();
        history.clear();
        for (TrackSession s : historyAll) {
            if (HistoryQuery.matches(s, q)) {
                history.add(s);
            }
        }
        Collections.sort(history, (a, b) -> HistoryQuery.compare(a, b, historySort, historySortDesc));
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
        boolean none = history.isEmpty();
        if (txtHistoryEmpty != null) {
            txtHistoryEmpty.setVisibility(none ? View.VISIBLE : View.GONE);
            if (none) {
                String trimmed = q.trim();
                if (historyAll.isEmpty()) {
                    txtHistoryEmpty.setText("还没有记录。没网也能测：打开测量页走一段再点结束，联网后可以看地图和地点。");
                } else {
                    txtHistoryEmpty.setText("没有匹配「" + trimmed + "」的记录");
                }
            }
        }
        if (listHistory != null) {
            listHistory.setVisibility(none ? View.GONE : View.VISIBLE);
        }
        if (txtHistoryCount != null) {
            if (historyAll.isEmpty()) {
                txtHistoryCount.setVisibility(View.GONE);
            } else {
                txtHistoryCount.setVisibility(View.VISIBLE);
                String order = HistoryQuery.sortLabel(historySort, historySortDesc);
                String clean = Prefs.histClean(this) ? " · 实际移动" : "";
                if (q.trim().isEmpty()) {
                    txtHistoryCount.setText(historyAll.size() + " 条 · " + order
                            + clean + " · 再点同一项反过来");
                } else {
                    txtHistoryCount.setText(history.size() + " / " + historyAll.size()
                            + " 条 · " + order + clean);
                }
            }
        }
    }

    private void hideHistoryKeyboard() {
        if (editHistoryQ == null) {
            return;
        }
        editHistoryQ.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editHistoryQ.getWindowToken(), 0);
        }
    }

    private void loadSettingsUi() {
        swAuto.setOnCheckedChangeListener(null);
        swAuto.setChecked(Prefs.autoStart(this));
        swAuto.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAutoStart(this, checked);
            engine.refresh();
        });
        swAutoMark.setOnCheckedChangeListener(null);
        swAutoMark.setChecked(Prefs.autoMarkOn(this));
        editAutoMarkMin.setText(String.valueOf(Prefs.autoMarkMinStored(this)));
        swAutoMark.setOnCheckedChangeListener((b, checked) -> persistAutoMark());
        bindAutoStillSwitch();
        bindGpsHoldSwitches();
        bindHistCleanSwitches();
        speedUnitGroup.check(Formats.speedUnitButtonId(Prefs.speedUnit(this)));
        editWeight.setText(trimNum(Prefs.weightJin(this)));
        editAccuracy.setText(String.valueOf(Prefs.maxAccuracyM(this)));
        editKey.setText(Prefs.amapKey(this));
        editGoalKm.setText(metersToKmField(Prefs.goalTripM(this)));
        editGoalSteps.setText(stepsField(Prefs.goalTripSteps(this)));
        editEveryKm.setText(metersToKmField(Prefs.everyM(this)));
        editEverySteps.setText(stepsField(Prefs.everySteps(this)));
        editTodayGoal.setText(stepsField(Prefs.todayGoalSteps(this)));
        bindGoalSwitch(swGoalKm, Prefs.goalKmOn(this));
        bindGoalSwitch(swGoalSteps, Prefs.goalStepsOn(this));
        bindGoalSwitch(swEveryKm, Prefs.everyKmOn(this));
        bindGoalSwitch(swEverySteps, Prefs.everyStepsOn(this));
        bindGoalSwitch(swTodayGoal, Prefs.todayGoalOn(this));
        for (SoundRow row : soundRows.values()) {
            loadSoundRow(row);
        }
        if (txtCache != null) {
            txtCache.setText(TileCache.sizeLabel(this));
        }
        refreshKeepAliveUi(KeepAlive.inspect(this));
        if (txtSync != null) {
            txtSync.setText(SyncPack.statusText(this));
        }
    }

    private void cycleSpeedUnit() {
        String next = Formats.nextSpeedUnit(Prefs.speedUnit(this));
        Prefs.setSpeedUnit(this, next);
        speedUnitGroup.check(Formats.speedUnitButtonId(next));
        engine.refresh();
        Toast.makeText(this, "时速单位：" + Formats.speedUnitLabel(next), Toast.LENGTH_SHORT).show();
    }

    private void showSyncHelp() {
        new AlertDialog.Builder(this)
                .setTitle("多机同步")
                .setMessage("推荐：几台手机都打开「设置 → 附近同步」，连同一 WiFi 或一台开热点另一台连上，点列表里的对方手机。发现不到就手填对方屏幕上的 IP。也可以都打开蓝牙，点带「蓝牙」的那一行。\n\n"
                        + "先给本机起个备注（如「妈妈手机」），对方列表里能看见。长按对方那一项可以再写你自己的备注（如「奶奶家」）。\n\n"
                        + "不用网盘、不用账号，数据只在这两台手机之间传。对话框关掉就停止等待。只同步已保存的记录，进行中的测量不同步。\n\n"
                        + "备选：各手机选同一个网盘文件夹，点「文件夹同步」。")
                .setPositiveButton("附近同步", (d, w) -> showNearbySync())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void pickSyncTree() {
        pickTreeForBackup = false;
        launchPickTree();
    }

    private void pickBackupFolder() {
        pickTreeForBackup = true;
        launchPickTree();
    }

    private void launchPickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        pickTree.launch(i);
    }

    private String snapshotBackup() {
        return Backups.autoEnabled(this) ? Backups.create(this, engine.store()) : null;
    }

    private void backupIfDue() {
        Backups.schedule(this);
        boolean edits = Backups.due(this);
        if (!edits && !Backups.scheduledDue(this)) {
            return;
        }
        TrackStore store = engine.store();
        Context app = getApplicationContext();
        new Thread(() -> {
            if (edits) {
                Backups.create(app, store);
            } else {
                Backups.runScheduled(app, store);
            }
        }).start();
    }

    private void showBackupPanel() {
        if (backupDialog != null && backupDialog.isShowing()) {
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        SwitchCompat auto = v.findViewById(R.id.backup_auto);
        TextView status = v.findViewById(R.id.backup_status);
        TextView schedule = v.findViewById(R.id.backup_schedule);
        TextView location = v.findViewById(R.id.backup_location);
        TextView head = v.findViewById(R.id.backup_list_head);
        TextView empty = v.findViewById(R.id.backup_empty);
        ListView list = v.findViewById(R.id.backup_list);
        Spinner keep = v.findViewById(R.id.backup_keep);
        Spinner period = v.findViewById(R.id.backup_period);

        List<Backups.Entry> entries = new ArrayList<>();
        BackupAdapter backupAdapter = new BackupAdapter(entries);
        list.setAdapter(backupAdapter);
        list.setOnItemClickListener((p, view, pos, id) -> confirmRestoreBackup(entries.get(pos)));

        List<String> keepLabels = new ArrayList<>();
        for (int k : Backups.KEEP_OPTIONS) {
            keepLabels.add("最近 " + k + " 份");
        }
        keep.setAdapter(spinnerAdapter(keepLabels));
        keep.setSelection(keepIndex(Backups.keep(this)));
        keep.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int chosen = Backups.KEEP_OPTIONS[position];
                if (chosen == Backups.keep(MainActivity.this)) {
                    return;
                }
                Backups.setKeep(MainActivity.this, chosen);
                new Thread(() -> {
                    Backups.rotateNow(MainActivity.this);
                    runOnUiThread(MainActivity.this::refreshBackupPanel);
                }).start();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        period.setAdapter(spinnerAdapter(Arrays.asList(Backups.PERIOD_LABELS)));
        period.setSelection(periodIndex(Backups.periodDays(this)));
        period.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int chosen = Backups.PERIOD_OPTIONS[position];
                if (chosen == Backups.periodDays(MainActivity.this)) {
                    return;
                }
                Backups.setPeriodDays(MainActivity.this, chosen);
                refreshBackupPanel();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        backupPanelRefresh = () -> {
            status.setText(Backups.statusLine(this));
            schedule.setText(Backups.scheduleLine(this));
            location.setText(Backups.locationLabel(this));
            entries.clear();
            entries.addAll(Backups.list(this));
            backupAdapter.notifyDataSetChanged();
            boolean none = entries.isEmpty();
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            list.setVisibility(none ? View.GONE : View.VISIBLE);
            head.setText(none ? "" : String.format(Locale.US,
                    "共 %d 份，占用 %s · 点按一份可覆盖恢复",
                    entries.size(), Backups.size(Backups.totalSize(entries))));
        };

        auto.setChecked(Backups.autoEnabled(this));
        auto.setOnCheckedChangeListener((b, on) -> {
            Backups.setAutoEnabled(this, on);
            refreshBackupPanel();
        });
        v.findViewById(R.id.btn_backup_now).setOnClickListener(x -> runManualBackup());
        v.findViewById(R.id.btn_backup_folder).setOnClickListener(x -> pickBackupFolder());
        v.findViewById(R.id.btn_backup_internal).setOnClickListener(x -> {
            Backups.setFolder(this, null);
            refreshBackupPanel();
            Toast.makeText(this, "备份改存应用目录（文件夹同步仍可用已选目录）",
                    Toast.LENGTH_SHORT).show();
        });
        refreshBackupPanel();

        backupDialog = new AlertDialog.Builder(this)
                .setTitle("备份与恢复")
                .setView(v)
                .setPositiveButton("关闭", null)
                .create();
        backupDialog.setOnDismissListener(d -> {
            backupDialog = null;
            backupPanelRefresh = null;
        });
        backupDialog.show();
    }

    private ArrayAdapter<String> spinnerAdapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private int keepIndex(int value) {
        for (int i = 0; i < Backups.KEEP_OPTIONS.length; i++) {
            if (Backups.KEEP_OPTIONS[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private int periodIndex(int value) {
        for (int i = 0; i < Backups.PERIOD_OPTIONS.length; i++) {
            if (Backups.PERIOD_OPTIONS[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private void refreshBackupPanel() {
        if (backupPanelRefresh != null) {
            backupPanelRefresh.run();
        }
    }

    private void runManualBackup() {
        AlertDialog dialog = showBusy("正在备份…");
        new Thread(() -> {
            String message = Backups.create(this, engine.store());
            runOnUiThread(() -> {
                dismissBusy(dialog);
                refreshBackupPanel();
                Toast.makeText(this, message == null ? "没有数据可备份" : message,
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void confirmRestoreBackup(Backups.Entry e) {
        if (engine.isActive()) {
            Toast.makeText(this, "正在测量，结束后再恢复备份", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("恢复备份")
                .setMessage("用 " + e.title() + " 覆盖当前全部记录？\n覆盖前会先给当前数据存一份备份。")
                .setPositiveButton("覆盖恢复", (d, w) -> {
                    AlertDialog dialog = showBusy("正在恢复备份…");
                    new Thread(() -> {
                        snapshotBackup();
                        String message;
                        try {
                            message = Backups.restore(this, engine.store(), e);
                        } catch (Exception ex) {
                            message = "恢复失败：" + (ex.getMessage() == null
                                    ? ex.getClass().getSimpleName() : ex.getMessage());
                        }
                        final String toastMsg = message;
                        runOnUiThread(() -> {
                            dismissBusy(dialog);
                            refreshHistory();
                            loadSettingsUi();
                            refreshBackupPanel();
                            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteBackup(Backups.Entry e) {
        new AlertDialog.Builder(this)
                .setMessage("删除备份 " + e.name + "？")
                .setPositiveButton("删除", (d, w) -> {
                    Toast.makeText(this, Backups.delete(this, e) ? "已删除" : "删除失败",
                            Toast.LENGTH_SHORT).show();
                    refreshBackupPanel();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private AlertDialog showBusy(String msg) {
        TextView t = new TextView(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        t.setText(msg);
        AlertDialog d = new AlertDialog.Builder(this)
                .setView(t)
                .setCancelable(false)
                .create();
        d.show();
        return d;
    }

    private void dismissBusy(AlertDialog d) {
        if (d != null && d.isShowing()) {
            d.dismiss();
        }
    }

    private void runFolderSync(boolean force) {
        String msg = SyncPack.syncFolder(this, engine.store(), force);
        loadSettingsUi();
        refreshHistory();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showNearbySync() {
        String[] need = NearbySession.runtimePerms();
        List<String> missing = new ArrayList<>();
        for (String p : need) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            nearbyPerms.launch(missing.toArray(new String[0]));
            return;
        }
        openNearbyDialog();
    }

    private void openNearbyDialog() {
        if (nearbyDialog != null && nearbyDialog.isShowing()) {
            return;
        }
        if (nearbySession != null) {
            nearbySession.stop();
            nearbySession = null;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_nearby_sync, null);
        TextView status = view.findViewById(R.id.text_nearby_status);
        EditText inputIp = view.findViewById(R.id.input_nearby_ip);
        EditText inputNick = view.findViewById(R.id.input_phone_nick);
        ListView list = view.findViewById(R.id.list_nearby_peers);
        inputNick.setText(Prefs.phoneNick(this));
        if (inputNick.getText().length() == 0) {
            inputNick.setHint("本机备注，如 妈妈手机（现在显示 " + PhoneNotes.selfName(this) + "）");
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);
        nearbyPeers.clear();
        nearbySession = new NearbySession(this, engine.store(), new NearbySession.Callbacks() {
            @Override
            public void onStatus(String line) {
                status.setText(line);
            }

            @Override
            public void onPeers(List<NearbySession.Peer> peers) {
                nearbyPeers.clear();
                nearbyPeers.addAll(peers);
                List<String> labels = new ArrayList<>();
                for (NearbySession.Peer p : peers) {
                    labels.add(p.displayLabel(MainActivity.this));
                }
                adapter.clear();
                adapter.addAll(labels);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onMerged(String message) {
                refreshHistory();
                loadSettingsUi();
                if (nearbySession != null) {
                    nearbySession.relabel();
                }
                status.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void requestBtDiscoverable() {
                try {
                    Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    btDiscoverable.launch(i);
                } catch (Exception ignored) {
                }
            }
        });
        view.findViewById(R.id.btn_save_phone_nick).setOnClickListener(v -> {
            String nick = PhoneNotes.sanitize(inputNick.getText().toString());
            Prefs.setPhoneNick(this, nick);
            inputNick.setText(nick);
            if (nearbySession != null) {
                nearbySession.relabel();
            }
            Toast.makeText(this, "已保存本机备注：" + PhoneNotes.selfName(this) + "。对方刷新后能看到。",
                    Toast.LENGTH_LONG).show();
        });
        view.findViewById(R.id.btn_nearby_connect_ip).setOnClickListener(v ->
                nearbySession.connectIp(inputIp.getText().toString()));
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < nearbyPeers.size()) {
                NearbySession.Peer peer = nearbyPeers.get(pos);
                status.setText("正在连接 " + peer.displayLabel(this) + " …");
                nearbySession.connect(peer);
            }
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < nearbyPeers.size()) {
                editPeerNote(nearbyPeers.get(pos));
            }
            return true;
        });
        nearbyDialog = new AlertDialog.Builder(this)
                .setTitle("附近同步")
                .setView(view)
                .setNeutralButton("刷新", null)
                .setNegativeButton("关闭", null)
                .create();
        nearbyDialog.setOnShowListener(d -> nearbyDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    if (nearbySession != null) {
                        nearbySession.refresh();
                    }
                }));
        nearbyDialog.setOnDismissListener(d -> {
            if (nearbySession != null) {
                nearbySession.stop();
                nearbySession = null;
            }
            nearbyDialog = null;
        });
        nearbyDialog.show();
        nearbySession.start();
    }

    private void editPeerNote(NearbySession.Peer peer) {
        final EditText input = new EditText(this);
        input.setHint("如 奶奶家、爸爸手机");
        input.setText(PhoneNotes.note(this, peer.keys()));
        new AlertDialog.Builder(this)
                .setTitle("备注这台手机")
                .setMessage("只存在你这台手机上，用来认出是谁。对方自己设的本机备注也会显示；你的备注优先。")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    PhoneNotes.setNote(this, input.getText().toString(), peer.keys());
                    if (nearbySession != null) {
                        nearbySession.relabel();
                    }
                    Toast.makeText(this, "已备注为 " + PhoneNotes.display(this,
                            peer.advertisedNick, peer.fallback, peer.keys()), Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("清除备注", (d, w) -> {
                    PhoneNotes.setNote(this, "", peer.keys());
                    if (nearbySession != null) {
                        nearbySession.relabel();
                    }
                    Toast.makeText(this, "已清除备注", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveSettings() {
        Prefs.setAutoStart(this, swAuto.isChecked());
        persistAutoMark();
        persistTrackClean();
        persistGpsHold();
        Prefs.setSpeedUnit(this, Formats.speedUnitFromButton(speedUnitGroup.getCheckedButtonId()));
        engine.refresh();
        try {
            float w = Float.parseFloat(editWeight.getText().toString().trim());
            if (w >= 20 && w <= 400) {
                Prefs.setWeightJin(this, w);
            }
        } catch (Exception ignored) {
        }
        try {
            int a = Integer.parseInt(editAccuracy.getText().toString().trim());
            if (a >= 8 && a <= 200) {
                Prefs.setMaxAccuracyM(this, a);
            }
        } catch (Exception ignored) {
        }
        String key = editKey.getText().toString().trim();
        if (!key.isEmpty()) {
            Prefs.setAmapKey(this, key);
        }
        persistGoals();
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    private int readAutoMarkMin() {
        try {
            int v = Integer.parseInt(editAutoMarkMin.getText().toString().trim());
            if (v >= 1 && v <= 30) {
                return v;
            }
        } catch (Exception ignored) {
        }
        return Prefs.autoMarkMinStored(this);
    }

    private void persistAutoMark() {
        Prefs.setAutoMark(this, swAutoMark.isChecked(), readAutoMarkMin());
        engine.refresh();
    }

    private void persistTrackClean() {
        if (swAutoStill != null) {
            Prefs.setAutoStill(this, swAutoStill.isChecked());
        }
        SwitchCompat src = swHistCleanSettings != null ? swHistCleanSettings : swHistClean;
        if (src != null) {
            Prefs.setHistClean(this, src.isChecked());
        }
        bindHistCleanSwitches();
        engine.refresh();
        refreshHistory();
        refreshStats();
    }

    private void bindAutoStillSwitch() {
        if (swAutoStill == null) {
            return;
        }
        swAutoStill.setOnCheckedChangeListener(null);
        swAutoStill.setChecked(Prefs.autoStill(this));
        swAutoStill.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setAutoStill(this, checked);
            engine.refresh();
        });
    }

    private void persistGpsHold() {
        if (swWaitGps != null) {
            Prefs.setWaitGpsStart(this, swWaitGps.isChecked());
        }
        if (swRecordGpsLost != null) {
            Prefs.setRecordIfGpsLost(this, swRecordGpsLost.isChecked());
        }
        engine.refresh();
    }

    private void bindGpsHoldSwitches() {
        if (swWaitGps != null) {
            swWaitGps.setOnCheckedChangeListener(null);
            swWaitGps.setChecked(Prefs.waitGpsStart(this));
            swWaitGps.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setWaitGpsStart(this, checked);
                engine.refresh();
            });
        }
        if (swRecordGpsLost != null) {
            swRecordGpsLost.setOnCheckedChangeListener(null);
            swRecordGpsLost.setChecked(Prefs.recordIfGpsLost(this));
            swRecordGpsLost.setOnCheckedChangeListener((b, checked) -> {
                Prefs.setRecordIfGpsLost(this, checked);
                engine.refresh();
            });
        }
    }

    private void bindHistCleanSwitches() {
        if (swHistClean != null) {
            swHistClean.setOnCheckedChangeListener(null);
            swHistClean.setChecked(Prefs.histClean(this));
            swHistClean.setOnCheckedChangeListener(this::onHistCleanChanged);
        }
        if (swHistCleanSettings != null) {
            swHistCleanSettings.setOnCheckedChangeListener(null);
            swHistCleanSettings.setChecked(Prefs.histClean(this));
            swHistCleanSettings.setOnCheckedChangeListener(this::onHistCleanChanged);
        }
    }

    private void onHistCleanChanged(CompoundButton b, boolean checked) {
        Prefs.setHistClean(this, checked);
        if (swHistClean != null && swHistClean != b) {
            swHistClean.setOnCheckedChangeListener(null);
            swHistClean.setChecked(checked);
            swHistClean.setOnCheckedChangeListener(this::onHistCleanChanged);
        }
        if (swHistCleanSettings != null && swHistCleanSettings != b) {
            swHistCleanSettings.setOnCheckedChangeListener(null);
            swHistCleanSettings.setChecked(checked);
            swHistCleanSettings.setOnCheckedChangeListener(this::onHistCleanChanged);
        }
        refreshHistory();
        refreshStats();
        if (previewingHistory) {
            redrawPreview();
        }
    }

    private void confirmClearTiles() {
        new AlertDialog.Builder(this)
                .setTitle("清空离线地图")
                .setMessage("删掉本机缓存的高德底图？" + TileCache.sizeLabel(this))
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    TileCache.clear(this);
                    loadSettingsUi();
                    js("onNet(" + Net.isOnline(this) + ")");
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void chooseDownloadArea() {
        if (!Net.isOnline(this)) {
            Toast.makeText(this, "下载地图需要联网，下完以后才能离线看", Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[]{
                "当前位置附近 1 公里（走路）",
                "当前位置附近 3 公里（跑步）",
                "当前位置附近 8 公里（骑车，要一会儿）",
                "当前地图看到的范围"
        };
        new AlertDialog.Builder(this)
                .setTitle("下载离线地图")
                .setItems(items, (d, which) -> {
                    if (which == 3) {
                        if (!mapReady) {
                            Toast.makeText(this, "地图还没就绪，稍等再试", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        js("reportBounds()");
                        return;
                    }
                    TrackPoint p = engine.lastFix();
                    if (p == null) {
                        p = lastPoint(engine.session());
                    }
                    if (p == null) {
                        Toast.makeText(this, "还没有定位，先到测量页等 GPS，或改选当前视野", Toast.LENGTH_LONG).show();
                        return;
                    }
                    OfflineMapDownloader.Job job;
                    if (which == 0) {
                        job = OfflineMapDownloader.around(p.latGcj, p.lngGcj, 1000, 14, 17, "附近 1 公里");
                    } else if (which == 1) {
                        job = OfflineMapDownloader.around(p.latGcj, p.lngGcj, 3000, 13, 17, "附近 3 公里");
                    } else {
                        job = OfflineMapDownloader.around(p.latGcj, p.lngGcj, 8000, 12, 16, "附近 8 公里");
                    }
                    confirmDownload(job);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDownload(OfflineMapDownloader.Job job) {
        int n = OfflineMapDownloader.urls(job).size();
        if (n == 0) {
            Toast.makeText(this, "这一片没有可下的图", Toast.LENGTH_SHORT).show();
            return;
        }
        String extra = n > OfflineMapDownloader.MAX_TILES
                ? "\n张数太多，只会下前 " + OfflineMapDownloader.MAX_TILES + " 张。"
                : "";
        new AlertDialog.Builder(this)
                .setTitle("下载「" + job.title + "」")
                .setMessage(OfflineMapDownloader.estimate(job) + extra + "\n请用 Wi-Fi。下完断网也能看底图，街道名仍要联网。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始下载", (d, w) -> startDownload(job))
                .show();
    }

    private void startDownload(OfflineMapDownloader.Job job) {
        downloadCancel.set(false);
        downloadDlg = new AlertDialog.Builder(this)
                .setTitle("正在下载 " + job.title)
                .setMessage("准备中…")
                .setCancelable(false)
                .setNegativeButton("取消", (d, w) -> downloadCancel.set(true))
                .show();
        OfflineMapDownloader.start(this, job, downloadCancel, new OfflineMapDownloader.Listener() {
            @Override
            public void onProgress(int done, int total, int neu, int skip, int fail) {
                if (downloadDlg != null && downloadDlg.isShowing()) {
                    downloadDlg.setMessage("已处理 " + done + " / " + total
                            + "\n新下 " + neu + " · 已有 " + skip + " · 失败 " + fail);
                }
            }

            @Override
            public void onDone(int neu, int skip, int fail, boolean cancelled) {
                if (downloadDlg != null && downloadDlg.isShowing()) {
                    downloadDlg.dismiss();
                }
                downloadDlg = null;
                loadSettingsUi();
                js("onNet(true)");
                String msg = cancelled
                        ? "已取消。新下 " + neu + " 张，已有 " + skip + " 张仍留着。"
                        : "下完了。新下 " + neu + " 张，跳过已有 " + skip + " 张"
                        + (fail > 0 ? "，失败 " + fail + " 张" : "")
                        + "。断网后到测量页就能看底图。";
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(cancelled ? "已取消" : "离线地图就绪")
                        .setMessage(msg)
                        .setPositiveButton("好", null)
                        .show();
            }
        });
    }

    private void jumpKeepAlive(boolean opened) {
        awaitingKeepAliveReturn = opened;
        if (!opened) {
            Toast.makeText(this, "打不开系统页，请到手机设置里搜阿米测距", Toast.LENGTH_LONG).show();
        }
    }

    private void onKeepAliveResume() {
        KeepAlive.Report r = KeepAlive.inspect(this);
        refreshKeepAliveUi(r);
        if (!awaitingKeepAliveReturn) {
            return;
        }
        awaitingKeepAliveReturn = false;
        if (r.allClear) {
            Toast.makeText(this, "后台限制已放开，锁屏也可以继续测", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, r.headline, Toast.LENGTH_LONG).show();
        }
    }

    private void maybePromptKeepAlive() {
        KeepAlive.Report r = KeepAlive.inspect(this);
        refreshKeepAliveUi(r);
        if (r.allClear || keepAlivePromptedThisProcess) {
            return;
        }
        keepAlivePromptedThisProcess = true;
        showKeepAliveDialog(r, false);
    }

    private void showKeepAliveDialog(KeepAlive.Report r, boolean fromBanner) {
        if (isFinishing()) {
            return;
        }
        if (keepAliveDlg != null && keepAliveDlg.isShowing()) {
            return;
        }
        if (r.allClear && fromBanner) {
            Toast.makeText(this, "后台限制已放开", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(r.headline)
                .setMessage(r.dialogText)
                .setNegativeButton("稍后", null)
                .setPositiveButton("去处理", (d, w) -> jumpKeepAlive(KeepAlive.openNext(this, r)));
        if (r.oemFamily && r.batteryIgnored && r.autoStart != KeepAlive.Tri.NO) {
            b.setNeutralButton("我已设好", (d, w) -> confirmOemKeepAlive());
        }
        keepAliveDlg = b.show();
    }

    private void confirmOemKeepAlive() {
        KeepAlive.Report r = KeepAlive.inspect(this);
        if (!r.batteryIgnored) {
            Toast.makeText(this, "电池优化还没放开，先点「去放开电池优化」", Toast.LENGTH_LONG).show();
            return;
        }
        if (r.autoStart == KeepAlive.Tri.NO) {
            Toast.makeText(this, "自启动还是关着，先点「去打开自启动」", Toast.LENGTH_LONG).show();
            return;
        }
        Prefs.setOemKeepAliveOk(this, true);
        refreshKeepAliveUi(KeepAlive.inspect(this));
        Toast.makeText(this, "已记下。锁屏后看通知栏还在不在「走路中 / 跑步中 / 骑车中 / 自动中」", Toast.LENGTH_LONG).show();
    }

    private void refreshKeepAliveUi(KeepAlive.Report r) {
        if (txtKeepAlive != null) {
            txtKeepAlive.setText(r.settingsText);
            txtKeepAlive.setTextColor(r.allClear ? 0xFF0F766E : 0xFFB45309);
        }
        if (txtKeepAliveBanner != null) {
            if (r.allClear) {
                txtKeepAliveBanner.setVisibility(View.GONE);
            } else {
                txtKeepAliveBanner.setVisibility(View.VISIBLE);
                txtKeepAliveBanner.setText(r.bannerText);
            }
        }
        Button battery = findViewById(R.id.btn_battery);
        if (battery != null) {
            battery.setText(r.batteryIgnored ? "电池优化已放开（再看一眼）" : "去放开电池优化");
        }
        if (btnAutoStartSys != null) {
            if (r.oemFamily) {
                btnAutoStartSys.setVisibility(View.VISIBLE);
                btnAutoStartSys.setText(r.brand == KeepAlive.Brand.HUAWEI
                        ? "去设应用启动（三项全开）"
                        : "去打开自启动");
            } else {
                btnAutoStartSys.setVisibility(View.GONE);
            }
        }
        if (btnOem != null) {
            if (r.brand == KeepAlive.Brand.XIAOMI) {
                btnOem.setVisibility(View.VISIBLE);
                btnOem.setText("去设省电策略（选无限制）");
            } else {
                btnOem.setVisibility(View.GONE);
            }
        }
        if (btnKeepAliveDone != null) {
            btnKeepAliveDone.setVisibility(r.oemFamily ? View.VISIBLE : View.GONE);
            btnKeepAliveDone.setText(r.oemConfirmed ? "厂商设置已确认（可再改）" : "厂商那几项我已设好");
        }
    }

    private void startPreviewLocation() {
        if (engine.isActive()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (previewLocations != null) {
            return;
        }
        previewLocations = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            previewLocations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, previewListener);
        } catch (Exception ignored) {
        }
        try {
            previewLocations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, previewNet);
        } catch (Exception ignored) {
        }
    }

    private void stopPreviewLocation() {
        if (previewLocations == null) {
            return;
        }
        try {
            previewLocations.removeUpdates(previewListener);
        } catch (Exception ignored) {
        }
        try {
            previewLocations.removeUpdates(previewNet);
        } catch (Exception ignored) {
        }
        previewLocations = null;
    }

    private final LocationListener previewListener = new SimpleLoc(true);
    private final LocationListener previewNet = new SimpleLoc(false);

    private final class SimpleLoc implements LocationListener {
        private final boolean gps;

        SimpleLoc(boolean gps) {
            this.gps = gps;
        }

        @Override
        public void onLocationChanged(Location location) {
            engine.onLocation(location, gps);
        }

        @Override
        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    private void registerNet() {
        if (netCbRegistered) {
            return;
        }
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return;
        }
        try {
            connectivity.registerDefaultNetworkCallback(netCb);
            netCbRegistered = true;
        } catch (Exception ignored) {
        }
    }

    private void unregisterNet() {
        if (!netCbRegistered || connectivity == null) {
            return;
        }
        try {
            connectivity.unregisterNetworkCallback(netCb);
        } catch (Exception ignored) {
        }
        netCbRegistered = false;
    }

    private void applyNetwork(boolean online) {
        if (txtNet != null) {
            txtNet.setVisibility(previewingHistory || online ? View.GONE : View.VISIBLE);
        }
        js("onNet(" + online + ")");
        if (online) {
            engine.backfillWhenOnline();
            if (paneHistory != null && paneHistory.getVisibility() == View.VISIBLE) {
                paneHistory.postDelayed(this::refreshHistory, 1600);
            }
        }
        lastPlace = "";
        if (txtPlace != null) {
            TrackSession s = engine.session();
            txtPlace.setText(placeLine(s, lastPoint(s)));
        }
    }

    private void applyKeepScreen() {
        if (TrackEngine.RUNNING.equals(engine.session().state)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void js(String code) {
        if (!mapReady) {
            return;
        }
        web.evaluateJavascript(code, null);
    }

    private static TrackPoint lastPoint(TrackSession s) {
        if (s == null || s.points.isEmpty()) {
            return null;
        }
        return s.points.get(s.points.size() - 1);
    }

    private void persistGoals() {
        Prefs.setGoals(this,
                kmFieldToM(editGoalKm),
                parseStepsField(editGoalSteps),
                kmFieldToM(editEveryKm),
                parseStepsField(editEverySteps),
                parseStepsField(editTodayGoal));
        if (swGoalKm != null && swGoalSteps != null && swEveryKm != null
                && swEverySteps != null && swTodayGoal != null) {
            Prefs.setGoalToggles(this,
                    swGoalKm.isChecked(),
                    swGoalSteps.isChecked(),
                    swEveryKm.isChecked(),
                    swEverySteps.isChecked(),
                    swTodayGoal.isChecked());
        }
        engine.refresh();
    }

    private static final class SoundRow {
        final AlertKind kind;
        SwitchCompat ring;
        SwitchCompat voice;
        TextView name;

        SoundRow(AlertKind kind) {
            this.kind = kind;
        }
    }

    private void bindSoundRow(AlertKind kind, int ringId, int voiceId, int nameId, int pickId) {
        SoundRow row = new SoundRow(kind);
        row.ring = findViewById(ringId);
        row.voice = findViewById(voiceId);
        row.name = findViewById(nameId);
        View pick = findViewById(pickId);
        if (pick != null) {
            if (pick instanceof TextView) {
                ((TextView) pick).setText(kind.pick);
            }
            pick.setOnClickListener(v -> openRingtonePicker(kind));
        }
        soundRows.put(kind, row);
    }

    private void loadSoundRow(SoundRow row) {
        if (row == null) {
            return;
        }
        if (row.ring != null) {
            row.ring.setOnCheckedChangeListener(null);
            row.ring.setChecked(Prefs.kindRing(this, row.kind));
            row.ring.setOnCheckedChangeListener((b, c) -> onKindRing(row.kind, c));
        }
        if (row.voice != null) {
            row.voice.setOnCheckedChangeListener(null);
            row.voice.setChecked(Prefs.kindVoice(this, row.kind));
            row.voice.setOnCheckedChangeListener((b, c) -> {
                Prefs.setKindVoice(this, row.kind, c);
            });
        }
        paintSoundRow(row);
    }

    private void onKindRing(AlertKind kind, boolean on) {
        Prefs.setKindRing(this, kind, on);
        paintSoundRow(soundRows.get(kind));
    }

    private void paintSoundRow(SoundRow row) {
        if (row == null || row.name == null) {
            return;
        }
        String head = row.kind.title;
        if (!Prefs.kindRing(this, row.kind)) {
            row.name.setText(head + " · 铃声已关");
            return;
        }
        if (!Prefs.kindHasOwnRingtone(this, row.kind)) {
            row.name.setText(head + " · 未单独选（系统默认，不影响其它项）");
            return;
        }
        String name = Prefs.kindOwnRingName(this, row.kind);
        row.name.setText(head + " · " + (name.isEmpty() ? "已选铃声" : name));
    }

    private void bindGoalSwitch(SwitchCompat sw, boolean on) {
        if (sw == null) {
            return;
        }
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, c) -> persistGoals());
    }

    private void openRingtonePicker(AlertKind kind) {
        pickingKind = kind;
        final List<TonePick.Item> items = TonePick.list(this);
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).label;
        }
        new AlertDialog.Builder(this)
                .setTitle("给「" + kind.setting + "」选铃声")
                .setItems(labels, (d, which) -> {
                    if (which < 0 || which >= items.size()) {
                        return;
                    }
                    TonePick.Item it = items.get(which);
                    if (it.pickFile) {
                        openToneFilePicker();
                        return;
                    }
                    if (it.silent) {
                        applyPickedTone(kind, null, "");
                        return;
                    }
                    playTonePreview(it.uri);
                    applyPickedTone(kind, it.uri, it.label);
                })
                .setNegativeButton("取消", null)
                .setOnDismissListener(d -> paneMeasure.removeCallbacks(stopTonePreview))
                .show();
    }

    private void openToneFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        if (Build.VERSION.SDK_INT >= 19) {
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "audio/*", "audio/mpeg", "audio/ogg", "application/ogg", "audio/x-wav"
            });
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            pickRingtone.launch(i);
        } catch (Exception e) {
            Toast.makeText(this, "打不开文件选择", Toast.LENGTH_LONG).show();
        }
    }

    private void applyPickedTone(AlertKind kind, Uri uri, String name) {
        if (kind == null) {
            return;
        }
        SoundRow row = soundRows.get(kind);
        if (uri == null) {
            Prefs.setKindRingtone(this, kind, "", "");
            Prefs.setKindRing(this, kind, false);
            if (row != null && row.ring != null) {
                row.ring.setOnCheckedChangeListener(null);
                row.ring.setChecked(false);
                row.ring.setOnCheckedChangeListener((b, c) -> onKindRing(kind, c));
            }
        } else {
            String label = name == null ? "" : name.trim();
            if (label.isEmpty()) {
                label = ringtoneTitle(uri);
            }
            Prefs.setKindRingtone(this, kind, uri.toString(), label);
            Prefs.setKindRing(this, kind, true);
            if (row != null && row.ring != null && !row.ring.isChecked()) {
                row.ring.setOnCheckedChangeListener(null);
                row.ring.setChecked(true);
                row.ring.setOnCheckedChangeListener((b, c) -> onKindRing(kind, c));
            }
        }
        paintSoundRow(row);
        if (uri == null) {
            Toast.makeText(this, "已关掉「" + kind.setting + "」的铃声，其它提醒不变",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "已给「" + kind.setting + "」记下铃声，其它提醒不变",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void playTonePreview(Uri uri) {
        stopTonePreview();
        if (uri == null) {
            return;
        }
        try {
            tonePreview = RingtoneManager.getRingtone(this, uri);
            if (tonePreview == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                tonePreview.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            tonePreview.play();
            if (paneMeasure != null) {
                paneMeasure.removeCallbacks(stopTonePreview);
                paneMeasure.postDelayed(stopTonePreview, 2500);
            }
        } catch (Exception ignored) {
            stopTonePreview();
        }
    }

    private void stopTonePreview() {
        if (paneMeasure != null) {
            paneMeasure.removeCallbacks(stopTonePreview);
        }
        try {
            if (tonePreview != null && tonePreview.isPlaying()) {
                tonePreview.stop();
            }
        } catch (Exception ignored) {
        }
        tonePreview = null;
    }

    private String ringtoneTitle(Uri uri) {
        try {
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r != null) {
                String t = r.getTitle(this);
                if (t != null && !t.trim().isEmpty()) {
                    return t.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "已选铃声";
    }

    private static String metersToKmField(float meters) {
        if (meters < 1) {
            return "";
        }
        float km = meters / 1000f;
        if (km == (int) km) {
            return String.valueOf((int) km);
        }
        if (km >= 0.1f) {
            return String.format(Locale.US, "%.2f", km);
        }
        return String.format(Locale.US, "%.3f", km);
    }

    private static String stepsField(int n) {
        return n < 1 ? "" : String.valueOf(n);
    }

    private static float kmFieldToM(EditText box) {
        if (box == null) {
            return 0f;
        }
        try {
            String t = box.getText().toString().trim();
            if (t.isEmpty()) {
                return 0f;
            }
            float km = Float.parseFloat(t);
            if (km <= 0) {
                return 0f;
            }
            return km * 1000f;
        } catch (Exception e) {
            return 0f;
        }
    }

    private static int parseStepsField(EditText box) {
        if (box == null) {
            return 0;
        }
        try {
            String t = box.getText().toString().trim();
            if (t.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(t);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String trimNum(float v) {
        if (v == (int) v) {
            return String.valueOf((int) v);
        }
        return String.format(Locale.US, "%.1f", v);
    }

    public class JsBridge {
        @JavascriptInterface
        public String amapKey() {
            return Prefs.amapKey(MainActivity.this);
        }

        @JavascriptInterface
        public boolean isOnline() {
            return Net.isOnline(MainActivity.this);
        }

        @JavascriptInterface
        public void onMapReady(String name) {
            runOnUiThread(() -> {
                mapReady = true;
                applyNetwork(Net.isOnline(MainActivity.this));
                if (spanPicking) {
                    js("setSpanPick(true)");
                }
                TrackSession s = engine.session();
                if (previewingHistory) {
                    js("setHist(true)");
                    redrawPreview();
                    return;
                }
                if (!s.points.isEmpty() || !s.marks.isEmpty()) {
                    redrawPath(s);
                }
            });
        }

        @JavascriptInterface
        public void onFollow(boolean v) {
            follow = v;
        }

        @JavascriptInterface
        public void onMapTap(final double lat, final double lng, final double zoom) {
            runOnUiThread(() -> onPreviewMapTap(lat, lng, zoom));
        }

        @JavascriptInterface
        public void onMapBounds(final double south, final double west,
                                final double north, final double east, final int zoom) {
            runOnUiThread(() -> confirmDownload(
                    OfflineMapDownloader.bounds(south, west, north, east, zoom)));
        }
    }

    private final class HistoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return history.size();
        }

        @Override
        public TrackSession getItem(int position) {
            return history.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_session, parent, false);
            }
            TrackSession s = getItem(position);
            TextView title = convertView.findViewById(R.id.item_title);
            TextView sub = convertView.findViewById(R.id.item_sub);
            title.setText(Formats.headline(s));
            if (TrackSession.ORIGIN_SYNC.equals(s.origin)) {
                title.setTextColor(0xFF0F766E);
            } else if (TrackSession.ORIGIN_RESTORE.equals(s.origin)) {
                title.setTextColor(0xFFB45309);
            } else {
                title.setTextColor(0xFF0F172A);
            }
            String route = Formats.routeLine(s);
            String place = route.isEmpty() ? addrLine(s) : route;
            sub.setText(Formats.when(s.startMs)
                    + "  配速 " + Formats.pace(s.distanceM, s.movingMs)
                    + (s.steps > 0 ? "  " + Formats.steps(s.steps) : "")
                    + battListBit(s)
                    + (s.marks.isEmpty() ? "" : "  " + Formats.marksCountLine(s.marks))
                    + (TrackClean.hint(s).isEmpty() ? "" : "  " + TrackClean.hint(s))
                    + (place.isEmpty() ? "" : "  " + place));
            convertView.findViewById(R.id.item_rename).setOnClickListener(v ->
                    editSessionLabels(s, false));
            convertView.findViewById(R.id.item_view).setOnClickListener(v -> previewSession(s));
            View marksBtn = convertView.findViewById(R.id.item_marks);
            if (s.marks.isEmpty()) {
                marksBtn.setVisibility(View.GONE);
            } else {
                marksBtn.setVisibility(View.VISIBLE);
                marksBtn.setOnClickListener(v -> showMarksDialog(s,
                        Formats.when(s.startMs) + " 的打点"));
            }
            convertView.findViewById(R.id.item_delete).setOnClickListener(v ->
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("删除这条 " + Formats.headline(s) + " 的记录？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("删除", (d, w) -> {
                                engine.store().delete(s.id);
                                refreshHistory();
                            })
                            .show());
            return convertView;
        }
    }

    private final class BackupAdapter extends BaseAdapter {
        private final List<Backups.Entry> items;

        BackupAdapter(List<Backups.Entry> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Backups.Entry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_backup, parent, false);
            }
            Backups.Entry e = items.get(position);
            ((TextView) convertView.findViewById(R.id.item_title)).setText(e.title());
            ((TextView) convertView.findViewById(R.id.item_sub)).setText(e.subtitle());
            convertView.findViewById(R.id.item_delete).setOnClickListener(v ->
                    confirmDeleteBackup(e));
            return convertView;
        }
    }
}
