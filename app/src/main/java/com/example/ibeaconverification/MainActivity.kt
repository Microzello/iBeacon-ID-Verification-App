package com.example.ibeaconverification

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ibeaconverification.databinding.ActivityMainBinding
import com.google.zxing.integration.android.IntentIntegrator
import java.nio.ByteBuffer
import java.util.UUID

/**
 * iBeacon ID Verification App
 * 
 * This application demonstrates how to use an Android device to broadcast as an iBeacon.
 * It allows users to set up company and personal identification, scan asset QR codes,
 * and then broadcast this information as an iBeacon advertisement packet.
 * 
 * The app follows the iBeacon specification from Apple, formatting the advertisement with:
 * - A UUID representing the company ID
 * - A Major value representing the personal ID
 * - A Minor value representing the asset ID (scanned from QR code)
 * 
 * Features:
 * - Bluetooth LE advertising as iBeacon
 * - QR code scanning for asset ID
 * - Proper permission handling for Android 11+
 * - Device compatibility checking
 */
class MainActivity : AppCompatActivity() {
    // Logging tag
    private val TAG = "iBeaconVerificationApp"

    // View binding for accessing UI elements
    private lateinit var binding: ActivityMainBinding
    
    // Bluetooth components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    
    // iBeacon configuration constants
    
    /**
     * Fixed company UUID for iBeacon - This would typically be a unique identifier for your organization
     * In a production app, this could be configured or fetched from a server
     */
    private val companyUUID = UUID.fromString("F2DEB72A-E93A-4385-9E9A-40680F510933")
    
    /**
     * Fixed major value (personal ID) - 2 bytes (0-65535)
     * This would represent the person's ID in the organization
     */
    private val majorValue: Int = 0xA5B9
    
    /**
     * Minor value (asset ID) - 2 bytes (0-65535)
     * This will be obtained from the QR code scan
     */
    private var minorValue: Int = 0
    
    // State tracking
    private var isAdvertising = false
    
    // Permission request launchers
    private lateinit var bluetoothPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var bluetoothEnableLauncher: ActivityResultLauncher<Intent>

    /**
     * Initialize the activity, set up UI components, and check device compatibility
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up permission request handlers
        setupPermissionLaunchers()
        
        // Initialize UI elements with data from resources
        binding.tvCompanyId.text = getString(R.string.company_id)
        binding.tvPersonalId.text = getString(R.string.personal_id)
        
        // Set up QR code scanner button
        binding.btnScanQr.setOnClickListener {
            checkCameraPermission()
        }
        
        // Set up authentication button
        binding.btnAuthenticate.setOnClickListener {
            checkBluetoothPermissions()
        }
        
        // Verify this device supports BLE Advertising
        checkBleAdvertisingSupport()
    }
    
    /**
     * Check if this device supports Bluetooth LE Advertising
     * This is required to function as an iBeacon
     */
    private fun checkBleAdvertisingSupport() {
        // Get the Bluetooth Manager service
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Log.e(TAG, "Bluetooth manager not available")
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            binding.btnAuthenticate.isEnabled = false
            return
        }
        
