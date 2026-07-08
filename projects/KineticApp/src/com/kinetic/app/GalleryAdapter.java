package com.kinetic.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * GridView adapter for the image gallery.
 * Loads images from internal storage with LRU memory cache.
 */
public class GalleryAdapter extends BaseAdapter {
    private final Context ctx;
    private final List<KineticDB.ImageEntry> items = new ArrayList<>();
    private final LruCache<String, Bitmap> cache;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.US);

    public GalleryAdapter(Context ctx) {
        this.ctx = ctx;
        int maxMem = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMem / 6;
        cache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bmp) {
                return bmp.getByteCount() / 1024;
            }
        };
    }

    public void setData(List<KineticDB.ImageEntry> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public KineticDB.ImageEntry getItemAt(int pos) {
        if (pos >= 0 && pos < items.size()) return items.get(pos);
        return null;
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int pos) { return items.get(pos); }

    @Override
    public long getItemId(int pos) { return items.get(pos).id; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_gallery, parent, false);
            holder = new ViewHolder();
            holder.image = convertView.findViewById(R.id.gridImage);
            holder.badge = convertView.findViewById(R.id.gridBadge);
            holder.favBadge = convertView.findViewById(R.id.gridFavBadge);
            holder.name = convertView.findViewById(R.id.gridName);
            holder.date = convertView.findViewById(R.id.gridDate);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        KineticDB.ImageEntry entry = items.get(pos);

        // Set info
        holder.badge.setText(entry.model != null ? entry.model : "");
        holder.name.setText(entry.name != null && !entry.name.isEmpty() ? entry.name : entry.prompt);
        holder.date.setText(sdf.format(new Date(entry.timestamp)));
        holder.favBadge.setVisibility(entry.favorite ? View.VISIBLE : View.GONE);

        // Load image
        holder.image.setImageResource(android.R.color.transparent);
        if (entry.filePath != null) {
            String cacheKey = entry.id + "_" + entry.filePath;
            Bitmap cached = cache.get(cacheKey);
            if (cached != null) {
                holder.image.setImageBitmap(cached);
            } else {
                holder.image.setTag(cacheKey);
                new ImageLoadTask(holder.image, cacheKey).execute(entry.filePath);
            }
        }

        return convertView;
    }

    private class ImageLoadTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        private final String cacheKey;

        ImageLoadTask(ImageView iv, String key) {
            this.imageView = iv;
            this.cacheKey = key;
        }

        @Override
        protected Bitmap doInBackground(String... paths) {
            try {
                File f = new File(paths[0]);
                if (!f.exists()) return null;

                // Decode bounds first
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), opts);

                // Calculate inSampleSize for 400px max
                int maxDim = 400;
                int inSample = 1;
                if (opts.outHeight > maxDim || opts.outWidth > maxDim) {
                    int halfH = opts.outHeight / 2;
                    int halfW = opts.outWidth / 2;
                    while ((halfH / inSample) >= maxDim && (halfW / inSample) >= maxDim) {
                        inSample *= 2;
                    }
                }

                opts.inJustDecodeBounds = false;
                opts.inSampleSize = inSample;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                if (bmp != null) {
                    cache.put(cacheKey, bmp);
                }
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            if (bmp != null && cacheKey.equals(imageView.getTag())) {
                imageView.setImageBitmap(bmp);
            }
        }
    }

    private static class ViewHolder {
        ImageView image;
        TextView badge;
        TextView favBadge;
        TextView name;
        TextView date;
    }
}
