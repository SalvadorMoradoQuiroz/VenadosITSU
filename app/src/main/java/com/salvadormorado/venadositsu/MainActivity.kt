package com.salvadormorado.venadositsu

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener,
    SimpleBluetoothDeviceInterface.OnMessageSentListener,
    SimpleBluetoothDeviceInterface.OnMessageReceivedListener,
    SimpleBluetoothDeviceInterface.OnErrorListener {

    companion object {
        private const val TAG = "MainActivity"
        private val MY_UUID_INSECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    //BT
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mBTDevices: ArrayList<BluetoothDevice>? = null
    var mDeviceListAdapter: DeviceListAdapter? = null
    var lvNewDevices: ListView? = null

    //BT libBluetooth
    var deviceMAC: String? = null
    var deviceName: String? = null

    // A CompositeDisposable that keeps track of all of our asynchronous tasks
    private val compositeDisposable = CompositeDisposable()

    // Our BluetoothManager!
    private var bluetoothManager: BluetoothManager? = null

    // Our Bluetooth Device! When disconnected it is null, so make sure we know that we need to deal with it potentially being null
    @Nullable
    private var deviceInterface: SimpleBluetoothDeviceInterface? = null
    private var flagBluetooth: Boolean = false
    private var switch_BtActivate: SwitchMaterial? = null

    private var stream_thread: HandlerThread? = null
    private var flash_thread: HandlerThread? = null
    private var rssi_thread: HandlerThread? = null
    private var stream_handler: Handler? = null
    private var flash_handler: Handler? = null
    private var rssi_handler: Handler? = null
    private var sendDataThread: HandlerThread? = null
    private var sendDataHandler: Handler? = null

    private var ID_SEND_DATA = 203
    private var sendData: Boolean = false

    private var imageMonitor: ImageView? = null
    private var imageButton_ClawClose: ImageButton? = null
    private var imageButton_Up: ImageButton? = null
    private var imageButton_Down: ImageButton? = null
    private var imageButton_ClawOpen: ImageButton? = null
    private var imageButton_ZipperUp: ImageButton? = null
    private var imageButton_Right: ImageButton? = null
    private var imageButton_Left: ImageButton? = null
    private var imageButton_ZipperDown: ImageButton? = null
    private var textView_DeviceSelected: TextView? = null
    private var letter: String = ""
    private var imageButton_Stop: ImageButton? = null
    private var imageButton_EmptyUp: ImageButton? = null
    private var imageButton_EmptyDown: ImageButton? = null

    private var imageButton_SacarBase: ImageButton? = null
    private var imageButton_MeterBase: ImageButton? = null
    //private var textView_Actividades: TextView? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mBroadcastReceiver1)
        unregisterReceiver(mBroadcastReceiver2)
        unregisterReceiver(mBroadcastReceiver3)
    }

    @RequiresApi(Build.VERSION_CODES.ECLAIR)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageMonitor = findViewById(R.id.imageMonitor)
        imageButton_ClawClose = findViewById(R.id.imageButton_ClawClose)
        imageButton_Up = findViewById(R.id.imageButton_Up)
        imageButton_Down = findViewById(R.id.imageButton_Down)
        imageButton_ClawOpen = findViewById(R.id.imageButton_ClawOpen)
        imageButton_ZipperUp = findViewById(R.id.imageButton_ZipperUp)
        imageButton_Right = findViewById(R.id.imageButton_Right)
        imageButton_Left = findViewById(R.id.imageButton_Left)
        imageButton_ZipperDown = findViewById(R.id.imageButton_ZipperDown)
        imageButton_Stop = findViewById(R.id.imageButton_Stop)
        imageButton_EmptyUp = findViewById(R.id.imageButton_EmptyUp)
        imageButton_EmptyDown = findViewById(R.id.imageButton_EmptyDown)
        imageButton_SacarBase = findViewById(R.id.imageButton_SacarBase)
        imageButton_MeterBase = findViewById(R.id.imageButton_MeterBase)
        //textView_Actividades = findViewById(R.id.textView_Actividades)

        imageButton_MeterBase!!.setOnClickListener(this)
        imageButton_SacarBase!!.setOnClickListener(this)
        imageButton_Stop!!.setOnClickListener(this)
        imageButton_EmptyUp!!.setOnClickListener(this)
        imageButton_EmptyDown!!.setOnClickListener(this)
        imageButton_ClawClose!!.setOnClickListener(this)
        imageButton_Up!!.setOnClickListener(this)
        imageButton_Down!!.setOnClickListener(this)
        imageButton_ClawOpen!!.setOnClickListener(this)
        imageButton_ZipperUp!!.setOnClickListener(this)
        imageButton_Right!!.setOnClickListener(this)
        imageButton_Left!!.setOnClickListener(this)
        imageButton_ZipperDown!!.setOnClickListener(this)

        stream_thread = HandlerThread("http")
        stream_thread!!.start()
        stream_handler = HttpHandler(this, stream_thread!!.looper)

        flash_thread = HandlerThread("http")
        flash_thread!!.start()
        flash_handler = HttpHandler(this, flash_thread!!.looper)

        rssi_thread = HandlerThread("http")
        rssi_thread!!.start()
        rssi_handler = HttpHandler(this, rssi_thread!!.looper)

        sendDataThread = HandlerThread("dataSend")
        sendDataThread!!.start()
        sendDataHandler = HttpHandler(this, sendDataThread!!.looper)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // Setup our BluetoothManager
        bluetoothManager = BluetoothManager.instance

        onTouchButtons()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_opciones, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        when (item.getItemId()) {
            R.id.conf_bt -> showDialogConfBt()
        }
        return super.onOptionsItemSelected(item)
    }

    //Acciones de los botones
    @SuppressLint("ClickableViewAccessibility")
    private fun onTouchButtons() {
        imageButton_Up!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("A", motionEvent)
            return@OnTouchListener false
        })
        imageButton_Down!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("B", motionEvent)
            return@OnTouchListener false
        })
        imageButton_ClawOpen!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("C", motionEvent)
            return@OnTouchListener false
        })
        imageButton_ClawClose!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("D", motionEvent)
            return@OnTouchListener false
        })
        imageButton_ZipperUp!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("E", motionEvent)
            return@OnTouchListener false
        })
        imageButton_ZipperDown!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("F", motionEvent)
            return@OnTouchListener false
        })
        imageButton_Left!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("G", motionEvent)
            return@OnTouchListener false
        })
        imageButton_Right!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("H", motionEvent)
            return@OnTouchListener false
        })
        imageButton_Stop!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("I", motionEvent)
            return@OnTouchListener false
        })
        imageButton_EmptyUp!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("J", motionEvent)
            return@OnTouchListener false
        })
        imageButton_EmptyDown!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("K", motionEvent)
            return@OnTouchListener false
        })
        imageButton_SacarBase!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("L", motionEvent)
            return@OnTouchListener false
        })
        imageButton_MeterBase!!.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            dataSendOnTouch("M", motionEvent)
            return@OnTouchListener false
        })
    }

    private fun dataSendOnTouch(letter: String, motionEvent: MotionEvent) {
        if (this.deviceInterface != null) {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    this.letter = letter
                    sendData = true
                    sendDataHandler!!.sendEmptyMessage(ID_SEND_DATA)
                }
                MotionEvent.ACTION_UP -> {
                    sendData = false
                }
            }
        } else {
            Toast.makeText(
                applicationContext,
                "Debes conectarte al dispositibo bluetooth.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onClick(v: View) {
        when (v.id) {

        }
    }

    private inner class HttpHandler(private val context: Context, looper: Looper) :
        Handler(looper) {
        override fun handleMessage(@NonNull msg: Message) {
            super.handleMessage(msg)

            when (msg.what) {
                this@MainActivity.ID_SEND_DATA -> {
                    this@MainActivity.sendData()
                }
                else -> {}
            }
        }
    }

    private fun sendData() {
        try {
            while (sendData) {
                deviceInterface!!.sendMessage(letter)
                Thread.sleep(250)
            }
            runOnUiThread({ deviceInterface!!.sendMessage("P") })
        } catch (e: Exception) {
            Toast.makeText(
                applicationContext,
                "Posiblemente se desconectó el dispositivo.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //BLUETOOTH-------------------------------------------------------------------------------------
//Método  para recibir de bt
    @SuppressLint("MissingPermission")
    fun showDialogConfBt() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.layout_config_bt, null)
        builder.setView(view)
        val dialogConfBt: AlertDialog = builder.create()
        dialogConfBt.setCancelable(false)
        dialogConfBt.show()

        this.switch_BtActivate =
            dialogConfBt.findViewById(R.id.switch_BtActivated) as SwitchMaterial
        var button_VisibleBt = dialogConfBt.findViewById(R.id.button_VisibleBt) as Button
        var button_SearchDevicesBt =
            dialogConfBt.findViewById(R.id.button_SearchDevicesBt) as Button
        lvNewDevices = dialogConfBt.findViewById(R.id.listView_DevicesBt) as ListView
        textView_DeviceSelected =
            dialogConfBt.findViewById(R.id.textView_DeviceSelected) as TextView
        var button_ConnectBt = dialogConfBt.findViewById(R.id.button_ConnectBt) as Button
        var button_CloseConfigBt = dialogConfBt.findViewById(R.id.button_CloseConfigBt) as Button

        mBTDevices = ArrayList<BluetoothDevice>()
        mDeviceListAdapter =
            DeviceListAdapter(applicationContext, R.layout.device_adapter_view, mBTDevices!!)
        lvNewDevices?.adapter = mDeviceListAdapter

        if (mBluetoothAdapter!!.isEnabled) {
            flagBluetooth = true
            this.switch_BtActivate!!.setText("Bluetooth activado")
            this.switch_BtActivate!!.isChecked = true
        }

        if (this.deviceInterface != null) {
            deviceMAC = this.deviceInterface!!.device.mac
            textView_DeviceSelected!!.setText("Dispositivo seleccionado: " + deviceMAC + " esta conectado.")
        }

        this.switch_BtActivate!!.setOnClickListener {
            enableDisableBT()
        }

        button_VisibleBt.setOnClickListener { doVisibleBT() }

        button_SearchDevicesBt.setOnClickListener { searchBT() }

        lvNewDevices!!.setOnItemClickListener(
            AdapterView.OnItemClickListener { parent, view, position, id ->
                mBluetoothAdapter?.cancelDiscovery()
                this.deviceMAC = mBTDevices!!.get(position).getAddress()
                this.deviceName = mBTDevices!!.get(position).getName()
                textView_DeviceSelected!!.setText(
                    "Dispositivo seleccionado: " + mBTDevices!!.get(
                        position
                    ).toString()
                )
            }
        )

        button_ConnectBt.setOnClickListener { connectToDeviceBT() }

        button_CloseConfigBt.setOnClickListener { dialogConfBt.dismiss() }

    }

    private fun connectToDeviceBT() {
        if (deviceMAC != null) {
            compositeDisposable.add(
                bluetoothManager!!.openSerialDevice(deviceMAC!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { device -> onConnected(device.toSimpleDeviceInterface()) },
                        ({ t -> onErrorConnected(t) })
                    )
            )
        } else {
            Toast.makeText(
                applicationContext,
                "Debes buscar y seleccionar un dispositivo primero.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Called once the library connects a bluetooth device
    private fun onConnected(deviceInterface: SimpleBluetoothDeviceInterface) {
        this.deviceInterface = deviceInterface
        if (this.deviceInterface != null) {
            this.deviceInterface!!.setListeners(this, this, this)
            Toast.makeText(
                applicationContext,
                "Se conectó al dispositivo: " + this.deviceInterface!!.device.mac,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                applicationContext,
                "Fallo al conectar, intente de nuevo.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //Error al conectar dispositivo
    private fun onErrorConnected(error: Throwable) {
        Toast.makeText(applicationContext, "Error al conectar el dispositivo.", Toast.LENGTH_SHORT)
            .show()
        Log.e("Error onConnected", error.message.toString())
    }

    override fun onMessageSent(message: String) {
        Toast.makeText(applicationContext, "Mensaje enviado: $message", Toast.LENGTH_SHORT).show()
    }

    override fun onMessageReceived(message: String) {
        //textView_Actividades?.setText(textView_Actividades!!.text.toString() + message + "/n")
        Toast.makeText(applicationContext, "Mensaje recibido: $message", Toast.LENGTH_SHORT).show()
    }

    //Error deviceInterface
    override fun onError(error: Throwable) {
        Log.e("Error onError deviceInterface", error.message.toString())
        bluetoothManager!!.close()
        deviceMAC = null
        Toast.makeText(
            applicationContext,
            "Se desconecto del dispositivo vinculado.",
            Toast.LENGTH_SHORT
        ).show()
        if (textView_DeviceSelected != null) {
            textView_DeviceSelected!!.setText("Dispositivo seleccionado: ")
        }
    }

    @SuppressLint("MissingPermission")
    private fun doVisibleBT() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverableIntent)
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        registerReceiver(mBroadcastReceiver2, intentFilter)
    }

    private val mBroadcastReceiver1: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state: Int =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        flagBluetooth = false
                        this@MainActivity.switch_BtActivate!!.setText("Bluetooth desactivado")
                        this@MainActivity.switch_BtActivate!!.isChecked = false
                        Toast.makeText(applicationContext, "Bluetooth apagado", Toast.LENGTH_SHORT)
                            .show()
                        if (mBTDevices != null) {
                            mBTDevices!!.clear()
                            mDeviceListAdapter!!.notifyDataSetChanged()
                            textView_DeviceSelected!!.setText("Dispositivo seleccionado: ")
                            deviceMAC = null
                        }
                        mBTDevices!!.clear();
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Toast.makeText(applicationContext, "Apagando bluetooth", Toast.LENGTH_SHORT)
                            .show()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        this@MainActivity.switch_BtActivate!!.setText("Bluetooth activado")
                        this@MainActivity.switch_BtActivate!!.isChecked = true
                        flagBluetooth = true
                        Toast.makeText(
                            applicationContext,
                            "Bluetooth encendido",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        Toast.makeText(
                            applicationContext,
                            "Encendiendo bluetooth",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 500) {//Intent para encender bluetooth o no
            if (resultCode == 0) {//Se rechazo
                this@MainActivity.switch_BtActivate!!.setText("Bluetooth desactivado")
                this@MainActivity.switch_BtActivate!!.isChecked = false
            }
        }
        Log.e("requestCode", requestCode.toString())
        Log.e("resultCode", resultCode.toString())
    }

    //Para ver los cambios de estado del bluetooth, si se enciende o expira discovery
    private val mBroadcastReceiver2: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                val mode: Int =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                when (mode) {
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad habilitada.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad deshabilitada. Capaz de recibir conexiones.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.SCAN_MODE_NONE -> {
                        Toast.makeText(
                            applicationContext,
                            "Visibilidad deshabilitada. No capaz de recibir conexiones.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    BluetoothAdapter.STATE_CONNECTING -> {
                        Toast.makeText(applicationContext, "Conectando...", Toast.LENGTH_SHORT)
                            .show()
                    }
                    BluetoothAdapter.STATE_CONNECTED -> {
                        Toast.makeText(applicationContext, "Conectado.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    //Para recibir la lista de dispositivos disponibles btnDiscover
    private val mBroadcastReceiver3: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            Log.d(TAG, "onReceive: ACTION FOUND.")
            Log.d("action", action!!)
            if (action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (!mBTDevices!!.contains(device!!)) {
                    Log.d(TAG, "onReceive: " + device?.getName().toString() + ": " + device.address)
                    mBTDevices!!.add(device!!)
                    mDeviceListAdapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    fun searchBT() {
        checkBTPermissions()

        var location: LocationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var flagBT: Boolean = mBluetoothAdapter!!.isEnabled
        var flagGPS: Boolean = location.isLocationEnabled

        if (flagBT && flagGPS) {

            mBTDevices!!.clear()
            mDeviceListAdapter!!.notifyDataSetChanged()

            if (mBluetoothAdapter!!.isDiscovering()) {
                mBluetoothAdapter!!.cancelDiscovery()
                checkBTPermissions()
                mBluetoothAdapter?.startDiscovery()
                val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
            } else if (!mBluetoothAdapter?.isDiscovering()!!) {
                checkBTPermissions()
                mBluetoothAdapter?.startDiscovery()
                val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
                registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
            }
        } else {
            if (!flagGPS) {
                Toast.makeText(applicationContext, "Debes encender la ubicación", Toast.LENGTH_LONG)
                    .show()
                locationOn(location)
            }
            if (flagGPS && (!flagBT)) {
                Toast.makeText(
                    applicationContext,
                    "Debes encender el bluetooth",
                    Toast.LENGTH_SHORT
                ).show()
                enableDisableBT()
            }
        }
    }

    private fun locationOn(location: LocationManager) {
        var intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivityForResult(intent, 501)
    }


    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkBTPermissions() {
        var permissionCheck: Int =
            this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")

        var permiso2: Int =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)

        if (permissionCheck != 0 && permiso2 != 0) {
            this.requestPermissions(
                arrayOf<String>(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1001
            ) //Any number
        }
    }

    @SuppressLint("MissingPermission")
    fun enableDisableBT() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(
                applicationContext,
                "El dispositivo no tiene bluetooth",
                Toast.LENGTH_SHORT
            ).show()
            Log.e("ERROR:", "El dispositivo no tiene bluetooth")
        }
        if (!mBluetoothAdapter!!.isEnabled) {
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBTIntent, 500)
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
        if (mBluetoothAdapter!!.isEnabled) {
            mBluetoothAdapter!!.disable()
            val BTIntent = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(mBroadcastReceiver1, BTIntent)
        }
    }
}
