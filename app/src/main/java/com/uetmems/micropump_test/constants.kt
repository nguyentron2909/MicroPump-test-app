package com.uetmems.micropump_test

const val SCAN_TAG = "BLE_SCAN"
const val LOCATION_TAG = "LOCATION_RESPONSE"
const val CONNECT_TAG = "CONNECT_ESP"

val UUIDs: List<String> = listOf(
    //size = 6
    "c61cf2de-b11a-4c64-9eba-f3c44757f8a5", //service

    "f7ffb23d-3e50-474e-a583-3dea7e4f87fb", //Rate
    "56ff2f09-bac7-4c72-9808-fe83a47a2e0e", //Target
    "64c88a24-32d5-42ec-8039-2113f09459ce", //Type
    "5b56d760-138a-4039-96f4-fbdcb9c870c1", //Mode
    "5cfa85d7-76d0-4ef7-9c91-d146d8313d01", //isRunning
)