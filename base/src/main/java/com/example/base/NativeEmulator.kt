package com.example.base

import kotlin.concurrent.timer

object NativeEmulator {

    val listeners = mutableListOf<Callback>()

    val msgs = listOf("MsgA", "MsgB", "MsgC", "MsgD")

    interface Callback{
        fun onCallback(msg:String)
    }

    init {
        timer("NativeEmulator", period = 1000){
            msgs.forEach {msg->
                val tmpListeners = listeners.toList()
                tmpListeners.forEach {listener->
                    listener.onCallback(msg)
                }
            }
        }
    }

    fun addCallback(cb:Callback){
        listeners.add(cb)
    }

    fun delCallback(cb:Callback){
        listeners.remove(cb)
    }
}