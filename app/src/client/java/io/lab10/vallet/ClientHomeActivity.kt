package io.lab10.vallet

import FaucetManager
import Web3jManager
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Parcelable
import android.support.design.widget.NavigationView
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.google.zxing.integration.android.IntentIntegrator
import io.ValletUriParser
import io.lab10.vallet.activites.BackupActivity
import io.lab10.vallet.activites.HistoryActivity
import io.lab10.vallet.connectivity.BTUtils
import io.lab10.vallet.events.*
import io.lab10.vallet.fragments.NoStoreFragment
import io.lab10.vallet.fragments.ProductListFragment
import io.lab10.vallet.models.*
import io.lab10.vallet.models.Token
import io.lab10.vallet.utils.Formatter
import io.lab10.vallet.utils.NetworkUtils
import io.lab10.vallet.utils.PayDialog
import it.lamba.random.nextAlphanumericString
import kotlinx.android.synthetic.client.activity_client_home_activty.*
import kotlinx.android.synthetic.client.app_bar_client_home_activty.*
import kotlinx.android.synthetic.client.voucher_item.view.*
import kotlinx.android.synthetic.main.fragment_product_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.math.BigInteger
import kotlin.random.Random


class ClientHomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, ProductListFragment.OnListFragmentInteractionListener {

    private var voucherWalletAddress: String = "";

