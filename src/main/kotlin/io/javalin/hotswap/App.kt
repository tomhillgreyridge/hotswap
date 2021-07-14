package io.javalin.hotswap

import io.javalin.Javalin

object App {
  private var server = Javalin.create()

  fun start(){
    server.start(7777)
    server.get("/",TestClass::home)
  }

  fun reload(){
    println("Hurray - our reloading logic is being called")
    server.stop()
    println("Boo - we never get here as the main function terminates after stop()")
    server = Javalin.create()
    start()
  }

}

//this is where the big problem comes from.  Obviously, this main method waits until the start method is complete and
//then terminates.  Unfortunately, stopping the Javalin server causes the method to terminate and so we never get the
//reload!
fun main(args: Array<String>){
  App.start()
}