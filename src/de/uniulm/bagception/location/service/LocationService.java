package de.uniulm.bagception.location.service;

import java.util.ArrayList;
import java.util.List;
import de.uniulm.bagception.services.attributes.OurLocation;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;

public class LocationService extends Service{
	
	private ResultReceiver resultReceiver;
	
	private LocationManager locationManager;
	private LocationListener locationListener;
	private BluetoothAdapter bluetoothAdapter;
	private BroadcastReceiver mReceiver;
	private boolean isBTRegistered = false;
	
	
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
	public void onDestroy() {
		super.onDestroy();
		unregisterListeners();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		resultReceiver = intent.getParcelableExtra("receiverTag");
		log("request received!");
		
		
		String requestType = "";
		if(intent.hasExtra(OurLocation.REQUEST_TYPE))requestType = intent.getStringExtra(OurLocation.REQUEST_TYPE);
		
		
		if(requestType.equals(OurLocation.GETLOCATION)){
			storedLocations.clear();
			log("RequestType: " + intent.getStringExtra(OurLocation.REQUEST_TYPE));
			
			// check if device supports bluetooth
			if(bluetoothAdapter != null){
				log("hasBT");
				if(bluetoothAdapter.isEnabled()){
					log("isEnabledBT");
					searchForBluetoothDevices();
					// TODO: BT search...
					// http://developer.android.com/guide/topics/connectivity/bluetooth.html
				}
			}

			// check if gps based location search is enabled
			if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
				log("gps based enabled");
			}
			
			// check if network and cell based location search is enabled
			if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
				// search for nearby wifi aps and check if their mac match with a location mac
				searchForWifiAccessPoints();
				log("network based enabled");
			}
			
			
		}
		
		
		if(requestType.equals(OurLocation.RESOLVE_ADDRESS)){
			
		}
		
		return super.onStartCommand(intent, flags, startId);
	}
	
	
	
	/**
	 * searches for nearby wifi access points and checks if a detected mac (bssid) matches with locations mac
	 */
	public void searchForWifiAccessPoints(){
		log("searchForWifiAPs");
		WifiManager mainWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		
		IntentFilter i = new IntentFilter();
	    i.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

	    registerReceiver(new BroadcastReceiver(){
	            @Override
	            public void onReceive(Context context, Intent intent) {
	                WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	                mWifiManager.getScanResults();
	                List<ScanResult> scanResults = mWifiManager.getScanResults();
	                for(ScanResult sr : scanResults){
	                	// TODO: 
	                	// 1. check if mac(bssid) is in db
	                	// 2. get location (lat,lng)
	                	// 3. call sendBestPositionFromLocations()
	                	log("Name: " + sr.SSID + " MAC: " + sr.BSSID);
	                	Location loc = new Location("WIFI");
	                	loc.setLatitude(99);
	                	loc.setLongitude(99);
	                	loc.setAccuracy(1);
	                	sendBestPositionFromLocations(loc);
	                }
	            }
	        }
	    ,i);
	    mainWifi.startScan();
	}
	
	public void searchForBluetoothDevices(){
		log("searchForBTDevices");
		// Create a BroadcastReceiver for ACTION_FOUND
		mReceiver = new BroadcastReceiver() {
		    public void onReceive(Context context, Intent intent) {
		        String action = intent.getAction();
		        // When discovery finds a device
		        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
		            // Get the BluetoothDevice object from the Intent
		            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                	// TODO: 
                	// 1. check if mac(device.getAddress()) is in db
                	// 2. get location (lat,lng)
		           Location loc = new Location("BLUETOOTH");
		           loc.setAccuracy(1);
		           loc.setLatitude(99);
		           loc.setLongitude(99);
		           sendBestPositionFromLocations(loc);
		        }
		    }
		};
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
		isBTRegistered = true;
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
			unregisterListeners();
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
	
	private void unregisterListeners(){
		// unregisterBluetooth
		if(isBTRegistered){
			unregisterReceiver(mReceiver); 
			isBTRegistered=false;
		}
		locationManager.removeUpdates(locationListener);
	}
	
	private void log(String s){
		Log.d("LocationService", s);
	}
}
