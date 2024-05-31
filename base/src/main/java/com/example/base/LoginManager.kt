package com.example.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine


class LoginManager : BaseManager() {

    fun login(account:String, passwd:String, listener: ResultListener){
        /**
        * 关于协程，需要思考的问题：
        * 1、如何连接非协程和协程的世界？
        *   - runBlocking：会阻塞调用线程，不可取
        *   - 使用CoroutineScope#launch/async（Global的生命周期不符合，使用自定义的）
        * 2、如何自己实现挂起行为？
        *   一般的自定义挂起函数都调用类系统的挂起函数自然具备类挂起的行为。若未调用系统挂起函数如何自己实现挂起行为？
        *   比如回调改为挂起实现。可以使用suspendCancellableCoroutine。
        * 3、如何取消自定义挂起函数？取消背后的逻辑是怎样的？
        * 4、如何实现协程并发？
        *   比如多个req并发执行。可以定义异步req,异步req创建单独协程以达到和其他req并发的效果
        * 5、多个协程多层级协程（父子）如何管理？
        *   结构化并发，普通job取消是双向的，父job会等所有子job完成，父job取消所有子job也取消，反过来子job异常也会导致整个
        *   父job退出。若需要这种影响单向传播（从父到子）可以使用SupervisorJob
        * 6、协程间数据如何传递？
        *   async只能返回单个值。要返回流式数据，比如一串响应挨个来，可以采用flow或chanel.
        *   -flow冷流，只有用户collect才会发射数据
        *   -channel热流，无论有没有用户都在发射
        * 7、异常的处理？
        *   launch的异常跟java的uncaught异常一样最终抛出到最外层（没人处理直接crash）。
        *   但是可以给launch设置一个自定义异常处理handler.
        *   async的异常由用户在await时自行catch,不会抛到最外层。
         *
         *  8、协程如何被创建的？如何被启动的？
         *  以launch为例：
         *  public fun CoroutineScope.launch(
         *     context: CoroutineContext = EmptyCoroutineContext,
         *     start: CoroutineStart = CoroutineStart.DEFAULT,
         *     block: suspend CoroutineScope.() -> Unit
         * ): Job {
         *     val newContext = newCoroutineContext(context) // 创建上下文
         *     val coroutine = if (start.isLazy)
         *         LazyStandaloneCoroutine(newContext, block) else
         *         StandaloneCoroutine(newContext, active = true) // 默认这里。
         *                                                  //继承关系：StandaloneCoroutine：AbstractCoroutine<Unit>: JobSupport(active), Job, Continuation<T>, CoroutineScope
         *                                                  // 可见coroutine既是Job也是Continuation, 还是CoroutineScope
         *
         *     // 协程创建后默认立即启动
         *     coroutine.start(start,
         *              coroutine,
         *              block) //public fun <R> start(start: CoroutineStart, receiver: R, block: suspend R.() -> T) {
         *                          start(block, receiver, this) // 此处是调用的CoroutineStart的invoke（运算符重载）
         *                                                      public operator fun <R, T> invoke(block: suspend R.() -> T, receiver: R, completion: Continuation<T>): Unit =
         *                                                      when (this) {
         *                                                          DEFAULT -> block.startCoroutineCancellable(receiver, completion) // 默认走这里
         *                                                                                                                           internal fun <R, T> (suspend (R) -> T).startCoroutineCancellable(
         *                                                                                                                               receiver: R, completion: Continuation<T>,
         *                                                                                                                               onCancellation: ((cause: Throwable) -> Unit)? = null
         *                                                                                                                           ) =
         *                                                                                                                               runSafely(completion) {
         *                                                                                                                                   createCoroutineUnintercepted(receiver, completion) //创建了一个continuation
         *                                                                                                                                                                          public actual fun <R, T> (suspend R.() -> T).createCoroutineUnintercepted(
         *                                                                                                                                                                              receiver: R,
         *                                                                                                                                                                              completion: Continuation<T>
         *                                                                                                                                                                          ): Continuation<Unit> {
         *                                                                                                                                                                              val probeCompletion = probeCoroutineCreated(completion)
         *                                                                                                                                                                              return if (this is BaseContinuationImpl)
         *                                                                                                                                                                                  // this就是(suspend () -> T)，会被编译成SuspendLambda子类， SuspendLambda又是 BaseContinuationImpl 的实现类，则执行 create() 方法创建协程载体:
         *                                                                                                                                                                                  create(receiver, probeCompletion) // >>>> 调用的BaseContinuationImpl#create,但这是一个空实现，具体的实现在他处。跳转到“launch反编译”继续浏览。
         *
         *                                                                                                                                                                              else {
         *                                                                                                                                                                                  createCoroutineFromSuspendFunction(probeCompletion) {
         *                                                                                                                                                                                      (this as Function2<R, Continuation<T>, Any?>).invoke(receiver, it)
         *                                                                                                                                                                                  }
         *                                                                                                                                                                              }
         *                                                                                                                                                                          }
         *                                                                                                                                   .intercepted() // 添加拦截器。（线程调度的关键）
         *                                                                                                                                   .resumeCancellableWith(Result.success(Unit), onCancellation) // 最终调用 BaseContinuationImpl.resumeWith(result)：
         *                                                                                                                                                                      internal abstract class BaseContinuationImpl(
         *                                                                                                                                                                              // This is `public val` so that it is private on JVM and cannot be modified by untrusted code, yet
         *                                                                                                                                                                              // it has a public getter (since even untrusted code is allowed to inspect its call stack).
         *                                                                                                                                                                              public val completion: Continuation<Any?>?
         *                                                                                                                                                                          ) : Continuation<Any?>, CoroutineStackFrame, Serializable {
         *                                                                                                                                                                              // This implementation is final. This fact is used to unroll resumeWith recursion.
         *                                                                                                                                                                              public final override fun resumeWith(result: Result<Any?>) {
         *                                                                                                                                                                                  // This loop unrolls recursion in current.resumeWith(param) to make saner and shorter stack traces on resume
         *                                                                                                                                                                                  var current = this
         *                                                                                                                                                                                  var param = result
         *                                                                                                                                                                                  while (true) {
         *                                                                                                                                                                                      // Invoke "resume" debug probe on every resumed continuation, so that a debugging library infrastructure
         *                                                                                                                                                                                      // can precisely track what part of suspended callstack was already resumed
         *                                                                                                                                                                                      probeCoroutineResumed(current)
         *                                                                                                                                                                                      with(current) {
         *                                                                                                                                                                                          val completion = completion!! // fail fast when trying to resume continuation without completion
         *                                                                                                                                                                                          val outcome: Result<Any?> =
         *                                                                                                                                                                                              try {
         *                                                                                                                                                                                                  val outcome = invokeSuspend(param)
         *                                                                                                                                                                                                  if (outcome === COROUTINE_SUSPENDED) return // 如果已经挂起则提前结束
         *                                                                                                                                                                                                  Result.success(outcome)
         *                                                                                                                                                                                              } catch (exception: Throwable) {
         *                                                                                                                                                                                                  Result.failure(exception)
         *                                                                                                                                                                                              }
         *                                                                                                                                                                                          //当invokeSuspend方法没有返回COROUTINE_SUSPENDED，那么当前状态机流转结束，即当前suspend方法执行完毕，释放拦截
         *                                                                                                                                                                                          releaseIntercepted() // this state machine instance is terminating
         *                                                                                                                                                                                          if (completion is BaseContinuationImpl) {
         *                                                                                                                                                                                              // unrolling recursion via loop
         *                                                                                                                                                                                              // 如果 completion 是 BaseContinuationImpl，内部还有suspend方法，则会进入循环递归
         *                                                                                                                                                                                              current = completion
         *                                                                                                                                                                                              param = outcome
         *                                                                                                                                                                                          } else {
         *                                                                                                                                                                                              // top-level completion reached -- invoke and return
         *                                                                                                                                                                                              // 否则是最顶层的completion，则会调用resumeWith恢复上一层并且return
         *                                                                                                                                                                                              // 这里实际调用的是其父类 AbstractCoroutine 的 resumeWith 方法
         *                                                                                                                                                                                              completion.resumeWith(outcome)
         *                                                                                                                                                                                              return
         *                                                                                                                                                                                          }
         *                                                                                                                                                                                      }
         *                                                                                                                                                                                  }
         *                                                                                                                                                                              }
         *                                                                                                                               }
         *
         *                                                          ATOMIC -> block.startCoroutine(receiver, completion)
         *                                                          UNDISPATCHED -> block.startCoroutineUndispatched(receiver, completion)
         *                                                          LAZY -> Unit // will start lazily
         *                                                      }
         *                      }
         *
         *     return coroutine
         * }
         *
         *
         * fun main(){
         *     GlobalScope.launch {
         *         println("start")
         *         println(getName())
         *         println("stop")
         *     }
         *     Thread.sleep(4000)
         * }
         *
         * launch反编译后：
         *反编译后增加了 CoroutineScope，CoroutineContext，CoroutineStart，Function2，3，Object等参数，这些都是kotlin编译器帮我们做了。
         * 这里就是最顶层的completion处理协程挂起与恢复的地方。 这里一旦挂起/恢复了，那么说明整个协程挂起/恢复了。
         *
         *    public static final void main() {
         *       BuildersKt.launch$default((CoroutineScope)GlobalScope.INSTANCE, (CoroutineContext)null, (CoroutineStart)null, (Function2)(new Function2((Continuation)null) {
         *          int label;
         *
         *          @Nullable
         *          public final Object invokeSuspend(@NotNull Object $result) { // 这里执行了launch尾缀闭包的内容，不过已经分片段用状态机管理了。
         *             Object var3 = IntrinsicsKt.getCOROUTINE_SUSPENDED();
         *             Object var10000;
         *             String var2;
         *             switch (this.label) {
         *                case 0:
         *                   ResultKt.throwOnFailure($result);
         *                   var2 = "start";
         *                   System.out.println(var2);
         *                   this.label = 1;
         *                   var10000 = CoroutineTestKt.getName(this);
         *                   if (var10000 == var3) {
         *                      return var3;
         *                   }
         *                   break;
         *                case 1:
         *                   ResultKt.throwOnFailure($result);
         *                   var10000 = $result;
         *                   break;
         *                default:
         *                   throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
         *             }
         *
         *             Object var4 = var10000;
         *             System.out.println(var4);
         *             var2 = "stop";
         *             System.out.println(var2);
         *             return Unit.INSTANCE;
         *          }
         *
         *          @NotNull
         *          public final Continuation create(@Nullable Object value, @NotNull Continuation completion) { // 这里就是上文“>>>> ”处create的实际实现
         *             Intrinsics.checkNotNullParameter(completion, "completion");
         *             Function2 var3 = new <anonymous constructor>(completion);
         *             return var3; // 返回Function2（也是Continuation）
         *          }
         *
         *          public final Object invoke(Object var1, Object var2) {
         *             return ((<undefinedtype>)this.create(var1, (Continuation)var2)).invokeSuspend(Unit.INSTANCE); // 链式调用
         *          }
         *       }), 3, (Object)null);
         *       Thread.sleep(4000L);
         *    }
         *
        * 8、挂起的本质？内部具体实现
        *   挂起的本质是函数return；恢复的本质是回调resume。
        *   以如下代码为例：
        *   suspend fun getName():String{
        *       //片段1
        *       println("delay 1")
        *       delay(1000)
        *       //片段2
        *       println("delay 2")
        *       delay(2000)
        *       //片段3
        *       return "gf"
        *   }
        *   编译后变成如下样子：
        *
        * public static final Object getName(@NotNull Continuation var0) { // 编译器会隐式传入一个Continuation参数，这就是为什么suspend函数需要在协程内部调用，因为只有协程内部才有Continuation。
         *      //说明：>>表示挂起的流程；<<表示恢复的流程
         *     Object $continuation;
        *     label27: {
        *        if (var0 instanceof <undefinedtype>) {
        *           $continuation = (<undefinedtype>)var0;
        *           if ((((<undefinedtype>)$continuation).label & Integer.MIN_VALUE) != 0) {
        *              ((<undefinedtype>)$continuation).label -= Integer.MIN_VALUE;
        *              break label27;
        *           }
        *        }
                 //>> 每个suspend函数内部会新建一个ContinuationImpl（:BaseContinuationImpl:Continuation）
        *        $continuation = new ContinuationImpl(var0) {
        *           Object result;
        *           int label; // 最开始的时候label=0

        *           //<< delay调用resumeWith进而调用其父Continuation#invokeSuspend（调用delay时传进去了$continuation）。
        *           // resumeWith由具有挂起功能suspend函数的实现者调用。比如delay库函数，
        *           // 或者自行实现“挂起-恢复”的话会调用suspendCoroutine，然后在适当的时机（如底层回调上来）调用resumeWith
        *           @Nullable
        *           public final Object invokeSuspend(@NotNull Object $result) { //<< result是resumeWith(result)传下来的。具体到本例是delay中传过来的
        *              this.result = $result;
        *              this.label |= Integer.MIN_VALUE;
        *              return CoroutineTestKt.getName(this); // 此处是第2次（后面还有第3次）调用getName
        *           }
        *        };
        *     }

        *     Object $result = ((<undefinedtype>)$continuation).result; //result在invokeSuspend里面赋值
        *     Object var4 = IntrinsicsKt.getCOROUTINE_SUSPENDED(); // 常量COROUTINE_SUSPENDED
        *     String var1;
        *     switch (((<undefinedtype>)$continuation).label) { //label初始值为0,后续在invokeSuspend里面赋值
        *        case 0: //>> 第一次调用getName会走到这里
        *           ResultKt.throwOnFailure($result);
        *           var1 = "delay 1";
        *           System.out.println(var1); // >> 打印"delay 1"（注意我们虽然会多次重入getName,但"delay 1"只打印了一次，原因就是编译器改变了我们的代码结构，将我们的执行体分割成了多个部分，每次执行一个部分，不会重复执行前面的）
        *           ((<undefinedtype>)$continuation).label = 1; //>> 改变状态标志，这样下次重入时状态机会执行另外的分支
        *           if (DelayKt.delay(1000L, (Continuation)$continuation) == var4) { //>> delay返回常量COROUTINE_SUSPENDED表示delay已挂起。
         *                                                                          // 注意这里把本函数的$continuation传给了下层suspend函数，
         *                                                                          // 这样下层会在适当的时机调用$continuation#invokeSuspend
        *              //>> getName同样返回COROUTINE_SUSPENDED。
        *              // 此时我们的getName调用已经在delay(1)处return了。虽然getName返回了，但是getName的调用者并不会执行getName后面的代码，
         *             // 因为getName的调用者也是一个suspend它就像getName挂起在delay处一样挂起在getName处了——它也在getName处返回COROUTINE_SUSPENDED。
         *             // 最终的整体表现就是执行流程在getName调用挂delay处挂起了。
         *             // 恢复需要等到delay的resumeWith执行，从而执行$continuation#invokeSuspend，进而重新调用getName。此时由于label已经改为1所以会走到case1.
        *              return var4;
        *           }
        *           break;
        *        case 1: // << 第二次走到了这里
        *           ResultKt.throwOnFailure($result);
        *           break;
        *        case 2: // << 第三次走到了这里
        *           ResultKt.throwOnFailure($result);
        *           return "gf"; // << 后面已经没有其他代码片段了，整个getName全部执行完成，返回最终结果。
        *        default:
        *           throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        *     }

        *     var1 = "delay 2";
        *     System.out.println(var1); // << 执行第二个代码片段
        *     ((<undefinedtype>)$continuation).label = 2; // << 同样的修改label,下次重入时走case2分支
        *     if (DelayKt.delay(2000L, (Continuation)$continuation) == var4) { // << 执行第二个delay,同样会返回COROUTINE_SUSPENDED
        *        return var4; //同样的继续层层返回COROUTINE_SUSPENDED，表现为挂起
        *     } else {
        *        return "gf";
        *     }
        *  }
         *
        *   总结：协程挂起的本质是函数return,恢复的本质是底层的suspend函数适当时机回调resume。具体的实现手段是“Continuation+状态机”。
         *   编译器会将suspend函数以其内部的suspend函数调用点为界限分割成几个片段，通过Continuation结合状态机管理这些片段的执行。
         *   编译器会为每个suspend方法添加一个（上级的）Continuation参数，并会在每个suspend函数内部创建一个Continuation，以上级Continuation为参数，这样所有的Continuation就链接在了一起。
         *   当某个片段执行到其内的suspend函数时，若该函数返回了常量COROUTINE_SUSPENDED则表示已挂起，函数从该片段return，返回值同样是COROUTINE_SUSPENDED以告知其上层，其上层会执行同样的流程，整体就表现为挂起。
         *   当适当的时机底下的suspend函数resumeWith回调触发，进而导致本suspend函数重新被调用,由于之前记录了状态，状态机这次会执行下一个片段，整体表现为从挂起处恢复。
        * */
        // !! 如何连接非协程和协程的世界？
        // runBlocking：会阻塞调用线程，不可取
        // 使用CoroutineScope#launch（Global的生命周期不符合，使用自定义的）
        createSession("login", account, passwd, listener){
            // 这里已经是协程

            println("=> login")
            // req是挂起函数。
            // !! 内部如何实现挂起？传统的挂起函数是调用了系统的挂起函数如delay()，但是我们没有，需要自己实现挂起行为。
            // !! req需要支持并发，如何支持？
            val ret = req("ReqA", "xx"){
                println("-> ReqA")
                //！！ 如何以自然便捷的方式注册onRsp等回调？
                // 注册回调
                onRsp{msg, content->
                    // ！！如何在此处控制流程跳转？？
                    when (msg){
                        "MsgA"-> {
                            println("<- ReqA: $msg $content")
                            reportFailed(1, "failed") // 会话已结束
                        }
                        "MsgB"-> {
                            println("<- ReqA: $msg $content")
//                            reportSuccess("login success")
                        }
                    }
                    // 1、正常匹配完所有响应。用户调用finish或者框架自动finish（但需要提供能够阻止的机制）
                    // 2、异常需提前上报失败。reportFailed(errorCode, errorInfo)，下层需结束会话。
                    // 3、已匹配完备但仍可以等待更多
                }

                // 注册超时回调
                onTimeout{
                    println("<- ReqA timeout!")
                }
            }

            println("================ret=$ret")

            req("ReqB", "xx"){
                println("-> ReqB")
                // 注册回调
                onRsp{msg, content->
                    when (msg){
                        "MsgC"-> {
                            println("<- ReqB: $msg $content")
//                            reportFailed(1, "failed") // 会话已结束
                        }
                        "MsgD"-> {
                            println("<- ReqB: $msg $content")
                        }
                    }
                }
            }

            reportSuccess("localhost")

            println("<= login")

        }
    }

}


fun main(){
    println("start")
    LoginManager().login("gf", "666", object :ResultListener{
        override fun onSuccess(result: Any?) {
            println("login success $result")
        }

        override fun onFailed(errorCode: Int, errorInfo: Any?) {
            println("login failed $errorCode, $errorInfo")
        }

        override fun onTimeout() {
            println("login timeout")
        }
    })
    Thread.sleep(3000)
    println("stop")
}