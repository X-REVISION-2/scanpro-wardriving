package net.wigle.wigleandroid.state;

import android.util.Log;

import net.wigle.wigleandroid.model.Network;

import java.util.List;

public class WifiStateHolder {

    // Compose-friendly observable list
    public static final androidx.compose.runtime.snapshots.SnapshotStateList<Network> networks =
            new androidx.compose.runtime.snapshots.SnapshotStateList<>();

    public static void setNetworks(List<Network> newList) {
        networks.clear();
        networks.addAll(newList);
    }

    public static void addOrUpdate(Network net) {
        Log.d("COMPOSE_DEBUG","ADDING NETWORK: " + net.getSsid());
        // simple version (you can improve later)
        for (int i = 0; i < networks.size(); i++) {
            if (networks.get(i).getBssid().equals(net.getBssid())) {
                networks.set(i, net);
                return;
            }
        }
        networks.add(net);
    }
}