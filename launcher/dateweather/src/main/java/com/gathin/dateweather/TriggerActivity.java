package com.gathin.dateweather;

import android.app.Activity;
import android.os.Bundle;

/** No UI: (re)builds the channel now, then finishes. Launch with `am start`. */
public class TriggerActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ChannelWorker.schedule(this);
        finish();
    }
}
