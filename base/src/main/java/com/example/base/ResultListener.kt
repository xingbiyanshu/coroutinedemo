package com.example.base

interface ResultListener{
    fun onSuccess(result:Any?)
    fun onFailed(errorCode:Int, errorInfo:Any?)
    fun onTimeout()
}
