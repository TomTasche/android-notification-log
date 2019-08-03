package org.hcilab.projects.nlogx.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.hcilab.projects.nlogx.R;
import org.hcilab.projects.nlogx.misc.Const;
import org.hcilab.projects.nlogx.misc.Util;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

class BrowseAdapter extends RecyclerView.Adapter<BrowseViewHolder> {

	private final static int LIMIT = Integer.MAX_VALUE;
	private final static String PAGE_SIZE = "20";

	private DateFormat format = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());

	private Activity context;
	private ArrayList<DataItem> data = new ArrayList<>();
	private HashMap<String, Drawable> iconCache = new HashMap<>();
	private Handler handler = new Handler();

	private String lastDate = "";
	private boolean shouldLoadMore = true;

	BrowseAdapter(Activity context) {
		this.context = context;
		loadMore(null);
	}

	@NonNull
	@Override
	public BrowseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_browse, parent, false);
		BrowseViewHolder vh = new BrowseViewHolder(view);
		vh.item.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String id = (String) v.getTag();
				if(id != null) {
					Intent intent = new Intent(context, DetailsActivity.class);
					intent.putExtra(DetailsActivity.EXTRA_ID, id);
					if(Build.VERSION.SDK_INT >= 21) {
						Pair<View, String> p1 = Pair.create(vh.icon, "icon");
						ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(context, p1);
						context.startActivityForResult(intent, 1, options.toBundle());
					} else {
						context.startActivityForResult(intent, 1);
					}
				}
			}
		});
		return vh;
	}

	@Override
	public void onBindViewHolder(@NonNull BrowseViewHolder vh, int position) {
		DataItem item = data.get(position);

		if(iconCache.containsKey(item.getPackageName()) && iconCache.get(item.getPackageName()) != null) {
			vh.icon.setImageDrawable(iconCache.get(item.getPackageName()));
		} else {
			vh.icon.setImageResource(R.mipmap.ic_launcher);
		}

		vh.item.setTag("" + item.getId());
		vh.name.setText(item.getAppName());

		if(item.getPreview().length() == 0) {
			vh.preview.setVisibility(View.GONE);
			vh.text.setVisibility(View.VISIBLE);
			vh.text.setText(item.getText());
		} else {
			vh.text.setVisibility(View.GONE);
			vh.preview.setVisibility(View.VISIBLE);
			vh.preview.setText(item.getPreview());
		}

		if(item.shouldShowDate()) {
			vh.date.setVisibility(View.VISIBLE);
			vh.date.setText(item.getDate());
		} else {
			vh.date.setVisibility(View.GONE);
		}

		if(position == getItemCount() - 1) {
			loadMore(item.getId());
		}
	}

	@Override
	public int getItemCount() {
		return data.size();
	}

	private void loadMore(String afterId) {
		if(!shouldLoadMore) {
			if(Const.DEBUG) System.out.println("not loading more items");
			return;
		}

		if(Const.DEBUG) System.out.println("loading more items");
		int before = getItemCount();
		try {
			FirebaseDatabase database = FirebaseDatabase.getInstance();

			DatabaseReference reference = database.getReference("OnlyMyNotifications");
			Query query = reference.orderByKey().limitToFirst(10);
			if (afterId != null) {
				query.startAt(afterId);
			}
			query.addListenerForSingleValueEvent(new ValueEventListener() {
				@Override
				public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
					for (DataSnapshot child : dataSnapshot.getChildren()) {
						if (afterId != null && child.getKey().equals(afterId)) {
							continue;
						}

						DataItem dataItem = new DataItem(context, child.getKey(), child.getValue(String.class));

						String thisDate = dataItem.getDate();
						if(lastDate.equals(thisDate)) {
							dataItem.setShowDate(false);
						}
						lastDate = thisDate;

						data.add(dataItem);
					}

					if(getItemCount() == 0) {
						Toast.makeText(context, R.string.empty_log_file, Toast.LENGTH_LONG).show();
						context.finish();

						return;
					}

					int after = getItemCount();

					if(before == after) {
						if(Const.DEBUG) System.out.println("no new items loaded: " + getItemCount());
						shouldLoadMore = false;
					}

					if(getItemCount() > LIMIT) {
						if(Const.DEBUG) System.out.println("reached the limit, not loading more items: " + getItemCount());
						shouldLoadMore = false;
					}

					handler.post(new Runnable(){
						public void run(){
							notifyDataSetChanged();
						}
					});
				}

				@Override
				public void onCancelled(@NonNull DatabaseError databaseError) {
				}
			});
		} catch (Exception e) {
			if(Const.DEBUG) e.printStackTrace();
		}
	}

	private class DataItem {

		private String id;
		private String packageName;
		private String appName;
		private String text;
		private String preview;
		private String date;
		private boolean showDate;

		DataItem(Context context, String id, String str) {
			this.id = id;
			try {
				JSONObject json = new JSONObject(str);
				packageName = json.getString("packageName");
				appName = Util.getAppNameFromPackage(context, packageName, false);
				text = str;

				String title = json.optString("title");
				String text = json.optString("text");
				preview = (title + "\n" + text).trim();

				if(!iconCache.containsKey(packageName)) {
					iconCache.put(packageName, Util.getAppIconFromPackage(context, packageName));
				}

				date = format.format(json.optLong("systemTime"));
				showDate = true;
			} catch (JSONException e) {
				if(Const.DEBUG) e.printStackTrace();
			}
		}

		public String getId() {
			return id;
		}

		public String getPackageName() {
			return packageName;
		}

		String getAppName() {
			return appName;
		}

		public String getText() {
			return text;
		}

		String getPreview() {
			return preview;
		}

		String getDate() {
			return date;
		}

		boolean shouldShowDate() {
			return showDate;
		}

		void setShowDate(boolean showDate) {
			this.showDate = showDate;
		}

	}

}
