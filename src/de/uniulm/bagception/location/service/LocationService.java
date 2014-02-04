package de.uniulm.bagception.location.service;

import java.util.ArrayList;
import java.util.List;

import de.uniulm.bagception.services.attributes.OurLocation;

import android.app.IntentService;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;


public class LocationService extends Service{
	
	private ResultReceiver resultReceiver;
	
	private LocationManager locationManager;
	private LocationListener locationListener;
	private BluetoothAdapter bluetoothAdapter;
	
	
	private ArrayList<Location> storedLocations;
	private float LOCATION_ACCURACY_THRESHOLD = 100f; // accuracy in meters

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		storedLocations = new ArrayList<Location>();
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
		
		
		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				log("new location arrived");
				sendBestPositionFromLocations(location);
			}

			@Override
			public void onProviderDisabled(String provider) {}
			@Override
			public void onProviderEnabled(String provider) {}
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {}
		};
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		storedLocations.clear();
		
		resultReceiver = intent.getParcelableExtra("receiverTag");
		log("request received!");
		String requestType = "";
		log("requesttype...." + intent.getStringExtra(OurLocation.REQUEST_TYPE));
		if(intent.hasExtra(OurLocation.REQUEST_TYPE))requestType = intent.getStringExtra(OurLocation.REQUEST_TYPE);
		
		if(requestType.equals(OurLocation.LOCATION)){
			log("RequestType: " + intent.getStringExtra(OurLocation.REQUEST_TYPE));
			
			// check if device supports bluetooth
			if(bluetoothAdapter != null){
				log("hasBT");
				if(bluetoothAdapter.isEnabled()){
					log("isEnabledBT");
					// TODO: BT search...
					// http://developer.android.com/guide/topics/connectivity/bluetooth.html
				}
			}

			// check if gps based location search is enabled
			if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
				log("isEnabledGPS");
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
			}
			
			// check if network and cell based location search is enabled
			if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
				log("isEnabledNetwork");
				WifiManager mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			    WifiInfo currentWifi = mainWifi.getConnectionInfo();
			    if(currentWifi != null){
			    	// TODO: Add MAC type
			    	// TODO: Check if mac is in db
			    	// just for testing...
			    	Location loc = new Location("WLAN");
			    	loc.setAccuracy(1);
			    	loc.setAltitude(99);
			    	loc.setLatitude(99);
			    	sendBestPositionFromLocations(loc);
			    }
			}
			
			
			// if nothing is enabled
//			if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
//				// TODO: kein dienst aktiviert... aufforderung senden!
////				storedLocations.clear();
//				locationManager.removeUpdates(locationListener);
//				Bundle b = new Bundle();
//				b.putString(OurLocation.RESPONSE_TYPE, OurLocation.LOCATION);
//				b.putString(OurLocation.PROVIDER, OurLocation.NONE);
//				resultReceiver.send(0, b);
//				log("sending answer...");
//			}
		}
			
		if(requestType.equals(OurLocation.RESOLVE_ADDRESS)){
			
			
		}
		
		return super.onStartCommand(intent, flags, startId);
	}
	

	/**
	 * Sends the location with the best accuracy back to the ResultReceiver.
	 * <br>The accuracy must be bether (lower) than LOCATION_ACCURACY_THRESHOLD.
	 * <br>Service will wait for a new location from LocationManager if accuracy > LOCATION_ACCURACY_THRESHOLD.
	 * @param location current location
	 */
	private void sendBestPositionFromLocations(Location location) {
		storedLocations.add(location);
		Location bestLocation = storedLocations.get(0);
		for(Location l : storedLocations){
			// search for the location with the best accuracy
			if(l.getAccuracy() >= bestLocation.getAccuracy()){
				bestLocation = l;
			}
		}
		log("bestLoc acc: " + bestLocation.getAccuracy() + " type: " + bestLocation.getProvider());
		if(bestLocation.getAccuracy() < LOCATION_ACCURACY_THRESHOLD){
			storedLocations.clear();
			locationManager.removeUpdates(locationListener);
			
			double latitude = bestLocation.getLatitude();
			double longitude = bestLocation.getLongitude();
			float accuracy = bestLocation.getAccuracy();
			String provider = bestLocation.getProvider();
			log("prv: " + provider + " acc: " + accuracy + " lat: " + latitude + " lng: " + longitude);
			
			// sending answer
			Bundle b = new Bundle();
			b.putString(OurLocation.RESPONSE_TYPE, OurLocation.LOCATION);
			b.putFloat(OurLocation.ACCURACY, accuracy);
			b.putDouble(OurLocation.LONGITUDE, longitude);
			b.putDouble(OurLocation.LATITUDE, latitude);
			b.putString(OurLocation.PROVIDER, provider);
			
			resultReceiver.send(0, b);
			
			log("sending answer...");
		}
	}
	
	private void log(String s){
		Log.d("LocationService", s);
	}
}
