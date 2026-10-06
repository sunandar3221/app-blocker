package com.appblocker.kiosk;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    public interface OnAppSelectedListener {
        void onAppSelected(AppInfo appInfo);
    }

    private List<AppInfo> originalList = new ArrayList<>();
    private List<AppInfo> filteredList = new ArrayList<>();
    private int selectedPosition = -1;
    private final OnAppSelectedListener listener;

    public AppAdapter(List<AppInfo> list, OnAppSelectedListener listener) {
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
        this.listener = listener;
    }

    public void updateList(List<AppInfo> list) {
        this.originalList = list;
        this.filteredList = new ArrayList<>(list);
        this.selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lower = query.toLowerCase().trim();
            for (AppInfo info : originalList) {
                if (info.getAppName().toLowerCase().contains(lower) ||
                    info.getPackageName().toLowerCase().contains(lower)) {
                    filteredList.add(info);
                }
            }
        }
        notifyDataSetChanged();
    }

    public AppInfo getSelectedItem() {
        if (selectedPosition >= 0 && selectedPosition < filteredList.size()) {
            return filteredList.get(selectedPosition);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = filteredList.get(position);
        holder.tvAppName.setText(app.getAppName());
        holder.tvPackageName.setText(app.getPackageName());
        if (app.getIcon() != null) {
            holder.ivAppIcon.setImageDrawable(app.getIcon());
        }
        holder.rbSelect.setChecked(position == selectedPosition);

        View.OnClickListener clickListener = v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (prev != -1) {
                notifyItemChanged(prev);
            }
            notifyItemChanged(selectedPosition);
            if (listener != null && selectedPosition >= 0 && selectedPosition < filteredList.size()) {
                listener.onAppSelected(filteredList.get(selectedPosition));
            }
        };

        holder.itemView.setOnClickListener(clickListener);
        holder.rbSelect.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvPackageName;
        RadioButton rbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            rbSelect = itemView.findViewById(R.id.rbSelect);
        }
    }
}
