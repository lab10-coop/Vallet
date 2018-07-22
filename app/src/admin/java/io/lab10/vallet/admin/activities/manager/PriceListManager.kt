package io.lab10.vallet.admin.activities.manager

import android.util.Log
import io.lab10.vallet.admin.interfaces.ValletApiService
import io.lab10.vallet.events.ErrorEvent
import io.lab10.vallet.models.TokenCreate
import io.lab10.vallet.models.TokenUpdate
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

class PriceListManager {

    companion object {
        fun createPriceList(token: TokenCreate) {
            val apiService = ValletApiService.create("http://192.168.1.103:3000")

            apiService.createPriceList(token).observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe ({
                        result ->
                        // TODO store it in objectbox
                        Log.d("Result", "There are ${result} Java developers in Lagos")
                    }, { error ->
                        EventBus.getDefault().post(ErrorEvent(error.message.toString()))
                    })
        }

        fun fetchPriceList(token_name: String) {
            val apiService = ValletApiService.create("http://192.168.1.103:3000")
            apiService.fetchPriceList(token_name).observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe ({
                        result ->
                        // TODO store it in the objectbox
                        Log.d("Result", "There are ${result} Java developers in Lagos")
                    }, { error ->
                        EventBus.getDefault().post(ErrorEvent(error.message.toString()))
                    })
        }

        fun updatePriceList(token: TokenUpdate) {
            val apiService = ValletApiService.create("http://192.168.1.103:3000")
            apiService.updatePriceList(token).observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe ({
                        result ->
                        // TODO store it in the objectbox
                        Log.d("Result", "There are ${result} Java developers in Lagos")
                    }, { error ->
                        EventBus.getDefault().post(ErrorEvent(error.message.toString()))
                    })
        }
    }
}