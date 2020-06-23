package com.stunner.moderstars;


import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import stunner.moderstars.R;

public class TabEditor extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SettingsAdapter());

    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {
        SettingsAdapter() {
            super();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.edit_list, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int ab) {
            final int position = holder.getBindingAdapterPosition();
            String text = UsefulThings.getname(getApplicationContext(), position);
            final OnClickListener b = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(TabEditor.this);
                    alertDialog.setMessage(getString(R.string.remove, UsefulThings.getname(getApplicationContext(), position)));
                    alertDialog.setPositiveButton(getText(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final Snackbar s = Snackbar.make(findViewById(R.id.editbtn), R.string.removed, BaseTransientBottomBar.LENGTH_SHORT);
                            final Snackbar.Callback callback = new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar transientBottomBar, int event) {
                                    super.onDismissed(transientBottomBar, event);
                                    UsefulThings.removename(getApplicationContext(), position);
                                    try {
                                        File a = UsefulThings.checkmod(getApplicationContext(), position + 1)[0].getParentFile();
                                        UsefulThings.delfile(a);
                                    } catch (Exception e) {
                                        UsefulThings.crashlytics.recordException(e);
                                    }
                                    notifyItemRemoved(position);
                                }
                            };
                            s.addCallback(callback);
                            s.setAction("Undo", new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    s.removeCallback(callback);
                                }
                            });
                            s.show();
                        }
                    });
                    alertDialog.setNegativeButton(getText(R.string.no), null);
                    alertDialog.show();
                }
            }, a = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(TabEditor.this);
                    alertDialog.setMessage(R.string.entername);
                    final EditText input = new EditText(TabEditor.this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT);
                    input.setLayoutParams(lp);
                    alertDialog.setView(input);
                    alertDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String newn = input.getText().toString().equals("") ? getString(R.string.mod, position + 1) : input.getText().toString();
                            UsefulThings.setname(getApplicationContext(), position, newn);
                            notifyItemChanged(position);
                        }
                    });
                    alertDialog.show();
                }
            };
            holder.setData(text, a, b);
        }

        @Override
        public int getItemCount() {
            return UsefulThings.modcount(getApplicationContext());
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            AppCompatImageButton edit, delete;

            ViewHolder(View v) {
                super(v);
                text = v.findViewById(R.id.title);
                edit = v.findViewById(R.id.editbtn);
                delete = v.findViewById(R.id.delbtn);
            }

            void setData(String text, OnClickListener edit, OnClickListener delete) {
                this.text.setText(text);
                this.edit.setOnClickListener(edit);
                this.delete.setOnClickListener(delete);
            }
        }
    }

}