/*
 * Performance Control - An Android CPU Control application Copyright (C) 2012
 * Jared Rummler Copyright (C) 2012 James Roberts
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.brewcrewfoo.performance.util;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import com.brewcrewfoo.performance.widget.PCWidget;

import java.io.*;

public class Helpers implements Constants {

    private static String mVoltagePath;

    /**
     * Checks device for SuperUser permission
     *
     * @return If SU was granted or denied
     */
    public static boolean checkSu() {
        if (!new File("/system/bin/su").exists() && !new File("/system/xbin/su").exists()) {
            Log.e(TAG, "su does not exist!!!");
            return false; // tell caller to bail...
        }

        try {
            if ((new CMDProcessor().su.runWaitFor("ls /data/app-private")).success()) {
                Log.i(TAG, " SU exists and we have permission");
                return true;
            } else {
                Log.i(TAG, " SU exists but we dont have permission");
                return false;
            }
        } catch (final NullPointerException e) {
            Log.e(TAG, e.getLocalizedMessage().toString());
            return false;
        }
    }

    /**
     * Checks to see if Busybox is installed in "/system/"
     *
     * @return If busybox exists
     */
    public static boolean checkBusybox() {
        if (!new File("/system/bin/busybox").exists() && !new File("/system/xbin/busybox").exists()) {
            Log.e(TAG, "Busybox not in xbin or bin!");
            return false;
        }

        try {
            if (!new CMDProcessor().su.runWaitFor("busybox mount").success()) {
                Log.e(TAG, " Busybox is there but it is borked! ");
                return false;
            }
        } catch (final NullPointerException e) {
            Log.e(TAG, e.getLocalizedMessage().toString());
            return false;
        }
        return true;
    }

    /**
     * Return mount points
     *
     * @param path
     * @return line if present
     */
    public static String[] getMounts(final String path) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"), 256);
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.contains(path)) {
                    return line.split(" ");
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "/proc/mounts does not exist");
        } catch (IOException e) {
            Log.d(TAG, "Error reading /proc/mounts");
        }
        return null;
    }

    /**
     * Get mounts
     *
     * @param mount
     * @return success or failure
     */
    public static boolean getMount(final String mount) {
        final CMDProcessor cmd = new CMDProcessor();
        final String mounts[] = getMounts("/system");
        if (mounts != null && mounts.length >= 3) {
            final String device = mounts[0];
            final String path = mounts[1];
            final String point = mounts[2];
            if (cmd.su.runWaitFor(
                    "mount -o " + mount + ",remount -t " + point + " " + device
                            + " " + path).success()) {
                return true;
            }
        }
        return (cmd.su.runWaitFor("busybox mount -o remount," + mount + " /system").success());
    }

    /**
     * Read one line from file
     *
     * @param fname
     * @return line
     */
    public static String readOneLine(String fname) {
        String line = null;
        if (new File(fname).exists()) {
        	BufferedReader br;
	        try {
	            br = new BufferedReader(new FileReader(fname), 512);
	            try {
	                line = br.readLine();
	            } finally {
	                br.close();
	            }
	        } catch (Exception e) {
	            Log.e(TAG, "IO Exception when reading sys file", e);
	            // attempt to do magic!
	            return readFileViaShell(fname, true);
	        }
        }
        return line;
    }

    /**
     * Read file via shell
     *
     * @param filePath
     * @param useSu
     * @return file output
     */
    public static String readFileViaShell(String filePath, boolean useSu) {
        CMDProcessor.CommandResult cr = null;
        if (useSu) {
            cr = new CMDProcessor().su.runWaitFor("cat " + filePath);
        } else {
            cr = new CMDProcessor().sh.runWaitFor("cat " + filePath);
        }
        if (cr.success())
            return cr.stdout;
        return null;
    }

    /**
     * Write one line to a file
     *
     * @param fname
     * @param value
     * @return if line was written
     */
    public static boolean writeOneLine(String fname, String value) {
    	if (!new File(fname).exists()) {return false;}
        try {
            FileWriter fw = new FileWriter(fname);
            try {
                fw.write(value);
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            String Error = "Error writing to " + fname + ". Exception: ";
            Log.e(TAG, Error, e);
            return false;
        }
        return true;
    }

    /**
     * Gets available schedulers from file
     *
     * @return available schedulers
     */
    public static String[] getAvailableIOSchedulers() {
        String[] schedulers = null;
        String[] aux = readStringArray(IO_SCHEDULER_PATH[0]);
        if (aux != null) {
            schedulers = new String[aux.length];
            for (int i = 0; i < aux.length; i++) {
                if (aux[i].charAt(0) == '[') {
                    schedulers[i] = aux[i].substring(1, aux[i].length() - 1);
                } else {
                    schedulers[i] = aux[i];
                }
            }
        }
        return schedulers;
    }

    /**
     * Reads string array from file
     *
     * @param fname
     * @return string array
     */
    private static String[] readStringArray(String fname) {
        String line = readOneLine(fname);
        if (line != null) {
            return line.split(" ");
        }
        return null;
    }

    /**
     * Get current IO Scheduler
     *
     * @return current io scheduler
     */
    public static String getIOScheduler() {
        String scheduler = null;
        String[] schedulers = readStringArray(IO_SCHEDULER_PATH[0]);
        if (schedulers != null) {
            for (String s : schedulers) {
                if (s.charAt(0) == '[') {
                    scheduler = s.substring(1, s.length() - 1);
                    break;
                }
            }
        }
        return scheduler;
    }
/*
     * @return available performance scheduler
     */
    public static Boolean GovernorExist(String gov) {
		if(readOneLine(GOVERNORS_LIST_PATH).indexOf(gov)>-1){
			return true;
		}
		else{
			return false;
		}
    }

    /**
     * Get total number of cpus
     * @return total number of cpus
     */
    public static int getNumOfCpus() {
        int numOfCpu = 1;
        String numOfCpus = Helpers.readOneLine(NUM_OF_CPUS_PATH);
        String[] cpuCount = numOfCpus.split("-");
        if (cpuCount.length > 1) {
            try {
                int cpuStart = Integer.parseInt(cpuCount[0]);
                int cpuEnd = Integer.parseInt(cpuCount[1]);

                numOfCpu = cpuEnd - cpuStart + 1;

                if (numOfCpu < 0)
                    numOfCpu = 1;
            } catch (NumberFormatException ex) {
                numOfCpu = 1;
            }
        }
        return numOfCpu;
    }

    /**
     * Check if any voltage control tables exist and set the voltage path if a
     * file is found.
     * <p/>
     * If false is returned, there was no tables found and none will be used.
     *
     * @return true/false if uv table exists
     */
    public static boolean voltageFileExists() {
        if (new File(UV_MV_PATH).exists()) {
            setVoltagePath(UV_MV_PATH);
            return true;
        } else if (new File(VDD_PATH).exists()) {
            setVoltagePath(VDD_PATH);
            return true;
        } else if (new File(VDD_SYSFS_PATH).exists()) {
            setVoltagePath(VDD_SYSFS_PATH);
            return true;
        } else if (new File(COMMON_VDD_PATH).exists()) {
            setVoltagePath(COMMON_VDD_PATH);
            return true;
        }
        return false;
    }

    /**
     * Sets the voltage file to be used by the rest of the app elsewhere.
     * @param voltageFile
     */
    public static void setVoltagePath(String voltageFile) {
        Log.d(TAG, "UV table path detected: "+voltageFile);
        mVoltagePath = voltageFile;
    }

    /**
     * Gets the currently set voltage path
     * @return voltage path
     */
    public static String getVoltagePath() {
        return mVoltagePath;
    }

    /**
     * Convert to MHz and append a tag
     * @param mhzString
     * @return tagged and converted String
     */
    public static String toMHz(String mhzString) {
        return new StringBuilder().append(Integer.valueOf(mhzString) / 1000).append(" MHz").toString();
    }

    /**
     * Restart the activity smoothly
     * @param activity
     */
    public static void restartPC(final Activity activity) {
        if (activity == null)
            return;
        final int enter_anim = android.R.anim.fade_in;
        final int exit_anim = android.R.anim.fade_out;
        activity.overridePendingTransition(enter_anim, exit_anim);
        activity.finish();
        activity.overridePendingTransition(enter_anim, exit_anim);
        activity.startActivity(activity.getIntent());
    }

    /**
     * Helper to update the app widget
     * @param context
     */
    public static void updateAppWidget(Context context) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, PCWidget.class);
        int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
        Intent update = new Intent();
        update.setAction("com.brewcrewfoo.performance.ACTION_FREQS_CHANGED");
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        context.sendBroadcast(update);
    }

    /**
     * Helper to create a bitmap to set as imageview or bg
     * @param bgcolor
     * @return bitmap
     */
    public static Bitmap getBackground(int bgcolor) {
        try {
            Bitmap.Config config = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = Bitmap.createBitmap(2, 2, config);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(bgcolor);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
    public static String binExist(String b) {
        CMDProcessor.CommandResult cr = null;
        cr = new CMDProcessor().sh.runWaitFor("busybox which " + b);
        if (cr.success()){ return  cr.stdout; }
        else{ return NOT_FOUND;}
    }

    public static String getCachePartition() {
        CMDProcessor.CommandResult cr = null;
        cr = new CMDProcessor().sh.runWaitFor("busybox echo `busybox mount | busybox grep cache | busybox cut -d' ' -f1`");
        if(cr.success()&& !cr.stdout.equals("") ){return cr.stdout;}
        else{return NOT_FOUND;}
    }

    public static boolean showBattery() {
	return ((new File(BLX_PATH).exists()) || (new File(FASTCHARGE_PATH).exists()));
    }

	public static void shCreate(){
		if (! new File(SH_PATH).exists()) {
			new CMDProcessor().su.runWaitFor("busybox touch "+SH_PATH );	
			new CMDProcessor().su.runWaitFor("busybox chmod 755 "+SH_PATH );
			Log.d(TAG, "create: "+SH_PATH);
		}
	}
	public static String shExec(StringBuilder s){
		if (new File(SH_PATH).exists()) {
			s.insert(0,"#!"+binExist("sh")+"\n\n");
			new CMDProcessor().su.runWaitFor("busybox echo \""+s.toString()+"\" > " + SH_PATH );
            CMDProcessor.CommandResult cr = null;
			cr=new CMDProcessor().su.runWaitFor(SH_PATH);
			//Log.d(TAG, "execute: "+s.toString());
            if(cr.success()){return cr.stdout;}
            else{Log.d(TAG, "execute: "+cr.stderr);return "";}
		}
		else{
			Log.d(TAG, "missing file: "+SH_PATH);
            return "";
		}
	}

    public static void get_assetsFile(String fn,Context c,String aux){
        byte[] buffer;
        final AssetManager assetManager = c.getAssets();
        try {
            InputStream f =assetManager.open(fn);
            buffer = new byte[f.available()];
            f.read(buffer);
            f.close();
            final String s = new String(buffer);
            final StringBuffer sb = new StringBuffer(s);
            if(!aux.equals("")){ sb.insert(0,aux+"\n"); }
            sb.insert(0,"#!"+Helpers.binExist("sh")+"\n\n");
            try {
                FileOutputStream fos;
                fos = c.openFileOutput(fn, Context.MODE_PRIVATE);
                fos.write(sb.toString().getBytes());
                fos.close();

            } catch (IOException e) {
                Log.d(TAG, "error write "+fn+" file");
                e.printStackTrace();
            }

        }
        catch (IOException e) {
            Log.d(TAG, "error read "+fn+" file");
            e.printStackTrace();
        }
    }
    public static String ReadableByteCount(long bytes) {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = String.valueOf("KMGTPE".charAt(exp-1));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}