    override fun onProductClickListner(item: Product) {
        payFor(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_home_activty)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setDefaultConfiguration()

        if (ValletApp.wallet == null) {
            var username = "User"
            if (intent.extras != null) {
                username = intent.extras.getString(CreateUserActivity.NAME_EXTRA)
            }

            val password: String = Random.nextAlphanumericString()

            val sharedPref = getSharedPreferences("voucher_pref", Context.MODE_PRIVATE)
            // Store password for the wallet file in shared pref in case if the db wil be corrupted that we can restore
            val editor = sharedPref.edit()
            editor.putString(resources.getString(R.string.shared_pref_wallet_password), password)
            editor.commit()

            val walletFile = Web3jManager.INSTANCE.createWallet(this, password)
            voucherWalletAddress = Web3jManager.INSTANCE.getWalletAddressFromFile(walletFile)
            ValletApp.wallet = Wallet(0, username, voucherWalletAddress, walletFile)
            FaucetManager.INSTANCE.getFounds(this, ValletApp.wallet!!.address)
        } else {
            var balance = Web3jManager.INSTANCE.getBalance(this, ValletApp.wallet!!.address)
            if (balance != null && balance.balance < BigInteger.valueOf(9977274220000)) {
                FaucetManager.INSTANCE.getFounds(this, ValletApp.wallet!!.address)
            }
        }

        // TODO move that to settings
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this,
                    "NFC NOT supported on this devices!",
                    Toast.LENGTH_SHORT).show();
        } else if (!nfcAdapter.isEnabled()) {
            Toast.makeText(this,
                    "NFC NOT Enabled!",
                    Toast.LENGTH_SHORT).show();
        }


        val toggle:ActionBarDrawerToggle = object: ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        {
            override fun onDrawerClosed(view:View){
                super.onDrawerClosed(view)
                reloadProductListFromLocalStorage()
                reloadProductListFromRemoteStorage()
                setPriceListHeader()

            }
        }
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        reloadProductListFromLocalStorage()
        reloadProductListFromRemoteStorage()
        refreshBalance()
        reloadNavigation()
        nav_view.setNavigationItemSelectedListener(this)
    }

    private fun setDefaultConfiguration() {
        val configBox = ValletApp.getBoxStore().boxFor(Configuration::class.java)
        // TODO do not override if will be already set
        configBox.put(Configuration(0, "ipfsAddress", resources.getString(R.string.ipfs_server)))
    }

    private fun showPlaceHolderFragment() {
        val noStorePlaceHolder = NoStoreFragment.newInstance()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.price_list_fragment_container, noStorePlaceHolder)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun showPriceListFragment() {
        setPriceListHeader()
        val productListFragment = ProductListFragment.newInstance()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.price_list_fragment_container, productListFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun setPriceListHeader() {
        if (ValletApp.activeToken != null) {
            toolbarBalance.text = Formatter.currency(Wallet.convertATS2EUR(ValletApp.activeToken!!.balance))
        }
    }

    private fun reloadNavigation(newToken: Boolean? = null) {
        val navigationView = findViewById<View>(R.id.nav_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)


        val versionCode = BuildConfig.VERSION_CODE
        val versionName = BuildConfig.VERSION_NAME

        val header = navigationView.getHeaderView(0)
        /*View view=navigationView.inflateHeaderView(R.layout.nav_header_main);*/
        val versionLabel = header.findViewById<View>(R.id.versionLabel) as TextView
        versionLabel.text = "v$versionName($versionCode)"

        val menu = navigationView.menu
        menu.clear()
        // If we having adding token in progress we show it here
        if (newToken != null) {
            val i = menu.add(resources.getString(R.string.adding_new_token))
            val txt = resources.getString(R.string.adding_new_token)
            val s = SpannableString(txt)
            s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.gray)), 0, txt.length, 0);
            i.title = s
        }

        if (ValletApp.activeToken == null && newToken == null) {
            val i = menu.add(resources.getString(R.string.no_token_available))
            val txt = resources.getString(R.string.no_token_available)
            val s = SpannableString(txt)
            s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.gray)), 0, txt.length, 0);
            i.setTitle(s)
        }
        Tokens.getVouchers().forEach { token ->
            val item = menu.add(token.name)
            item.isCheckable = true
            item.isChecked = token.active
            item.setActionView(R.layout.voucher_balance_in_nav)
            val view = item.actionView
            view.voucherBalance.text = Formatter.currency(Wallet.convertATS2EUR(token.balance))
            item.setOnMenuItemClickListener { _ ->
                ValletApp.activeToken = token
                reloadProductListFromLocalStorage()
                reloadProductListFromRemoteStorage()
                setPriceListHeader()
                drawer_layout.closeDrawer(GravityCompat.START)
                true
            }
        }

        val scanMenu = menu.add(resources.getString(R.string.add_new_store))
        var txt = resources.getString(R.string.add_new_store)
        var s = SpannableString(txt)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)), 0, txt.length, 0);
        scanMenu.title = s
        scanMenu.setOnMenuItemClickListener { _ ->
            val integrator = IntentIntegrator(this)
            integrator.setBeepEnabled(false);
            integrator.setBarcodeImageEnabled(true);
            integrator.initiateScan()
            true
        }
        val qrMenu = menu.add(2,2,5, resources.getString(R.string.your_qrcode))
        txt = resources.getString(R.string.your_qrcode)
        s = SpannableString(txt)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)), 0, txt.length, 0);
        qrMenu.title = s
        qrMenu.setOnMenuItemClickListener { _ ->
            val intent = Intent(this, ShowQrCodeActivity::class.java)
            startActivity(intent)
            true
        }
        val backup = menu.add(2,2,5, resources.getString(R.string.backup))
        txt = resources.getString(R.string.backup)
        s = SpannableString(txt)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.blue)), 0, txt.length, 0);
        backup.title = s
        backup.setOnMenuItemClickListener { _ ->
            val intent = Intent(this, BackupActivity::class.java)
            startActivity(intent)
            true
        }
        navigationView.invalidate()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.getItemId()) {
                R.id.menu_history -> {
                    val intent = Intent(this, HistoryActivity::class.java)
                    startActivity(intent)
                    return true
                }
                else -> return super.onOptionsItemSelected(item)
            }
        } else {
            return true
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onStart() {
        super.onStart()
        refreshBalance()
        EventBus.getDefault().register(this);
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this);
    }

    override fun onResume() {
        super.onResume()

        resolveIntent(intent)

        NetworkUtils.isInternetAvailable()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val pendingIntent = PendingIntent.getActivity(this, 0,
                Intent(this, this.javaClass)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)

        if (nfcAdapter != null) {
            if (nfcAdapter.isEnabled())
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }

        reloadProductListFromLocalStorage()
        reloadProductListFromRemoteStorage()
        showPriceListFragment()
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        resolveIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (ValletApp.activeToken != null) {
            showPriceListFragment()
        } else {
            showPlaceHolderFragment()
        }
    }

    private val REQUEST_BT_ENABLE = 100

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Return from QR code scanning
        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            if (result != null && result.contents != null) {
                try {
                    val uri = Uri.parse(result.contents)
                    ValletUriParser.invoke(uri)
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid uri: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (requestCode == REQUEST_BT_ENABLE) {
            if (resultCode == Activity.RESULT_OK) {
                startBroadcastingAddress()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNoInternetEvent(event: NoInternetEvent) {
        Toast.makeText(this, "No internet connection please connect to the internet to continue", Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProductChangedEvent(event: ProductChangedEvent) {
        showPriceListFragment()
        reloadProductListFromLocalStorage()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onErrorEvent(event: ErrorEvent) {
        Crashlytics.log(event.message)
        Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDebug(event: DebugEvent) {
        Toast.makeText(this, "Debug: " + event.message, Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessage(event: MessageEvent) {
        Toast.makeText(this, "Info: " + event.message, Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNewTokenEvent(event: NewTokenEvent) {
        reloadNavigation()
        showPriceListFragment()
        reloadProductListFromLocalStorage()
    }

    @Subscribe
    fun onPriceListEvent(event: PriceListAddressEvent) {
        val token = Token(0, "", event.tokenAddress, 0, "", "", true, 0)
        token.storage().fetch(event.ipfsAddress)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProductPaid(event: ProductPaidEvent) {
        // Reduce the balance on the view as soon as user will take the action.
        // This is not persitant and we should mark it somehow
        if (toolbarBalance != null) {
            toolbarBalance.text = Formatter.currency(Wallet.convertATS2EUR(ValletApp.activeToken!!.balance - event.price)) + " ..."
        }

    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onAddNewStore(event: AddNewStoreEvent) {

        val stickyEvent = EventBus.getDefault().removeStickyEvent(AddNewStoreEvent::class.java)
        if (stickyEvent != null) {
            reloadNavigation(newToken = true)
            drawer_layout.openDrawer(GravityCompat.START)
            NetworkUtils.doAsync {
                Web3jManager.INSTANCE.fetchPriceListAddress(this, event.tokenAddress)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPriceListStartRefresh(event: PriceListStartRefreshEvent) {
        reloadProductListFromRemoteStorage()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStartRefreshingEvent(event: StartRefreshingEvent) {
        showPriceListFragment()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProductRefreshEvent(event: RefreshProductsEvent) {
        showPriceListFragment()
        reloadProductListFromLocalStorage()
        reloadProductListFromRemoteStorage()
        refreshProductListView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNewBalanceAvailabelEvent(event: TokenBalanceEvent) {
        val tokenBox = ValletApp.getBoxStore().boxFor(Token::class.java)
        val token = tokenBox.query().equal(Token_.tokenAddress, event.address).build().findFirst()
        if (token != null) {
            token.balance = event.balance
            tokenBox.put(token)
        }
        setPriceListHeader()
        reloadNavigation()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRefreshBalanceEvent(event: RefreshBalanceEvent) {
        if (toolbarBalance != null) {
            toolbarBalance.text = Formatter.currency(Wallet.convertATS2EUR(ValletApp.activeToken!!.balance)) + " ..."
        }
        refreshBalance()
    }

    @Subscribe(sticky = true)
    fun onPendingTransaction(event: PendingTransactionEvent) {

        val stickyEvent = EventBus.getDefault().removeStickyEvent(PendingTransactionEvent::class.java)
        if (stickyEvent != null) {
            val transaction = ValletTransaction(0, event.name, event.amount, event.blockNumber, event.transactionId, event.to, event.tokenAddress)
            History.addTransaction(transaction)
            EventBus.getDefault().post(RefreshBalanceEvent())
        }

    }

    private fun reloadProductListFromLocalStorage() {
        if (ValletApp.activeToken != null) {
            Products.refresh(ValletApp.activeToken!!)
            var productFragment = supportFragmentManager.findFragmentById(R.id.price_list_fragment_container)
            if (productFragment is ProductListFragment) {
                productFragment.notifyAboutchange()
                if (productFragment.view != null) {
                    productFragment.swiperefresh.isRefreshing = false
                }
            }
        }
    }

    private fun reloadProductListFromRemoteStorage() {
        if (ValletApp.activeToken != null) {
            if (ValletApp.activeToken!!.remoteReadStoragePresent()) {
                var productFragment = supportFragmentManager.findFragmentById(R.id.price_list_fragment_container)
                if (productFragment is ProductListFragment && productFragment.view != null) {
                    productFragment.swiperefresh.isRefreshing = true
                    NetworkUtils.doAsync {
                        Web3jManager.INSTANCE.fetchPriceListAddress(this, ValletApp.activeToken!!.tokenAddress)
                    }


                }
            }
        }
    }

    private fun refreshProductListView() {
        var productFragment = supportFragmentManager.findFragmentById(R.id.price_list_fragment_container)
        if (productFragment is ProductListFragment) {
            Products.refresh(ValletApp.activeToken!!)
            productFragment.notifyAboutchange()
            productFragment.swiperefresh.isRefreshing = false
        }
    }

    private fun resolveIntent(intent: Intent) {
        val action = intent.action

        if (NfcAdapter.ACTION_TAG_DISCOVERED == action
                || NfcAdapter.ACTION_TECH_DISCOVERED == action
                || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {

            val tag = intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag
            var result = ""
            for (b in tag.id) {
                val st = String.format("%02X", b)
                result += st
            }
            val productBox = ValletApp.getBoxStore().boxFor(Product::class.java)
            val product = productBox.query().equal(Product_.nfcTagId, result).build().findFirst()
            if (product != null) {
                payFor(product)
            }
        }
    }

    private fun payFor(product: Product) {
        if (product.price > ValletApp.activeToken!!.balance) {
            Toast.makeText(this, "Not enough funds to pay for this item!", Toast.LENGTH_LONG).show()
        } else {
            val cdd = PayDialog(this, product)
            cdd.show()
        }
    }

    private fun refreshBalance() {
        NetworkUtils.doAsync {
            // Trigger balance check for each token
            // copy the list to avoid ConcurrentModificationException
            val vouchers: MutableList<Token> = ArrayList()

            vouchers.addAll(Tokens.getVouchers())
            vouchers.forEach { e ->
                Web3jManager.INSTANCE.getClientBalance(this, e.tokenAddress, ValletApp.wallet!!.address)
            }
        }
    }



    private var scanningInProgress = false

    private fun startBroadcastingAddress() {
        if (!scanningInProgress) {
            // TODO make sure that BT is on if not turn it on

            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                Toast.makeText(this, "This devices does not support BT please use QR code instead", Toast.LENGTH_SHORT).show()
            } else {
                val address = ValletApp.wallet!!.address
                // Address is always with 0x which we don't need to transfer
                val part1 = address.substring(2, BTUtils.SERVICE_NAME_SIZE + 2)
                val part2 = address.substring(BTUtils.SERVICE_NAME_SIZE + 2)
                val uuid1 = BTUtils.encodeAddress(part1)
                val uuid2 = BTUtils.encodeAddress(part2)
                if (!mBluetoothAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    this.startActivityForResult(enableBtIntent, REQUEST_BT_ENABLE)
                } else {
                    scanningInProgress = true
                    //if (debugMode)
                    //    Toast.makeText(this, "Broadcasting address", Toast.LENGTH_LONG).show()
                    //startLoaderAnimation()
                    //loader.setBackgroundResource(R.drawable.loader)
                    //scanningLabel.text = resources.getString(R.string.broadcasting_wallet_address)
                    mBluetoothAdapter.listenUsingRfcommWithServiceRecord(getString(R.string.app_name), uuid1)
                    mBluetoothAdapter.listenUsingRfcommWithServiceRecord(getString(R.string.app_name), uuid2)
                }
            }
        }
    }

}
