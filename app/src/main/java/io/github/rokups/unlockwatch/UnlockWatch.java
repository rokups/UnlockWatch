package io.github.rokups.unlockwatch;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UnlockWatch extends AppCompatActivity {
    static String TAG = UnlockWatch.class.getSimpleName();

    public static class AdminReceiver extends DeviceAdminReceiver {
        private String _failedLoginsSetting = "failedLogins";
        SharedPreferences _settings;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (_settings == null) {
                _settings = context.getSharedPreferences(UnlockWatch.class.toString(), 0);
            }

            int targetFailedAttempts = _settings.getInt("attempts", 0);
            int targetAction = _settings.getInt("action", 0);
            if (targetFailedAttempts == 0 || targetAction == 0) {
                // Disabled state
                return;
            }

            int failedAttempts = _settings.getInt(_failedLoginsSetting, 0);
            if (action.equals(ACTION_PASSWORD_FAILED)) {
                failedAttempts += 1;
                Log.d(TAG, "setFailedAttempts = " + failedAttempts);
                _settings.edit().putInt(_failedLoginsSetting, failedAttempts).apply();
            } else if (intent.getAction().equals(ACTION_PASSWORD_SUCCEEDED)) {
                failedAttempts = 0;
                Log.d(TAG, "setFailedAttempts = " + failedAttempts);
                _settings.edit().putInt(_failedLoginsSetting, failedAttempts).apply();
            }
            super.onReceive(context, intent);

            if (failedAttempts >= targetFailedAttempts) {
                Log.d(TAG, "setFailedAttempts = " + 0);
                _settings.edit().putInt(_failedLoginsSetting, 0).apply();
                try {
                    if (targetAction == 1) {
                        Runtime.getRuntime().exec("su -c reboot");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Reboot error", e);
                }
            }
        }
    }

    private DevicePolicyManager _DPM;
    private ComponentName _DeviceAdmin;
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private SharedPreferences _settings;
    private Spinner _failureCount;
    private Spinner _actionSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        _settings = getSharedPreferences(UnlockWatch.class.toString(), 0);

        // Prepare to work with the DPM
        _DPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        _DeviceAdmin = new ComponentName(this, AdminReceiver.class);

        _failureCount = findViewById(R.id.failure_count);
        _actionSpinner = findViewById(R.id.action);

        // Verify device admin access
        if (_DPM.isAdminActive(_DeviceAdmin)) {
            _DPM.setPasswordMinimumLength(_DeviceAdmin, _DPM.getPasswordMinimumLength(_DeviceAdmin));
            _failureCount.setSelection(_settings.getInt("attempts", 0));
        } else {
            _failureCount.setSelection(0);
            _settings.edit().putInt("attempts", 0).apply();
        }

        // Verify root access
        int selectedAction = _settings.getInt("action", 0);
        if (selectedAction == 1 && !isRootGiven()) {
            _actionSpinner.setSelection(0);
            _settings.edit().putInt("action", 0).apply();
        } else {
            _actionSpinner.setSelection(selectedAction);
        }

        _failureCount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                _settings.edit().putInt("attempts", position).apply();
                if (position > 0) {
                    if (!_DPM.isAdminActive(_DeviceAdmin)) {
                        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, _DeviceAdmin);
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Application requires permission to observe failed unlock attempts.");
                        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                _settings.edit().putInt("attempts", 0).apply();
            }
        });

        _actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position == 1) {    // Reboot
                    if (!isRootGiven()) {
                        _actionSpinner.setSelection(0);
                        return;
                    }
                }

                _settings.edit().putInt("action", position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                _settings.edit().putInt("action", 0).apply();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (_DPM.isAdminActive(_DeviceAdmin)) {
                _DPM.setPasswordMinimumLength(_DeviceAdmin, _DPM.getPasswordMinimumLength(_DeviceAdmin));
            } else {
                _failureCount.setSelection(0);
            }
        }
    }

    public static boolean isRootGiven() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = in.readLine();
            if (output != null && output.toLowerCase().contains("uid=0(root)"))
                return true;
        } catch (Exception e) {
            Log.e(TAG, "su failed", e);
        } finally {
            if (process != null)
                process.destroy();
        }

        return false;
    }
}
