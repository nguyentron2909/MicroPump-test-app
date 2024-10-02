package com.uetmems.micropump_test

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
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
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlin.math.floor


class MainActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "UET-MEMS MainActivity"
        private const val REQUEST_ENABLE_BT = 891
        private const val REQUEST_ENABLE_LOCATION = 596
        private const val SCAN_TAG = "BLE_SCAN"
        private const val LOCATION_TAG = "LOCATION_RESPONSE"
        private const val SCAN_TIME: Long = 10000


        private const val START_PROGRESS = 100
        private var CURRENT_USER = "user0000"
        private var isConnected = false
    }

    private lateinit var etPumpSpeed : EditText
    private lateinit var etPumpVol : EditText
    private lateinit var tvTimeCalc : TextView
    private lateinit var buttonStart : Button
    private lateinit var buttonStop : Button
    private lateinit var spinnerSyringeType : Spinner
    private lateinit var spinnerOPMode: Spinner
    private lateinit var pbPumpProgress : ProgressBar
    private lateinit var tvTaskRunning: TextView

    //bluetooth LE functionalities
    private lateinit var btnConnect : Button
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    //firebase shitz
    private var db = Firebase.firestore

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
        //todo shortcut:


        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            showNoBLECapability()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }


        etPumpSpeed = findViewById(R.id.etPumpSpeed)
        etPumpVol = findViewById(R.id.etPumpVol)
        tvTimeCalc = findViewById(R.id.tvTimeCalc)
        spinnerOPMode = findViewById(R.id.spinnerOPMode)
        spinnerSyringeType = findViewById(R.id.spinnerSyringeType)
        buttonStart = findViewById(R.id.buttonStart)
        buttonStop = findViewById(R.id.buttonStop)
        pbPumpProgress = findViewById(R.id.pbPumpProgress)
        tvTaskRunning = findViewById(R.id.tvTaskRunning)
        btnConnect = findViewById(R.id.btnConnect)

        var timeEstimated = 0.0
        //edit text
        etPumpSpeed.addTextChangedListener( object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
               Log.i(TAG,"Pump SPEED set to $p0")
                //error checking
                timeEstimated = calculateTime()
                updateTimeUI(timeEstimated)

            }
        })
        etPumpVol.addTextChangedListener( object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                Log.i(TAG,"Pump VOL set to $p0")
                timeEstimated = calculateTime()
                updateTimeUI(timeEstimated)
            }
        })

        //dropdown menu setup
        val syringeType = resources.getStringArray(R.array.Syringe_type)
        val adapterSyringe = ArrayAdapter(
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
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val opModes = resources.getStringArray(R.array.Operation_mode)
        val adapterOP = ArrayAdapter(
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
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        //Buttons
        buttonStop.isEnabled = false
        buttonStop.isClickable = false
        buttonStart.setOnClickListener{
            Log.i(TAG,"START button clicked")
            startPump(timeEstimated)
        }

        btnConnect.setOnClickListener{
            Log.i(TAG,"Connect clicked")
            btnConnect.isEnabled = false
            btnConnect.setText(R.string.connecting)
            requestBLEPermission()

        }
        //Progress bar
        pbPumpProgress.rotation = 179f
        pbPumpProgress.setProgress(START_PROGRESS,true)


    }

    private fun requestBLEPermission() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,SCAN_TIME)
            .setMinUpdateIntervalMillis(5000)
            .build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { locationSettingsResponse->
            Log.i(LOCATION_TAG,"location turned on! ${locationSettingsResponse}")
            }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException){
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(this@MainActivity,
                        REQUEST_ENABLE_LOCATION)
                } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                }
            }
        }

        //request permissions
        if (bluetoothAdapter!!.isEnabled){
            Log.i(TAG,"Permission already granted")
            Toast.makeText(this,"Bluetooth permission already granted",Toast.LENGTH_LONG).show()
            scanForESP32()
        }
        else {
            if (bluetoothAdapter == null) {
                //if there is no adapter => cant use ble
                showNoBLECapability()
            }

            if (!bluetoothAdapter.isEnabled) {
                val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                checkPermission(Manifest.permission.BLUETOOTH_CONNECT,2)
                startActivityForResult(enableBluetoothIntent,REQUEST_ENABLE_BT)
            }
            else {
                Log.i(TAG,"Permission already granted")
                Toast.makeText(this,"Bluetooth permission already granted",Toast.LENGTH_LONG).show()
                scanForESP32()
            }
        }
    }

    private fun checkPermission(permission: String, requestCode: Int)
    {
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

    private val scanCallback = object: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device
            checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)
            Log.d(SCAN_TAG,"device found: ${device?.name}, @${device?.address}")
            if (device?.name == R.string.BLE_device_name.toString())
            {
                //todo store address to something
                stopScan()
            }
            super.onScanResult(callbackType, result)

        }

        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(this@MainActivity, "Cannot initiate scanning", Toast.LENGTH_LONG).show()
            Log.e(SCAN_TAG, "Scan failed with error: $errorCode")
            super.onScanFailed(errorCode)
            return
        }
    }

    private fun scanForESP32() {
        Log.i(TAG,"Start scanning")
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)
        bluetoothLeScanner?.startScan(scanCallback)
        Handler(Looper.getMainLooper()).postDelayed({
            stopScan()
            btnConnect.isEnabled = true
            btnConnect.setText(R.string.connect)
            Toast.makeText(
                this,
                "Cannot find device",
                Toast.LENGTH_LONG
            ).show()
        }, SCAN_TIME)
    }
    private fun stopScan() {
        checkPermission(Manifest.permission.BLUETOOTH_CONNECT, REQUEST_ENABLE_BT)
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(SCAN_TAG, "Stopped BLE scan")
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK)
        {
            Log.i(TAG,"bluetooth permission granted!")
            //Toast.makeText(this,"Bluetooth permission granted",Toast.LENGTH_LONG).show()
            scanForESP32()
        }
        else {
            Toast.makeText(this,"pls enable bluetooth",Toast.LENGTH_LONG).show()
            requestBLEPermission()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }


    private fun showNoBLECapability() {
        Log.w(TAG,"Device does not support BLE")
        AlertDialog.Builder(this)
            .setTitle("Your device does not have Bluetooth Low Energy functionality")
            .setPositiveButton("OK"){_,_, ->
                finish()
            }.show()
    }

    private fun startPump(time: Double){
        //error checking
        if (etPumpSpeed.text.isBlank() ||
            etPumpVol.text.isBlank() ||
            time <= 0)
        {
            Toast.makeText(this,"Please enter Rate / Target ",Toast.LENGTH_SHORT).show()
            return
        }
        //todo: implement a way to push data up to firebase then start the pump
        val dataToSend = hashMapOf(
            "Mode" to spinnerOPMode.selectedItem,
            "Syringe" to spinnerSyringeType.selectedItem,
            "Rate" to etPumpSpeed.text.toString().toDouble(),
            "Target" to etPumpVol.text.toString().toDouble(),
            "isRunning" to true,
        )
        Log.w(TAG,"bruhhhh : ${dataToSend.size}")
        //todo make a for loop to do all this before stopping by increments

        db.collection("test_q").document(CURRENT_USER)
            .set(dataToSend)
            .addOnCompleteListener{ sendValuesTask ->
                if(!sendValuesTask.isSuccessful)
                {
                    Log.e(TAG,"exeption in sending to firestore",sendValuesTask.exception)
                    Toast.makeText(this, "failed to send to firestore",Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, "data sent!")

            }

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

         val timer = object : CountDownTimer(floor(time).toLong()*1000 + 1,1000) {
            override fun onTick(p0: Long) {
                updateTimeUI(p0.toDouble()/1000)

                val percentageLeft =  ( p0 / floor(time).toLong() / 10).toInt()
                Log.i(TAG,"%: $percentageLeft")
                pbPumpProgress.setProgress(percentageLeft,true)
            }

            override fun onFinish() {
                stopPump()
                //CURRENT_PROGRESS = FINISHED
                Toast.makeText(this@MainActivity,"Complete !",Toast.LENGTH_LONG).show()
            }
        }.start()


        buttonStop.isEnabled = true
        buttonStop.setOnClickListener{
            Log.i(TAG,"STOP button clicked")
            //CURRENT_PROGRESS = pbPumpProgress.progress
            //Toast.makeText(this,"current progress: $CURRENT_PROGRESS",Toast.LENGTH_SHORT).show()
            stopPump()
            timer.cancel()
            //Get progress to save pbPumpProgress.progress
        }


    }

    private fun stopPump() {
        //sending boolean false to stop running
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
        //todo: record current time left and progress
        tvTaskRunning.text = " "
        updateTimeUI(calculateTime())
        //pbPumpProgress.setProgress(CURRENT_PROGRESS,true)

        //disable some function
        buttonStop.isEnabled = false
        buttonStop.isClickable = false
        //re enable some functions
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
        val hh = floor(timeCalc / 3600)
        val mm = floor(timeCalc/60 - hh*60)
        val ss = floor(timeCalc - mm*60 - hh*3600)
        //update UI (convert seconds to hh mm ss)
        tvTimeCalc.text = buildString {
            append("%.0f".format(hh))
            append(" : ")

            append("%.0f".format(mm))
            append(" : ")

            append("%.0f".format(ss))
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
        //val type = spinnerSyringeType.selectedItem.toString()
        //val mode = spinnerOPMode.selectedItem.toString()

        //calculate time
        var timeCalc = vol / (rate * 1e-3 / 60)
        //timeCalc -= (100 - CURRENT_PROGRESS)/100 * timeCalc
        Log.i(TAG,"timecalc: $timeCalc")
        return timeCalc
    }
}