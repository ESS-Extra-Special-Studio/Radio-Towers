package net.mcreator.radiotowers.client.gui;

/**
 * Screens that can open {@link InternetStationScreen} and need their tower-station state updated when a stream is chosen.
 */
public interface InternetStationParent {

    void onInternetStreamChosen(String deadAirStationId);
}
