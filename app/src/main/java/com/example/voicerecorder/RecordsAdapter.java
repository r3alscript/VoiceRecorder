package com.example.voicerecorder;

import android.net.Uri;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.VH> {

    public static class RecordItem {
        public final String name;
        public final Uri uri;
        public RecordItem(String name, Uri uri) { this.name = name; this.uri = uri; }
    }

    public interface Listener {
        void onPlayPauseClicked(RecordItem item, Button playBtn);
        void onDeleteClicked(RecordItem item);
    }

    private final List<RecordItem> items = new ArrayList<>();
    private final Listener listener;

    public RecordsAdapter(Listener l) { this.listener = l; }

    public void setData(List<RecordItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void addFirst(RecordItem item) {
        items.add(0, item);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 12, 16, 12);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(parent.getContext());
        LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(name, lpName);

        Button play = new Button(parent.getContext());
        play.setText("Відтворити");
        row.addView(play, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(parent.getContext());
        row.addView(spacer, new LinearLayout.LayoutParams(16, 1));

        Button del = new Button(parent.getContext());
        del.setText("Видалити");
        row.addView(del, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new VH(row, name, play, del);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        RecordItem it = items.get(position);
        h.name.setText(it.name);
        h.play.setOnClickListener(v -> listener.onPlayPauseClicked(it, h.play));
        h.del.setOnClickListener(v -> listener.onDeleteClicked(it));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        Button play;
        Button del;
        VH(@NonNull View itemView, TextView n, Button p, Button d) {
            super(itemView);
            name = n; play = p; del = d;
        }
    }
}
