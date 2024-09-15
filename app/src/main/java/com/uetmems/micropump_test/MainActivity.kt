package com.uetmems.micropump_test

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.floor

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var etPumpSpeed : EditText
    private lateinit var etPumpVol : EditText
    private lateinit var tvTimeCalc : TextView
    private lateinit var buttonStart : Button
    private lateinit var buttonStop : Button
    private lateinit var spinnerSyringeType : Spinner
    private lateinit var spinnerOPMode: Spinner

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

        //edit text
        etPumpSpeed.addTextChangedListener( object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
               Log.i(TAG,"Pump SPEED set to $p0")
                //error checking
                calculateTime()

            }
        })
        etPumpVol.addTextChangedListener( object: TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                Log.i(TAG,"Pump VOL set to $p0")
                calculateTime()
            }
        })


        //dropdown menu setup
        spinnerOPMode = findViewById(R.id.spinnerOPMode)
        spinnerSyringeType = findViewById(R.id.spinnerSyringeType)

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
        buttonStart = findViewById(R.id.buttonStart)
        buttonStart.setOnClickListener{
            Log.i(TAG,"START button clicked")
        }

        buttonStop = findViewById(R.id.buttonStop)
        buttonStop.setOnClickListener{
            Log.i(TAG,"STOP button clicked")
        }

    }

    private fun calculateTime() {
        //error checking
        if(
            etPumpSpeed.text.isEmpty() ||
            etPumpVol.text.isEmpty() ||
            etPumpSpeed.text.toString().toFloat() == 0f ||
            etPumpVol.text.toString().toFloat() == 0f ||
            etPumpSpeed.text.toString() == "." ||
            etPumpVol.text.toString() =="."
            ) {
            tvTimeCalc.text = " "
            return
        }

        //get values
        val rate = etPumpSpeed.text.toString().toDouble()
        val vol = etPumpVol.text.toString().toDouble()
        val type = spinnerSyringeType.selectedItem.toString()

        //calculate time
        val timeCalc = vol / (rate * 1e-3 / 60)

        val hh = floor(timeCalc / 3600)
        val mm = floor(timeCalc/60 - hh*60)
        val ss = floor(timeCalc - mm*60 - hh*3600)
        //update UI (convert seconds to hh mm ss)
        tvTimeCalc.text = buildString {
            if(hh>=1) {
                append("%.0f".format(hh))
                append(":")
            }
            if(mm>=1) {
                append("%.0f".format(mm))
                append(":")
            }
            append("%.0f".format(ss))
            //append(" s")
        }

    }
}