package io.javalin.hotswap

import io.javalin.Javalin

object App {
  private lateinit var server: Javalin

  fun start(){
    if (this::server.isInitialized){
      server.stop()
    }

    server = Javalin.create()
    server.start(7777)
    server.get("/",TestClass::home)
    server.get("/new",TestClass::home)
  }

  fun reload(){
    AppRunner.reload()
  }
}

//this is where the big problem comes from.  Obviously, this main method waits until the start method is complete and
//then terminates.  Unfortunately, stopping the Javalin server causes the method to terminate and so we never get the
//reload!
fun main(args: Array<String>){
  AppRunner.start()
}