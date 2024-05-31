package com.example.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

abstract class BaseManager {

    val reqRspSeq = mapOf("ReqA" to listOf("MsgA", "MsgB"), "ReqB" to listOf("MsgC", "MsgD"))

    private val sessions by lazy {
        mutableMapOf<String, Session>()
    }

    companion object{
        var sessionCount=0
    }

    fun createSession(name:String, vararg paras: Any?, block: suspend Session.() -> Unit):String{
        val sid = "${++sessionCount}"
        val resultListener = if (paras.size>0 && paras.last() is ResultListener) paras.last() as ResultListener else null
        val session = Session(sid, name, resultListener)
        sessions[sid] = session
        session.launch {
            block.invoke(session)
        }
        return sid
    }

    fun cancelSession(sid:String){
        sessions[sid]!!.cancel()
    }


    inner class Session(val sid:String, val sname:String, val resultListener:ResultListener?):CoroutineScope{
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Default+ Job()

        var reqCount=0

        val requests by lazy {
            mutableMapOf<String, Request>()
        }

        suspend fun req(reqMsg:String, vararg paras:Any?, block:Request.()->Unit){
            reqCount++
            val rid = "$reqCount"
            val request = Request(sid, rid, reqMsg, null, null)
            requests[rid] = request
            block.invoke(request)

            suspendCancellableCoroutine {continuation->
                request.continuation = continuation
                val cb = object : NativeEmulator.Callback{
                    override fun onCallback(msg: String) {
                        println("onCallback $msg")
                        if (msg!="Timeout") {
                            val rspSeq = reqRspSeq[reqMsg]
                            if (rspSeq!!.contains(msg)) {
                                request.onRsp?.invoke(msg, "")

                                println("isActive $isActive")

                                if (rspSeq.last() == msg) {
                                    NativeEmulator.delCallback(this)
                                    println("request $reqMsg finished")
//                                    continuation.cancel()
                                    continuation.resume("logged in"){
                                        println("request $request canceled")
                                    }
                                }

//                            if (request.state=="FINISHED"){
//                                continuation.resume("logged in")
//                            }
                            }
                        }else{
                            request.onTimeout?.invoke()
                            println("request $reqMsg timeout")
                            continuation.resume("timeout")
                            continuation.cancel()
                        }
                    }
                }
                NativeEmulator.addCallback(cb)
                continuation.invokeOnCancellation {
                    println("request $request canceled")
                    NativeEmulator.delCallback(cb)
                }
            }
        }

        suspend fun asyncReq(msg:String, vararg paras:Any?, block:Request.()->Unit){

        }

        fun cancelReq(rid:String){

        }


        fun reportSuccess(result:String){
            resultListener?.onSuccess(result)
            sessions.remove(sid)
            cancel()
        }

        fun reportFailed(errorCode:Int, errorInfo:String){
            resultListener?.onFailed(errorCode, errorInfo)
            sessions.remove(sid)
            cancel()
//            requests.forEach{
//                it.value.continuation
//            }
        }

        fun reportTimeout(){
            resultListener?.onTimeout()
            sessions.remove(sid)
            cancel()
        }

    }


    inner class Request(val sid:String, val rid:String, val req:String, var onRsp:((msg:String, content:Any)->Unit)?, var onTimeout:(()->Unit)?){
        var state="IDLE"
        var continuation:Continuation<Any>?=null

        fun onRsp(block:(msg:String, content:Any)->Unit){onRsp=block}
        fun onTimeout(block: ()->Unit){onTimeout=block}

        fun reportSuccess(result:String){
            val session = sessions[sid]
            session!!.resultListener?.onSuccess(result)
            sessions.remove(sid)
            session.cancel()
        }

        fun reportFailed(errorCode:Int, errorInfo:String){
            val session = sessions[sid]
            session!!.resultListener?.onFailed(errorCode, errorInfo)
            sessions.remove(sid)
            session.cancel()
//            session.requests.forEach{
//                it.value.continuation
//            }
        }

        fun reportTimeout(){
            val session = sessions[sid]
            session!!.resultListener?.onTimeout()
            sessions.remove(sid)
            session.cancel()
        }
    }

}