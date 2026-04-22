package com.example.myapplication;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class OfficialVideoAdapter extends RecyclerView.Adapter<OfficialVideoAdapter.Holder> {
    public interface OnSelectListener { void onSelect(ActionStrategy.OfficialVideo video); }

    private final List<ActionStrategy.OfficialVideo> list;
    private final OnSelectListener listener;

    OfficialVideoAdapter(List<ActionStrategy.OfficialVideo> list, OnSelectListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup p, int viewType) {
        TextView tv = new TextView(p.getContext());
        tv.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48,
                        p.getContext().getResources().getDisplayMetrics())));
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return new Holder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ActionStrategy.OfficialVideo v = list.get(position);
        h.tv.setText(v.displayName);
        h.tv.setOnClickListener(vv -> listener.onSelect(v));
    }

    @Override public int getItemCount() { return list.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tv;
        Holder(TextView tv) { super(tv); this.tv = tv; }
    }
}
