package com.uetmems.micropump_test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
//import com.google.firebase.Firebase
///import com.google.firebase.firestore.firestore


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

        private val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN
        )
    }

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

    //bluetooth LE functionalities
    private lateinit var btnConnect : Button

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var gattGlobal: BluetoothGatt? = null
    //firebase shitz
    //private var db = Firebase.firestore


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

    private fun hasPermissions() :Boolean
    {
        return requiredPermissions.all{
            ContextCompat.checkSelfPermission(this,it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBLEPermission() {
        var bluetoothEnabled = false
        //  BLUETOOT
        if (bluetoothAdapter == null ){
            showNoBLECapability()
            return
        }
        else {
            var lauchOneRequest = true
            while (!bluetoothEnabled)
            {
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
                Log.i(LOCATION_TAG,"location turned on! ${locationSettingsResponse}")
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
        /*
        AlertDialog.Builder(this)
            .setTitle("Requesting permissions")
            .setMessage("We need location and bluetooth to be on for the app to work")
            .setPositiveButton("ok") {_,_ ->
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,SCAN_TIME)
                    .setMinUpdateIntervalMillis(5000)
                    .build()
                val builder = LocationSettingsRequest.Builder()
                    .addLocationRequest(locationRequest)
                val client = LocationServices.getSettingsClient(this)
                val task1: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

                task1.addOnSuccessListener { locationSettingsResponse->
                    Log.i(LOCATION_TAG,"location turned on! ${locationSettingsResponse}")
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
            }.show()


        */
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
            Toast.makeText(this,"pls enable bluetooth",Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this@MainActivity, "Cannot initiate scanning", Toast.LENGTH_SHORT).show()
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
                "Cannot find device",
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

        Handler().postDelayed(noDeviceFoundDialog, SCAN_TIME)
    }
    private fun stopScan() {
        Log.i(SCAN_TAG, "Stopped BLE scan")
        isGoodToStartScan = false;
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)
        Handler().removeCallbacksAndMessages(null)
        Handler().removeCallbacks(noDeviceFoundDialog)
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
                isConnected = true
                isGoodToStartScan = false
                btnConnect.isEnabled = true

                btnConnect.setOnClickListener{
                    gatt?.disconnect()
                    Log.i(TAG, "Disconnect clicked")
                    btnConnect.isEnabled = true
                    isConnected = false
                    btnConnect.setText(R.string.connect)
                }

                updateConnectionStatus()
                gatt?.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                // disconnected from the GATT Server
                isConnected = false
                isGoodToStartScan = true
                btnConnect.setText(R.string.connect)
                btnConnect.setOnClickListener{
                    gatt?.connect()
                    btnConnect.setText(R.string.disconnect)
                }
                updateConnectionStatus()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val listOfServices = gatt?.services
            if (listOfServices != null) {
                for (i in 0..<listOfServices.size) {
                    val uuid = listOfServices[i].uuid
                    Log.i(CONNECT_TAG,"Service UUID $i: ${uuid}")
                    if(uuid.toString() == UUIDs[0]) {
                        val characteristics = listOfServices[i].characteristics

                        for (j in characteristics.indices) {
                            Log.d(CONNECT_TAG,"chars uuid $i: ${characteristics[j].uuid}")
                        }
                        break
                    }
                }
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
                Log.i(TAG,"write to ${characteristic?.uuid} succeeded !")
                sendSucess = true
            }
            else {
                Log.e(CONNECT_TAG,"Value couldnt be sent to ${characteristic?.uuid}")
                sendSucess = false
            }
        }
    }
    private fun connectToESP32(address:String) {
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)

        bluetoothAdapter?.let{ adapter ->
            val device = adapter.getRemoteDevice(address)
            // connect to the GATT server on the device
            device.connectGatt(this, false, bluetoothGattCallback )
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
            (rate*rateUnit).toString(),
            (vol*volUnit).toString(),
            spinnerSyringeType.selectedItemPosition.toString(),
            spinnerOPMode.selectedItemPosition.toString(),
            isRunning.toString()
        )

        Log.i(TAG,"[rate, target, syringe, mode")
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

    @SuppressLint("MissingPermission")
    private fun startPump(time: Double){
        //error checking
        if (etPumpSpeed.text.isBlank() ||
            etPumpVol.text.isBlank() ||
            time <= 0)
        {
            Toast.makeText(this,"Please enter Rate / Target ",Toast.LENGTH_SHORT).show()
            return
        }

        if (!isConnected){
            Toast.makeText(this,"Please connect to micropump first",Toast.LENGTH_SHORT).show()
            return
        }
        else {
            val serviceUUID = UUID.fromString(UUIDs[0])
            Log.i(TAG, "Sending data to device: ${gattGlobal.toString()}")
            val dataToSend = parseData()

            for (i in 1..<UUIDs.size) {
                //1 = rate
                //2 = target
                //3 = syringe
                //4 = mode
                //5 = run

                //get the uuids, set write type
                val characteristicUUID = UUID.fromString(UUIDs[i])
                val characteristic = gattGlobal?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
                characteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                var logOnlyOnce = true
                while (!sendSucess && i != 1 )
                {
                    if (logOnlyOnce) {
                        Log.d(TAG, "waiting for a success call")
                        logOnlyOnce = false
                    }
                }
                //begin writing
                characteristic.let {
                    //get a byte array of character set UTF-8
                    val dataToWrite = dataToSend[i-1].toByteArray(Charsets.UTF_8)

                    // Set the data to write (it = characteristics(var) )
                    it?.value = dataToWrite

                    // Write data
                    val succ = gattGlobal?.writeCharacteristic(it)
                    if (succ == true) {
                        Log.d("BLE_WRITE", "Write no$i initiated")
                        sendSucess = false //resetting the onCHarWrite listener
                    } else {
                        Log.d("BLE_WRITE", "Write no$i failed")
                    }
                }
            }
        }
/*
        //firebase stuff
        val dataToSend = hashMapOf(
            "Mode" to spinnerOPMode.selectedItem,
            "Syringe" to spinnerSyringeType.selectedItem,
            "Rate" to etPumpSpeed.text.toString().toDouble(),
            "Target" to etPumpVol.text.toString().toDouble(),
            "isRunning" to true,
        )
        Log.i(TAG,"bruhhhh : ${dataToSend.size}")

        db.collection("test_q").document(CURRENT_USER)
            .set(dataToSend)
            .addOnCompleteListener {sendValuesTask ->
                if(!sendValuesTask.isSuccessful)
                {
                    Log.e(TAG,"exeption in sending to firestore",sendValuesTask.exception)
                    Toast.makeText(this, "failed to send to firestore",Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, "data sent!")
            }
*/
        //disable some functions
        buttonStart.isEnabled = false
        etPumpSpeed.isEnabled = false
        etPumpVol.isEnabled = false
        spinnerOPMode.isEnabled = false
        spinnerSyringeType.isEnabled = false

        tvTaskRunning.text = buildString {
            append(spinnerOPMode.selectedItem.toString())
            append(" in progress ...")
        }

        if (sendSucess) {
            val timer = object : CountDownTimer(floor(time).toLong() * 1000 + 1, 1000) {
                override fun onTick(p0: Long) {
                    updateTimeUI(p0.toDouble() / 1000)

                    val percentageLeft = (p0 / floor(time).toLong() / 10).toInt()
                    //Log.i(TAG,"%: $percentageLeft")
                    pbPumpProgress.setProgress(percentageLeft, true)
                    if (bluetoothAdapter?.isEnabled == false) {
                        stopPump()
                        isConnected = false
                        btnConnect.setText(R.string.connect)
                        Log.e(TAG, "Bluetooth turned off")
                        Toast.makeText(
                            this@MainActivity,
                            "Bluetooth turned off, cant continue",
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
                    stopPump()
                    btnConnect.isEnabled = true
                    //CURRENT_PROGRESS = FINISHED
                    Toast.makeText(this@MainActivity, "Complete !", Toast.LENGTH_LONG).show()
                }
            }.start()

            buttonStop.isEnabled = true
            buttonStop.setOnClickListener {
                Log.i(TAG, "STOP button clicked")
                stopPump()
                timer.cancel()
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun stopPump() {
        /*
        //firebase stuff
        val dataToSend = mapOf("isRunning" to false)
        db.collection("test_q").document(CURRENT_USER)
            .update(dataToSend)
            .continueWith {dataUploadTask ->
                Log.i(TAG,"")
            }
            .addOnCompleteListener{ stopPumpTask ->
                if (!stopPumpTask.isSuccessful )
                {
                    Log.e(TAG, "exception when stopping the pump",stopPumpTask.exception)
                    Toast.makeText(this, "Failed to send STOP command, please switch off the device",Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, "stopped the pump!")

            }

         */

        if (sendSucess)
        {
            val serviceUUID = UUID.fromString(UUIDs[0])
            Log.i(TAG, "Sending data to device: ${gattGlobal.toString()}")
            val characteristicUUID = UUID.fromString(UUIDs[5]) // isRunning
            val characteristic = gattGlobal?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
            characteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.let {
                //get a byte array of character set UTF-8
                val isRunning = 0 
                val dataToWrite = isRunning.toString().toByteArray(Charsets.UTF_8)

                // Set the data to write (it = characteristics(var) )
                it?.value = dataToWrite

                // Write data
                val succ = gattGlobal?.writeCharacteristic(it)
                if (succ == true) {
                    Log.d("BLE_WRITE", "Write initiated")
                    sendSucess = false //resetting the onCHarWrite listener
                } else {
                    Log.d("BLE_WRITE", "Write failed")
                }
            }
        }


        tvTaskRunning.text = " "
        updateTimeUI(calculateTime())
        pbPumpProgress.setProgress(START_PROGRESS,true)


        //disable some function
        buttonStop.isEnabled = false
        buttonStop.isClickable = false
        //re enable some functions
        btnConnect.isEnabled = true
        spinnerOPMode.isEnabled = true
        spinnerSyringeType.isEnabled = true
        buttonStart.isEnabled = true
        buttonStart.isClickable = true
        etPumpVol.isEnabled = true
        etPumpSpeed.isEnabled = true


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
            0 -> 1.0 / 60.0 // ml/min
            1 -> 1.0        //ml/s
            2 -> 1e-3 / 3600//ul/h
            3 -> 1e-3 / 60  //ul/min
            4 -> 1e-3       //ul/s

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
        val timeCalc = (vol * volUnit) / (rate * rateUnit)
        Log.i(TAG,"timecalc: $timeCalc")
        return timeCalc
    }
}