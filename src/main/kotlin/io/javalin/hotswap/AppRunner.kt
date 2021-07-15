package io.javalin.hotswap

object AppRunner {
  fun start(){
    App.start()

    while(true){
      Thread.sleep(200)
    }
  }

  fun reload(){
    App.start()
  }
}