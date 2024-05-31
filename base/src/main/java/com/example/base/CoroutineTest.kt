package com.example.base

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun getName():String{
    println("delay 1")
    delay(1000)
    println("delay 2")
    delay(2000)
    return "gf"
}

fun main(){
//    runBlocking {
//    }
    GlobalScope.launch {
        println("start")
        println(getName())
        println("stop")
    }
    Thread.sleep(4000)
}