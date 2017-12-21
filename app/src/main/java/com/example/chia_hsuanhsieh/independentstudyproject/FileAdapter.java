package com.example.chia_hsuanhsieh.independentstudyproject;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends ArrayAdapter<String> {
    private Context context;
    private int layoutResourceId;
    public List<String> list = new ArrayList<String>();
    public String currPath;
    private Bitmap ic_folder, ic_file, ic_return;

    public FileAdapter(Context context, int layoutResourceId, ArrayList<String> data) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.list = data;
        this.currPath = Environment.getExternalStorageDirectory().getPath();

        ic_folder = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_folder);
        ic_file = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_file);
        ic_return = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_return);
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ItemHolder holder = null;

        if(row == null) {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new ItemHolder();
            holder.img = (ImageView)row.findViewById(R.id.imgIcon);
            holder.text = (TextView)row.findViewById(R.id.textTitle);
            row.setTag(holder);
        }
        else {
            holder = (ItemHolder)row.getTag();
        }

        String file = list.get(position);
        holder.text.setText(file);
        if( file.contains(".") ){
            holder.img.setImageBitmap(ic_file);
        } else {
            if( file.equals("Return   ") ){
                holder.img.setImageBitmap(ic_return);
            } else {
                holder.img.setImageBitmap(ic_folder);
            }
        }

        return row;
    }
    static class ItemHolder
    {
        ImageView img;
        TextView text;
    }

    public void scanFiles(String top_path, String path) {
        list.clear();

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (dir.isHidden()) return false;
                File file = new File(dir.getPath() + File.separator + name);
                if (!file.isHidden()) return true;
                else return false;
            }
        };

        if( !path.equals(top_path) ){
            list.add("Return   ");
        }
        File[] files = new File(path).listFiles(filter);

        if( files!=null ){
            for (File file : files) {
                list.add( file.getName() );
            }
        }
        this.notifyDataSetChanged();
        currPath = path;
    }

}
