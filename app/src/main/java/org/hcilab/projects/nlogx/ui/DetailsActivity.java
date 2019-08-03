package org.hcilab.projects.nlogx.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.hcilab.projects.nlogx.R;
import org.hcilab.projects.nlogx.misc.Const;
import org.hcilab.projects.nlogx.misc.DatabaseHelper;
import org.hcilab.projects.nlogx.misc.Util;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class DetailsActivity extends AppCompatActivity {

	public static final String EXTRA_ID = "id";
	public static final String EXTRA_ACTION = "action";
	public static final String ACTION_REFRESH = "refresh";

	private static final boolean SHOW_RELATIVE_DATE_TIME = true;

	private String id;
	private String packageName;
	private int appUid;
	private AlertDialog dialog;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_details);

		Intent intent = getIntent();
		if(intent != null) {
			id = intent.getStringExtra(EXTRA_ID);
			if(id != null) {
				loadDetails(id);
			} else {
				finishWithToast();
			}
		} else {
			finishWithToast();
		}
	}

	@Override
	protected void onPause() {
		if(dialog != null && dialog.isShowing()) {
			dialog.dismiss();
			dialog = null;
		}
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.details, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.menu_delete) {
			confirmDelete();
		}
		return super.onOptionsItemSelected(item);
	}

	private void loadDetails(String id) {
		FirebaseDatabase database = FirebaseDatabase.getInstance();

		DatabaseReference reference = database.getReference("OnlyMyNotifications");
		Query query = reference.child(id).orderByKey();
		query.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
				JSONObject json = null;
				String str = "error";

				try {
					json = new JSONObject(dataSnapshot.getValue(String.class));
					str = json.toString(2);
				} catch (JSONException e) {
					if(Const.DEBUG) e.printStackTrace();
				}

				TextView tvJSON = findViewById(R.id.json);
				tvJSON.setText(str);

				CardView card = findViewById(R.id.card);
				CardView buttons = findViewById(R.id.buttons);
				if(json != null) {
					packageName = json.optString("packageName", "???");
					String titleText   = json.optString("title");
					String contentText = json.optString("text");
					String text = (titleText + "\n" + contentText).trim();
					if(!"".equals(text)) {
						card.setVisibility(View.VISIBLE);
						ImageView icon = findViewById(R.id.icon);
						icon.setImageDrawable(Util.getAppIconFromPackage(DetailsActivity.this, packageName));
						TextView tvName = findViewById(R.id.name);
						tvName.setText(Util.getAppNameFromPackage(DetailsActivity.this, packageName, false));
						TextView tvText = findViewById(R.id.text);
						tvText.setText(text);
						TextView tvDate = findViewById(R.id.date);
						if(SHOW_RELATIVE_DATE_TIME) {
							tvDate.setText(DateUtils.getRelativeDateTimeString(
									DetailsActivity.this,
									json.optLong("systemTime"),
									DateUtils.MINUTE_IN_MILLIS,
									DateUtils.WEEK_IN_MILLIS,
									0));
						} else {
							DateFormat format = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT, Locale.getDefault());
							tvDate.setText(format.format(json.optLong("systemTime")));
						}

						try {
							ApplicationInfo app = DetailsActivity.this.getPackageManager().getApplicationInfo(packageName, 0);
							buttons.setVisibility(View.VISIBLE);
							appUid = app.uid;
						} catch (PackageManager.NameNotFoundException e) {
							if(Const.DEBUG) e.printStackTrace();
							buttons.setVisibility(View.GONE);
						}
					} else {
						card.setVisibility(View.GONE);

					}
				} else {
					card.setVisibility(View.GONE);
					buttons.setVisibility(View.GONE);
				}
			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {
			}
		});
	}

	private void finishWithToast() {
		Toast.makeText(this, R.string.details_error, Toast.LENGTH_SHORT).show();
		finish();
	}

	private void confirmDelete() {
		if(dialog != null && dialog.isShowing()) {
			dialog.dismiss();
		}

		dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.delete_dialog_title)
				.setMessage(R.string.delete_dialog_text)
				.setPositiveButton(R.string.delete_dialog_yes, doDelete)
				.setNegativeButton(R.string.delete_dialog_no, null)
				.show();
	}

	private DialogInterface.OnClickListener doDelete = (dialog, which) -> {
		int affectedRows = 0;
		try {
			DatabaseHelper databaseHelper = new DatabaseHelper(this);
			SQLiteDatabase db = databaseHelper.getWritableDatabase();
			affectedRows = db.delete(DatabaseHelper.PostedEntry.TABLE_NAME,
					DatabaseHelper.PostedEntry._ID + " = ?",
					new String[] { id });
			db.close();
			databaseHelper.close();
		} catch (Exception e) {
			if(Const.DEBUG) e.printStackTrace();
		}

		if(affectedRows > 0) {
			Intent data = new Intent();
			data.putExtra(EXTRA_ACTION, ACTION_REFRESH);
			setResult(RESULT_OK, data);
			finish();
		}
	};

	public void openNotificationSettings(View v) {
		try {
			Intent intent = new Intent();
			if(Build.VERSION.SDK_INT > 25) {
				intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
				intent.putExtra("android.provider.extra.APP_PACKAGE", packageName);
			} else if(Build.VERSION.SDK_INT >= 21) {
				intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
				intent.putExtra("app_package", packageName);
				intent.putExtra("app_uid", appUid);
			} else {
				intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
				intent.addCategory(Intent.CATEGORY_DEFAULT);
				intent.setData(Uri.parse("package:" + packageName));
			}
			startActivity(intent);
		} catch (Exception e) {
			if(Const.DEBUG) e.printStackTrace();
		}
	}

}