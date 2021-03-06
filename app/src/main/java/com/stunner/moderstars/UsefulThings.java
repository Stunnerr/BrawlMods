package com.stunner.moderstars;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.stunner.moderstars.modlist.models.ModListFile;
import com.stunner.moderstars.modlist.models.ModListFolder;
import com.stunner.moderstars.signer.apksigner.Main;
import com.stunner.moderstars.ui.home.HomeFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import static com.stunner.moderstars.RedesignActivity.showSnackBar;


public class UsefulThings {
    public static final String TAG = "BSL";
    private static final Comparator<File> fileThenFolderComp = new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
            Boolean a = o1.isDirectory(), b = o2.isDirectory();
            if (a == b) return o1.getName().compareTo(o2.getName());
            return b.compareTo(a);
        }
    };
    @SuppressLint("StaticFieldLeak")
    public static Context ctx;
    public static FirebaseCrashlytics crashlytics;
    public static List<Object> checked = new ArrayList<>();
    public static boolean root = false;
    static String su = "su -c ";
    static String bspath;
    private static byte[] output;
    private static ProgressDialog pd;
    private static Runtime process;

    public static String calculateSHA(File f) {
        try {
            return calculateSHA(new FileInputStream(f));
        } catch (Exception ignore) {
        }
        return "no";
    }

    public static String calculateSHA(InputStream is) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {

            Log.e(TAG, "Exception while getting digest", e);
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try {
            StringBuilder sb = new StringBuilder();
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            for (byte b : md5sum) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                Log.e(TAG, "Exception on closing MD5 input stream", e);
            }
        }
    }

    private static File[] listMods(Context context) {
        String d = context.getExternalFilesDir(null) + "/Mods";
        new File(d).mkdirs();
        File[] mods = new File(d).listFiles();
        for (File i : mods)
            i.renameTo(new File(i.getParentFile().toString() + "/" + i.getName().toLowerCase()));
        mods = new File(d).listFiles();
        if (mods != null)
            Arrays.sort(mods, fileThenFolderComp);
        return mods;
    }

    public static File[] getMod(Context context, int modn) {
        modn--;
        String d = context.getExternalFilesDir(null) + "/Mods/" + modn;
        Log.d(TAG, d);
        File[] mods = new File(d).listFiles();
        if (mods != null)
            Arrays.sort(mods, fileThenFolderComp);
        return mods;
    }

    public static void delMod(Context context, int index) {
        File[] files = listMods(context);
        delFile(listMods(context)[index]);
        int repoid = getRepoModId(context, index);
        if (repoid != -1) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().remove("repo" + repoid).apply();
        }
        clearModName(context, index);
        for (int i = index + 1; i < files.length; ++i) {
            files[i].renameTo(new File(files[i].getAbsolutePath().replace("/Mods/" + i, "/Mods/" + (i - 1))));
        }
    }

    public static File[] listFiles(File modFolder) {
        File[] files = modFolder.listFiles();
        if (files != null)
            Arrays.sort(files, fileThenFolderComp);
        return files;

    }

    static void delFile(File file) {
        for (File fof : file.listFiles()) {
            if (fof.isFile()) fof.delete();
            else delFile(fof);
        }
        file.delete();
    }

    static void cmdCopy(File src, File dst) {
        output = new byte[256];
        if (process == null) {
            process = Runtime.getRuntime();
        }
        try {
            String owner;
            int len = new DataInputStream(process.exec(su + "stat -c %U " + bspath).getInputStream()).read(output);
            owner = new String(output).substring(0, len - 1);
            new DataInputStream(process.exec(su + "cp -r " + src.getAbsolutePath() + " " + dst.getAbsolutePath() + "").getInputStream()).read(output);
            String cmd = su + "chown " + owner + " " + dst.getAbsolutePath() + "";
            process.exec(cmd);
            output = new byte[256];
            new DataInputStream(process.exec(cmd.replace("chown", "chgrp")).getInputStream()).read(output);
            String a = new String(output);
        } catch (Exception e) {
            crashlytics.recordException(e);
        }

    }

    static void cmdCopy(String src, String dst) {
        output = new byte[256];
        if (process == null) {
            process = Runtime.getRuntime();
        }
        try {
            if (root)
                new DataInputStream(process.exec(su + "cp -r " + src + " " + dst + "").getInputStream()).readFully(output);
            Log.d(TAG, "copy out: " + new String(output));
        } catch (Exception e) {
            crashlytics.recordException(e);
        }

    }

    public static int getRepoModId(Context context, int id) {
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString("repo_ids", "[]");
        int ret = -1;
        try {
            JSONArray jsonArray = new JSONArray(json);
            try {
                ret = jsonArray.getInt(id);
            } catch (JSONException e) {
                jsonArray.put(id, -1);
                json = jsonArray.toString();
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("repo_ids", json).apply();
            }
        } catch (JSONException e) {
            crashlytics.setCustomKey("json", json);
            crashlytics.recordException(e);
        }
        return ret;
    }

    public static void setRepoModId(Context context, int id, int modId) {
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString("repo_ids", "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            jsonArray.put(id, modId);
            json = jsonArray.toString();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("repo_ids", json).apply();
        } catch (JSONException e) {
            crashlytics.setCustomKey("json", json);
            crashlytics.recordException(e);
        }
    }

    public static String getModName(Context context, int id) {
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString("names", "[]");
        String ret = context.getString(R.string.mod, id + 1);
        try {
            JSONArray jsonArray = new JSONArray(json);
            try {
                ret = jsonArray.getString(id);
            } catch (JSONException e) {
                ret = context.getString(R.string.mod, id + 1);
                jsonArray.put(id, ret);
                json = jsonArray.toString();
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("names", json).apply();
            }
        } catch (JSONException e) {
            crashlytics.setCustomKey("json", json);
            crashlytics.recordException(e);
        }
        return ret;
    }

    public static void setModName(Context context, int id, String name) {
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString("names", "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            jsonArray.put(id, name);
            json = jsonArray.toString();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("names", json).apply();
        } catch (JSONException e) {
            crashlytics.setCustomKey("json", json);
            crashlytics.recordException(e);
        }
    }

    static void clearModName(Context context, int id) {
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString("names", "");
        try {
            JSONArray jsonArray = new JSONArray(json);
            jsonArray.remove(id);
            json = jsonArray.toString();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("names", json).apply();
        } catch (JSONException e) {
            crashlytics.setCustomKey("json", json);
            crashlytics.recordException(e);
        }
    }

    public static void installAPK(File apkFile) {
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri apkURI = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", apkFile);
            intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }
        ctx.startActivity(intent);
        showSnackBar(ctx.getString(R.string.success));
    }

    public static int modСount(Context context) {
        String d = context.getExternalFilesDir(null) + "/Mods";
        File file = new File(d);
        file.mkdirs();
        int count = 0;
        try {
            for (File x : file.listFiles()) {
                if (x.listFiles().length - 1 != -1) count++;
            }
        } catch (Exception e) {
            count = 0;
        }
        return count;
    }

    static String sudo(String cmd) {
        output = new byte[256];
        if (process == null) {
            process = Runtime.getRuntime();
        }
        try {
            new DataInputStream(process.exec(su + cmd).getInputStream()).readFully(output);
            return new String(output);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String trimFolderName(String s) {
        String s1 = s.split("/csv_logic/")[0];
        s1 = s1.split("/badge/")[0];
        s1 = s1.split("/csv_client/")[0];
        s1 = s1.split("/font/")[0];
        s1 = s1.split("/image/")[0];
        s1 = s1.split("/localization/")[0];
        s1 = s1.split("/music/")[0];
        s1 = s1.split("/sc/")[0];
        s1 = s1.split("/sc3d/")[0];
        s1 = s1.split("/sfx/")[0];
        s1 = s1.split("/shader/")[0];
        s1 = s1.split("/fingerprint.json")[0];
        return s1.endsWith("/") ? (s1) : (s1 + "/");
    }

    public static class Signer extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(ctx);
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(false);
            pd.setTitle("BSL.Install");
            pd.setMessage(ctx.getString(R.string.installing).replace(":", "..."));
            pd.show();
        }

        @Override
        protected String doInBackground(String... strings) {
            try {
                ZipInputStream zin = new ZipInputStream(new FileInputStream(ctx.getExternalFilesDir(null) + "/bs_original.apk"));
                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(ctx.getExternalFilesDir(null) + "/bs_mod_unsigned.apk"));
                ZipEntry ze;
                int len;
                boolean flag;
                try {
                    while ((ze = zin.getNextEntry()) != null) {
                        flag = false;
                        zout.putNextEntry(new ZipEntry(ze.getName()));
                        if (ze.getName().contains("assets/")) {
                            for (Object z : checked) {
                                String x = z.getClass() == ModListFile.class ? ((ModListFile) z).getforcopy() : ((ModListFolder) z).getforcopy();
                                if (x.matches(ctx.getExternalFilesDir(null) + "/(\\d+)/" + ze.getName().replace("assets/", ""))) {
                                    FileInputStream fis = new FileInputStream(x);
                                    ZipEntry entry = new ZipEntry(x);
                                    zout.putNextEntry(entry);
                                    publishProgress(ctx.getString(R.string.installing) + x);
                                    byte[] buffer = new byte[16384];
                                    while ((len = fis.read(buffer)) != -1) {
                                        zout.write(buffer, 0, len);
                                    }
                                    zout.closeEntry();
                                    flag = true;
                                    break;
                                }

                            }
                            if (flag) continue;
                        }
                        //if (ze.getName().contains("META-INF")) continue;
                        publishProgress(ctx.getString(R.string.installing) + ze.getName());
                        byte[] buffer = new byte[16384];
                        while ((len = zin.read(buffer)) != -1) zout.write(buffer, 0, len);
                        zout.closeEntry();
                    }
                    zout.close();
                } catch (Exception e) {
                    crashlytics.recordException(e);
                    publishProgress(e.getMessage());
                }
                publishProgress(ctx.getString(R.string.signing));
                String[] strings1 = new String[3];
                strings1[0] = ctx.getExternalFilesDir(null) + "/sign";
                strings1[1] = ctx.getExternalFilesDir(null) + "/bs_mod_unsigned.apk";
                strings1[2] = ctx.getExternalFilesDir(null) + "/bs_mod_signed.apk";
                Main.main(strings1);
            } catch (Exception e) {
                crashlytics.recordException(e);
                Log.e(TAG, "Signing: ", e);
                //e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            pd.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(String ret) {
            if (ret != null) showSnackBar(ret);
            else {
                pd.dismiss();
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx);
                builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Runnable() {
                            @Override
                            public void run() {
                                installAPK(new File(ctx.getExternalFilesDir(null).getAbsolutePath() + "/bs_mod_signed.apk"));
                            }
                        }.run();
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        showSnackBar(ctx.getString(R.string.insuccess));
                    }
                });
                builder.setTitle("BSL");
                builder.setMessage("If you were using official version from Google Play, you must reinstall Brawl Stars, Make sure that your account is linked with Supercell ID. Continue?");
                builder.show();
            }
        }
    }

    public static class Unzipper extends AsyncTask<String, String, Void> {

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(ctx);
            pd.setTitle("BSL");
            pd.setMessage(ctx.getString(R.string.extracting).replace(":", "..."));
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(false);
            pd.show();
        }

        @Override
        protected Void doInBackground(String... zapk) {
            for (String str : zapk) {
                publishProgress(str);
                try (ZipFile zip = new ZipFile(new File(str))) {
                    if (zip.getEntry("classes.dex") != null) {
                        if (zip.getEntry("assets/fingerprint.json") != null) {//unapk
                            try {
                                File file = new File(str);
                                int b = listMods(ctx).length;
                                ctx.getExternalFilesDir(null).mkdirs();
                                ZipFile zipFile = new ZipFile(file);
                                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                                ZipEntry zipEntry = zipFile.getEntry("assets/fingerprint.json");
                                File file2 = new File((ctx.getExternalFilesDir(null).getAbsolutePath() + "/Temp/" + b + "/" + zipEntry.getName().replace("assets/", "")));
                                file2.getParentFile().mkdirs();
                                StringBuilder json = new StringBuilder();
                                BufferedInputStream bufferedInputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                byte[] bArr = new byte[8192];
                                BufferedOutputStream bufferedOutputStream;
                                while (true) {
                                    int read = bufferedInputStream.read(bArr);
                                    if (read == -1) {
                                        break;
                                    }
                                    json.append(new String(bArr));
                                }
                                bufferedInputStream.close();
                                JSONObject jsonObject = new JSONObject(json.toString());
                                JSONArray files = jsonObject.getJSONArray("files");
                                Map<String, String> list = new ArrayMap<>();
                                for (int i = 0; i < files.length(); ++i) {
                                    list.put(files.getJSONObject(i).getString("file").replace("\\/", "/"), files.getJSONObject(i).getString("sha"));
                                }
                                while (entries.hasMoreElements()) {
                                    publishProgress("Searching for changed files");
                                    zipEntry = entries.nextElement();
                                    if (!zipEntry.getName().contains("assets/")) continue;
                                    file2 = new File((ctx.getExternalFilesDir(null).getAbsolutePath() + "/Mods/" + b + "/" + zipEntry.getName().replace("assets/", "")));
                                    if (zipEntry.isDirectory()) continue;
                                    if (zipEntry.getName().replace(trimFolderName(zipEntry.getName()), "").equals(zipEntry.getName()))
                                        continue;
                                    if (zipEntry.getName().contains("fingerprint.json")) continue;
                                    bufferedInputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                    try {
                                        if (list.get(zipEntry.getName().replace("assets/", "")).equals(calculateSHA(bufferedInputStream)))
                                            continue;
                                    } catch (NullPointerException ignored) {
                                    }

                                    publishProgress(zipEntry.getName());
                                    file2.getParentFile().mkdirs();
                                    bufferedInputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                    bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file2), 8192);
                                    while (true) {
                                        int read = bufferedInputStream.read(bArr);
                                        if (read == -1) {
                                            break;
                                        }
                                        bufferedOutputStream.write(bArr, 0, read);
                                    }
                                    bufferedOutputStream.flush();
                                    bufferedOutputStream.close();
                                    bufferedInputStream.close();
                                }

                            } catch (Exception e) {
                                crashlytics.recordException(e);
                                Log.e(TAG, "Error: " + e);
                            }
                        } else {
                            cancel(true);
                            return null;
                        }
                    } else {        //unzipping
                        try {
                            File file = new File(str);
                            int b = listMods(ctx).length;
                            new File(ctx.getExternalFilesDir(null).getAbsolutePath()).mkdirs();
                            ZipFile zipFile = new ZipFile(file);
                            Enumeration<? extends ZipEntry> entries = zipFile.entries();
                            while (entries.hasMoreElements()) {
                                ZipEntry zipEntry = entries.nextElement();
                                //Log.d(TAG, "ZE: " + zipEntry.getName());
                                publishProgress(zipEntry.getName());
                                File file2 = new File((ctx.getExternalFilesDir(null).getAbsolutePath() + "/Mods/" + b + "/" + zipEntry.getName().replace(trimFolderName(zipEntry.getName()), "")));
                                file2.getParentFile().mkdirs();
                                if (!zipEntry.isDirectory()) {
                                    Log.d(TAG, "Extracting " + file2);
                                    BufferedInputStream bufferedInputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                                    byte[] bArr = new byte[1024];
                                    BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file2), 1024);
                                    while (true) {
                                        int read = bufferedInputStream.read(bArr);
                                        if (read == -1) {
                                            break;
                                        }
                                        bufferedOutputStream.write(bArr, 0, read);
                                    }
                                    bufferedOutputStream.flush();
                                    bufferedOutputStream.close();
                                    bufferedInputStream.close();
                                }
                            }
                        } catch (Exception e) {
                            crashlytics.recordException(e);
                            Log.e(TAG, "Error :" + e);
                        }//unzip-end
                    }
                } catch (Exception e) {
                    crashlytics.recordException(e);
                    Log.e(TAG, "doInBackground: ", e);
                }
                new File(str).delete();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            pd.setMessage(ctx.getString(R.string.extracting) + values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            pd.cancel();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            HomeFragment.mTabsAdapter.notifyDataSetChanged();
            pd.cancel();
            super.onPostExecute(aVoid);
        }
    }

    public static class Deploy extends AsyncTask<Void, String, String> {
        ProgressDialog pd;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd = new ProgressDialog(ctx);
            pd.setTitle("BSL.Install");
            pd.setMessage(ctx.getString(R.string.installing).replace(":", "..."));
            pd.setCancelable(false);
            pd.setCanceledOnTouchOutside(false);
            pd.show();
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                for (Object o : checked) {
                    if (o.getClass().equals(ModListFolder.class)) {
                        ModListFolder x = (ModListFolder) o;
                        publishProgress(ctx.getString(R.string.installing) + x.getforcopy());
                        if (x.getTitle().equals("fingerprint.json")) {
                            sudo("mkdir -p " + (bspath + "update/" + x.getTitle()));
                            cmdCopy(x.getPath(), new File(bspath + "update/" + x.getforcopy()));
                            Log.d(TAG, "mkdir -p " + (bspath + "update/" + x.getTitle()));
                            Log.d(TAG, x.getPath().getAbsolutePath());
                        } else
                            Log.d(TAG, sudo("mkdir -p " + (bspath + "update/" + x.getforcopy())));
                        Log.d(TAG, x.getforcopy());
                    } else if (o.getClass().equals(ModListFile.class)) {
                        ModListFile x = (ModListFile) o;
                        publishProgress(ctx.getString(R.string.installing) + x.getforcopy());
                        sudo("mkdir -p " + (bspath + "update/" + x.getforcopy().replace("/" + x.getOption1(), "/")));
                        cmdCopy(x.getPath(), new File(bspath + "update/" + x.getforcopy()));
                        Log.d(TAG, "mkdir -p " + (bspath + "update/" + x.getforcopy().replace("/" + x.getOption1(), "/")));
                        Log.d(TAG, x.getPath().getAbsolutePath());
                        Log.d(TAG, bspath + x.getforcopy());
                    } else {
                        cancel(true);
                        Log.d(TAG, "doInBackground: wtf");
                        return "error";
                    }
                }
            } catch (Exception e) {
                cancel(true);
                return e.getLocalizedMessage();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            pd.setMessage(values[0]);
        }

        @Override
        protected void onCancelled(String aVoid) {
            pd.dismiss();
            showSnackBar("Cancelled");
            super.onCancelled(aVoid);
        }

        @Override
        protected void onPostExecute(String aVoid) {
            super.onPostExecute(aVoid);
            pd.dismiss();
            showSnackBar(aVoid == null ? (String) ctx.getText(R.string.success) : aVoid);
        }
    }
}
