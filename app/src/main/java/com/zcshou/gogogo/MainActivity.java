package com.cxorz.anywhere;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.elvishew.xlog.XLog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import com.cxorz.anywhere.database.DataBaseHistoryLocation;
import com.cxorz.anywhere.database.DataBaseHistorySearch;
import com.cxorz.anywhere.service.ServiceGo;
import com.cxorz.anywhere.utils.GoUtils;
import com.cxorz.anywhere.utils.ShareUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;

public class MainActivity extends BaseActivity implements SensorEventListener, OnGetGeoCoderResultListener {
    
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    // 地图标记图标（需要确保 res/drawable/ic_marker_pin.png 存在）
    private final BitmapDescriptor mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_pin);

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /* ============================== 主界面地图 相关 ============================== */
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private LocationClient mLocationClient;
    private Marker mCurrentMarker;
    private GeoCoder mGeoCoder;
    private PoiSearch mPoiSearch;

    private LatLng mMarkLatLng = new LatLng(39.9042, 116.4074);
    private String mMarkName = null;
    
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];
    private float[] mMagValues = new float[3];
    private final float[] mR = new float[9];
    private final float[] mDirectionValues = new float[3];
    
    private double mCurrentLat = 0.0;
    private double mCurrentLon = 0.0;
    private float mCurrentDirection = 0.0f;
    private float mCurrentBearing = 0.0f;
    
    private boolean isMockServStart = false;
    private static boolean isWifiWarningShown = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private FloatingActionButton mButtonStart;
    
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;

    private double mCurrentZoom = 16.0;
    private boolean mIsFirstLoc = true;
    private String mCurrentCity = "全国";

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble("MAP_ZOOM", mCurrentZoom);
        if (mMarkLatLng != null) {
            outState.putDouble("MARK_LAT", mMarkLatLng.latitude);
            outState.putDouble("MARK_LNG", mMarkLatLng.longitude);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentZoom = savedInstanceState.getDouble("MAP_ZOOM", 16.0);
            double lat = savedInstanceState.getDouble("MARK_LAT", 39.9042);
            double lng = savedInstanceState.getDouble("MARK_LNG", 116.4074);
            mMarkLatLng = new LatLng(lat, lng);
        }

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        initMap();
        initLocation();

        initStoreHistory();

        boolean hasHistory = false;
        if (savedInstanceState == null) {
            hasHistory = moveToLastHistoryLocation();
        } else {
            if (mMarkLatLng != null && mBaiduMap != null) {
                MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mMarkLatLng);
                mBaiduMap.setMapStatus(msu);
                addMarker(mMarkLatLng, mMarkName);
            }
        }

        initMapButton();
        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder) service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        initSearchView();
        handleIntent(getIntent());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DrawerLayout drawer = findViewById(R.id.drawer_layout);
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });
    }

    private void initMap() {
        mMapView = findViewById(R.id.bdMapView);
        mBaiduMap = mMapView.getMap();
        
        // 设置地图类型
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        // 启用定位图层
        mBaiduMap.setMyLocationEnabled(true);
        
        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo((float) mCurrentZoom);
        mBaiduMap.setMapStatus(msu);
        
        // 地图点击监听
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mMarkLatLng = latLng;
                mMarkName = null;
                addMarker(latLng, "Selected Location");
                reverseGeocode(latLng);
            }
            
            @Override
            public void onMapPoiClick(MapPoi mapPoi) {
                if (mapPoi != null && mapPoi.getPosition() != null) {
                    mMarkLatLng = mapPoi.getPosition();
                    mMarkName = mapPoi.getName();
                    addMarker(mMarkLatLng, mMarkName);
                    showPoiInfo(mMarkLatLng, mMarkName);
                }
            }
        });
        
        mGeoCoder = GeoCoder.newInstance();
        mGeoCoder.setOnGetGeoCodeResultListener(this);
        
        mPoiSearch = PoiSearch.newInstance();
        mPoiSearch.setOnGetPoiSearchResultListener(new OnGetPoiSearchResultListener() {
            @Override
            public void onGetPoiResult(PoiResult result) {
                if (result != null && result.getAllPoi() != null && !result.getAllPoi().isEmpty()) {
                    List<Map<String, Object>> data = new ArrayList<>();
                    for (PoiInfo poi : result.getAllPoi()) {
                        Map<String, Object> poiItem = new HashMap<>();
                        poiItem.put(POI_NAME, poi.name);
                        poiItem.put(POI_ADDRESS, poi.address);
                        poiItem.put(POI_LONGITUDE, String.valueOf(poi.location.longitude));
                        poiItem.put(POI_LATITUDE, String.valueOf(poi.location.latitude));
                        data.add(poiItem);
                    }
                    runOnUiThread(() -> {
                        SimpleAdapter simAdapt = new SimpleAdapter(
                                MainActivity.this, data, R.layout.search_poi_item,
                                new String[]{POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE},
                                new int[]{R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                        mSearchList.setAdapter(simAdapt);
                        mSearchLayout.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> GoUtils.DisplayToast(MainActivity.this,
                            getResources().getString(R.string.app_search_null)));
                }
            }
            
            @Override
            public void onGetPoiDetailResult(PoiDetailResult result) {}
            @Override
            public void onGetPoiDetailResult(PoiDetailSearchResult result) {}
            @Override
            public void onGetPoiIndoorResult(PoiIndoorResult result) {}
        });
    }

    private void initLocation() {
        try {
            mLocationClient = new LocationClient(getApplicationContext());
            mLocationClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation location) {
                    if (location == null || mMapView == null) return;
                    
                    int locType = location.getLocType();
                    XLog.i("Location type: " + locType);
                    
                    if (locType == BDLocation.TypeGpsLocation || locType == BDLocation.TypeNetWorkLocation) {
                        mCurrentLat = location.getLatitude();
                        mCurrentLon = location.getLongitude();
                        mCurrentBearing = location.getDirection();
                        
                        MyLocationData locData = new MyLocationData.Builder()
                                .accuracy(location.getRadius())
                                .direction(mCurrentBearing)
                                .latitude(location.getLatitude())
                                .longitude(location.getLongitude())
                                .build();
                        mBaiduMap.setMyLocationData(locData);
                        
                        // 定位图层配置
                        MyLocationConfiguration config = new MyLocationConfiguration(
                                MyLocationConfiguration.LocationMode.NORMAL, true, null);
                        mBaiduMap.setMyLocationConfiguration(config);
                        
                        if (mIsFirstLoc) {
                            mIsFirstLoc = false;
                            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
                            MapStatus.Builder builder = new MapStatus.Builder();
                            builder.target(ll).zoom(18.0f);
                            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
                            mMarkLatLng = ll;
                            GoUtils.DisplayToast(MainActivity.this, "已成功获取当前位置");
                        }
                    } else {
                        // 定位失败，重新请求
                        mLocClient.requestLocation();
                    }
                }
            });
            
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll");
            option.setScanSpan(1000);
            option.setIsNeedAddress(true);
            option.setOpenGps(true);
            option.setLocationNotify(true);
            option.setIgnoreKillProcess(true);
            option.setEnableSimulateGps(false);
            mLocationClient.setLocOption(option);
            
            mLocationClient.start();
        } catch (Exception e) {
            XLog.e("MainActivity: LocationClient initialization failed", e);
        }
    }

    private void reverseGeocode(LatLng latLng) {
        ReverseGeoCodeOption option = new ReverseGeoCodeOption()
                .location(latLng)
                .newVersion(1);
        mGeoCoder.reverseGeoCode(option);
    }

    @Override
    public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
        if (result == null || result.getAddress() == null) return;
        
        mMarkName = result.getAddress();
        showPoiInfo(mMarkLatLng, mMarkName);
        
        if (mCurrentMarker != null) {
            mCurrentMarker.setTitle(mMarkName);
            mCurrentMarker.showInfoWindow();
        }
    }

    @Override
    public void onGetGeoCodeResult(GeoCodeResult result) {}

    private void showPoiInfo(LatLng latLng, String address) {
        View poiView = View.inflate(MainActivity.this, R.layout.location_poi_info, null);
        TextView poiAddress = poiView.findViewById(R.id.poi_address);
        TextView poiLongitude = poiView.findViewById(R.id.poi_longitude);
        TextView poiLatitude = poiView.findViewById(R.id.poi_latitude);

        poiAddress.setText(address);
        poiLongitude.setText(String.valueOf(latLng.longitude));
        poiLatitude.setText(String.valueOf(latLng.latitude));

        ImageButton ibSave = poiView.findViewById(R.id.poi_save);
        ibSave.setOnClickListener(v -> {
            recordCurrentLocation(mMarkLatLng.longitude, mMarkLatLng.latitude);
            GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_location_save));
        });
        
        ImageButton ibCopy = poiView.findViewById(R.id.poi_copy);
        ibCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData mClipData = ClipData.newPlainText("Label", 
                    latLng.longitude + "," + latLng.latitude);
            cm.setPrimaryClip(mClipData);
            GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_location_copy));
        });
        
        ImageButton ibShare = poiView.findViewById(R.id.poi_share);
        ibShare.setOnClickListener(v -> ShareUtils.shareText(MainActivity.this, "分享位置",
                poiLongitude.getText() + "," + poiLatitude.getText()));
        
        ImageButton ibFly = poiView.findViewById(R.id.poi_fly);
        ibFly.setOnClickListener(this::doGoLocation);
    }

    // 添加标记 - 使用存在的图标资源
    private void addMarker(LatLng latLng, String title) {
        if (mCurrentMarker != null) {
            mCurrentMarker.remove();
        }
        
        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .icon(mMapIndicator)
                .title(title != null ? title : "Selected Location");
        
        mCurrentMarker = (Marker) mBaiduMap.addOverlay(options);
        if (mCurrentMarker != null) {
            mCurrentMarker.showInfoWindow();
        }
    }

    private void initMapButton() {
        ImageButton curPosBtn = findViewById(R.id.cur_position);
        curPosBtn.setOnClickListener(v -> resetMap());

        ImageButton zoomInBtn = findViewById(R.id.zoom_in);
        zoomInBtn.setOnClickListener(v -> {
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn());
        });

        ImageButton zoomOutBtn = findViewById(R.id.zoom_out);
        zoomOutBtn.setOnClickListener(v -> {
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomOut());
        });

        ImageButton inputPosBtn = findViewById(R.id.input_pos);
        inputPosBtn.setOnClickListener(v -> showInputPositionDialog());
    }

    private void showInputPositionDialog() {
        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("请输入经度和纬度");
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.location_input, null);
        builder.setView(view);
        dialog = builder.show();

        final EditText dialog_lng = view.findViewById(R.id.joystick_longitude);
        final EditText dialog_lat = view.findViewById(R.id.joystick_latitude);
        final EditText dialog_ip = view.findViewById(R.id.input_ip_address);
        final com.google.android.material.button.MaterialButton btnGetIp = view.findViewById(R.id.btn_get_ip_location);

        btnGetIp.setOnClickListener(v3 -> {
            String ip = dialog_ip.getText().toString();
            GoUtils.getIpLocation(ip, new GoUtils.LocationCallback() {
                @Override
                public void onSuccess(double lat, double lng) {
                    runOnUiThread(() -> {
                        dialog_lat.setText(String.valueOf(lat));
                        dialog_lng.setText(String.valueOf(lng));
                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.ip_location_success));
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        GoUtils.DisplayToast(MainActivity.this,
                                getResources().getString(R.string.ip_location_error) + ": " + msg);
                    });
                }
            });
        });

        final com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.input_position_cancel);
        final com.google.android.material.button.MaterialButton btnGo = view.findViewById(R.id.input_position_ok);

        btnGo.setOnClickListener(v2 -> {
            String dialog_lng_str = dialog_lng.getText().toString();
            String dialog_lat_str = dialog_lat.getText().toString();

            if (TextUtils.isEmpty(dialog_lng_str) || TextUtils.isEmpty(dialog_lat_str)) {
                GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input));
            } else {
                try {
                    double dialog_lng_double = Double.parseDouble(dialog_lng_str);
                    double dialog_lat_double = Double.parseDouble(dialog_lat_str);

                    if (dialog_lng_double > 180.0 || dialog_lng_double < -180.0) {
                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_longitude));
                    } else if (dialog_lat_double > 90.0 || dialog_lat_double < -90.0) {
                        GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_latitude));
                    } else {
                        mMarkLatLng = new LatLng(dialog_lat_double, dialog_lng_double);
                        mMarkName = "手动输入的坐标";
                        addMarker(mMarkLatLng, mMarkName);
                        
                        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mMarkLatLng);
                        mBaiduMap.setMapStatus(msu);
                        dialog.dismiss();
                    }
                } catch (NumberFormatException e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_input));
                }
            }
        });

        btnCancel.setOnClickListener(v1 -> dialog.dismiss());
    }

    private void resetMap() {
        if (mCurrentLat != 0 && mCurrentLon != 0) {
            LatLng currentLoc = new LatLng(mCurrentLat, mCurrentLon);
            MapStatus.Builder builder = new MapStatus.Builder();
            builder.target(currentLoc).zoom(18.0f);
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            mMarkLatLng = currentLoc;
        } else {
            if (!GoUtils.isGpsOpened(this)) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_gps));
            } else {
                GoUtils.DisplayToast(this, "定位失败：请确保处于室外开阔地带并稍后重试");
            }
        }
    }

    private boolean moveToLastHistoryLocation() {
        if (mLocationHistoryDB == null) return false;
        boolean found = false;
        try {
            Cursor cursor = mLocationHistoryDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[]{"0"},
                    null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", "1");

            if (cursor.moveToFirst()) {
                String lngStr = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84));
                String latStr = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84));

                double lng = Double.parseDouble(lngStr);
                double lat = Double.parseDouble(latStr);

                mMarkLatLng = new LatLng(lat, lng);
                mMarkName = cursor.getString(cursor.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LOCATION));

                if (mBaiduMap != null) {
                    MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mMarkLatLng);
                    mBaiduMap.setMapStatus(msu);
                    addMarker(mMarkLatLng, mMarkName);
                    GoUtils.DisplayToast(this, "已恢复至上次位置");
                    found = true;
                }
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: moveToLastHistoryLocation", e);
        }
        return found;
    }

    private void performSearch(String query) {
        mSearchLayout.setVisibility(View.INVISIBLE);
        
        PoiCitySearchOption option = new PoiCitySearchOption()
                .keyword(query)
                .pageNum(0)
                .pageCapacity(20);
        
        if (mCurrentCity != null && !mCurrentCity.isEmpty()) {
            option.city(mCurrentCity);
        } else {
            option.city("全国");
        }
        
        mPoiSearch.searchInCity(option);
        
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
        contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
        contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
        contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
    }

    private void startGoLocation() {
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        bindService(serviceGoIntent, mConnection, BIND_AUTO_CREATE);
        serviceGoIntent.putExtra(LNG_MSG_ID, mMarkLatLng.longitude);
        serviceGoIntent.putExtra(LAT_MSG_ID, mMarkLatLng.latitude);
        double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
        serviceGoIntent.putExtra(ALT_MSG_ID, alt);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceGoIntent);
        } else {
            startService(serviceGoIntent);
        }
        XLog.d("startForegroundService: ServiceGo");

        isMockServStart = true;
    }

    private void stopGoLocation() {
        unbindService(mConnection);
        Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
        stopService(serviceGoIntent);
        isMockServStart = false;
    }

    private void doGoLocation(View v) {
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_network));
            return;
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.showEnableGpsDialog(this);
            return;
        }

        if (!Settings.canDrawOverlays(getApplicationContext())) {
            GoUtils.showEnableFloatWindowDialog(this);
            XLog.e("无悬浮窗权限!");
            return;
        }

        if (isMockServStart) {
            if (mMarkLatLng == null) {
                stopGoLocation();
                Snackbar.make(v, "模拟位置已终止", Snackbar.LENGTH_LONG).show();
                mButtonStart.setImageResource(R.drawable.ic_position);
            } else {
                double alt = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
                mServiceBinder.setPosition(mMarkLatLng.longitude, mMarkLatLng.latitude, alt);
                Snackbar.make(v, "已传送到新位置", Snackbar.LENGTH_LONG).show();
                recordCurrentLocation(mMarkLatLng.longitude, mMarkLatLng.latitude);

                if (mCurrentMarker != null) mCurrentMarker.remove();
                mMarkLatLng = null;

                if (GoUtils.isWifiEnabled(MainActivity.this) && !isWifiWarningShown) {
                    GoUtils.showWifiWarningToast(MainActivity.this);
                    isWifiWarningShown = true;
                }
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
                XLog.e("无模拟位置权限!");
            } else {
                if (mMarkLatLng == null) {
                    Snackbar.make(v, "请先点击地图位置或者搜索位置", Snackbar.LENGTH_LONG).show();
                } else {
                    startGoLocation();
                    mButtonStart.setImageResource(R.drawable.ic_fly);
                    Snackbar.make(v, "模拟位置已启动", Snackbar.LENGTH_LONG).show();
                    recordCurrentLocation(mMarkLatLng.longitude, mMarkLatLng.latitude);

                    if (mCurrentMarker != null) mCurrentMarker.remove();
                    mMarkLatLng = null;

                    if (GoUtils.isWifiEnabled(MainActivity.this) && !isWifiWarningShown) {
                        GoUtils.showWifiWarningToast(MainActivity.this);
                        isWifiWarningShown = true;
                    }
                }
            }
        }
    }

    private void recordCurrentLocation(double lng, double lat) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, mMarkName != null ? mMarkName : "Unknown Location");
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(lng));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(lat));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
        contentValues.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));

        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, contentValues);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccValues = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagValues = event.values.clone();
        }
        
        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);
        if (mCurrentDirection < 0) {
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void initNavigationView() {
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void initStoreHistory() {
        try {
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);
        mSearchList = findViewById(R.id.search_list_view);
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkLatLng = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));

            MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mMarkLatLng);
            mBaiduMap.setMapStatus(msu);
            addMarker(mMarkLatLng, mMarkName);

            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                    ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.INVISIBLE);
            if (searchItem != null) searchItem.collapseActionView();
        });
        
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                mMarkLatLng = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mMarkLatLng);
                mBaiduMap.setMapStatus(msu);
                addMarker(mMarkLatLng, null);

                mHistoryLayout.setVisibility(View.INVISIBLE);
                if (searchItem != null) searchItem.collapseActionView();
            } else if (searchIsLoc.equals("0")) {
                try {
                    if (searchView != null) {
                        searchView.setQuery(searchKey, true);
                    }
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                }
            }
        });
    }

    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();
        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[]{"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }
        return data;
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("SHOW_LOCATION", false)) {
            String name = intent.getStringExtra("NAME");
            String lngStr = intent.getStringExtra("LNG");
            String latStr = intent.getStringExtra("LAT");
            if (lngStr != null && latStr != null) {
                try {
                    double lng = Double.parseDouble(lngStr);
                    double lat = Double.parseDouble(latStr);
                    mMarkLatLng = new LatLng(lat, lng);
                    mMarkName = name;

                    if (mBaiduMap != null) {
                        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mMarkLatLng);
                        mBaiduMap.setMapStatus(msu);
                        addMarker(mMarkLatLng, mMarkName);
                    }
                } catch (NumberFormatException e) {
                    XLog.e("Invalid coordinates in intent");
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
        if (mSensorManager != null) {
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isMockServStart) {
            unbindService(mConnection);
            Intent serviceGoIntent = new Intent(MainActivity.this, ServiceGo.class);
            stopService(serviceGoIntent);
        }
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        if (mLocationClient != null) {
            mLocationClient.stop();
        }
        if (mGeoCoder != null) {
            mGeoCoder.destroy();
        }
        if (mPoiSearch != null) {
            mPoiSearch.destroy();
        }
        if (mMapView != null) {
            mMapView.onDestroy();
        }
        if (mLocationHistoryDB != null) {
            mLocationHistoryDB.close();
        }
        if (mSearchHistoryDB != null) {
            mSearchHistoryDB.close();
        }
        if (mBaiduMap != null) {
            mBaiduMap.setMyLocationEnabled(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                List<Map<String, Object>> data = getSearchHistory();
                if (!data.isEmpty()) {
                    SimpleAdapter simAdapt = new SimpleAdapter(
                            MainActivity.this, data, R.layout.search_item,
                            new String[]{DataBaseHistorySearch.DB_COLUMN_KEY,
                                    DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP,
                                    DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM,
                                    DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                            new int[]{R.id.search_key, R.id.search_description, R.id.search_timestamp,
                                    R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude});
                    mSearchHistoryList.setAdapter(simAdapt);
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }
                return true;
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);
        searchView.onActionViewExpanded();
        searchView.setIconifiedByDefault(true);
        searchView.setSubmitButtonEnabled(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;
            }
        });

        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (et != null) et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });

        return true;
    }
    }