        // Get the Bluetooth adapter
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            binding.btnAuthenticate.isEnabled = false
            return
        }
        
        // Check if this device supports BLE
        val isBleAdvertiserSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!isBleAdvertiserSupported) {
            Log.e(TAG, "BLE not supported")
            Toast.makeText(this, "Bluetooth LE not supported on this device", Toast.LENGTH_LONG).show()
            binding.btnAuthenticate.isEnabled = false
            return
        }
        
        // Check if Bluetooth is enabled
        if (!bluetoothAdapter!!.isEnabled) {
            requestEnableBluetooth()
        } else {
            // Try to get the advertiser
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "Advertising not supported")
                Toast.makeText(this, "Bluetooth LE advertising not supported on this device", Toast.LENGTH_LONG).show()
                binding.btnAuthenticate.isEnabled = false
            }
        }
    }
    
    /**
     * Request the user to enable Bluetooth
     * This handles the different permission models for Android versions
     */
    private fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothEnableLauncher.launch(enableBtIntent)
            } else {
                // Request BLUETOOTH_CONNECT permission first
                checkBluetoothPermissions()
            }
        } else {
            bluetoothEnableLauncher.launch(enableBtIntent)
        }
    }
    
    /**
     * Set up the permission request launchers
     * These handle the asynchronous permission requests and their results
     */
    private fun setupPermissionLaunchers() {
        // Bluetooth permissions launcher - handles the result of Bluetooth permission requests
        bluetoothPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            Log.d(TAG, "Bluetooth permissions result: $permissions")
            
            // Android 12+ (API 31+) uses new Bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val advertiseGranted = permissions[Manifest.permission.BLUETOOTH_ADVERTISE] ?: false
                val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
                val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
                
                if (advertiseGranted && connectGranted && scanGranted) {
                    Log.d(TAG, "All Bluetooth permissions granted")
                    
                    // Check if Bluetooth is enabled
                    if (bluetoothAdapter?.isEnabled == true) {
                        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                        startAdvertising()
                    } else {
                        requestEnableBluetooth()
                    }
                } else {
                    Log.d(TAG, "Some Bluetooth permissions denied")
                    Toast.makeText(this, "All Bluetooth permissions are required to authenticate", Toast.LENGTH_LONG).show()
                }
            } else {
                // For Android 11 (API 30) and lower, use the old permission model
                if (permissions[Manifest.permission.BLUETOOTH] == true && 
                    permissions[Manifest.permission.BLUETOOTH_ADMIN] == true) {
                    
                    // Check if Bluetooth is enabled
                    if (bluetoothAdapter?.isEnabled == true) {
                        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                        startAdvertising()
                    } else {
                        requestEnableBluetooth()
                    }
                } else {
                    Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // Bluetooth enable launcher - handles the result of the Bluetooth enable request
        bluetoothEnableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled successfully")
                bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                if (bluetoothLeAdvertiser == null) {
                    Log.e(TAG, "Advertising not supported even after enabling Bluetooth")
                    Toast.makeText(this, "BLE Advertising not supported on this device", Toast.LENGTH_LONG).show()
                    binding.btnAuthenticate.isEnabled = false
                } else {
                    startAdvertising()
                }
            } else {
                Log.d(TAG, "Bluetooth enable request denied")
                Toast.makeText(this, "Bluetooth needs to be enabled for authentication", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Camera permission launcher - handles the result of the camera permission request
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                initiateScan()
            } else {
                Toast.makeText(this, "Camera permission required to scan QR codes", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Check and request necessary Bluetooth permissions 
     * Handles different requirements based on Android version
     */
    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) requires these specific Bluetooth permissions
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
            
            // Check which permissions are needed
            val permissionsToRequest = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (permissionsToRequest.isNotEmpty()) {
                Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
                bluetoothPermissionLauncher.launch(permissionsToRequest)
            } else {
                // All permissions granted, proceed
                Log.d(TAG, "All Bluetooth permissions already granted")
                if (bluetoothAdapter?.isEnabled == true) {
                    bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                    startAdvertising()
                } else {
                    requestEnableBluetooth()
                }
            }
        } else {
            // For Android 11 (API 30) and lower, use the old permission model
            val oldPermissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
            
            val oldPermissionsToRequest = oldPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()
            
            if (oldPermissionsToRequest.isNotEmpty()) {
                bluetoothPermissionLauncher.launch(oldPermissionsToRequest)
            } else {
                if (bluetoothAdapter?.isEnabled == true) {
                    bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
                    startAdvertising()
                } else {
                    requestEnableBluetooth()
                }
            }
        }
    }
    
    /**
     * Check and request camera permission for QR code scanning
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            initiateScan()
        }
    }
    
    /**
     * Launch the QR code scanner
     * This uses the ZXing library to scan and parse QR codes
     */
    private fun initiateScan() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan Asset QR Code for Authentication")
        integrator.setCameraId(0)  // Use default camera
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(false)
        integrator.initiateScan()
    }
    
    /**
     * Handle the result from the QR code scanner
     * Parses the scanned QR code as a hex value for the minor (asset ID) part of the iBeacon
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
            } else {
                try {
                    // Try to parse the QR code result as a 2-byte hex value
                    val hexValue = result.contents.replace(" ", "")
                    minorValue = Integer.parseInt(hexValue, 16)
                    
                    // Display the scanned value
                    binding.tvScannedCode.text = "Scanned Code: $hexValue"
                    
                    // Enable the authenticate button
                    binding.btnAuthenticate.isEnabled = true
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid QR code format. Please scan a valid hex code.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    
    /**
     * Start broadcasting as an iBeacon
     * This sets up and begins the BLE advertisement with the iBeacon format
     */
    private fun startAdvertising() {
        Log.d(TAG, "startAdvertising() called")
        
        // If already advertising, stop first
        if (isAdvertising) {
            Log.d(TAG, "Already advertising, stopping first")
            stopAdvertising()
        }
        
        // Check/initialize the Bluetooth LE advertiser
        if (bluetoothLeAdvertiser == null) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            
            if (!bluetoothAdapter!!.isEnabled) {
                Log.d(TAG, "Bluetooth is not enabled")
                Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
                requestEnableBluetooth()
                return
            }
            
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "BLE Advertising not supported on this device")
                Toast.makeText(this, "Bluetooth LE advertising not supported on this device", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Double-check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "BLUETOOTH_ADVERTISE permission not granted")
                Toast.makeText(this, "Bluetooth advertising permission required", Toast.LENGTH_SHORT).show()
                checkBluetoothPermissions()
                return
            }
        }
        
        // Configure the advertisement settings for maximum visibility
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // Highest frequency
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)     // Maximum power
            .setConnectable(false)                                          // iBeacons are not connectable
            .setTimeout(0)                                                  // Don't timeout
            .build()
        
        // Build the iBeacon advertisement data
        val iBeaconData = buildIBeaconData(companyUUID, majorValue, minorValue, -59)
        
        try {
            // Start the advertisement
            Log.d(TAG, "Starting advertising with data: UUID=$companyUUID, major=$majorValue, minor=$minorValue")
            Log.d(TAG, "Bluetooth adapter name: ${bluetoothAdapter?.name}, address: ${bluetoothAdapter?.address}")
            
            bluetoothLeAdvertiser?.startAdvertising(settings, iBeaconData, advertiseCallback)
            binding.tvStatus.text = getString(R.string.advertising_message)
            isAdvertising = true
            
            // Stop advertising after 10 seconds to conserve battery
            Handler(Looper.getMainLooper()).postDelayed({
                stopAdvertising()
            }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
            Toast.makeText(this, "Error starting advertisement: ${e.message}", Toast.LENGTH_SHORT).show()
            isAdvertising = false
        }
    }
    
    /**
     * Stop the iBeacon advertisement broadcast
     */
    private fun stopAdvertising() {
        try {
            if (!isAdvertising) {
                Log.d(TAG, "Not currently advertising, nothing to stop")
                return
            }
            
            // Check permission again before stopping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Can't stop advertising - permission not granted")
                    return
                }
            }
            
            // Stop the advertisement
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            binding.tvStatus.text = ""
            isAdvertising = false
            Log.d(TAG, "Advertising stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertisement", e)
        }
    }
    
    /**
     * Stop advertising when the app is paused (user navigates away, etc.)
     */
    override fun onPause() {
        super.onPause()
        if (isAdvertising) {
            stopAdvertising()
        }
    }
    
    /**
     * Callback for Bluetooth LE advertising events
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertisement started successfully")
            Log.d(TAG, "Advertise settings in effect: " +
                "mode=${settingsInEffect.mode}, " +
                "txPowerLevel=${settingsInEffect.txPowerLevel}, " +
                "timeout=${settingsInEffect.timeout}")
            
            runOnUiThread {
                binding.tvStatus.text = getString(R.string.advertising_message)
                Toast.makeText(applicationContext, "Authentication beacon started", Toast.LENGTH_SHORT).show()
            }
        }
        
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            val errorMessage = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Unknown error $errorCode"
            }
            Log.e(TAG, "Failed to start advertisement: $errorMessage (code $errorCode)")
            
            runOnUiThread {
                Toast.makeText(applicationContext, "Failed to start beacon: $errorMessage", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = ""
                isAdvertising = false
            }
        }
    }
    
    /**
     * Build the iBeacon advertisement data packet
     * 
     * This follows Apple's iBeacon format:
     * - Prefix: 0x02, 0x15
     * - UUID: 16 bytes
     * - Major: 2 bytes
     * - Minor: 2 bytes
     * - TX Power: 1 byte
     * 
     * @param uuid The company/organization UUID (16 bytes)
     * @param major The major value (2 bytes) - typically representing personal ID
     * @param minor The minor value (2 bytes) - typically representing asset ID
     * @param txPower The calibrated TX power at 1 meter
     * @return The formatted AdvertiseData object
     */
    private fun buildIBeaconData(uuid: UUID, major: Int, minor: Int, txPower: Int): AdvertiseData {
        // Convert UUID to bytes first
        val uuidBytes = getUuidBytes(uuid)
        
        // Create the payload directly - exact iBeacon format
        val payload = ByteArray(23) // Length: 2 (prefix) + 16 (UUID) + 2 (major) + 2 (minor) + 1 (tx power)
        
        // iBeacon prefix (0x02, 0x15)
        payload[0] = 0x02 // Type
        payload[1] = 0x15 // Length of remaining data (21 bytes)
        
        // Copy UUID bytes (16 bytes)
        System.arraycopy(uuidBytes, 0, payload, 2, 16)
        
        // Major value (2 bytes) - big endian
        payload[18] = (major shr 8).toByte()
        payload[19] = major.toByte()
        
        // Minor value (2 bytes) - big endian
        payload[20] = (minor shr 8).toByte()
        payload[21] = minor.toByte()
        
        // TX Power at 1m
        payload[22] = txPower.toByte()
        
        Log.d(TAG, "iBeacon payload created: ${payload.joinToString(", ") { "0x%02X".format(it) }}")
        
        return AdvertiseData.Builder()
            .addManufacturerData(0x004C, payload) // 0x004C is Apple's company ID
            .setIncludeTxPowerLevel(false) // Don't include TX power in the advertisement
            .setIncludeDeviceName(false)   // Don't include device name
            .build()
    }
    
    /**
     * Convert a UUID to a byte array
     * 
     * @param uuid The UUID to convert
     * @return Byte array representation of the UUID
     */
    private fun getUuidBytes(uuid: UUID): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return bb.array()
    }
    
    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
} 