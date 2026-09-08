package com.example.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.PdfFile;
import com.example.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.List;

public class PdfAdapter extends RecyclerView.Adapter<PdfAdapter.PdfViewHolder> {

    private final List<PdfFile> pdfList = new ArrayList<>();
    private final OnPdfClickListener listener;

    public interface OnPdfClickListener {
        void onPdfClick(PdfFile pdfFile);
        void onPdfOptionsClick(PdfFile pdfFile, View anchorView, int position);
    }

    public PdfAdapter(OnPdfClickListener listener) {
        this.listener = listener;
    }

    public void setPdfList(List<PdfFile> newList) {
        this.pdfList.clear();
        if (newList != null) {
            this.pdfList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PdfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf, parent, false);
        return new PdfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PdfViewHolder holder, int position) {
        PdfFile pdfFile = pdfList.get(position);
        holder.bind(pdfFile, listener, position);
    }

    @Override
    public int getItemCount() {
        return pdfList.size();
    }

    static class PdfViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgThumbnail;
        private final TextView txtName;
        private final TextView txtInfo;
        private final ImageView btnOptions;

        public PdfViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.img_thumbnail);
            txtName = itemView.findViewById(R.id.txt_name);
            txtInfo = itemView.findViewById(R.id.txt_info);
            btnOptions = itemView.findViewById(R.id.btn_options);
        }

        public void bind(final PdfFile pdfFile, final OnPdfClickListener listener, final int position) {
            txtName.setText(pdfFile.getName());
            txtInfo.setText(pdfFile.getFormattedSize() + "  |  " + pdfFile.getFormattedDate());
            
            // Set unique test tags for UI tests if needed
            itemView.setTag("task_item_card");
            btnOptions.setTag("three_dots_menu");

            // Load thumbnail asynchronously
            ThumbnailLoader.getInstance().loadThumbnail(pdfFile.getFilepath(), imgThumbnail);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPdfClick(pdfFile);
                }
            });

            btnOptions.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPdfOptionsClick(pdfFile, btnOptions, position);
                }
            });
        }
    }
}
