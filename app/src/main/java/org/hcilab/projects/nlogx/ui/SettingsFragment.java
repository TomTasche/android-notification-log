package org.hcilab.projects.nlogx.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.hcilab.projects.nlogx.BuildConfig;
import org.hcilab.projects.nlogx.R;
import org.hcilab.projects.nlogx.misc.Const;
import org.hcilab.projects.nlogx.misc.Util;
import org.hcilab.projects.nlogx.service.NotificationHandler;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat {

	public static final String TAG = SettingsFragment.class.getName();

	private DatabaseReference reference;
	private BroadcastReceiver updateReceiver;

	private Preference prefStatus;
	private Preference prefText;
	private Preference prefOngoing;
	private Preference prefEntries;

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);

		PreferenceManager pm = getPreferenceManager();

		prefStatus = pm.findPreference(Const.PREF_STATUS);
		prefStatus.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
				return true;
			}
		});

		pm.findPreference(Const.PREF_BROWSE).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(getActivity(), BrowseActivity.class));
				return true;
			}
		});

		prefText = pm.findPreference(Const.PREF_TEXT);
		prefOngoing = pm.findPreference(Const.PREF_ONGOING);
		prefEntries = pm.findPreference(Const.PREF_ENTRIES);

		pm.findPreference(Const.PREF_VERSION).setSummary(BuildConfig.VERSION_NAME + (Const.DEBUG ? " dev" : ""));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		FirebaseDatabase database = FirebaseDatabase.getInstance();
		reference = database.getReference("OnlyMyNotifications");

		updateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				update();
			}
		};
	}

	@Override
	public void onResume() {
		super.onResume();

		if(Util.isNotificationAccessEnabled(getActivity())) {
			prefStatus.setSummary(R.string.settings_notification_access_enabled);
			prefText.setEnabled(true);
			prefOngoing.setEnabled(true);

			FirebaseMessaging.getInstance().unsubscribeFromTopic("OnlyMyNotificationsTopic");
		} else {
			prefStatus.setSummary(R.string.settings_notification_access_disabled);
			prefText.setEnabled(false);
			prefOngoing.setEnabled(false);

			FirebaseMessaging.getInstance().subscribeToTopic("OnlyMyNotificationsTopic");
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(NotificationHandler.BROADCAST);
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(updateReceiver, filter);

		update();
	}

	@Override
	public void onPause() {
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(updateReceiver);
		super.onPause();
	}

	private void update() {
		try {
			Query query = reference.orderByKey();
			query.addListenerForSingleValueEvent(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
					long numRowsPosted = dataSnapshot.getChildrenCount();
					prefEntries.setSummary("" + numRowsPosted);
				}

				@Override
				public void onCancelled(@NonNull DatabaseError databaseError) {
				}
			});
		} catch (Exception e) {
			if(Const.DEBUG) e.printStackTrace();
		}
	}

}