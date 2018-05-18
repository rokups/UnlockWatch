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
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UnlockWatch extends AppCompatActivity {
    static String TAG = UnlockWatch.class.getSimpleName();

    public static class AdminReceiver extends DeviceAdminReceiver {
        private String mFailedLoginsSetting = "failedLogins";

        int getFailedAttempts(Context context) {
            SharedPreferences settings = context.getSharedPreferences(UnlockWatch.class.toString(), 0);
            return settings.getInt(mFailedLoginsSetting, 0);
        }

        void setFailedAttempts(Context context, int value) {
            Log.d(TAG, "setFailedAttempts = " + value);
            SharedPreferences settings = context.getSharedPreferences(UnlockWatch.class.toString(), 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(mFailedLoginsSetting, value);
            editor.apply();
        }

        void addFailedAttempt(Context context) {
            setFailedAttempts(context, getFailedAttempts(context) + 1);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(ACTION_PASSWORD_FAILED))
                addFailedAttempt(context);
            else if (intent.getAction().equals(ACTION_PASSWORD_SUCCEEDED))
                setFailedAttempts(context, 0);
            super.onReceive(context, intent);

            if (getFailedAttempts(context) >= 3) {
                setFailedAttempts(context, 0);
                try {
                    Runtime.getRuntime().exec("su -c reboot");
                } catch (IOException e) {
                    Log.e(TAG, "Reboot error", e);
                }
            }
        }
    }

    private DevicePolicyManager _DPM;
    private ComponentName _DeviceAdmin;
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Prepare to work with the DPM
        _DPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        _DeviceAdmin = new ComponentName(this, AdminReceiver.class);

        final Button buttonAdmin = findViewById(R.id.buttonDevAdmin);
        if (_DPM.isAdminActive(_DeviceAdmin)) {
            _DPM.setPasswordMinimumLength(_DeviceAdmin, _DPM.getPasswordMinimumLength(_DeviceAdmin));
            buttonAdmin.setEnabled(false);
        }

        buttonAdmin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, _DeviceAdmin);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allow application to administer device.");
                startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
            }
        });

        final Button buttonSu = findViewById(R.id.buttonSu);
        buttonSu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                buttonSu.setEnabled(!isRootGiven());
            }
        });
        buttonSu.setEnabled(!isRootGiven());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (_DPM.isAdminActive(_DeviceAdmin)) {
                final Button buttonAdmin = findViewById(R.id.buttonDevAdmin);
                buttonAdmin.setEnabled(false);
                _DPM.setPasswordMinimumLength(_DeviceAdmin, _DPM.getPasswordMinimumLength(_DeviceAdmin));
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
            e.printStackTrace();
        } finally {
            if (process != null)
                process.destroy();
        }

        return false;
    }
}
