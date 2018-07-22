package io.lab10.vallet.models

import com.google.gson.Gson
import io.lab10.vallet.ValletApp

object Tokens {

    private val ITEMS: MutableList<Token> = ArrayList()

    enum class Type {
        EUR, VOUCHER
    }

    fun getVouchers(): MutableList<Token> {
        return ITEMS
    }

    fun refresh() {
        val voucherBox = ValletApp.getBoxStore().boxFor(Token::class.java)
        ITEMS.clear()
        ITEMS.addAll(voucherBox.query().build().find())
    }

    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(ITEMS)
    }

    fun fromJson(json: String) {
        val gson = Gson()
        ITEMS.clear()
        var tmp: MutableList<Token> = ArrayList()
        val products = gson.fromJson(json, Array<Token>::class.java)
        products.forEach { v ->
            // TODO add check if voucher is valid?
            if (!isVoucherOnList(v)) {
                ITEMS.add(v)
            }
        }
    }

    fun addItem(item: Token){
        if (!isVoucherOnList(item)) {
            ITEMS.add(item)
        }
    }

    private fun isVoucherOnList(item: Token): Boolean {
        for(voucher in ITEMS) {
            if (voucher.id.equals(item.id)) {
                return true;
            }
        }
        return false;
    }


}