package com.uetmems.micropump_test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import java.util.UUID
import kotlin.math.floor

class MainActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "UET-MEMS MainActivity"
        private const val REQUEST_ENABLE_BT = 891
        private const val REQUEST_PERMISSIONS = 596
        private const val SCAN_TIME: Long = 10000

        private const val START_PROGRESS = 100 //%
        //private var CURRENT_USER = "user0000"
        private var isConnected = false
        private var isGoodToStartScan = false
        private var sendSucess = false
        private var ESP_ADDRESS = "null"

        @RequiresApi(Build.VERSION_CODES.S)
        private val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }

    //UI
    private lateinit var etPumpSpeed : EditText
    private lateinit var etPumpVol : EditText
    private lateinit var spnRateUnit : Spinner
    private lateinit var spnTargetUnit : Spinner
    private lateinit var tvTimeCalc : TextView
    private lateinit var buttonStart : Button
    private lateinit var buttonStop : Button
    private lateinit var spinnerSyringeType : Spinner
    private lateinit var spinnerOPMode: Spinner
    private lateinit var pbPumpProgress : ProgressBar
    private lateinit var tvTaskRunning: TextView
    private lateinit var tvConnectStatus :TextView

    //timer
    private var timeElapsed = 0
    //bluetooth LE functionalities
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var btnConnect : Button

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var gattGlobal: BluetoothGatt? = null
    private lateinit var bluetoothLEDevice: BluetoothDevice


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //checking if the system supports ble
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            showNoBLECapability()
        }

        //request to access location permission
        if(!hasPermissions())
        {
            val requestPermissionsLauncher =
                registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                )
                { permissions->
                    if(permissions.all{ it.value})
                    {
                        Log.i(TAG,"All permissions granted.")
                    }
                    else {
                        Log.w(TAG,"Missing permissions")


                    }
                }

            requestPermissionsLauncher.launch(requiredPermissions)
        }

        /*
        //shortcut
        showFinishedDialog()
        */

        //items
        etPumpSpeed = findViewById(R.id.etPumpSpeed)
        etPumpVol = findViewById(R.id.etPumpVol)
        spnRateUnit = findViewById(R.id.spnRateUnit)
        spnTargetUnit = findViewById(R.id.spnTargetUnit)

        tvTimeCalc = findViewById(R.id.tvTimeCalc)
        spinnerOPMode = findViewById(R.id.spinnerOPMode)
        spinnerSyringeType = findViewById(R.id.spinnerSyringeType)
        buttonStart = findViewById(R.id.buttonStart)
        buttonStop = findViewById(R.id.buttonStop)
        pbPumpProgress = findViewById(R.id.pbPumpProgress)
        tvTaskRunning = findViewById(R.id.tvTaskRunning)
        btnConnect = findViewById(R.id.btnConnect)
        tvConnectStatus = findViewById(R.id.tvConnectionStatus)

        // initialization
        updateConnectionStatus()
        //edit text
        etPumpSpeed.addTextChangedListener( object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
               Log.i(TAG,"Pump SPEED set to $p0")
                updateTimeUI(calculateTime())

            }
        })
        etPumpVol.addTextChangedListener( object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                Log.i(TAG,"Pump VOL set to $p0")
                updateTimeUI(calculateTime())
            }
        })

        //dropdown menu setup
        //syringe
        val syringeType = resources.getStringArray(R.array.Syringe_type)
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            syringeType
        ).also {adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSyringeType.adapter = adapter
        }
        spinnerSyringeType.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                Log.i(TAG,"Selected syringe:"+ syringeType[p2])
                updateTimeUI(calculateTime())
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        //operation mode
        val opModes = resources.getStringArray(R.array.Operation_mode)
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            opModes
        ).also{adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerOPMode.adapter = adapter
        }
        spinnerOPMode.onItemSelectedListener= object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                Log.i(TAG,"Selected MODE: " + opModes[p2])
                updateTimeUI(calculateTime())
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        //rate unit
        val rateUnit = resources.getStringArray(R.array.rate_units)
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            rateUnit
        ).also{adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spnRateUnit.adapter = adapter
        }
        spnRateUnit.onItemSelectedListener= object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                Log.i(TAG,"Selected rate unit: " + rateUnit[p2])
                updateTimeUI(calculateTime())
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }


        val targetUnit = resources.getStringArray(R.array.target_units)
        ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            targetUnit
        ).also{adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spnTargetUnit.adapter = adapter
        }
        spnTargetUnit.onItemSelectedListener= object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                Log.i(TAG,"Selected target unit: " + targetUnit[p2])
                updateTimeUI(calculateTime())
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        //Buttons
        buttonStop.isEnabled = false
        buttonStop.isClickable = false
        buttonStart.setOnClickListener {
            Log.i(TAG,"START button clicked")
            startPump(calculateTime())
        }

        btnConnect.setOnClickListener {
                Log.i(TAG, "Connect clicked")
                btnConnect.isEnabled = false
                btnConnect.setText(R.string.connecting)
                requestBLEPermission()
        }

        //Progress bar
        pbPumpProgress.rotation = 179f
        pbPumpProgress.setProgress(START_PROGRESS,true)
    }

    private fun showNoBLECapability() {
        Log.w(TAG,"Device does not support BLE")
        AlertDialog.Builder(this)
            .setTitle("Your device does not have Bluetooth Low Energy functionality")
            .setPositiveButton("OK"){_,_ ->
                finish()
            }.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasPermissions() :Boolean
    {
        return requiredPermissions.all{
            ContextCompat.checkSelfPermission(this,it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBLEPermission() {
        var bluetoothEnabled = false
        //  BLUETOOTh
        if (bluetoothAdapter == null ){
            showNoBLECapability()
            return
        }
        else {
            var lauchOneRequest = true
            while (!bluetoothEnabled)
            {
                checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_PERMISSIONS)

                if (!bluetoothAdapter.isEnabled) {
                    //if bluetooth is not enabled
                    val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                    if (lauchOneRequest) {
                        lauchOneRequest = false
                        startActivityForResult(enableBluetoothIntent,REQUEST_ENABLE_BT)
                    }
                }
                else {
                    Log.i(TAG,"Bluetooth already enabled")
                    bluetoothEnabled = true
                }
            }

        }

        //LOCATION
        var locationEnabled = false
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_PERMISSIONS)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        {
            //if locatoin is not enabled
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,SCAN_TIME)
                .setMinUpdateIntervalMillis(5000)
                .build()
            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
            val client = LocationServices.getSettingsClient(this)
            val task1: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

            task1.addOnSuccessListener { locationSettingsResponse->
                Log.i(LOCATION_TAG,"location turned on! $locationSettingsResponse")
                locationEnabled = true
            }
            task1.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        exception.startResolutionForResult(
                            this@MainActivity,
                            REQUEST_PERMISSIONS
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        }
        else {
            Log.i(TAG,"Location already enabled ")
            locationEnabled = true
        }

        isGoodToStartScan = bluetoothEnabled && locationEnabled
        if (isGoodToStartScan)
        {
            scanForESP32()
        }
    }

    private fun checkPermission(permission: String, requestCode: Int) {
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
               permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    requestCode
                )
            }
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK)
        {
            Log.i(TAG,"bluetooth enabled! @activityresult")
            //bluetoothEnabled = true
            isGoodToStartScan = true
            return
        }
        else {//if(requestCode == REQUEST_ENABLE_BT && resultCode != Activity.RESULT_OK) {
            Log.i(TAG,"bluetooth not enabled! @activityresult")
            Toast.makeText(this,R.string.enable_bt,Toast.LENGTH_SHORT).show()
            isGoodToStartScan = false
            requestBLEPermission()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private val scanCallback = object: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device
            checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)
            Log.d(SCAN_TAG,"device found: ${device?.name}, @${device?.address}")
            //Log.i(SCAN_TAG,"looking for device named ${R.string.BLE_device_name}")
            if (device?.name == resources.getString(R.string.BLE_device_name))
            {
                Log.i(SCAN_TAG,"Device found!")
                stopScan()
                ESP_ADDRESS = device.address
                connectToESP32(ESP_ADDRESS)
                //update ui
                btnConnect.isEnabled = true
                btnConnect.setText(R.string.disconnect)
                updateConnectionStatus()
            }
            super.onScanResult(callbackType, result)
        }

        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(this@MainActivity, R.string.scan_failed, Toast.LENGTH_SHORT).show()
            Log.e(SCAN_TAG, "Scan failed with error: $errorCode")
            return
        }
    }
    private val noDeviceFoundDialog = Runnable {
        //todo: find a way to remove callbacks from this runnable
        if (!isConnected) {
            stopScan()
            isGoodToStartScan = true
            btnConnect.isEnabled = true
            btnConnect.isClickable = true
            btnConnect.setText(R.string.connect)
            Log.w(SCAN_TAG, "cannot find device")
            Toast.makeText(
                this,
                R.string.no_device,
                Toast.LENGTH_LONG
            ).show()
        }
        else {
            Log.i(SCAN_TAG, "Device already connected")
            Log.i(SCAN_TAG, "How to turn this one off :'( ")
        }
    }
    @SuppressLint("MissingPermission")
    private fun scanForESP32() {
        //scanning
        Log.i(TAG,"Start scanning")

        bluetoothLeScanner?.startScan(scanCallback)

        handler.postDelayed(noDeviceFoundDialog, SCAN_TIME)
    }
    private fun stopScan() {
        Log.i(SCAN_TAG, "Stopped BLE scan")
        isGoodToStartScan = false;
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)
        handler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(noDeviceFoundDialog)
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val bluetoothGattCallback = object: BluetoothGattCallback(){
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                // successfully connected to the GATT Server
                gattGlobal = gatt
                Log.i(CONNECT_TAG,"Device connected ${gatt.toString()}")

                btnConnect.setOnClickListener{
                    gatt?.disconnect()
                    Log.i(TAG, "Disconnect clicked")
                    btnConnect.isEnabled = true
                    isConnected = false
                    btnConnect.setText(R.string.connect)
                }

                gatt?.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                // disconnected from the GATT Server
                isConnected = false
                isGoodToStartScan = true
                btnConnect.setText(R.string.connect)

                btnConnect.setOnClickListener{
                    //gatt?.connect()
                    Log.i(TAG, "Connect clicked")
                    btnConnect.isEnabled = false
                    btnConnect.setText(R.string.connecting)
                    scanForESP32()
                }
                updateConnectionStatus()
            }

        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val listOfServices = gatt?.services
            if (listOfServices != null) {
                isConnected = true
                isGoodToStartScan = false
                btnConnect.isEnabled = true
                updateConnectionStatus()
                for (i in 0..<listOfServices.size) {
                    val uuid = listOfServices[i].uuid
                    Log.i(CONNECT_TAG,"Service UUID $i: ${uuid}")
                    if(uuid.toString() == UUIDs[0]) {
                        val characteristics = listOfServices[i].characteristics

                        for (j in characteristics.indices) {
                            Log.d(CONNECT_TAG,"chars uuid $j: ${characteristics[j].uuid}")
                        }
                        break
                    }
                }
                updateConnectionStatus()
            }
            else {
                Log.w(CONNECT_TAG,"Did not find any services")
                return
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                Log.d(BLE_WRITE,"Write to ${characteristic?.uuid} succeeded !")
                sendSucess = true
            }
            else {
                Log.e(BLE_WRITE,"Write failed ${characteristic?.uuid}: ${characteristic!!.value}, status: $status")
                if (retryCount < 3){
                    writeToESP(characteristic,characteristic.value)
                    retryCount++
                }
                else {
                    Log.e(BLE_WRITE,"Max attempt reached, skipping")
                    retryCount = 0
                }

                sendSucess = false

            }
        }
    }
    private fun connectToESP32(address:String) {
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)

        bluetoothAdapter?.let{ adapter ->
            bluetoothLEDevice = adapter.getRemoteDevice(address)
            // connect to the GATT server on the device
            bluetoothLEDevice.connectGatt(this, false, bluetoothGattCallback )
        }
    }

    //base functions
    private fun parseData(): List<String> {
        val rateUnit:Double = when (spnRateUnit.selectedItemPosition) {
            0 -> 1.0 / 60.0 // ml/min
            1 -> 1.0        //ml/s
            2 -> 1e-3 / 3600//ul/h
            3 -> 1e-3 / 60  //ul/min
            4 -> 1e-3       //ul/s

            else -> -1.0 //error
        }
        val rate = etPumpSpeed.text.toString().toDouble()

        val volUnit:Double = when (spnTargetUnit.selectedItemPosition) {
            0 -> 1.0    //ml
            1 -> 1e-3   //ul

            else -> -1.0 // error
        }
        val vol = etPumpVol.text.toString().toDouble()

        val isRunning = 1
        val list = listOf(
            "%4f".format(rate*rateUnit),
            (vol*volUnit).toString(),
            spinnerSyringeType.selectedItemPosition.toString(),
            spinnerOPMode.selectedItemPosition.toString(),
            isRunning.toString()
        )

        Log.i(TAG,"[rate, target, syringe, mode]")
        Log.i(TAG,"$list")

        return list
    }
    private fun updateConnectionStatus(){
        if(isConnected) {
            tvConnectStatus.setText(R.string.connected)
            tvConnectStatus.setTextColor(ContextCompat.getColor(this,R.color.connected))
            btnConnect.setText(R.string.disconnect)
            //tvConnectStatus.setBackgroundColor(ContextCompat.getColor(this,R.color.connected))

        }
        else {
            tvConnectStatus.setText(R.string.disconnected)
            tvConnectStatus.setTextColor(ContextCompat.getColor(this,R.color.not_connected))
            btnConnect.setText(R.string.connect)

        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    private fun startPump(time: Double){
        //error checking
        if (etPumpSpeed.text.isBlank() ||
            etPumpVol.text.isBlank() ||
            time <= 0)
        {
            Toast.makeText(this,R.string.no_info,Toast.LENGTH_SHORT).show()
            return
        }

        if (!isConnected){
            Toast.makeText(this,R.string.not_connect,Toast.LENGTH_SHORT).show()
            return
        }
        else {
            val serviceUUID = UUID.fromString(UUIDs[0])
            Log.i(TAG, "Sending data to device: ${gattGlobal.toString()}")
            val dataToSend = parseData()

            //write characteristics one by one
            for (i in 1..<UUIDs.size) {
                /* number meaning
                //1 = rate
                //2 = target
                //3 = syringe
                //4 = mode
                //5 = ispumping
                */

                //get the uuids
                Log.i(BLE_WRITE, "Writing chars number $i");
                val characteristicUUID = UUID.fromString(UUIDs[i])
                val characteristic = gattGlobal?.getService(serviceUUID)
                    ?.getCharacteristic(characteristicUUID)

                //write data to characteristic
                writeToESP(characteristic,dataToSend[i-1].toByteArray(Charsets.UTF_8))
            }
        }
        //update UI
        btnConnect.isClickable = false
        buttonStart.isEnabled = false
        etPumpSpeed.isEnabled = false
        etPumpVol.isEnabled = false
        spinnerOPMode.isEnabled = false
        spinnerSyringeType.isEnabled = false

        tvTaskRunning.text = spinnerOPMode.selectedItem.toString()


        //If everything sent successfully
        if (sendSucess) {
            //make a timer
            val timer = object : CountDownTimer(floor(time).toLong() * 1000 + 1, 1000) {
                override fun onTick(p0: Long) {
                    //update the UI
                    updateTimeUI(p0.toDouble() / 1000)
                    val percentageLeft = (100 * (p0.toDouble() / (p0.toDouble() + timeElapsed*1000)) ).toInt()
                    pbPumpProgress.setProgress(percentageLeft, true)
                    //add elapsed time
                    timeElapsed++

                    //Exception check
                    //if bluetooth is turned off
                    if (bluetoothAdapter?.isEnabled == false) {
                        stopPump(STOP_PUMP)
                        isConnected = false
                        btnConnect.setText(R.string.connect)
                        Log.e(TAG, "Bluetooth turned off")
                        Toast.makeText(
                            this@MainActivity,
                            R.string.bluetooth_turned_off,
                            Toast.LENGTH_LONG
                        ).show()

                        btnConnect.setOnClickListener {
                            Log.i(TAG, "Connect clicked")
                            btnConnect.isEnabled = false
                            btnConnect.setText(R.string.connecting)
                            requestBLEPermission()
                        }
                        cancel()
                    }

                    val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    //if micro pump is disconnected, stop the pump
                    if (btManager.getConnectionState(bluetoothLEDevice,BluetoothProfile.GATT) == BluetoothProfile.STATE_DISCONNECTED) {
                        stopPump(STOP_PUMP)
                        isConnected = false
                        btnConnect.setText(R.string.connect)
                        Log.e(TAG, "Bluetooth device disconnected")
                        Toast.makeText(
                            this@MainActivity,
                            R.string.bluetooth_disconnected,
                            Toast.LENGTH_LONG
                        ).show()

                        btnConnect.setOnClickListener {
                            Log.i(TAG, "Connect clicked")
                            btnConnect.isEnabled = false
                            btnConnect.setText(R.string.connecting)
                            requestBLEPermission()
                        }
                        cancel()
                    }
                }

                override fun onFinish() {
                    Toast.makeText(this@MainActivity, R.string.finished_toast, Toast.LENGTH_LONG).show()
                    stopPump(STOP_PUMP)
                    btnConnect.isEnabled = true

                }
            }.start()

            buttonStop.isEnabled = true
            buttonStop.setOnClickListener {
                //todo: make it so that it pauses and 1 more click make it stop in its original place
                //todo change button lable to pause -> stop after pasing
                //todo change start to continue after pausing
                Log.i(TAG, "PAUSE button clicked")
                Log.i(TAG, "Time elasped: $timeElapsed")
                stopPump(PAUSE_PUMP)

                timer.cancel()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun writeToESP(characteristic: BluetoothGattCharacteristic?, data: ByteArray) {
        sendSucess = false
        //begin writing
        if (characteristic != null) {
            gattGlobal?.writeCharacteristic(characteristic,data,BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }
        //wait for a success call before continue
        var logOnlyOnce = true
        while (!sendSucess)
        {
            if (logOnlyOnce) {
                Log.i(BLE_WRITE, "Waiting for a success call")
                logOnlyOnce = false
            }
        }

    }

    private fun showFinishedDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)
        alertDialogBuilder.setTitle(R.string.finished_dialog)
            .setIcon(R.mipmap.ic_launcher_round)
            .setMessage(R.string.finished_message)
            .setPositiveButton(R.string.finished_return) {dialog, which->
                Log.i(TAG,"return pressed")
                Toast.makeText(this@MainActivity,R.string.returning,Toast.LENGTH_LONG)
                    .show()
                dialog.cancel()
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    private fun stopPump(isPumping: Int) {
        if(isPumping == STOP_PUMP) { //if it is stopped
            //write to IsPumping 0
            if (sendSucess) {
                val serviceUUID = UUID.fromString(UUIDs[0])
                Log.i(TAG, "Sending data to device: ${gattGlobal.toString()}")
                val characteristicUUID = UUID.fromString(UUIDs[5]) // isRunning
                val characteristic = gattGlobal?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
                //get a byte array of character set UTF-8
                val dataToWrite = isPumping.toString().toByteArray(Charsets.UTF_8)
                writeToESP(characteristic,dataToWrite)
            }
            //resetting time elasped
            timeElapsed = 0

            //update the UI
            updateTimeUI(calculateTime())
            tvTaskRunning.text = " "
            pbPumpProgress.setProgress(START_PROGRESS,true)
            //disable some function
            buttonStop.isEnabled = false
            buttonStop.isClickable = false
            buttonStart.setText(R.string.button_start)
            //re enable some

            btnConnect.isClickable = true
            btnConnect.isEnabled = true
            spinnerOPMode.isEnabled = true
            spinnerSyringeType.isEnabled = true
            buttonStart.isEnabled = true
            buttonStart.isClickable = true
            etPumpVol.isEnabled = true
            etPumpSpeed.isEnabled = true

            showFinishedDialog()
        }
        else if (isPumping == PAUSE_PUMP) {
            //if it is paused
            //write to IsPumping 2
            if (sendSucess) {
                val serviceUUID = UUID.fromString(UUIDs[0])
                Log.i(TAG, "Sending data to device: ${gattGlobal.toString()}")
                val characteristicUUID = UUID.fromString(UUIDs[5]) // isRunning
                val characteristic = gattGlobal?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
                //get a byte array of character set UTF-8
                val dataToWrite = isPumping.toString().toByteArray(Charsets.UTF_8)
                writeToESP(characteristic,dataToWrite)
            }
            //update the UI **keep time elasped the same
            updateTimeUI(calculateTime())
            val originalTime = calculateTime() + timeElapsed
            val currentProgress = floor(100 - (timeElapsed / originalTime)*100)
            tvTaskRunning.setText(R.string.paused)
            pbPumpProgress.setProgress(currentProgress.toInt(),true)

            //re-enable some function
            buttonStop.isEnabled = true
            buttonStop.isClickable = true
            buttonStop.setText(R.string.button_stop)
            buttonStop.setOnClickListener{
                Log.i(TAG,"STOP clicked")
                buttonStop.setText(R.string.button_pause)
                stopPump(STOP_PUMP)
            }
            buttonStart.isEnabled = true
            buttonStart.isClickable = true
            buttonStart.setText(R.string.button_continue)
        }

    }
    private fun updateTimeUI(timeCalc :Double) {
        if (timeCalc <= 0.0)
        {
            tvTimeCalc.text = " "
            return
        }
        if (timeCalc <= 10)
        {
            tvTimeCalc.text = buildString {
                append("%.3f".format(timeCalc))
                append(" s")
            }
            return
        }
        val hh = floor(timeCalc / 3600)
        val mm = floor(timeCalc/60 - hh*60)
        val ss = floor(timeCalc - mm*60 - hh*3600)
        //update UI (convert seconds to hh mm ss)
        tvTimeCalc.text = buildString {
            append("%.0f".format(hh))
            append(" : ")

            append("%.0f".format(mm))
            append(" : ")

            append("%.2f".format(ss))
            //append(" s")
        }
    }
    private fun calculateTime() :Double {
        //error checking
        if( etPumpSpeed.text.isEmpty() ||
            etPumpVol.text.isEmpty() ||
            etPumpSpeed.text.toString() == "0"||
            etPumpVol.text.toString() == "0" ||
            etPumpSpeed.text.toString() == "." ||
            etPumpVol.text.toString() == "."
            )
        {
            tvTimeCalc.text = " "
            return 0.0
        }

        //get values
        val rate = etPumpSpeed.text.toString().toDouble()
        val vol = etPumpVol.text.toString().toDouble()
        val rateUnit:Double = when (spnRateUnit.selectedItemPosition) {
            0 -> 1.0 / 60.0     // ml/min
            1 -> 1.0            //ml/s
            2 -> 1e-3 / 3600    //ul/h
            3 -> 1e-3 / 60      //ul/min
            4 -> 1e-3           //ul/s

            else -> -1.0 //error
        }
        val volUnit:Double = when (spnTargetUnit.selectedItemPosition) {
            0 -> 1.0    //ml
            1 -> 1e-3   //ul

            else -> -1.0 // error
        }
        //val type = spinnerSyringeType.selectedItem.toString()
        //val mode = spinnerOPMode.selectedItem.toString()

        //calculate time
        val timeCalc = (vol * volUnit) / (rate * rateUnit) + 1 - timeElapsed
        Log.i(TAG,"timecalc: $timeCalc")
        return timeCalc
    }
}