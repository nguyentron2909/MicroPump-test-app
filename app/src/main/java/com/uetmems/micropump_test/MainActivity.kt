package com.uetmems.micropump_test

import android.os.Bundle
import android.os.CountDownTimer
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlin.math.floor


private const val s = ""

class MainActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "MainActivity"
        private const val START_PROGRESS = 100
        private const val FINISHED = 0
        //private var CURRENT_PROGRESS = 100
        private var CURRENT_USER = "user0000"
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

    //firebase shitz
    private var db = Firebase.firestore

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

        etPumpSpeed = findViewById(R.id.etPumpSpeed)
        etPumpVol = findViewById(R.id.etPumpVol)
        tvTimeCalc = findViewById(R.id.tvTimeCalc)
        spinnerOPMode = findViewById(R.id.spinnerOPMode)
        spinnerSyringeType = findViewById(R.id.spinnerSyringeType)
        buttonStart = findViewById(R.id.buttonStart)
        buttonStop = findViewById(R.id.buttonStop)
        pbPumpProgress = findViewById(R.id.pbPumpProgress)
        tvTaskRunning = findViewById(R.id.tvTaskRunning)

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

        //Progress bar
        pbPumpProgress.rotation = 179f
        pbPumpProgress.setProgress(START_PROGRESS,true)

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