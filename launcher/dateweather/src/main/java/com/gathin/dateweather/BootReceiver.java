package com.gathin.dateweather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-publishes and re-schedules the channel after a reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ChannelWorker.schedule(context);
    }
}
