package com.stunner.moderstars.pro.ViewHolders;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.stunner.moderstars.pro.Adapters.MyAdapter;


import java.io.File;

import stunner.moderstars.R;

import static com.stunner.moderstars.ActivityPro.ctx;


public class TitleChildViewHolder extends ChildViewHolder {
    public TextView option1;
    public CheckBox checkBox1;
    String path;
    public int count =0;
    public TitleChildViewHolder(View itemView) {
        super(itemView);
        checkBox1 = itemView.findViewById(R.id.checkBox1);
        option1 = itemView.findViewById(R.id.option1);
    }
    public String getPath() {
        return path;
    }

}

